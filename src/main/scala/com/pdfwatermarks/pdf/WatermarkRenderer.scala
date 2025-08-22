package com.pdfwatermarks.pdf

import com.pdfwatermarks.domain.*
import com.pdfwatermarks.errors.ErrorPatterns
import com.pdfwatermarks.logging.{StructuredLogging, PerformanceMonitoring}
import zio.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.{PDType1Font, PDFont, Standard14Fonts}
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.io.File
import java.awt.Color
import scala.util.Random

/**
 * Watermark text rendering implementation using Apache PDFBox.
 * 
 * This module provides comprehensive watermark text rendering capabilities
 * including text positioning, rotation, font sizing, color application,
 * and support for multiple watermark instances on PDF pages.
 */
object WatermarkRenderer {

  /**
   * Apply watermarks to a PDF document according to the configuration.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the watermarked document
   * @param config The watermark configuration
   * @return Either a domain error or the watermarked file
   */
  def applyWatermarks(sourceFile: File, targetFile: File, config: WatermarkConfig): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("watermark_apply_all") {
      ErrorPatterns.safely {
        val document = Loader.loadPDF(sourceFile)
        try {
          val totalPages = document.getNumberOfPages
          
          // Apply watermarks to all pages
          (0 until totalPages).foreach { pageIndex =>
            val page = document.getPage(pageIndex)
            val pageDimensions = PageDimensions(
              page.getMediaBox.getWidth.toDouble,
              page.getMediaBox.getHeight.toDouble
            )
            
            // Generate watermark instances for this page with their seeds
            val watermarkInstancesWithSeeds = generateWatermarkInstancesWithSeeds(pageDimensions, config)
            
            // Apply each watermark instance to the page
            watermarkInstancesWithSeeds.foreach { case (instance, seed) =>
              applyWatermarkToPageWithConfig(document, page, instance, config.color, seed)
            }
          }
          
          document.save(targetFile)
          targetFile
        } finally {
          document.close()
        }
      }.mapError {
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to apply watermarks to PDF document")
        case other => other
      }
    }

  /**
   * Apply a single watermark instance to a PDF page with color configuration support.
   * 
   * @param document The PDF document
   * @param page The page to add the watermark to
   * @param watermark The watermark instance to apply
   * @param colorConfig The color configuration for advanced coloring options
   * @param watermarkSeed The seed for this specific watermark's randomization
   */
  private def applyWatermarkToPageWithConfig(
    document: PDDocument, 
    page: PDPage, 
    watermark: WatermarkInstance, 
    colorConfig: ColorConfig,
    watermarkSeed: Long = 0L
  ): Unit = {
    val contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
    
    try {
      // Set graphics state for transparency if needed
      val graphicsState = new PDExtendedGraphicsState()
      graphicsState.setNonStrokingAlphaConstant(0.5f) // Semi-transparent
      contentStream.setGraphicsStateParameters(graphicsState)
      
      // Set font
      val font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
      contentStream.setFont(font, watermark.fontSize.toFloat)
      
      colorConfig match {
        case ColorConfig.RandomPerLetter =>
          renderTextWithPerLetterColors(contentStream, watermark, font, watermarkSeed)
        case _ =>
          renderTextWithSingleColor(contentStream, watermark, font)
      }
      
    } finally {
      contentStream.close()
    }
  }

  /**
   * Render text with per-letter random colors using watermark-specific seed.
   */
  private def renderTextWithPerLetterColors(
    contentStream: PDPageContentStream,
    watermark: WatermarkInstance,
    font: PDFont,
    watermarkSeed: Long
  ): Unit = {
    val random = new Random(watermarkSeed + 5000) // Add offset for per-letter color randomization
    val chars = watermark.text.toCharArray
    var currentX = 0.0f
    
    // Calculate base transformation matrix
    val baseTransform = Matrix.getTranslateInstance(watermark.position.x.toFloat, watermark.position.y.toFloat)
    if (watermark.angle != 0.0) {
      val rotation = Matrix.getRotateInstance(Math.toRadians(watermark.angle), 0, 0)
      baseTransform.concatenate(rotation)
    }
    
    // Render each character individually
    chars.foreach { char =>
      // Generate random color for this character
      val charColor = new Color(random.nextFloat(), random.nextFloat(), random.nextFloat())
      
      // Set color and begin text
      contentStream.setNonStrokingColor(charColor)
      contentStream.beginText()
      
      // Calculate character position
      val charTransform = baseTransform.clone()
      charTransform.concatenate(Matrix.getTranslateInstance(currentX, 0))
      contentStream.setTextMatrix(charTransform)
      
      // Draw the character
      contentStream.showText(char.toString)
      contentStream.endText()
      
      // Calculate character width for next position
      val charWidth = try {
        font.getStringWidth(char.toString) / 1000.0f * watermark.fontSize.toFloat
      } catch {
        case _: Exception => watermark.fontSize.toFloat * 0.6f // fallback estimate
      }
      currentX += charWidth
    }
  }

  /**
   * Render text with single color (legacy behavior).
   */
  private def renderTextWithSingleColor(
    contentStream: PDPageContentStream,
    watermark: WatermarkInstance,
    font: PDFont
  ): Unit = {
    // Set text color
    contentStream.setNonStrokingColor(watermark.color)
    
    // Begin text rendering
    contentStream.beginText()
    
    // Calculate text transformation matrix for positioning and rotation
    val transform = Matrix.getTranslateInstance(watermark.position.x.toFloat, watermark.position.y.toFloat)
    if (watermark.angle != 0.0) {
      val rotation = Matrix.getRotateInstance(Math.toRadians(watermark.angle), 0, 0)
      transform.concatenate(rotation)
    }
    
    contentStream.setTextMatrix(transform)
    
    // Draw the text
    contentStream.showText(watermark.text)
    
    contentStream.endText()
  }

  /**
   * Apply a single watermark instance to a PDF page (legacy method for compatibility).
   * 
   * @param document The PDF document
   * @param page The page to add the watermark to
   * @param watermark The watermark instance to apply
   */
  private def applyWatermarkToPage(document: PDDocument, page: PDPage, watermark: WatermarkInstance): Unit = {
    // Generate a seed for this watermark instance for backward compatibility
    val legacySeed = new Random().nextLong()
    applyWatermarkToPageWithConfig(document, page, watermark, ColorConfig.Fixed(watermark.color), legacySeed)
  }

  /**
   * Generate watermark instances with their seeds for independent randomization.
   * 
   * @param pageDimensions The dimensions of the page
   * @param config The watermark configuration
   * @return List of (watermark instance, seed) pairs
   */
  def generateWatermarkInstancesWithSeeds(pageDimensions: PageDimensions, config: WatermarkConfig): List[(WatermarkInstance, Long)] = {
    // Create a base random generator to ensure independent seeds for each watermark
    val baseRandom = new Random()
    
    (1 to config.quantity).map { index =>
      // Generate a unique seed for this specific watermark instance
      val watermarkSeed = baseRandom.nextLong()
      
      val position = generatePosition(pageDimensions, config.position, watermarkSeed, index)
      val angle = generateAngle(config.orientation, watermarkSeed, index)
      val fontSize = generateFontSize(config.fontSize, watermarkSeed, index)
      val color = generateColor(config.color, config.text, watermarkSeed, index)
      val boundingBox = calculateBoundingBox(position, config.text, fontSize, angle, pageDimensions)
      
      val instance = WatermarkInstance(
        text = config.text,
        position = position,
        angle = angle,
        fontSize = fontSize,
        color = color,
        boundingBox = boundingBox
      )
      
      (instance, watermarkSeed)
    }.toList
  }

  /**
   * Generate watermark instances based on configuration (backward compatibility).
   * 
   * @param pageDimensions The dimensions of the page
   * @param config The watermark configuration
   * @return List of watermark instances
   */
  def generateWatermarkInstances(pageDimensions: PageDimensions, config: WatermarkConfig): List[WatermarkInstance] = {
    generateWatermarkInstancesWithSeeds(pageDimensions, config).map(_._1)
  }

  /**
   * Generate position based on configuration using watermark-specific seed.
   */
  private def generatePosition(pageDimensions: PageDimensions, positionConfig: PositionConfig, watermarkSeed: Long, index: Int): Point = {
    positionConfig match {
      case PositionConfig.Fixed(x, y) => Point(x, y)
      case PositionConfig.Random =>
        val random = new Random(watermarkSeed + 1000) // Add offset for position randomization
        val margin = 50.0 // Margin from edges
        Point(
          x = margin + random.nextDouble() * (pageDimensions.width - 2 * margin),
          y = margin + random.nextDouble() * (pageDimensions.height - 2 * margin)
        )
      case PositionConfig.Template(template) =>
        // For simplicity, use center position for template case in this legacy renderer
        // The new service implementations handle templates properly
        Point(pageDimensions.width / 2, pageDimensions.height / 2)
    }
  }

  /**
   * Generate angle based on configuration using watermark-specific seed.
   */
  private def generateAngle(orientationConfig: OrientationConfig, watermarkSeed: Long, index: Int): Double = {
    orientationConfig match {
      case OrientationConfig.Fixed(angle) => angle
      case OrientationConfig.Random =>
        val random = new Random(watermarkSeed + 2000) // Add offset for orientation randomization
        random.nextDouble() * 360.0
      case OrientationConfig.Preset(preset) =>
        preset match {
          case OrientationPreset.Horizontal => 0.0
          case OrientationPreset.DiagonalUp => 45.0
          case OrientationPreset.Vertical => 90.0
          case OrientationPreset.DiagonalDown => 135.0
          case OrientationPreset.UpsideDown => 180.0
          case OrientationPreset.DiagonalUpReverse => 225.0
          case OrientationPreset.VerticalReverse => 270.0
          case OrientationPreset.DiagonalDownReverse => 315.0
        }
    }
  }

  /**
   * Generate font size based on configuration using watermark-specific seed.
   */
  private def generateFontSize(fontSizeConfig: FontSizeConfig, watermarkSeed: Long, index: Int): Double = {
    fontSizeConfig match {
      case FontSizeConfig.Fixed(size) => size
      case FontSizeConfig.Random(min, max) =>
        val random = new Random(watermarkSeed + 3000) // Add offset for font size randomization
        min + random.nextDouble() * (max - min)
      case FontSizeConfig.DynamicScale(baseSize, scaleFactor) =>
        // For this legacy renderer, use base size directly
        // The new service implementations handle dynamic scaling properly
        baseSize * scaleFactor
      case FontSizeConfig.Recommended(documentType) =>
        // Use reasonable defaults based on document type
        documentType match {
          case DocumentType.Legal => 18.0
          case DocumentType.Academic => 20.0
          case DocumentType.Business => 24.0
          case DocumentType.Certificate => 36.0
          case DocumentType.Marketing => 32.0
          case DocumentType.Technical => 22.0
          case DocumentType.Creative => 28.0
        }
    }
  }

  /**
   * Generate color based on configuration using watermark-specific seed.
   */
  private def generateColor(colorConfig: ColorConfig, text: String, watermarkSeed: Long, index: Int): Color = {
    colorConfig match {
      case ColorConfig.Fixed(color) => color
      case ColorConfig.RandomPerLetter =>
        // Return a single random color per watermark - individual letter coloring happens in rendering
        val random = new Random(watermarkSeed + 4000) // Add offset for color randomization
        new Color(random.nextFloat(), random.nextFloat(), random.nextFloat())
      case ColorConfig.Palette(palette) =>
        // For this legacy renderer, use a simple palette selection
        // The new service implementations handle palettes properly
        val paletteColors = getPaletteColors(palette)
        if (paletteColors.nonEmpty) {
          paletteColors(index % paletteColors.length)
        } else {
          Color.BLACK // Fallback
        }
    }
  }

  private def getPaletteColors(palette: ColorPalette): List[Color] = {
    palette match {
      case ColorPalette.Professional => List(Color.DARK_GRAY, Color.BLUE, Color.BLACK)
      case ColorPalette.Vibrant => List(Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA)
      case ColorPalette.Pastel => List(Color.PINK, Color.CYAN, Color.LIGHT_GRAY)
      case ColorPalette.Monochrome => List(Color.BLACK, Color.GRAY, Color.WHITE)
      case ColorPalette.Warm => List(Color.RED, Color.ORANGE, Color.YELLOW)
      case ColorPalette.Cool => List(Color.BLUE, Color.CYAN, Color.GREEN)
      case ColorPalette.Earth => List(new Color(139, 69, 19), new Color(34, 139, 34), new Color(160, 82, 45))
      case ColorPalette.Custom(colors) => colors
    }
  }

  /**
   * Calculate bounding box for a watermark instance.
   */
  private def calculateBoundingBox(
    position: Point, 
    text: String, 
    fontSize: Double, 
    angle: Double, 
    pageDimensions: PageDimensions
  ): BoundingBox = {
    // Approximate text width calculation (more precise calculation would require font metrics)
    val avgCharWidth = fontSize * 0.6 // Rough estimate
    val textWidth = text.length * avgCharWidth
    val textHeight = fontSize * 1.2 // Include some padding
    
    // For rotated text, we'd need more complex calculations
    // For now, use a simple rectangular bounding box
    val margin = 10.0
    BoundingBox(
      topLeft = Point(
        math.max(0, position.x - margin),
        math.max(0, position.y - margin)
      ),
      bottomRight = Point(
        math.min(pageDimensions.width, position.x + textWidth + margin),
        math.min(pageDimensions.height, position.y + textHeight + margin)
      )
    )
  }

  /**
   * Apply a single watermark to all pages of a document.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the watermarked document
   * @param text The watermark text
   * @param position The position for the watermark
   * @param fontSize The font size
   * @param angle The rotation angle in degrees
   * @param color The text color
   * @return Either a domain error or the watermarked file
   */
  def applySingleWatermark(
    sourceFile: File,
    targetFile: File,
    text: String,
    position: Point,
    fontSize: Double = 48.0,
    angle: Double = 0.0,
    color: Color = Color.LIGHT_GRAY
  ): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("watermark_apply_single") {
      ErrorPatterns.safely {
        val document = Loader.loadPDF(sourceFile)
        try {
          val totalPages = document.getNumberOfPages
          
          (0 until totalPages).foreach { pageIndex =>
            val page = document.getPage(pageIndex)
            val pageDimensions = PageDimensions(
              page.getMediaBox.getWidth.toDouble,
              page.getMediaBox.getHeight.toDouble
            )
            
            val watermark = WatermarkInstance(
              text = text,
              position = position,
              angle = angle,
              fontSize = fontSize,
              color = color,
              boundingBox = calculateBoundingBox(position, text, fontSize, angle, pageDimensions)
            )
            
            applyWatermarkToPage(document, page, watermark)
          }
          
          document.save(targetFile)
          targetFile
        } finally {
          document.close()
        }
      }.mapError {
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to apply single watermark to PDF document")
        case other => other
      }
    }

  /**
   * Preview watermark layout without actually applying it.
   * 
   * @param pageDimensions The page dimensions
   * @param config The watermark configuration
   * @return Watermark layout for preview
   */
  def previewWatermarkLayout(pageDimensions: PageDimensions, config: WatermarkConfig): IO[DomainError, PageWatermarkLayout] =
    ZIO.attempt {
      val watermarkInstances = generateWatermarkInstances(pageDimensions, config)
      PageWatermarkLayout(
        pageNumber = 1,
        pageDimensions = pageDimensions,
        watermarks = watermarkInstances
      )
    }.mapError { _ =>
      DomainError.InternalError("Failed to generate watermark layout preview")
    }

  /**
   * Validate watermark positioning against page boundaries.
   * 
   * @param position The watermark position
   * @param pageDimensions The page dimensions
   * @param fontSize The font size
   * @param text The watermark text
   * @return Either a validation error or unit
   */
  def validateWatermarkPosition(
    position: Point,
    pageDimensions: PageDimensions,
    fontSize: Double,
    text: String
  ): IO[DomainError, Unit] = {
    val avgCharWidth = fontSize * 0.6
    val textWidth = text.length * avgCharWidth
    val textHeight = fontSize * 1.2
    
    val errors = scala.collection.mutable.ListBuffer[String]()
    
    if (position.x < 0 || position.x > pageDimensions.width) {
      errors += s"X position ${position.x} is outside page width (0-${pageDimensions.width})"
    }
    
    if (position.y < 0 || position.y > pageDimensions.height) {
      errors += s"Y position ${position.y} is outside page height (0-${pageDimensions.height})"
    }
    
    if (position.x + textWidth > pageDimensions.width) {
      errors += "Watermark text extends beyond page width"
    }
    
    if (position.y + textHeight > pageDimensions.height) {
      errors += "Watermark text extends beyond page height"
    }
    
    if (errors.nonEmpty) {
      ZIO.fail(DomainError.InvalidConfiguration(errors.toList))
    } else {
      ZIO.unit
    }
  }

  /**
   * Calculate optimal font size for given page and text length.
   * 
   * @param pageDimensions The page dimensions
   * @param text The watermark text
   * @param maxWidthRatio Maximum ratio of page width the text should occupy
   * @return Recommended font size
   */
  def calculateOptimalFontSize(
    pageDimensions: PageDimensions,
    text: String,
    maxWidthRatio: Double = 0.8
  ): IO[DomainError, Double] =
    ZIO.attempt {
      val maxWidth = pageDimensions.width * maxWidthRatio
      val avgCharWidth = 0.6 // Rough ratio of character width to font size
      val optimalFontSize = maxWidth / (text.length * avgCharWidth)
      
      // Clamp to reasonable bounds
      math.max(ConfigConstraints.MinFontSize, 
               math.min(ConfigConstraints.MaxFontSize, optimalFontSize))
    }.mapError { _ =>
      DomainError.InternalError("Failed to calculate optimal font size")
    }

  /**
   * Check for watermark overlaps in a layout.
   * 
   * @param watermarks List of watermark instances
   * @return List of overlapping pairs
   */
  def detectOverlaps(watermarks: List[WatermarkInstance]): List[(Int, Int)] = {
    val overlaps = scala.collection.mutable.ListBuffer[(Int, Int)]()
    
    for {
      i <- watermarks.indices
      j <- (i + 1) until watermarks.length
    } {
      if (watermarks(i).boundingBox.overlaps(watermarks(j).boundingBox)) {
        overlaps += ((i, j))
      }
    }
    
    overlaps.toList
  }

  /**
   * Get available fonts for watermark rendering.
   * 
   * @return List of available font names
   */
  def getAvailableFonts(): List[String] = {
    List(
      "Helvetica",
      "Helvetica-Bold",
      "Helvetica-Oblique",
      "Helvetica-BoldOblique",
      "Times-Roman",
      "Times-Bold",
      "Times-Italic",
      "Times-BoldItalic",
      "Courier",
      "Courier-Bold",
      "Courier-Oblique",
      "Courier-BoldOblique"
    )
  }

  /**
   * Get font object by name.
   * 
   * @param fontName The font name
   * @return PDFont object or default font
   */
  def getFontByName(fontName: String): PDFont = {
    fontName.toLowerCase match {
      case "helvetica" => new PDType1Font(Standard14Fonts.FontName.HELVETICA)
      case "helvetica-bold" => new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
      case "helvetica-oblique" => new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
      case "helvetica-boldoblique" => new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE)
      case "times-roman" => new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)
      case "times-bold" => new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)
      case "times-italic" => new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC)
      case "times-bolditalic" => new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC)
      case "courier" => new PDType1Font(Standard14Fonts.FontName.COURIER)
      case "courier-bold" => new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD)
      case "courier-oblique" => new PDType1Font(Standard14Fonts.FontName.COURIER_OBLIQUE)
      case "courier-boldoblique" => new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE)
      case _ => new PDType1Font(Standard14Fonts.FontName.HELVETICA) // Default fallback
    }
  }

  /**
   * Estimate rendering time for watermark application.
   * 
   * @param pageCount Number of pages in document
   * @param watermarkCount Number of watermarks per page
   * @return Estimated processing time in milliseconds
   */
  def estimateRenderingTime(pageCount: Int, watermarkCount: Int): Long = {
    // Rough estimate: 50ms per watermark + 10ms per page overhead
    (pageCount * watermarkCount * 50) + (pageCount * 10)
  }
}