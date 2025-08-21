package com.pdfwatermarks.logging

import com.pdfwatermarks.domain.*
import zio.*
import zio.logging.*
import zio.json.*
import java.time.Instant

/**
 * Structured logging utilities for the PDF Watermarking Application.
 * 
 * This module provides consistent logging patterns, structured context,
 * and performance monitoring capabilities using ZIO Logging with
 * structured data and context propagation.
 */

// ========== Structured Log Context Models ==========

/**
 * Structured context for request tracing and correlation.
 */
case class RequestContext(
  requestId: String,
  sessionId: Option[String] = None,
  userId: Option[String] = None,
  operation: String,
  startTime: Instant = Instant.now()
)

object RequestContext {
  implicit val encoder: JsonEncoder[RequestContext] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[RequestContext] = DeriveJsonDecoder.gen
}

/**
 * Performance metrics for operations.
 */
case class PerformanceMetrics(
  operation: String,
  duration: Duration,
  success: Boolean,
  errorType: Option[String] = None,
  resourcesUsed: Map[String, String] = Map.empty
)

object PerformanceMetrics {
  implicit val encoder: JsonEncoder[PerformanceMetrics] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PerformanceMetrics] = DeriveJsonDecoder.gen
}

/**
 * Business event context for audit and monitoring.
 */
case class BusinessEvent(
  event: String,
  entity: String,
  entityId: String,
  action: String,
  details: Map[String, String] = Map.empty,
  timestamp: Instant = Instant.now()
)

object BusinessEvent {
  implicit val encoder: JsonEncoder[BusinessEvent] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[BusinessEvent] = DeriveJsonDecoder.gen
}

// ========== Structured Logging Utilities ==========

/**
 * Main structured logging utility object.
 */
object StructuredLogging {

  /**
   * Create a request context with generated ID.
   */
  def createRequestContext(
    operation: String,
    sessionId: Option[String] = None,
    userId: Option[String] = None
  ): RequestContext =
    RequestContext(
      requestId = java.util.UUID.randomUUID().toString,
      sessionId = sessionId,
      userId = userId,
      operation = operation
    )

  /**
   * Log with structured request context.
   */
  def logWithContext[R](
    level: LogLevel,
    message: String,
    context: RequestContext,
    additionalData: Map[String, String] = Map.empty
  ): ZIO[R, Nothing, Unit] =
    ZIO.logLevel(level)(message) @@ 
    ZIO.logAnnotate("requestId", context.requestId) @@
    ZIO.logAnnotate("operation", context.operation) @@
    (context.sessionId match {
      case Some(sid) => ZIO.logAnnotate("sessionId", sid)
      case None => ZIO.unit
    }) @@
    (context.userId match {
      case Some(uid) => ZIO.logAnnotate("userId", uid) 
      case None => ZIO.unit
    }) @@
    ZIO.foreachDiscard(additionalData.toList) { case (key, value) =>
      ZIO.logAnnotate(key, value)
    }

  /**
   * Log business events for audit and monitoring.
   */
  def logBusinessEvent[R](event: BusinessEvent): ZIO[R, Nothing, Unit] =
    ZIO.logInfo(s"Business Event: ${event.event}") @@
    ZIO.logAnnotate("event_type", "business_event") @@
    ZIO.logAnnotate("entity", event.entity) @@
    ZIO.logAnnotate("entity_id", event.entityId) @@
    ZIO.logAnnotate("action", event.action) @@
    ZIO.logAnnotate("event_timestamp", event.timestamp.toString) @@
    ZIO.foreachDiscard(event.details.toList) { case (key, value) =>
      ZIO.logAnnotate(s"detail_$key", value)
    }

  /**
   * Log performance metrics.
   */
  def logPerformanceMetrics[R](metrics: PerformanceMetrics): ZIO[R, Nothing, Unit] =
    ZIO.logInfo(s"Performance: ${metrics.operation} completed in ${metrics.duration}") @@
    ZIO.logAnnotate("metric_type", "performance") @@
    ZIO.logAnnotate("operation", metrics.operation) @@
    ZIO.logAnnotate("duration_ms", metrics.duration.toMillis.toString) @@
    ZIO.logAnnotate("success", metrics.success.toString) @@
    (metrics.errorType match {
      case Some(errorType) => ZIO.logAnnotate("error_type", errorType)
      case None => ZIO.unit
    }) @@
    ZIO.foreachDiscard(metrics.resourcesUsed.toList) { case (key, value) =>
      ZIO.logAnnotate(s"resource_$key", value)
    }

  /**
   * Log domain errors with structured context.
   */
  def logDomainError[R](
    error: DomainError,
    context: RequestContext,
    additionalContext: Map[String, String] = Map.empty
  ): ZIO[R, Nothing, Unit] =
    ZIO.logError(s"Domain Error: ${error.getClass.getSimpleName} - ${error}") @@
    ZIO.logAnnotate("error_type", "domain_error") @@
    ZIO.logAnnotate("error_class", error.getClass.getSimpleName) @@
    ZIO.logAnnotate("requestId", context.requestId) @@
    ZIO.logAnnotate("operation", context.operation) @@
    ZIO.foreachDiscard(additionalContext.toList) { case (key, value) =>
      ZIO.logAnnotate(key, value)
    }

  /**
   * Create logs directory if it doesn't exist.
   */
  def ensureLogsDirectory(): UIO[Unit] =
    ZIO.attemptBlocking {
      val logsDir = new java.io.File("logs")
      if (!logsDir.exists()) {
        logsDir.mkdirs()
      }
    }.orDie
}

// ========== Performance Monitoring Aspects ==========

/**
 * Performance monitoring aspects and utilities.
 */
object PerformanceMonitoring {

  /**
   * Measure execution time and log performance metrics.
   */
  def measureAndLog[R, E, A](
    operation: String,
    effect: ZIO[R, E, A]
  ): ZIO[R, E, A] =
    for {
      start <- Clock.currentTime(java.time.temporal.ChronoUnit.MILLIS)
      result <- effect.either
      end <- Clock.currentTime(java.time.temporal.ChronoUnit.MILLIS)
      duration = Duration.fromMillis(end - start)
      metrics = result match {
        case Right(_) => PerformanceMetrics(operation, duration, success = true)
        case Left(error) => PerformanceMetrics(
          operation, 
          duration, 
          success = false, 
          errorType = Some(error.getClass.getSimpleName)
        )
      }
      _ <- StructuredLogging.logPerformanceMetrics(metrics)
      finalResult <- ZIO.fromEither(result)
    } yield finalResult

  /**
   * Monitor resource usage during operation.
   */
  def monitorResourceUsage[R, E, A](
    operation: String,
    effect: ZIO[R, E, A]
  ): ZIO[R, E, A] =
    for {
      runtime <- ZIO.runtime[R]
      memoryBefore <- ZIO.attemptBlocking(Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory())
      start <- Clock.currentTime(java.time.temporal.ChronoUnit.MILLIS)
      result <- effect.either
      end <- Clock.currentTime(java.time.temporal.ChronoUnit.MILLIS)
      memoryAfter <- ZIO.attemptBlocking(Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory())
      duration = Duration.fromMillis(end - start)
      memoryUsed = memoryAfter - memoryBefore
      
      resourcesUsed = Map(
        "memory_used_bytes" -> memoryUsed.toString,
        "memory_before_bytes" -> memoryBefore.toString,
        "memory_after_bytes" -> memoryAfter.toString
      )
      
      metrics = result match {
        case Right(_) => PerformanceMetrics(operation, duration, success = true, resourcesUsed = resourcesUsed)
        case Left(error) => PerformanceMetrics(
          operation, 
          duration, 
          success = false, 
          errorType = Some(error.getClass.getSimpleName),
          resourcesUsed = resourcesUsed
        )
      }
      
      _ <- StructuredLogging.logPerformanceMetrics(metrics)
      finalResult <- ZIO.fromEither(result)
    } yield finalResult

  /**
   * Add performance monitoring to any ZIO effect.
   */
  def withPerformanceMonitoring[R, E, A](
    operation: String,
    includeResourceMonitoring: Boolean = false
  )(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    if (includeResourceMonitoring) {
      monitorResourceUsage(operation, effect)
    } else {
      measureAndLog(operation, effect)
    }
}

// ========== Audit Logging ==========

/**
 * Audit logging for compliance and security monitoring.
 */
object AuditLogging {

  /**
   * Log file upload events.
   */
  def logFileUpload[R](
    sessionId: String,
    filename: String,
    fileSize: Long,
    success: Boolean
  ): ZIO[R, Nothing, Unit] = {
    val event = BusinessEvent(
      event = "file_upload",
      entity = "pdf_document",
      entityId = sessionId,
      action = if (success) "upload_success" else "upload_failed",
      details = Map(
        "filename" -> filename,
        "file_size_bytes" -> fileSize.toString
      )
    )
    StructuredLogging.logBusinessEvent(event)
  }

  /**
   * Log watermark configuration events.
   */
  def logWatermarkConfiguration[R](
    sessionId: String,
    config: WatermarkConfig
  ): ZIO[R, Nothing, Unit] = {
    val event = BusinessEvent(
      event = "watermark_configuration",
      entity = "watermark_config",
      entityId = sessionId,
      action = "config_updated",
      details = Map(
        "text_length" -> config.text.length.toString,
        "quantity" -> config.quantity.toString,
        "position_type" -> config.position.getClass.getSimpleName,
        "orientation_type" -> config.orientation.getClass.getSimpleName,
        "font_size_type" -> config.fontSize.getClass.getSimpleName,
        "color_type" -> config.color.getClass.getSimpleName
      )
    )
    StructuredLogging.logBusinessEvent(event)
  }

  /**
   * Log PDF processing events.
   */
  def logPdfProcessing[R](
    sessionId: String,
    documentId: String,
    success: Boolean,
    processingTimeMs: Long,
    errorMessage: Option[String] = None
  ): ZIO[R, Nothing, Unit] = {
    val baseDetails = Map(
      "processing_time_ms" -> processingTimeMs.toString
    )
    val details = errorMessage match {
      case Some(error) => baseDetails + ("error_message" -> error)
      case None => baseDetails
    }
    
    val event = BusinessEvent(
      event = "pdf_processing",
      entity = "pdf_document",
      entityId = documentId,
      action = if (success) "processing_success" else "processing_failed",
      details = details
    )
    StructuredLogging.logBusinessEvent(event)
  }

  /**
   * Log file download events.
   */
  def logFileDownload[R](
    sessionId: String,
    filename: String,
    success: Boolean
  ): ZIO[R, Nothing, Unit] = {
    val event = BusinessEvent(
      event = "file_download",
      entity = "pdf_document", 
      entityId = sessionId,
      action = if (success) "download_success" else "download_failed",
      details = Map("filename" -> filename)
    )
    StructuredLogging.logBusinessEvent(event)
  }
}

// ========== Security Logging ==========

/**
 * Security-focused logging for monitoring and threat detection.
 */
object SecurityLogging {

  /**
   * Log suspicious file upload attempts.
   */
  def logSuspiciousUpload[R](
    sessionId: String,
    filename: String,
    reason: String,
    clientIp: Option[String] = None
  ): ZIO[R, Nothing, Unit] = {
    val baseDetails = Map(
      "filename" -> filename,
      "reason" -> reason
    )
    val details = clientIp match {
      case Some(ip) => baseDetails + ("client_ip" -> ip)
      case None => baseDetails
    }
    
    val event = BusinessEvent(
      event = "suspicious_upload",
      entity = "security_event",
      entityId = sessionId,
      action = "blocked_upload",
      details = details
    )
    StructuredLogging.logBusinessEvent(event) @@
    ZIO.logWarn(s"Suspicious upload attempt blocked: $reason")
  }

  /**
   * Log rate limiting events.
   */
  def logRateLimit[R](
    sessionId: Option[String],
    clientIp: String,
    endpoint: String,
    requestCount: Int,
    timeWindowMs: Long
  ): ZIO[R, Nothing, Unit] = {
    val event = BusinessEvent(
      event = "rate_limit_exceeded",
      entity = "security_event",
      entityId = sessionId.getOrElse(clientIp),
      action = "request_blocked",
      details = Map(
        "client_ip" -> clientIp,
        "endpoint" -> endpoint,
        "request_count" -> requestCount.toString,
        "time_window_ms" -> timeWindowMs.toString
      )
    )
    StructuredLogging.logBusinessEvent(event) @@
    ZIO.logWarn(s"Rate limit exceeded for IP $clientIp on endpoint $endpoint")
  }

  /**
   * Log validation failures that might indicate attacks.
   */
  def logValidationFailure[R](
    sessionId: Option[String],
    validationType: String,
    failureReason: String,
    inputData: Option[String] = None
  ): ZIO[R, Nothing, Unit] = {
    val baseDetails = Map(
      "validation_type" -> validationType,
      "failure_reason" -> failureReason
    )
    val details = inputData match {
      case Some(data) => baseDetails + ("input_sample" -> data.take(100)) // Truncate sensitive data
      case None => baseDetails
    }
    
    val event = BusinessEvent(
      event = "validation_failure",
      entity = "security_event", 
      entityId = sessionId.getOrElse("anonymous"),
      action = "input_rejected",
      details = details
    )
    StructuredLogging.logBusinessEvent(event)
  }
}