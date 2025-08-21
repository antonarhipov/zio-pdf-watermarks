package com.pdfwatermarks.errors

import com.pdfwatermarks.domain.DomainError
import zio.*
import zio.http.*
import zio.json.*

/**
 * Comprehensive error handling patterns and utilities for the PDF Watermarking Application.
 * 
 * This module provides error conversion, HTTP status mapping, user-friendly messaging,
 * and logging patterns following ZIO error handling best practices.
 */

// ========== HTTP Error Response Models ==========

/**
 * Standardized error response for HTTP APIs.
 */
case class ErrorResponse(
  error: String,
  message: String,
  details: Option[List[String]] = None,
  code: String,
  timestamp: String = java.time.Instant.now().toString
)

object ErrorResponse {
  implicit val encoder: JsonEncoder[ErrorResponse] = DeriveJsonEncoder.gen[ErrorResponse]
  implicit val decoder: JsonDecoder[ErrorResponse] = DeriveJsonDecoder.gen[ErrorResponse]
}

// ========== Error Handling Utilities ==========

/**
 * Utilities for converting domain errors to HTTP responses and user-friendly messages.
 */
object ErrorHandling {

  /**
   * Convert a domain error to an HTTP status code.
   */
  def toHttpStatus(error: DomainError): Status = error match {
    case DomainError.InvalidFileFormat(_) => Status.BadRequest
    case DomainError.FileSizeExceeded(_, _) => Status.BadRequest
    case DomainError.InvalidConfiguration(_) => Status.BadRequest
    case DomainError.SessionNotFound(_) => Status.NotFound
    case DomainError.DocumentNotFound(_) => Status.NotFound
    case DomainError.PdfProcessingError(_) => Status.InternalServerError
    case DomainError.InternalError(_) => Status.InternalServerError
  }

  /**
   * Convert a domain error to a user-friendly error message.
   */
  def toUserMessage(error: DomainError): String = error match {
    case DomainError.InvalidFileFormat(message) => 
      s"Invalid file format: $message. Please upload a valid PDF file."
    
    case DomainError.FileSizeExceeded(actualSize, maxSize) =>
      s"File size too large (${formatFileSize(actualSize)}). Maximum allowed size is ${formatFileSize(maxSize)}."
    
    case DomainError.InvalidConfiguration(errors) =>
      s"Configuration error: ${errors.mkString(", ")}. Please check your watermark settings."
    
    case DomainError.SessionNotFound(sessionId) =>
      "Your session has expired. Please start over by uploading a new file."
    
    case DomainError.DocumentNotFound(documentId) =>
      "The requested document could not be found. Please upload a new file."
    
    case DomainError.PdfProcessingError(message) =>
      s"Error processing PDF: $message. Please try again or use a different PDF file."
    
    case DomainError.InternalError(message) =>
      "An internal error occurred. Please try again later or contact support if the problem persists."
  }

  /**
   * Get error code for tracking and debugging.
   */
  def getErrorCode(error: DomainError): String = error match {
    case DomainError.InvalidFileFormat(_) => "INVALID_FILE_FORMAT"
    case DomainError.FileSizeExceeded(_, _) => "FILE_SIZE_EXCEEDED"
    case DomainError.InvalidConfiguration(_) => "INVALID_CONFIGURATION"
    case DomainError.SessionNotFound(_) => "SESSION_NOT_FOUND"
    case DomainError.DocumentNotFound(_) => "DOCUMENT_NOT_FOUND"
    case DomainError.PdfProcessingError(_) => "PDF_PROCESSING_ERROR"
    case DomainError.InternalError(_) => "INTERNAL_ERROR"
  }

  /**
   * Extract detailed error information for debugging.
   */
  def getErrorDetails(error: DomainError): Option[List[String]] = error match {
    case DomainError.InvalidConfiguration(errors) => Some(errors)
    case DomainError.FileSizeExceeded(actualSize, maxSize) => 
      Some(List(s"Actual size: ${formatFileSize(actualSize)}", s"Max size: ${formatFileSize(maxSize)}"))
    case _ => None
  }

  /**
   * Create a standardized error response.
   */
  def toErrorResponse(error: DomainError): ErrorResponse =
    ErrorResponse(
      error = error.getClass.getSimpleName.replace("$", ""),
      message = toUserMessage(error),
      details = getErrorDetails(error),
      code = getErrorCode(error)
    )

  /**
   * Create an HTTP response from a domain error.
   */
  def toHttpResponse(error: DomainError): Response =
    Response.json(toErrorResponse(error).toJson).withStatus(toHttpStatus(error))

  /**
   * Format file size in human-readable format.
   */
  private def formatFileSize(bytes: Long): String = {
    val units = Array("B", "KB", "MB", "GB")
    var size = bytes.toDouble
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024
      unitIndex += 1
    }
    
    f"$size%.1f ${units(unitIndex)}"
  }
}

// ========== Error Handling Aspects and Patterns ==========

/**
 * Reusable error handling patterns and aspects.
 */
object ErrorPatterns {

  /**
   * Generic error handler that logs errors and converts them to HTTP responses.
   */
  def handleErrors[R, E <: DomainError, A](
    effect: ZIO[R, E, A]
  ): ZIO[R, Nothing, Response] =
    effect.foldZIO(
      error => 
        for {
          _ <- ZIO.logError(s"Application error: ${error}")
          _ <- ZIO.logDebug(s"Error details: ${ErrorHandling.getErrorDetails(error)}")
        } yield ErrorHandling.toHttpResponse(error),
      success =>
        ZIO.succeed(Response.json(success.toJson))
    )

  /**
   * Error handler with custom success response transformation.
   */
  def handleErrorsWithResponse[R, E <: DomainError, A](
    effect: ZIO[R, E, A],
    successResponse: A => Response
  ): ZIO[R, Nothing, Response] =
    effect.foldZIO(
      error =>
        for {
          _ <- ZIO.logError(s"Application error: ${error}")
          _ <- ZIO.logDebug(s"Error details: ${ErrorHandling.getErrorDetails(error)}")
        } yield ErrorHandling.toHttpResponse(error),
      success =>
        ZIO.succeed(successResponse(success))
    )

  /**
   * Retry pattern for transient errors.
   */
  def retryTransientErrors[R, E <: DomainError, A](
    effect: ZIO[R, E, A],
    maxRetries: Int = 3
  ): ZIO[R, E, A] =
    effect.retry(
      Schedule.exponential(1.second) && Schedule.recurs(maxRetries) && Schedule.recurWhile {
        case DomainError.InternalError(_) => true
        case DomainError.PdfProcessingError(_) => true
        case _ => false
      }
    )

  /**
   * Timeout pattern with custom error message.
   */
  def withTimeout[R, E >: DomainError, A](
    effect: ZIO[R, E, A],
    duration: Duration,
    timeoutMessage: String = "Operation timed out"
  ): ZIO[R, E, A] =
    effect.timeoutFail(DomainError.InternalError(timeoutMessage))(duration)

  /**
   * Resource cleanup pattern that ensures cleanup even on errors.
   */
  def withResourceCleanup[R, E, A, B](
    acquire: ZIO[R, E, A],
    use: A => ZIO[R, E, B],
    release: A => UIO[Unit]
  ): ZIO[R, E, B] =
    ZIO.acquireReleaseWith(acquire)(release)(use)

  /**
   * Validation pattern that accumulates multiple validation errors.
   */
  def validateAll[R, A](
    validations: List[ZIO[R, DomainError, A]]
  ): ZIO[R, DomainError, List[A]] =
    ZIO.validatePar(validations)(identity).mapError { errors =>
      val messages = errors.collect {
        case DomainError.InvalidConfiguration(msgs) => msgs
        case other => List(ErrorHandling.toUserMessage(other))
      }.flatten
      DomainError.InvalidConfiguration(messages)
    }

  /**
   * Safe operation wrapper that catches and converts exceptions.
   */
  def safely[A](operation: => A, errorMessage: String = "Unexpected error occurred"): IO[DomainError, A] =
    ZIO.attempt(operation).mapError { throwable =>
      DomainError.InternalError(s"$errorMessage: ${throwable.getMessage}")
    }

  /**
   * Database operation error handler (for future database integration).
   */
  def handleDatabaseErrors[R, A](
    operation: ZIO[R, Throwable, A]
  ): ZIO[R, DomainError, A] =
    operation.mapError {
      case _: java.sql.SQLException => 
        DomainError.InternalError("Database operation failed")
      case _: java.io.IOException => 
        DomainError.InternalError("I/O operation failed")
      case throwable => 
        DomainError.InternalError(s"Unexpected error: ${throwable.getMessage}")
    }
}

// ========== Error Recovery Strategies ==========

/**
 * Error recovery and fallback strategies.
 */
object ErrorRecovery {

  /**
   * Provide a fallback value when an operation fails.
   */
  def withFallback[R, E, A](
    primary: ZIO[R, E, A],
    fallback: A
  ): ZIO[R, Nothing, A] =
    primary.orElse(ZIO.succeed(fallback))

  /**
   * Try alternative operations in sequence until one succeeds.
   */
  def tryAlternatives[R, E, A](
    alternatives: List[ZIO[R, E, A]]
  ): ZIO[R, E, A] =
    alternatives match {
      case head :: tail =>
        head.orElse(tryAlternatives(tail))
      case Nil =>
        ZIO.fail(throw new IllegalArgumentException("No alternatives provided"))
    }

  /**
   * Circuit breaker pattern for external service calls.
   */
  def withCircuitBreaker[R, E <: DomainError, A](
    operation: ZIO[R, E, A],
    failureThreshold: Int = 5,
    resetTimeout: Duration = 30.seconds
  ): ZIO[R, E, A] = {
    // This is a simplified circuit breaker implementation
    // In production, you would use a more sophisticated implementation
    operation.retry(Schedule.recurs(failureThreshold))
  }

  /**
   * Graceful degradation - return partial results when possible.
   */
  def withGracefulDegradation[R, E, A, B](
    operation: ZIO[R, E, A],
    partialResult: E => Option[B]
  ): ZIO[R, E, Either[B, A]] =
    operation.foldZIO(
      error => partialResult(error) match {
        case Some(partial) => ZIO.succeed(Left(partial))
        case None => ZIO.fail(error)
      },
      success => ZIO.succeed(Right(success))
    )
}