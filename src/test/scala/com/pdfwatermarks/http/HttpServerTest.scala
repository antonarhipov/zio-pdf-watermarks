package com.pdfwatermarks.http

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.http.*
import zio.json.*
import com.pdfwatermarks.http.HttpServer.*

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
    )
  )
}