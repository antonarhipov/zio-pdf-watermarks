package com.pdfwatermarks.config

import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

import java.nio.file.Path
import java.time.Duration

/**
 * Application configuration case classes for the PDF Watermarking Application.
 * 
 * These configurations are loaded from application.conf and can be overridden
 * via environment variables for different deployment environments.
 */

/**
 * Temporary file management configuration.
 */
final case class TempFileConfig(
  baseDir: String,
  maxAgeHours: Int,
  maxTotalSizeMb: Long,
  cleanupIntervalMinutes: Int,
  uploadPrefix: String,
  processedPrefix: String
) {
  def baseDirPath: Path = java.nio.file.Paths.get(baseDir)
  def maxAgeDuration: Duration = Duration.ofHours(maxAgeHours.toLong)
  def cleanupInterval: Duration = Duration.ofMinutes(cleanupIntervalMinutes.toLong)
  def maxTotalSizeBytes: Long = maxTotalSizeMb * 1024 * 1024
}

/**
 * HTTP server configuration.
 */
final case class HttpConfig(
  host: String,
  port: Int,
  maxFileSizeMb: Long,
  maxRequestSizeMb: Long
) {
  def maxFileSizeBytes: Long = maxFileSizeMb * 1024 * 1024
  def maxRequestSizeBytes: Long = maxRequestSizeMb * 1024 * 1024
}

/**
 * PDF processing configuration.
 */
final case class PdfConfig(
  maxPages: Int,
  processingTimeoutSeconds: Int
) {
  def processingTimeout: Duration = Duration.ofSeconds(processingTimeoutSeconds.toLong)
}

/**
 * Session management configuration.
 */
final case class SessionConfig(
  timeoutHours: Int,
  cleanupIntervalMinutes: Int
) {
  def timeoutDuration: Duration = Duration.ofHours(timeoutHours.toLong)
  def cleanupInterval: Duration = Duration.ofMinutes(cleanupIntervalMinutes.toLong)
}

/**
 * Logging configuration.
 */
final case class LoggingConfig(
  level: String,
  pattern: String
)

/**
 * Complete application configuration.
 */
final case class ApplicationConfig(
  tempFiles: TempFileConfig,
  http: HttpConfig,
  pdf: PdfConfig,
  sessions: SessionConfig,
  logging: LoggingConfig
)

object ApplicationConfig {
  
  /**
   * ZIO Config descriptors for automatic configuration parsing.
   */
  private val tempFileConfigDescriptor = deriveConfig[TempFileConfig].mapKey(toKebabCase)
  private val httpConfigDescriptor = deriveConfig[HttpConfig].mapKey(toKebabCase)
  private val pdfConfigDescriptor = deriveConfig[PdfConfig].mapKey(toKebabCase)
  private val sessionConfigDescriptor = deriveConfig[SessionConfig].mapKey(toKebabCase)
  private val loggingConfigDescriptor = deriveConfig[LoggingConfig].mapKey(toKebabCase)
  
  private val applicationConfigDescriptor = deriveConfig[ApplicationConfig].mapKey(toKebabCase)
  
  /**
   * Load configuration from application.conf with environment variable overrides.
   */
  val layer: ZLayer[Any, Config.Error, ApplicationConfig] = {
    ZLayer.fromZIO(
      read(applicationConfigDescriptor.from(TypesafeConfigProvider.fromResourcePath()))
    )
  }
  
  /**
   * Alternative layer that loads complete config at once.
   */
  val simpleLayer: ZLayer[Any, Config.Error, ApplicationConfig] = {
    ZLayer.fromZIO(
      read(applicationConfigDescriptor.from(TypesafeConfigProvider.fromResourcePath()))
    )
  }
  
  /**
   * Access methods for individual config sections.
   */
  def tempFileConfig: URIO[ApplicationConfig, TempFileConfig] =
    ZIO.serviceWith[ApplicationConfig](_.tempFiles)
    
  def httpConfig: URIO[ApplicationConfig, HttpConfig] =
    ZIO.serviceWith[ApplicationConfig](_.http)
    
  def pdfConfig: URIO[ApplicationConfig, PdfConfig] =
    ZIO.serviceWith[ApplicationConfig](_.pdf)
    
  def sessionConfig: URIO[ApplicationConfig, SessionConfig] =
    ZIO.serviceWith[ApplicationConfig](_.sessions)
    
  def loggingConfig: URIO[ApplicationConfig, LoggingConfig] =
    ZIO.serviceWith[ApplicationConfig](_.logging)
}