
package com.pdfwatermarks

import zio._
import zio.http.Server
import zio.logging.backend.SLF4J
import com.pdfwatermarks.http.HttpServer
import com.pdfwatermarks.services.Layers

/**
 * Main application entry point for the PDF Watermarking Application.
 * 
 * This application provides a web-based service for adding customizable watermarks
 * to PDF documents using ZIO for functional effect management, ZIO HTTP for web services,
 * and Apache PDFBox for PDF manipulation.
 */
object PdfWatermarkApp extends ZIOAppDefault {

  /**
   * Main application logic.
   * 
   * Starts the ZIO HTTP server with all configured services:
   * - Web server with routing and middleware
   * - PDF processing services
   * - Health check endpoints
   * - CORS configuration for web interface
   */
  override def run: ZIO[Any, Any, Any] =
    for {
      _ <- ZIO.logInfo("Starting PDF Watermarking Application")
      config = HttpServer.ServerConfig(port = 8080, host = "0.0.0.0")
      _ <- ZIO.logInfo(s"Configuring HTTP server on ${config.host}:${config.port}")
      _ <- ZIO.logInfo("Application services initialized successfully")
      serverConfig = Server.Config.default.port(config.port).binding(config.host, config.port)
      serverLayer = ZLayer.succeed(serverConfig) >>> Server.live
      exitCode <- HttpServer.runWithGracefulShutdown(config).provide(
        Layers.appLayer >+> serverLayer
      )
    } yield exitCode

  /**
   * Bootstrap layer providing logging configuration and core services.
   * 
   * This layer will be expanded to include:
   * - PDF processing services
   * - Watermark rendering services  
   * - Configuration management
   * - HTTP server setup
   */
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j
}

