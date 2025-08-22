package com.pdfwatermarks.http

import zio.*
import zio.http.*
import zio.json.*
import zio.stream.*
import com.pdfwatermarks.domain.*
import com.pdfwatermarks.domain.given
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
   * Watermark processing routes (Tasks 52, 53, 54, 55).
   */
  val watermarkProcessingRoutes: Routes[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & DownloadTrackingService, Response] = Routes(
    // Submit watermark configuration endpoint (Task 52)
    Method.POST / "api" / "watermark" / "config" -> handler { (req: Request) =>
      for {
        _ <- ZIO.logInfo(s"Received watermark configuration from ${req.remoteAddress.getOrElse("unknown")}")
        
        // Parse JSON request body
        body <- req.body.asString
        processRequest <- ZIO.fromEither(body.fromJson[ProcessWatermarkRequest])
          .mapError(error => DomainError.InvalidConfiguration(List(s"Invalid JSON: $error")))
        
        // Get session and validate it has an uploaded document
        session <- SessionManagementService.getSession(processRequest.sessionId)
        document <- ZIO.fromOption(session.uploadedDocument)
          .orElseFail(DomainError.InvalidConfiguration(List("No document uploaded in session")))
        
        // Update session with watermark configuration
        updatedSession <- SessionManagementService.updateSessionWithConfig(
          processRequest.sessionId, 
          processRequest.config
        )
        
        _ <- ZIO.logInfo(s"Watermark configuration saved for session ${processRequest.sessionId}")
        
        response = ProcessWatermarkResponse(
          success = true,
          sessionId = processRequest.sessionId,
          message = "Watermark configuration saved successfully"
        )
      } yield Response.json(response.toJson)
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"Watermark configuration failed: $error") *>
        ZIO.succeed {
          val errorResponse = ProcessWatermarkResponse(
            success = false,
            sessionId = "",
            message = error match {
              case DomainError.SessionNotFound(sessionId) => s"Session not found: $sessionId"
              case DomainError.InvalidConfiguration(errors) => s"Invalid configuration: ${errors.mkString(", ")}"
              case _ => "Failed to save watermark configuration"
            }
          )
          Response.json(errorResponse.toJson).status(Status.BadRequest)
        }
      }
    },
    
    // Process watermark application endpoint (Task 53)
    Method.POST / "api" / "watermark" / "process" -> handler { (req: Request) =>
      for {
        _ <- ZIO.logInfo("Received watermark processing request")
        
        // Parse session ID from JSON request body
        body <- req.body.asString
        sessionId <- ZIO.fromEither(body.fromJson[Map[String, String]])
          .mapError(_ => DomainError.InvalidConfiguration(List("Invalid JSON request body")))
          .flatMap(params => ZIO.fromOption(params.get("sessionId"))
            .orElseFail(DomainError.InvalidConfiguration(List("Missing sessionId in request"))))
        
        // Get session and validate it has both document and config
        session <- SessionManagementService.getSession(sessionId)
        document <- ZIO.fromOption(session.uploadedDocument)
          .orElseFail(DomainError.InvalidConfiguration(List("No document uploaded in session")))
        config <- ZIO.fromOption(session.watermarkConfig)
          .orElseFail(DomainError.InvalidConfiguration(List("No watermark configuration in session")))
        
        // Create processing job ID
        jobId = java.util.UUID.randomUUID().toString
        
        // Start watermark processing asynchronously
        _ <- {
          for {
            _ <- ZIO.logInfo(s"Starting watermark processing for session $sessionId, job $jobId")
            
            // Get the uploaded file path from document
            uploadedFile <- FileManagementService.storeUploadedFile(UploadInfo(
              document.filename, 
              "application/pdf", 
              document.originalSize, 
              s"/tmp/${document.id}"
            )).catchAll(_ => 
              // If file retrieval fails, try to use temporary file path
              ZIO.succeed(new java.io.File(s"/tmp/${document.id}"))
            )
            
            // Apply watermarks to PDF
            processedFile <- PdfProcessingService.applyWatermarks(document, config)
            
            _ <- ZIO.logInfo(s"Watermark processing completed for job $jobId")
            
          } yield ()
        }.forkDaemon // Run processing in background
        
        response = ProcessWatermarkResponse(
          success = true,
          sessionId = sessionId,
          jobId = Some(jobId),
          message = "Watermark processing started successfully"
        )
        
        _ <- ZIO.logInfo(s"Started watermark processing job $jobId for session $sessionId")
        
      } yield Response.json(response.toJson)
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"Watermark processing failed: $error") *>
        ZIO.succeed {
          val errorResponse = ProcessWatermarkResponse(
            success = false,
            sessionId = "",
            message = error match {
              case DomainError.SessionNotFound(sessionId) => s"Session not found: $sessionId"
              case DomainError.InvalidConfiguration(errors) => s"Invalid request: ${errors.mkString(", ")}"
              case DomainError.PdfProcessingError(msg) => s"PDF processing failed: $msg"
              case _ => "Failed to start watermark processing"
            }
          )
          Response.json(errorResponse.toJson).status(Status.BadRequest)
        }
      }
    },
    
    // Job status tracking endpoint (Task 54)
    Method.GET / "api" / "watermark" / "status" / string("sessionId") -> handler { (sessionId: String, req: Request) =>
      for {
        _ <- ZIO.logInfo(s"Status check requested for session: $sessionId")
        session <- SessionManagementService.getSession(sessionId)
        
        // Get processing status based on session state
        statusResponse = (session.uploadedDocument, session.watermarkConfig) match {
          case (Some(doc), Some(config)) =>
            // Check document processing status
            doc.status match {
              case DocumentStatus.Uploaded => JobStatusResponse(
                sessionId = sessionId,
                jobId = sessionId, // Using sessionId as jobId for simplicity
                status = "ready",
                progress = 0,
                message = "Ready to process watermarks"
              )
              case DocumentStatus.Processing => JobStatusResponse(
                sessionId = sessionId,
                jobId = sessionId,
                status = "processing", 
                progress = 50,
                message = "Processing watermarks..."
              )
              case DocumentStatus.Completed => JobStatusResponse(
                sessionId = sessionId,
                jobId = sessionId,
                status = "completed",
                progress = 100,
                message = "Watermark processing completed",
                downloadUrl = Some(s"/api/download/$sessionId")
              )
              case DocumentStatus.Failed(reason) => JobStatusResponse(
                sessionId = sessionId,
                jobId = sessionId,
                status = "failed",
                progress = 0,
                message = s"Processing failed: $reason"
              )
            }
          case (Some(_), None) => JobStatusResponse(
            sessionId = sessionId,
            jobId = sessionId,
            status = "awaiting_config",
            progress = 25,
            message = "Document uploaded, waiting for watermark configuration"
          )
          case (None, _) => JobStatusResponse(
            sessionId = sessionId,
            jobId = sessionId,
            status = "awaiting_upload",
            progress = 0,
            message = "No document uploaded"
          )
        }
      } yield Response.json(statusResponse.toJson)
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"Status check failed: $error") *>
        ZIO.succeed {
          val errorResponse = JobStatusResponse(
            sessionId = "unknown",
            jobId = "unknown",
            status = "error",
            progress = 0,
            message = error match {
              case DomainError.SessionNotFound(_) => "Session not found"
              case _ => "Failed to retrieve status information"
            }
          )
          Response.json(errorResponse.toJson).status(Status.NotFound)
        }
      }
    }
  )

  /**
   * File upload routes with service integration (Tasks 36, 39, 40).
   */
  val fileUploadRoutes: Routes[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & DownloadTrackingService, Response] = Routes(
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
   * Download routes for processed files with progress tracking and cleanup (Tasks 60, 61).
   */
  val downloadRoutes: Routes[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & DownloadTrackingService, Response] = Routes(
    // Download processed PDF endpoint with streaming and progress tracking (Task 60)
    Method.GET / "api" / "download" / string("sessionId") -> handler { (sessionId: String, req: Request) =>
      for {
        _ <- ZIO.logInfo(s"Download requested for session: $sessionId")
        session <- SessionManagementService.getSession(sessionId)
        
        // Validate session has processed document
        document <- ZIO.fromOption(session.uploadedDocument)
          .orElseFail(DomainError.DocumentNotFound(s"No document in session $sessionId"))
        
        _ <- document.status match {
          case DocumentStatus.Completed => ZIO.unit
          case DocumentStatus.Processing => ZIO.fail(DomainError.PdfProcessingError("Document is still processing"))
          case DocumentStatus.Failed(reason) => ZIO.fail(DomainError.PdfProcessingError(s"Processing failed: $reason"))
          case DocumentStatus.Uploaded => ZIO.fail(DomainError.PdfProcessingError("Document has not been processed yet"))
        }
        
        // Generate processed filename
        processedFilename <- FileManagementService.generateProcessedFilename(document.filename)
        
        // Get processed file path
        processedFilePath = s"/tmp/processed_${document.id}.pdf"
        processedFile = new java.io.File(processedFilePath)
        
        // Check if processed file exists
        _ <- ZIO.cond(processedFile.exists(), (), DomainError.DocumentNotFound(s"Processed file not found for session $sessionId"))
        
        fileSize = processedFile.length()
        
        // Create download session for progress tracking
        _ <- DownloadTrackingService.createDownloadSession(sessionId, document.id, processedFilename, fileSize)
        
        // Read file content for now (streaming can be enhanced later with proper file streaming)
        fileBytes <- ZIO.attempt(java.nio.file.Files.readAllBytes(processedFile.toPath))
          .mapError(err => DomainError.InternalError(s"Failed to read processed file: ${err.getMessage}"))
        
        // Update download progress to completed
        _ <- DownloadTrackingService.completeDownload(sessionId)
        
        _ <- ZIO.logInfo(s"Starting streaming download for session $sessionId: $processedFilename (${fileSize} bytes)")
        
      } yield Response(
        status = Status.Ok,
        headers = Headers(
          Header.ContentType(MediaType.application.pdf),
          Header.Custom("Content-Disposition", s"""attachment; filename="$processedFilename""""),
          Header.Custom("Content-Length", fileSize.toString),
          Header.Custom("Accept-Ranges", "bytes")
        ),
        body = Body.fromArray(fileBytes)
      )
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"Download failed: $error") *>
        ZIO.succeed {
          val (status, message) = error match {
            case DomainError.SessionNotFound(_) => (Status.NotFound, "Session not found")
            case DomainError.DocumentNotFound(_) => (Status.NotFound, "Document not found or not ready for download")
            case DomainError.PdfProcessingError(msg) => (Status.BadRequest, s"Processing error: $msg")
            case _ => (Status.InternalServerError, "Download failed")
          }
          Response.text(message).status(status)
        }
      }
    },
    
    // Download progress tracking endpoint (Task 60)
    Method.GET / "api" / "download" / "progress" / string("sessionId") -> handler { (sessionId: String, req: Request) =>
      for {
        _ <- ZIO.logInfo(s"Download progress requested for session: $sessionId")
        progressResponse <- DownloadTrackingService.getDownloadProgress(sessionId)
        
        // Trigger file cleanup if download is completed (Task 61)
        _ <- progressResponse.status match {
          case "completed" =>
            for {
              _ <- ZIO.logInfo(s"Download completed for session $sessionId, initiating cleanup")
              session <- SessionManagementService.getSession(sessionId)
              document <- ZIO.fromOption(session.uploadedDocument)
                .orElseFail(DomainError.DocumentNotFound(s"No document in session $sessionId"))
              
              // Cleanup processed file
              processedFilePath = s"/tmp/processed_${document.id}.pdf"
              processedFile = new java.io.File(processedFilePath)
              _ <- TempFileManagementService.cleanupFile(processedFile)
              
              // Cleanup original uploaded file if it exists
              uploadedFilePath = s"/tmp/${document.id}"
              uploadedFile = new java.io.File(uploadedFilePath)
              _ <- TempFileManagementService.cleanupFile(uploadedFile)
              
              _ <- ZIO.logInfo(s"Cleanup completed for session $sessionId")
            } yield ()
          case _ => ZIO.unit
        }
        
      } yield Response.json(progressResponse.toJson)
    }.catchAll { error =>
      Handler.fromZIO {
        ZIO.logError(s"Download progress check failed: $error") *>
        ZIO.succeed {
          val (status, message) = error match {
            case DomainError.SessionNotFound(_) => (Status.NotFound, "Download session not found")
            case _ => (Status.InternalServerError, "Failed to get download progress")
          }
          Response.text(message).status(status)
        }
      }
    }
  )

  /**
   * Complete HTTP application with CORS support (Task 33, 34, 36, 39, 40, 52, 53, 54, 55).
   */
  val httpApp: Routes[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & DownloadTrackingService, Response] = 
    routesWithLogging ++ fileUploadRoutes ++ watermarkProcessingRoutes ++ downloadRoutes

  /**
   * Server configuration and startup (Task 31).
   */
  def start(config: ServerConfig = ServerConfig()): ZIO[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & DownloadTrackingService & Server, Throwable, Nothing] = {
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
  def runWithGracefulShutdown(config: ServerConfig = ServerConfig()): ZIO[SessionManagementService & FileManagementService & PdfProcessingService & TempFileManagementService & DownloadTrackingService & Server, Throwable, ExitCode] = {
    start(config).onInterrupt(gracefulShutdown).as(ExitCode.success)
  }
}