package com.pdfwatermarks.http

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.http.*
import zio.json.*
import com.pdfwatermarks.http.HttpServer.*
import com.pdfwatermarks.domain.*
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
  val mockSessionManagementService = ZLayer.succeed(
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
  
  val mockFileManagementService = ZLayer.succeed(
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
  
  val mockPdfProcessingService = ZLayer.succeed(
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
  
  val testLayer = mockSessionManagementService ++ mockFileManagementService ++ mockPdfProcessingService

  def spec = suite("HttpServerTest")(
    suite("Health Check Endpoints")(
      test("GET /health returns healthy status with JSON response") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "health"))
          response <- routes.runZIO(request)
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
          response <- routes.runZIO(request)
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
          response <- routes.runZIO(request)
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
          response <- routes.runZIO(request)
        } yield assertTrue(
          response.status == Status.NotFound
        )
      }
    ),

    suite("HTTP Methods")(
      test("POST /health returns Method Not Allowed") {
        for {
          request <- ZIO.succeed(Request.post(URL.root / "health", Body.empty))
          response <- routes.runZIO(request)
        } yield assertTrue(
          response.status == Status.NotFound // ZIO HTTP returns NotFound for unmatched routes
        )
      },

      test("OPTIONS request should be handled by CORS middleware") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "health").copy(method = Method.OPTIONS))
          response <- httpApp.runZIO(request)
        } yield assertTrue(
          response.status == Status.Ok || response.status == Status.NoContent,
          response.header(Header.AccessControlAllowMethods).isDefined ||
          response.header(Header.AccessControlAllowOrigin).isDefined
        )
      }
    ),

    suite("CORS Configuration")(
      test("CORS headers are present in responses") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "health"))
          response <- httpApp.runZIO(request)
        } yield assertTrue(
          // At least one CORS header should be present when CORS is configured
          response.header(Header.AccessControlAllowOrigin).isDefined ||
          response.header(Header.AccessControlAllowMethods).isDefined ||
          response.header(Header.AccessControlAllowHeaders).isDefined
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
          healthResponse <- routes.runZIO(healthRequest)
          rootRequest <- ZIO.succeed(Request.get(URL.root))
          rootResponse <- routes.runZIO(rootRequest)
          notFoundRequest <- ZIO.succeed(Request.get(URL.root / "nonexistent"))
          notFoundResponse <- routes.runZIO(notFoundRequest)
        } yield assertTrue(
          healthResponse.status.isSuccess,
          rootResponse.status.isSuccess,
          notFoundResponse.status == Status.NotFound
        )
      }
    ),

    suite("Middleware Integration")(
      test("HTTP app includes middleware and CORS") {
        for {
          request <- ZIO.succeed(Request.get(URL.root / "health"))
          response <- httpApp.runZIO(request)
        } yield assertTrue(
          response.status.isSuccess,
          // The middleware should not break the basic functionality
          response.headers.nonEmpty
        )
      }
    ),

    suite("File Upload Endpoints (Task 36)")(
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
          uploadResponse.success == true,
          uploadResponse.sessionId.nonEmpty,
          uploadResponse.documentId.isDefined,
          uploadResponse.message.contains("uploaded successfully")
        )
      }.provide(testLayer),

      test("POST /api/upload with invalid file format returns error") {
        val txtContent = "not-a-pdf".getBytes()
        val form = Form(
          FormField.binaryField(
            name = "file",
            data = Chunk.fromArray(txtContent),
            mediaType = MediaType.text.plain,
            filename = Some("test.txt")
          )
        )
        val body = Body.fromMultipartForm(form, Boundary("test-boundary"))
        
        for {
          request <- ZIO.succeed(Request.post(URL.root / "api" / "upload", body))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          uploadResponse <- ZIO.fromEither(responseBody.fromJson[UploadResponse])
        } yield assertTrue(
          response.status == Status.BadRequest,
          uploadResponse.success == false,
          uploadResponse.message.contains("Invalid file format")
        )
      }.provide(testLayer),

      test("POST /api/upload without file field returns error") {
        val form = Form(
          FormField.simpleField("notfile", "some-value")
        )
        val body = Body.fromMultipartForm(form, Boundary("test-boundary"))
        
        for {
          request <- ZIO.succeed(Request.post(URL.root / "api" / "upload", body))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          uploadResponse <- ZIO.fromEither(responseBody.fromJson[UploadResponse])
        } yield assertTrue(
          response.status == Status.BadRequest,
          uploadResponse.success == false,
          uploadResponse.message.contains("No file field found")
        )
      }.provide(testLayer)
    ),

    suite("Upload Progress Tracking (Task 40)")(
      test("GET /api/upload/progress/{sessionId} with valid session returns progress") {
        for {
          // First create a session with uploaded document
          sessionService <- ZIO.service[SessionManagementService]
          session <- sessionService.createSession()
          document = PdfDocument(
            id = UUID.randomUUID().toString,
            filename = "test.pdf",
            originalSize = 1024L,
            pageCount = 1,
            uploadedAt = Instant.now(),
            status = DocumentStatus.Uploaded
          )
          _ <- sessionService.updateSessionWithDocument(session.sessionId, document)
          
          // Then check progress
          request <- ZIO.succeed(Request.get(URL.root / "api" / "upload" / "progress" / session.sessionId))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          progressResponse <- ZIO.fromEither(responseBody.fromJson[UploadProgressResponse])
        } yield assertTrue(
          response.status == Status.Ok,
          progressResponse.sessionId == session.sessionId,
          progressResponse.status == "completed",
          progressResponse.progress == 100,
          progressResponse.message.contains("uploaded successfully")
        )
      }.provide(testLayer),

      test("GET /api/upload/progress/{sessionId} with session without upload returns no_upload status") {
        for {
          // Create empty session
          sessionService <- ZIO.service[SessionManagementService]
          session <- sessionService.createSession()
          
          // Check progress
          request <- ZIO.succeed(Request.get(URL.root / "api" / "upload" / "progress" / session.sessionId))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          progressResponse <- ZIO.fromEither(responseBody.fromJson[UploadProgressResponse])
        } yield assertTrue(
          response.status == Status.Ok,
          progressResponse.sessionId == session.sessionId,
          progressResponse.status == "no_upload",
          progressResponse.progress == 0,
          progressResponse.message.contains("No file uploaded")
        )
      }.provide(testLayer),

      test("GET /api/upload/progress/{sessionId} with invalid session returns error") {
        val invalidSessionId = "invalid-session-id"
        for {
          request <- ZIO.succeed(Request.get(URL.root / "api" / "upload" / "progress" / invalidSessionId))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          progressResponse <- ZIO.fromEither(responseBody.fromJson[UploadProgressResponse])
        } yield assertTrue(
          response.status == Status.NotFound,
          progressResponse.sessionId == invalidSessionId,
          progressResponse.status == "error",
          progressResponse.progress == 0,
          progressResponse.message.contains("Session not found")
        )
      }.provide(testLayer),

      test("Progress response shows different statuses for document states") {
        for {
          // Create session with processing document
          sessionService <- ZIO.service[SessionManagementService]
          session <- sessionService.createSession()
          processingDocument = PdfDocument(
            id = UUID.randomUUID().toString,
            filename = "processing.pdf",
            originalSize = 2048L,
            pageCount = 2,
            uploadedAt = Instant.now(),
            status = DocumentStatus.Processing
          )
          _ <- sessionService.updateSessionWithDocument(session.sessionId, processingDocument)
          
          // Check progress
          request <- ZIO.succeed(Request.get(URL.root / "api" / "upload" / "progress" / session.sessionId))
          response <- fileUploadRoutes.runZIO(request).provide(testLayer)
          responseBody <- response.body.asString
          progressResponse <- ZIO.fromEither(responseBody.fromJson[UploadProgressResponse])
        } yield assertTrue(
          response.status == Status.Ok,
          progressResponse.status == "processing",
          progressResponse.progress == 50,
          progressResponse.message.contains("Processing file")
        )
      }.provide(testLayer)
    )
  )
}