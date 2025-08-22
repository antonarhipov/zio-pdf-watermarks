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
  status: DocumentStatus,
  processedFilePath: Option[String] = None
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
  case Template(template: PositionTemplate)

/**
 * Predefined position templates for common watermark layouts.
 */
enum PositionTemplate:
  case Center
  case TopLeft
  case TopRight
  case BottomLeft
  case BottomRight
  case TopCenter
  case BottomCenter
  case LeftCenter
  case RightCenter
  case FourCorners
  case Grid(rows: Int, cols: Int)
  case Diagonal
  case Border

/**
 * Orientation configuration for watermark rotation.
 */
enum OrientationConfig:
  case Fixed(angle: Double) // 0-360 degrees
  case Random
  case Preset(preset: OrientationPreset)

/**
 * Predefined orientation presets for common rotation angles.
 */
enum OrientationPreset:
  case Horizontal        // 0°
  case DiagonalUp        // 45°
  case Vertical          // 90°
  case DiagonalDown      // 135°
  case UpsideDown        // 180°
  case DiagonalUpReverse // 225°
  case VerticalReverse   // 270°
  case DiagonalDownReverse // 315°

/**
 * Font size configuration for watermark text.
 */
enum FontSizeConfig:
  case Fixed(size: Double)
  case Random(min: Double, max: Double)
  case DynamicScale(baseSize: Double, scaleFactor: Double)
  case Recommended(documentType: DocumentType)

/**
 * Document types for font size recommendations.
 */
enum DocumentType:
  case Legal       // Legal documents, contracts
  case Academic    // Research papers, theses
  case Business    // Reports, presentations
  case Certificate // Certificates, diplomas
  case Marketing   // Brochures, flyers
  case Technical   // Manuals, specifications
  case Creative    // Artistic, design documents

/**
 * Font scaling utilities for different page sizes and document types.
 */
object FontScaling {
  /**
   * Calculate recommended font size based on page dimensions and document type.
   */
  def getRecommendedSize(pageDimensions: PageDimensions, documentType: DocumentType): Double = {
    val pageArea = pageDimensions.width * pageDimensions.height
    val baseSize = calculateBaseFontSize(pageArea)
    
    val typeMultiplier = documentType match {
      case DocumentType.Legal => 0.7      // Subtle for legal docs
      case DocumentType.Academic => 0.8    // Conservative for academic
      case DocumentType.Business => 1.0    // Standard for business
      case DocumentType.Certificate => 1.5 // Prominent for certificates
      case DocumentType.Marketing => 1.3   // Eye-catching for marketing
      case DocumentType.Technical => 0.9   // Clear but not distracting
      case DocumentType.Creative => 1.2    // Artistic flexibility
    }
    
    baseSize * typeMultiplier
  }
  
  /**
   * Calculate base font size from page area (in points²).
   */
  def calculateBaseFontSize(pageArea: Double): Double = {
    // Standard US Letter is ~612x792 points = ~484,704 points²
    // Use this as reference for 24pt base font
    val referenceArea = 612.0 * 792.0
    val referenceFontSize = 24.0
    
    val scaleFactor = math.sqrt(pageArea / referenceArea)
    math.max(8.0, math.min(72.0, referenceFontSize * scaleFactor))
  }
  
  /**
   * Apply dynamic scaling based on page size.
   */
  def applyDynamicScaling(
    baseSize: Double, 
    scaleFactor: Double, 
    pageDimensions: PageDimensions
  ): Double = {
    val pageArea = pageDimensions.width * pageDimensions.height
    val referenceArea = 612.0 * 792.0 // US Letter
    val dynamicFactor = math.sqrt(pageArea / referenceArea)
    
    baseSize * scaleFactor * dynamicFactor
  }
  
  /**
   * Get font size recommendations for different scenarios.
   */
  def getFontSizeRecommendations(pageDimensions: PageDimensions): Map[String, Double] = {
    Map(
      "subtle" -> getRecommendedSize(pageDimensions, DocumentType.Legal),
      "standard" -> getRecommendedSize(pageDimensions, DocumentType.Business),
      "prominent" -> getRecommendedSize(pageDimensions, DocumentType.Certificate),
      "minimal" -> getRecommendedSize(pageDimensions, DocumentType.Technical) * 0.7,
      "maximum" -> getRecommendedSize(pageDimensions, DocumentType.Marketing) * 1.5
    )
  }
}

/**
 * Color configuration for watermark text.
 */
enum ColorConfig:
  case Fixed(color: Color)
  case RandomPerLetter
  case Palette(palette: ColorPalette)

/**
 * Predefined color palettes for watermark styling.
 */
enum ColorPalette:
  case Professional     // Dark grays, navy blue
  case Vibrant          // Bright colors
  case Pastel           // Soft, light colors
  case Monochrome       // Black, white, grays
  case Warm             // Reds, oranges, yellows
  case Cool             // Blues, greens, purples
  case Earth            // Browns, greens, earth tones
  case Custom(colors: List[Color])

/**
 * Color contrast utilities for accessibility and readability.
 */
object ColorContrast {
  /**
   * Calculate relative luminance of a color (0.0 to 1.0).
   */
  def relativeLuminance(color: Color): Double = {
    def gammaCorrect(c: Double): Double = {
      if (c <= 0.03928) c / 12.92
      else math.pow((c + 0.055) / 1.055, 2.4)
    }
    
    val r = gammaCorrect(color.getRed / 255.0)
    val g = gammaCorrect(color.getGreen / 255.0)
    val b = gammaCorrect(color.getBlue / 255.0)
    
    0.2126 * r + 0.7152 * g + 0.0722 * b
  }
  
  /**
   * Calculate contrast ratio between two colors (1:1 to 21:1).
   */
  def contrastRatio(color1: Color, color2: Color): Double = {
    val l1 = relativeLuminance(color1)
    val l2 = relativeLuminance(color2)
    val lighter = math.max(l1, l2)
    val darker = math.min(l1, l2)
    (lighter + 0.05) / (darker + 0.05)
  }
  
  /**
   * Check if contrast meets WCAG AA standards (4.5:1 for normal text).
   */
  def meetsAccessibilityStandard(textColor: Color, backgroundColor: Color): Boolean = {
    contrastRatio(textColor, backgroundColor) >= 4.5
  }
  
  /**
   * Suggest accessible color alternatives if contrast is insufficient.
   */
  def suggestAccessibleColor(originalColor: Color, backgroundColor: Color): Color = {
    if (meetsAccessibilityStandard(originalColor, backgroundColor)) {
      originalColor
    } else {
      // If contrast is poor, suggest darker or lighter version
      val bgLuminance = relativeLuminance(backgroundColor)
      if (bgLuminance > 0.5) {
        // Light background, suggest darker text
        new Color(
          math.max(0, originalColor.getRed - 100),
          math.max(0, originalColor.getGreen - 100),
          math.max(0, originalColor.getBlue - 100)
        )
      } else {
        // Dark background, suggest lighter text
        new Color(
          math.min(255, originalColor.getRed + 100),
          math.min(255, originalColor.getGreen + 100),
          math.min(255, originalColor.getBlue + 100)
        )
      }
    }
  }
}

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

// ========== Preview System Models ==========

/**
 * Preview canvas configuration for real-time watermark rendering.
 */
case class PreviewConfig(
  canvasWidth: Int,
  canvasHeight: Int,
  zoomLevel: Double,
  panOffset: Point,
  showGrid: Boolean,
  showRulers: Boolean
)

/**
 * Preview watermark rendering data.
 */
case class PreviewRender(
  watermarkInstances: List[WatermarkInstance],
  pageDimensions: PageDimensions,
  previewConfig: PreviewConfig,
  renderTime: Long // milliseconds
)

/**
 * Preview update trigger types.
 */
enum PreviewUpdateTrigger:
  case ConfigurationChange(field: String)
  case ZoomChange(oldZoom: Double, newZoom: Double)
  case PanChange(oldOffset: Point, newOffset: Point)
  case ManualRefresh

/**
 * Preview performance metrics.
 */
case class PreviewPerformance(
  renderTimeMs: Long,
  watermarkCount: Int,
  canvasSize: Point,
  frameRate: Double,
  memoryUsage: Long
)

// ========== Enhanced UI Models ==========

/**
 * UI navigation configuration for tabbed or wizard-style interface.
 */
enum NavigationMode:
  case Tabbed
  case Wizard
  case SinglePage

/**
 * UI navigation state and configuration.
 */
case class NavigationConfig(
  mode: NavigationMode,
  currentStep: Int,
  totalSteps: Int,
  availableSteps: List[NavigationStep],
  canNavigateBack: Boolean,
  canNavigateForward: Boolean
)

/**
 * Navigation steps in the watermark configuration workflow.
 */
enum NavigationStep:
  case Upload
  case BasicConfig
  case AdvancedPositioning
  case OrientationSettings
  case FontConfiguration
  case ColorSettings
  case MultipleWatermarks
  case Preview
  case Review
  case Download

/**
 * Dynamic form section configuration based on user selections.
 */
case class FormSectionConfig(
  section: FormSection,
  visible: Boolean,
  expanded: Boolean,
  required: Boolean,
  dependencies: List[String], // Field names this section depends on
  helpText: Option[String]
)

/**
 * Form sections for watermark configuration.
 */
enum FormSection:
  case TextInput
  case PositionConfig
  case OrientationConfig
  case FontSizeConfig
  case ColorConfig
  case QuantityConfig
  case PreviewConfig
  case AdvancedOptions

/**
 * Configuration summary for review page.
 */
case class ConfigurationSummary(
  watermarkText: String,
  positionSummary: String,
  orientationSummary: String,
  fontSizeSummary: String,
  colorSummary: String,
  quantitySummary: String,
  estimatedProcessingTime: Long, // seconds
  warnings: List[String],
  recommendations: List[String]
)

/**
 * User guidance and tooltip information.
 */
case class UserGuidance(
  tooltips: Map[String, String],
  helpMessages: Map[String, String],
  quickTips: List[String],
  keyboardShortcuts: Map[String, String]
)

/**
 * Keyboard shortcut configuration.
 */
case class KeyboardShortcut(
  key: String,
  modifier: Option[String], // Ctrl, Alt, Shift
  action: String,
  description: String,
  enabled: Boolean
)

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

// Geometry models JSON codecs
given JsonCodec[PageDimensions] = DeriveJsonCodec.gen[PageDimensions]
given JsonCodec[BoundingBox] = DeriveJsonCodec.gen[BoundingBox]
given JsonCodec[WatermarkInstance] = DeriveJsonCodec.gen[WatermarkInstance]
given JsonCodec[PageWatermarkLayout] = DeriveJsonCodec.gen[PageWatermarkLayout]

// Position template JSON codec
given JsonCodec[PositionTemplate] = DeriveJsonCodec.gen[PositionTemplate]

// Position configuration JSON codec
given JsonCodec[PositionConfig] = DeriveJsonCodec.gen[PositionConfig]

// Orientation preset JSON codec
given JsonCodec[OrientationPreset] = DeriveJsonCodec.gen[OrientationPreset]

// Orientation configuration JSON codec  
given JsonCodec[OrientationConfig] = DeriveJsonCodec.gen[OrientationConfig]

// Document type JSON codec
given JsonCodec[DocumentType] = DeriveJsonCodec.gen[DocumentType]

// Font size configuration JSON codec
given JsonCodec[FontSizeConfig] = DeriveJsonCodec.gen[FontSizeConfig]

// Color palette JSON codec
given JsonCodec[ColorPalette] = DeriveJsonCodec.gen[ColorPalette]

// Color configuration JSON codec
given JsonCodec[ColorConfig] = DeriveJsonCodec.gen[ColorConfig]

// Preview models JSON codecs
given JsonCodec[PreviewConfig] = DeriveJsonCodec.gen[PreviewConfig]
given JsonCodec[PreviewRender] = DeriveJsonCodec.gen[PreviewRender]
given JsonCodec[PreviewUpdateTrigger] = DeriveJsonCodec.gen[PreviewUpdateTrigger]
given JsonCodec[PreviewPerformance] = DeriveJsonCodec.gen[PreviewPerformance]

// Enhanced UI models JSON codecs
given JsonCodec[NavigationMode] = DeriveJsonCodec.gen[NavigationMode]
given JsonCodec[NavigationStep] = DeriveJsonCodec.gen[NavigationStep]
given JsonCodec[NavigationConfig] = DeriveJsonCodec.gen[NavigationConfig]
given JsonCodec[FormSection] = DeriveJsonCodec.gen[FormSection]
given JsonCodec[FormSectionConfig] = DeriveJsonCodec.gen[FormSectionConfig]
given JsonCodec[ConfigurationSummary] = DeriveJsonCodec.gen[ConfigurationSummary]
given JsonCodec[UserGuidance] = DeriveJsonCodec.gen[UserGuidance]
given JsonCodec[KeyboardShortcut] = DeriveJsonCodec.gen[KeyboardShortcut]

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