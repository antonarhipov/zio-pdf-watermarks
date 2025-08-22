package com.pdfwatermarks.domain

import java.awt.Color
import java.io.File
import java.time.Instant
import zio.json.*

/**
 * Core domain models for the PDF Watermarking Application.
 * 
 * These models represent the fundamental data structures used throughout
 * the application for PDF processing, watermark configuration, and
 * document management following functional programming principles.
 */

// ========== PDF Document Models ==========

/**
 * Represents a PDF document with metadata and processing information.
 */
case class PdfDocument(
  id: String,
  filename: String,
  originalSize: Long,
  pageCount: Int,
  uploadedAt: Instant,
  status: DocumentStatus
)

/**
 * Status of a PDF document during processing.
 */
enum DocumentStatus:
  case Uploaded
  case Processing
  case Completed
  case Failed(reason: String)

// ========== Watermark Configuration Models ==========

/**
 * Complete watermark configuration containing all customization options.
 */
case class WatermarkConfig(
  text: String,
  position: PositionConfig,
  orientation: OrientationConfig,
  fontSize: FontSizeConfig,
  color: ColorConfig,
  quantity: Int
)

/**
 * Position configuration for watermark placement.
 */
enum PositionConfig:
  case Fixed(x: Double, y: Double)
  case Random

/**
 * Orientation configuration for watermark rotation.
 */
enum OrientationConfig:
  case Fixed(angle: Double) // 0-360 degrees
  case Random

/**
 * Font size configuration for watermark text.
 */
enum FontSizeConfig:
  case Fixed(size: Double)
  case Random(min: Double, max: Double)

/**
 * Color configuration for watermark text.
 */
enum ColorConfig:
  case Fixed(color: Color)
  case RandomPerLetter

// ========== Coordinate and Geometry Models ==========

/**
 * 2D coordinate point for positioning.
 */
case class Point(x: Double, y: Double)

/**
 * Page dimensions for boundary checking.
 */
case class PageDimensions(width: Double, height: Double)

/**
 * Bounding box for overlap detection and positioning.
 */
case class BoundingBox(
  topLeft: Point,
  bottomRight: Point
) {
  def width: Double = bottomRight.x - topLeft.x
  def height: Double = bottomRight.y - topLeft.y
  
  def contains(point: Point): Boolean =
    point.x >= topLeft.x && point.x <= bottomRight.x &&
    point.y >= topLeft.y && point.y <= bottomRight.y
    
  def overlaps(other: BoundingBox): Boolean =
    !(bottomRight.x < other.topLeft.x || 
      topLeft.x > other.bottomRight.x ||
      bottomRight.y < other.topLeft.y ||
      topLeft.y > other.bottomRight.y)
}

// ========== Processing Models ==========

/**
 * Represents a single watermark instance with computed properties.
 */
case class WatermarkInstance(
  text: String,
  position: Point,
  angle: Double,
  fontSize: Double,
  color: Color,
  boundingBox: BoundingBox
)

/**
 * Complete watermark layout for a PDF page.
 */
case class PageWatermarkLayout(
  pageNumber: Int,
  pageDimensions: PageDimensions,
  watermarks: List[WatermarkInstance]
)

/**
 * Processing job for a PDF document.
 */
case class ProcessingJob(
  id: String,
  document: PdfDocument,
  config: WatermarkConfig,
  createdAt: Instant,
  status: JobStatus
)

/**
 * Status of a processing job.
 */
enum JobStatus:
  case Pending
  case InProgress
  case Completed(resultPath: String)
  case Failed(error: String)

// ========== Validation Models ==========

/**
 * Validation result for configuration parameters.
 */
enum ValidationResult:
  case Valid
  case Invalid(errors: List[String])

/**
 * Configuration constraints and limits.
 */
object ConfigConstraints {
  val MaxWatermarkQuantity: Int = 100
  val MinFontSize: Double = 8.0
  val MaxFontSize: Double = 144.0
  val MaxFileSizeBytes: Long = 50 * 1024 * 1024 // 50MB
  val SupportedFileExtensions: Set[String] = Set(".pdf")
  
  def isValidAngle(angle: Double): Boolean = angle >= 0.0 && angle <= 360.0
  def isValidFontSize(size: Double): Boolean = size >= MinFontSize && size <= MaxFontSize
  def isValidQuantity(quantity: Int): Boolean = quantity > 0 && quantity <= MaxWatermarkQuantity
}

// ========== Session and Upload Models ==========

/**
 * User session for multi-step workflow.
 */
case class UserSession(
  sessionId: String,
  uploadedDocument: Option[PdfDocument],
  watermarkConfig: Option[WatermarkConfig],
  createdAt: Instant,
  lastActivity: Instant
)

/**
 * File upload information.
 */
case class UploadInfo(
  filename: String,
  contentType: String,
  size: Long,
  tempPath: String
)

// ========== Error Models ==========

/**
 * Domain-specific errors for the application.
 */
enum DomainError:
  case InvalidFileFormat(message: String)
  case FileSizeExceeded(actualSize: Long, maxSize: Long)
  case InvalidConfiguration(errors: List[String])
  case PdfProcessingError(message: String)
  case SessionNotFound(sessionId: String)
  case DocumentNotFound(documentId: String)
  case InternalError(message: String)

// ========== JSON Serialization ==========

/**
 * JSON encoders and decoders for HTTP API integration.
 */

// Custom Color JSON codec
given JsonCodec[Color] = JsonCodec(
  JsonEncoder.int.contramap[Color](_.getRGB),
  JsonDecoder.int.map(rgb => new Color(rgb, true))
)

// Point JSON codec
given JsonCodec[Point] = DeriveJsonCodec.gen[Point]

// Position configuration JSON codec
given JsonCodec[PositionConfig] = DeriveJsonCodec.gen[PositionConfig]

// Orientation configuration JSON codec  
given JsonCodec[OrientationConfig] = DeriveJsonCodec.gen[OrientationConfig]

// Font size configuration JSON codec
given JsonCodec[FontSizeConfig] = DeriveJsonCodec.gen[FontSizeConfig]

// Color configuration JSON codec
given JsonCodec[ColorConfig] = DeriveJsonCodec.gen[ColorConfig]

// Watermark configuration JSON codec
given JsonCodec[WatermarkConfig] = DeriveJsonCodec.gen[WatermarkConfig]

// Processing request model for HTTP API
case class ProcessWatermarkRequest(
  sessionId: String,
  config: WatermarkConfig
)

given JsonCodec[ProcessWatermarkRequest] = DeriveJsonCodec.gen[ProcessWatermarkRequest]

// Processing response model for HTTP API
case class ProcessWatermarkResponse(
  success: Boolean,
  sessionId: String,
  jobId: Option[String] = None,
  message: String
)

given JsonCodec[ProcessWatermarkResponse] = DeriveJsonCodec.gen[ProcessWatermarkResponse]

// Job status response for HTTP API
case class JobStatusResponse(
  sessionId: String,
  jobId: String,
  status: String,
  progress: Int, // 0-100 percentage
  message: String,
  downloadUrl: Option[String] = None
)

given JsonCodec[JobStatusResponse] = DeriveJsonCodec.gen[JobStatusResponse]

// ========== Download Models ==========

/**
 * Download session tracking for progress monitoring (Task 60).
 */
case class DownloadSession(
  sessionId: String,
  documentId: String,
  filename: String,
  fileSize: Long,
  bytesTransferred: Long,
  startedAt: Instant,
  lastActivity: Instant,
  status: DownloadStatus
)

/**
 * Status of a download operation.
 */
enum DownloadStatus:
  case Starting
  case InProgress
  case Completed
  case Failed(reason: String)
  case Cancelled

/**
 * Download progress response for HTTP API (Task 60).
 */
case class DownloadProgressResponse(
  sessionId: String,
  filename: String,
  fileSize: Long,
  bytesTransferred: Long,
  progress: Int, // 0-100 percentage
  status: String,
  transferRate: Option[Long] = None, // bytes per second
  estimatedTimeRemaining: Option[Long] = None, // seconds
  message: String
)

given JsonCodec[DownloadProgressResponse] = DeriveJsonCodec.gen[DownloadProgressResponse]