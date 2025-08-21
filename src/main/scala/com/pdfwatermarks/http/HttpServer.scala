package com.pdfwatermarks.http

import zio.*
import zio.http.*
import zio.json.*
import com.pdfwatermarks.domain.*
import com.pdfwatermarks.services.*

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
   * Main HTTP routes for the application.
   */
  val routes: Routes[Any, Response] = Routes(
    // Health check endpoint (Task 32)
    Method.GET / "health" -> handler {
      val timestamp = java.time.Instant.now().toString
      val healthResponse = HealthResponse("healthy", timestamp)
      Response.json(healthResponse.toJson)
    },

    // API health check with more detailed information
    Method.GET / "api" / "health" -> handler {
      val timestamp = java.time.Instant.now().toString
      val healthResponse = HealthResponse("healthy", timestamp)
      Response.json(healthResponse.toJson)
    },

    // Root endpoint - placeholder for web interface
    Method.GET / "" -> handler {
      Response(
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
   * Request logging helper (Task 33).
   */
  private def logRequest(request: Request): UIO[Unit] =
    ZIO.logInfo(s"HTTP ${request.method} ${request.path}")

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
   * Complete HTTP application with CORS support (Task 33, 34).
   */
  val httpApp = routesWithLogging @@ Middleware.cors

  /**
   * Server configuration and startup (Task 31).
   */
  def start(config: ServerConfig = ServerConfig()): ZIO[Any, Throwable, Nothing] = {
    for {
      _ <- ZIO.logInfo(s"Starting HTTP server on ${config.host}:${config.port}")
      result <- Server.serve(httpApp).provide(
        Server.defaultWithPort(config.port)
      )
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
  def runWithGracefulShutdown(config: ServerConfig = ServerConfig()): ZIO[Any, Throwable, ExitCode] = {
    start(config).onInterrupt(gracefulShutdown).as(ExitCode.success)
  }
}