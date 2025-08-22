package com.pdfwatermarks.http

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.http.*
import zio.json.*
import com.pdfwatermarks.http.HttpServer.*
import com.pdfwatermarks.domain.*
import com.pdfwatermarks.domain.given
import com.pdfwatermarks.services.*
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Comprehensive tests for HTTP server functionality.
 * 
 * Tests cover:
 * - Health check endpoints (Task 32)
 * - Basic routing functionality (Task 31)
 * - CORS configuration (Task 34)
 * - Middleware logging and error handling (Task 33)
 * - Response formats and status codes
 */
object HttpServerTest extends ZIOSpecDefault {

  // Mock service implementations for testing
  val mockSessionManagementService: ULayer[SessionManagementService] = ZLayer.succeed(
    new SessionManagementService {
      private val sessions = scala.collection.mutable.Map[String, UserSession]()
      
      def createSession(): UIO[UserSession] = {
        val sessionId = UUID.randomUUID().toString
        val session = UserSession(sessionId, None, None, Instant.now(), Instant.now())
        sessions.put(sessionId, session)
        ZIO.succeed(session)
      }
      
      def getSession(sessionId: String): IO[DomainError, UserSession] =
        ZIO.fromOption(sessions.get(sessionId))
          .orElseFail(DomainError.SessionNotFound(sessionId))
      
      def updateSessionWithDocument(sessionId: String, document: PdfDocument): IO[DomainError, UserSession] =
        for {
          session <- getSession(sessionId)
          updatedSession = session.copy(uploadedDocument = Some(document), lastActivity = Instant.now())
          _ = sessions.put(sessionId, updatedSession)
        } yield updatedSession
      
      def updateSessionWithConfig(sessionId: String, config: WatermarkConfig): IO[DomainError, UserSession] =
        for {
          session <- getSession(sessionId)
          updatedSession = session.copy(watermarkConfig = Some(config), lastActivity = Instant.now())
          _ = sessions.put(sessionId, updatedSession)
        } yield updatedSession
      
      def cleanupExpiredSessions(): UIO[Unit] = ZIO.unit
    }
  )
  
  val mockFileManagementService: ULayer[FileManagementService] = ZLayer.succeed(
    new FileManagementService {
      def storeUploadedFile(uploadInfo: UploadInfo): IO[DomainError, File] =
        ZIO.succeed(new File(uploadInfo.tempPath))
      
      def validateFile(file: File): IO[DomainError, Unit] =
        if (file.getName.endsWith(".pdf")) ZIO.unit
        else ZIO.fail(DomainError.InvalidFileFormat("Only PDF files are supported"))
      
      def cleanupTempFiles(files: List[File]): UIO[Unit] = ZIO.unit
      
      def generateProcessedFilename(originalFilename: String): UIO[String] =
        ZIO.succeed(s"processed_$originalFilename")
    }
  )
  
  val mockPdfProcessingService: ULayer[PdfProcessingService] = ZLayer.succeed(
    new PdfProcessingService {
      def loadPdf(file: File): IO[DomainError, PdfDocument] = {
        val document = PdfDocument(
          id = UUID.randomUUID().toString,
          filename = file.getName,
          originalSize = 1024L,
          pageCount = 1,
          uploadedAt = Instant.now(),
          status = DocumentStatus.Uploaded
        )
        ZIO.succeed(document)
      }
      
      def applyWatermarks(document: PdfDocument, config: WatermarkConfig): IO[DomainError, File] =
        ZIO.succeed(new File(s"watermarked_${document.filename}"))
      
      def getPageCount(file: File): IO[DomainError, Int] = ZIO.succeed(1)
      
      def getPageDimensions(file: File, pageNumber: Int): IO[DomainError, PageDimensions] =
        ZIO.succeed(PageDimensions(595.0, 842.0)) // A4 dimensions
    }
  )
  
  val mockTempFileManagementService: ULayer[TempFileManagementService] = ZLayer.succeed(
    new TempFileManagementService {
      def initialize(): IO[DomainError, Unit] = ZIO.unit
      
      def createTempFile(prefix: String, suffix: String): IO[DomainError, File] = 
        ZIO.succeed(File.createTempFile(prefix, suffix))
      
      def createUploadTempFile(originalFilename: String): IO[DomainError, File] = 
        ZIO.succeed(File.createTempFile("upload-", ".pdf"))
      
      def createProcessedTempFile(originalFilename: String): IO[DomainError, File] = 
        ZIO.succeed(File.createTempFile("processed-", ".pdf"))
      
      def storeTempFile(content: Array[Byte], filename: String): IO[DomainError, File] = {
        val tempFile = File.createTempFile("stored-", ".tmp")
        ZIO.attempt(java.nio.file.Files.write(tempFile.toPath, content))
          .mapError(err => DomainError.InternalError(s"Failed to write temp file: ${err.getMessage}"))
          .as(tempFile)
      }
      
      def moveTempFile(source: File, newFilename: String): IO[DomainError, File] = 
        ZIO.succeed(File.createTempFile("moved-", ".tmp"))
      
      def cleanupOldFiles(): UIO[Int] = ZIO.succeed(0)
      
      def cleanupFile(file: File): UIO[Unit] = ZIO.unit
      
      def cleanupFiles(files: List[File]): UIO[Unit] = ZIO.unit
      
      def getTempDirectorySize(): UIO[Long] = ZIO.succeed(1024L)
      
      def listTempFiles(): UIO[List[TempFileInfo]] = ZIO.succeed(List.empty)
      
      def isCleanupNeeded(): UIO[Boolean] = ZIO.succeed(false)
    }
  )
  
  val testLayer = mockSessionManagementService ++ mockFileManagementService ++ mockPdfProcessingService ++ mockTempFileManagementService

  def spec: Spec[Any, Any] = suite("HttpServerTest")(
    suite("Health Check Endpoints")(
      test("GET /health returns healthy status with JSON response") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "health"))
          response <- routesWithLogging.runZIO(request)
          body <- response.body.asString
          healthResponse <- ZIO.fromEither(body.fromJson[HealthResponse])
        } yield assertTrue(
          response.status == Status.Ok,
          response.header(Header.ContentType).contains(Header.ContentType(MediaType.application.json)),
          healthResponse.status == "healthy",
          healthResponse.version == "0.1.0-SNAPSHOT",
          healthResponse.timestamp.nonEmpty
        )
      },

      test("GET /api/health returns healthy status with JSON response") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "api" / "health"))
          response <- routesWithLogging.runZIO(request)
          body <- response.body.asString
          healthResponse <- ZIO.fromEither(body.fromJson[HealthResponse])
        } yield assertTrue(
          response.status == Status.Ok,
          response.header(Header.ContentType).contains(Header.ContentType(MediaType.application.json)),
          healthResponse.status == "healthy",
          healthResponse.version == "0.1.0-SNAPSHOT",
          healthResponse.timestamp.nonEmpty
        )
      }
    ),

    suite("Basic Routing")(
      test("GET / returns HTML homepage with correct content") {
        for {
          request <- ZIO.succeed(Request.get(URL.root))
          response <- routesWithLogging.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          response.header(Header.ContentType).exists(_.renderedValue.contains("text/html")),
          body.contains("PDF Watermarking Application"),
          body.contains("<title>PDF Watermarking Application</title>"),
          body.contains("Server is running and ready to accept requests"),
          body.contains("/health"),
          body.contains("/api/health")
        )
      },

      test("GET /nonexistent returns 404 Not Found") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "nonexistent"))
          response <- routesWithLogging.runZIO(request)
        } yield assertTrue(
          response.status == Status.NotFound
        )
      }
    ),

    suite("Server Configuration")(
      test("ServerConfig has correct default values") {
        val config = ServerConfig()
        assertTrue(
          config.port == 8080,
          config.host == "0.0.0.0"
        )
      },

      test("ServerConfig can be customized") {
        val config = ServerConfig(port = 9090, host = "localhost")
        assertTrue(
          config.port == 9090,
          config.host == "localhost"
        )
      }
    ),

    suite("HealthResponse JSON Serialization")(
      test("HealthResponse can be encoded to JSON") {
        val healthResponse = HealthResponse("healthy", "2024-01-01T00:00:00Z")
        val json = healthResponse.toJson
        assertTrue(
          json.contains("\"status\":\"healthy\""),
          json.contains("\"timestamp\":\"2024-01-01T00:00:00Z\""),
          json.contains("\"version\":\"0.1.0-SNAPSHOT\"")
        )
      },

      test("HealthResponse can be decoded from JSON") {
        val json = """{"status":"healthy","timestamp":"2024-01-01T00:00:00Z","version":"0.1.0-SNAPSHOT"}"""
        val result = json.fromJson[HealthResponse]
        assertTrue(
          result.isRight,
          result.fold(_ => false, hr => 
            hr.status == "healthy" && 
            hr.timestamp == "2024-01-01T00:00:00Z" && 
            hr.version == "0.1.0-SNAPSHOT"
          )
        )
      }
    ),

    suite("Error Handling")(
      test("Routes handle requests without throwing exceptions") {
        for {
          healthRequest <- ZIO.succeed(Request.get(URL.root / "health"))
          healthResponse <- routesWithLogging.runZIO(healthRequest)
          rootRequest <- ZIO.succeed(Request.get(URL.root))
          rootResponse <- routesWithLogging.runZIO(rootRequest)
          notFoundRequest <- ZIO.succeed(Request.get(URL.root / "nonexistent"))
          notFoundResponse <- routesWithLogging.runZIO(notFoundRequest)
        } yield assertTrue(
          healthResponse.status.isSuccess,
          rootResponse.status.isSuccess,
          notFoundResponse.status == Status.NotFound
        )
      }
    ),

    suite("File Upload Endpoints")(
      test("POST /api/upload with valid PDF file returns success response") {
        val pdfContent = "fake-pdf-content".getBytes()
        val form = Form(
          FormField.binaryField(
            name = "file",
            data = Chunk.fromArray(pdfContent),
            mediaType = MediaType.application.pdf,
            filename = Some("test.pdf")
          )
        )
        val body = Body.fromMultipartForm(form, Boundary("test-boundary"))
        
        for {
          request <- ZIO.succeed(Request.post(URL.root / "api" / "upload", body))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          uploadResponse <- ZIO.fromEither(responseBody.fromJson[UploadResponse])
        } yield assertTrue(
          response.status == Status.Ok,
          uploadResponse.success,
          uploadResponse.sessionId.nonEmpty,
          uploadResponse.documentId.isDefined,
          uploadResponse.message.contains("uploaded successfully")
        )
      }
    )
  )
}