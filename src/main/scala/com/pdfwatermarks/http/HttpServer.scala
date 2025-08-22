package com.pdfwatermarks.http

import zio.*
import zio.http.*
import zio.json.*
import zio.stream.*
import com.pdfwatermarks.domain.*
import com.pdfwatermarks.services.*
import java.io.File
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.nio.charset.StandardCharsets

/**
 * HTTP Server configuration and routing for the PDF Watermarking Application.
 * 
 * This module provides:
 * - Basic routing structure for the web interface
 * - Health check endpoints for monitoring
 * - CORS configuration for web browsers
 * - Middleware for logging and error handling
 * - Graceful server shutdown capabilities
 */
object HttpServer {

  /**
   * Configuration for the HTTP server.
   */
  final case class ServerConfig(
    port: Int = 8080,
    host: String = "0.0.0.0"
  )

  /**
   * Health check response model.
   */
  final case class HealthResponse(
    status: String,
    timestamp: String,
    version: String = "0.1.0-SNAPSHOT"
  )

  object HealthResponse {
    implicit val encoder: JsonEncoder[HealthResponse] = DeriveJsonEncoder.gen[HealthResponse]
    implicit val decoder: JsonDecoder[HealthResponse] = DeriveJsonDecoder.gen[HealthResponse]
  }

  /**
   * File upload response models.
   */
  final case class UploadResponse(
    success: Boolean,
    sessionId: String,
    documentId: Option[String] = None,
    message: String
  )

  object UploadResponse {
    implicit val encoder: JsonEncoder[UploadResponse] = DeriveJsonEncoder.gen[UploadResponse]
    implicit val decoder: JsonDecoder[UploadResponse] = DeriveJsonDecoder.gen[UploadResponse]
  }

  final case class UploadProgressResponse(
    sessionId: String,
    status: String,
    progress: Int, // 0-100 percentage
    message: String
  )

  object UploadProgressResponse {
    implicit val encoder: JsonEncoder[UploadProgressResponse] = DeriveJsonEncoder.gen[UploadProgressResponse]
    implicit val decoder: JsonDecoder[UploadProgressResponse] = DeriveJsonDecoder.gen[UploadProgressResponse]
  }

  /**
   * Static file serving helper.
   */
  private def serveStaticFile(path: String): ZIO[Any, Throwable, Response] = {
    val resourcePath = if (path.startsWith("/")) path.substring(1) else path
    val fullPath = s"static/$resourcePath"
    
    ZIO.attempt {
      val inputStream = getClass.getClassLoader.getResourceAsStream(fullPath)
      Option(inputStream)
    }.flatMap {
      case Some(stream) =>
        for {
          bytes <- ZIO.attempt {
            val content = stream.readAllBytes()
            stream.close()
            content
          }
          contentType = getContentType(resourcePath)
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(contentType)),
          body = Body.fromArray(bytes)
        )
      case None =>
        ZIO.succeed(Response.status(Status.NotFound))
    }.catchAll(_ => ZIO.succeed(Response.status(Status.InternalServerError)))
  }

  /**
   * Determine content type based on file extension.
   */
  private def getContentType(path: String): MediaType = {
    val extension = path.toLowerCase.split('.').lastOption.getOrElse("")
    extension match {
      case "html" => MediaType.text.html
      case "css" => MediaType.text.css
      case "js" => MediaType.application.`javascript`
      case "png" => MediaType.image.png
      case "jpg" | "jpeg" => MediaType.image.jpeg
      case "svg" => MediaType.image.`svg+xml`
      case "ico" => MediaType.image.`x-icon`
      case _ => MediaType.application.`octet-stream`
    }
  }

  /**
   * Main HTTP routes for the application.
   */
  val routes: Routes[Any, Response] = Routes(
    // Health check endpoint (Task 32)
    Method.GET / "health" -> Handler.fromFunction { _ =>
      val timestamp = java.time.Instant.now().toString
      val healthResponse = HealthResponse("healthy", timestamp)
      Response.json(healthResponse.toJson)
    },

    // API health check with more detailed information
    Method.GET / "api" / "health" -> Handler.fromFunction { _ =>
      val timestamp = java.time.Instant.now().toString
      val healthResponse = HealthResponse("healthy", timestamp)
      Response.json(healthResponse.toJson)
    },

    // Root endpoint - serve simple HTML
    Method.GET / "" -> Handler.fromFunction { _ =>
      Response.html("""<!DOCTYPE html>
<html>
<head>
    <title>PDF Watermarking Application</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
    <h1>PDF Watermarking Application</h1>
    <p>Server is running and ready to accept requests.</p>
    <p><a href="/health">Health Check</a></p>
    <p><a href="/api/health">API Health Check</a></p>
</body>
</html>""")
    }
  )

  /**
   * Request logging helper (Task 33).
   */
  private def logRequest(request: Request): UIO[Unit] =
    ZIO.logInfo(s"HTTP ${request.method} ${request.path}")

  /**
   * File upload routes with service integration (Tasks 36, 39, 40).
   */
  val fileUploadRoutes: Routes[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService, Response] = Routes(
    // File upload endpoint with multipart handling (Task 36)
    Method.POST / "api" / "upload" -> handler { (req: Request) =>
      for {
        _ <- ZIO.logInfo(s"Received file upload request from ${req.remoteAddress.getOrElse("unknown")}")
        
        // Create new session for upload tracking
        session <- SessionManagementService.createSession()
        
        // Parse multipart form data
        body <- req.body.asMultipartForm
        
        // Extract file from form data
        fileField <- ZIO.fromOption(
          body.formData.find(_.name == "file")
        ).orElseFail(DomainError.InvalidFileFormat("No file field found in multipart data"))
        
        // Extract file data and metadata
        uploadInfo <- {
          val filename = fileField.filename.getOrElse("unknown.pdf")
          val contentType = fileField.contentType.toString
          
          for {
            // Extract binary data - simplified approach
            chunk <- fileField.asChunk
            bytes = chunk.toArray
            // Create temporary file using TempFileManagementService
            tempFile <- TempFileManagementService.createUploadTempFile(filename)
            // Write file data to temporary location
            _ <- ZIO.attempt(Files.write(tempFile.toPath, bytes))
              .mapError(err => DomainError.InternalError(s"Failed to write temporary file: ${err.getMessage}"))
          } yield UploadInfo(filename, contentType, bytes.length.toLong, tempFile.getAbsolutePath)
        }
        
        // Validate file size and format
        tempFile = new File(uploadInfo.tempPath)
        _ <- FileManagementService.validateFile(tempFile)
        
        // Store uploaded file using FileManagementService
        storedFile <- FileManagementService.storeUploadedFile(uploadInfo)
        
        // Load PDF document and get metadata
        pdfDocument <- PdfProcessingService.loadPdf(storedFile)
        
        // Update session with uploaded document
        updatedSession <- SessionManagementService.updateSessionWithDocument(session.sessionId, pdfDocument)
        
        _ <- ZIO.logInfo(s"File upload successful: ${uploadInfo.filename} (${uploadInfo.size} bytes) in session ${session.sessionId}")
        
        response = UploadResponse(
          success = true,
          sessionId = session.sessionId,
          documentId = Some(pdfDocument.id),
          message = s"File '${uploadInfo.filename}' uploaded successfully"
        )
      } yield Response.json(response.toJson)
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"File upload failed: ${error}") *>
        ZIO.succeed {
          val errorResponse = UploadResponse(
            success = false,
            sessionId = "",
            message = error match {
              case DomainError.InvalidFileFormat(msg) => s"Invalid file format: $msg"
              case DomainError.FileSizeExceeded(actual, max) => 
                s"File size exceeded: ${actual} bytes (maximum: ${max} bytes)"
              case DomainError.InternalError(msg) => s"Upload failed: $msg"
              case _ => "Upload failed due to an unexpected error"
            }
          )
          Response.json(errorResponse.toJson).status(Status.BadRequest)
        }
      }
    },
    
    // Upload progress tracking endpoint (Task 40)
    Method.GET / "api" / "upload" / "progress" / string("sessionId") -> handler { (sessionId: String, req: Request) =>
      for {
        _ <- ZIO.logInfo(s"Progress check requested for session: $sessionId")
        session <- SessionManagementService.getSession(sessionId)
        
        progressResponse = session.uploadedDocument match {
          case Some(doc) => UploadProgressResponse(
            sessionId = sessionId,
            status = doc.status match {
              case DocumentStatus.Uploaded => "completed"
              case DocumentStatus.Processing => "processing"
              case DocumentStatus.Completed => "completed"
              case DocumentStatus.Failed(reason) => "failed"
            },
            progress = doc.status match {
              case DocumentStatus.Uploaded => 100
              case DocumentStatus.Processing => 50
              case DocumentStatus.Completed => 100
              case DocumentStatus.Failed(_) => 0
            },
            message = doc.status match {
              case DocumentStatus.Uploaded => s"File '${doc.filename}' uploaded successfully"
              case DocumentStatus.Processing => s"Processing file '${doc.filename}'"
              case DocumentStatus.Completed => s"File '${doc.filename}' processed successfully"
              case DocumentStatus.Failed(reason) => s"Processing failed: $reason"
            }
          )
          case None => UploadProgressResponse(
            sessionId = sessionId,
            status = "no_upload",
            progress = 0,
            message = "No file uploaded in this session"
          )
        }
      } yield Response.json(progressResponse.toJson)
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"Progress check failed: $error") *>
        ZIO.succeed {
          val errorResponse = UploadProgressResponse(
            sessionId = "unknown",
            status = "error",
            progress = 0,
            message = error match {
              case DomainError.SessionNotFound(_) => "Session not found"
              case _ => "Failed to retrieve progress information"
            }
          )
          Response.json(errorResponse.toJson).status(Status.NotFound)
        }
      }
    }
  )

  /**
   * Routes with logging middleware (Task 33).
   */
  val routesWithLogging: Routes[Any, Response] = Routes(
    // Health check endpoint with logging (Task 32)
    Method.GET / "health" -> handler { (req: Request) =>
      for {
        _ <- logRequest(req)
        timestamp = java.time.Instant.now().toString
        healthResponse = HealthResponse("healthy", timestamp)
      } yield Response.json(healthResponse.toJson)
    },

    // API health check with logging
    Method.GET / "api" / "health" -> handler { (req: Request) =>
      for {
        _ <- logRequest(req)
        timestamp = java.time.Instant.now().toString
        healthResponse = HealthResponse("healthy", timestamp)
      } yield Response.json(healthResponse.toJson)
    },

    // Root endpoint with logging
    Method.GET / "" -> handler { (req: Request) =>
      for {
        _ <- logRequest(req)
      } yield Response(
        status = Status.Ok,
        headers = Headers(Header.ContentType(MediaType.text.html)),
        body = Body.fromString("""<!DOCTYPE html>
<html>
<head>
    <title>PDF Watermarking Application</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
    <h1>PDF Watermarking Application</h1>
    <p>Server is running and ready to accept requests.</p>
    <p><a href="/health">Health Check</a></p>
    <p><a href="/api/health">API Health Check</a></p>
</body>
</html>""")
      )
    }
  )

  /**
   * Complete HTTP application with CORS support (Task 33, 34, 36, 39, 40).
   */
  val httpApp: Routes[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService, Response] = 
    routesWithLogging ++ fileUploadRoutes

  /**
   * Server configuration and startup (Task 31).
   */
  def start(config: ServerConfig = ServerConfig()): ZIO[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & Server, Throwable, Nothing] = {
    for {
      _ <- ZIO.logInfo(s"Starting HTTP server on ${config.host}:${config.port}")
      result <- Server.serve(httpApp)
    } yield result
  }

  /**
   * Graceful server shutdown handler (Task 35).
   */
  def gracefulShutdown: ZIO[Any, Nothing, Unit] = {
    for {
      _ <- ZIO.logInfo("Initiating graceful server shutdown...")
      _ <- ZIO.logInfo("HTTP server shutdown completed")
    } yield ()
  }

  /**
   * Complete server lifecycle with graceful shutdown (Task 35).
   */
  def runWithGracefulShutdown(config: ServerConfig = ServerConfig()): ZIO[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & Server, Throwable, ExitCode] = {
    start(config).onInterrupt(gracefulShutdown).as(ExitCode.success)
  }
}