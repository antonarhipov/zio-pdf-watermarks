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
            
            // Generate watermark instances for this page
            val watermarkInstances = generateWatermarkInstances(pageDimensions, config)
            
            // Apply each watermark instance to the page
            watermarkInstances.foreach { instance =>
              applyWatermarkToPage(document, page, instance)
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
   * Apply a single watermark instance to a PDF page.
   * 
   * @param document The PDF document
   * @param page The page to add the watermark to
   * @param watermark The watermark instance to apply
   */
  private def applyWatermarkToPage(document: PDDocument, page: PDPage, watermark: WatermarkInstance): Unit = {
    val contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
    
    try {
      // Set graphics state for transparency if needed
      val graphicsState = new PDExtendedGraphicsState()
      graphicsState.setNonStrokingAlphaConstant(0.5f) // Semi-transparent
      contentStream.setGraphicsStateParameters(graphicsState)
      
      // Set text color
      contentStream.setNonStrokingColor(watermark.color)
      
      // Begin text rendering
      contentStream.beginText()
      
      // Set font and size
      val font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
      contentStream.setFont(font, watermark.fontSize.toFloat)
      
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
      
    } finally {
      contentStream.close()
    }
  }

  /**
   * Generate watermark instances based on configuration.
   * 
   * @param pageDimensions The dimensions of the page
   * @param config The watermark configuration
   * @return List of watermark instances
   */
  def generateWatermarkInstances(pageDimensions: PageDimensions, config: WatermarkConfig): List[WatermarkInstance] = {
    (1 to config.quantity).map { index =>
      val position = generatePosition(pageDimensions, config.position, index)
      val angle = generateAngle(config.orientation, index)
      val fontSize = generateFontSize(config.fontSize, index)
      val color = generateColor(config.color, config.text, index)
      val boundingBox = calculateBoundingBox(position, config.text, fontSize, angle, pageDimensions)
      
      WatermarkInstance(
        text = config.text,
        position = position,
        angle = angle,
        fontSize = fontSize,
        color = color,
        boundingBox = boundingBox
      )
    }.toList
  }

  /**
   * Generate position based on configuration.
   */
  private def generatePosition(pageDimensions: PageDimensions, positionConfig: PositionConfig, index: Int): Point = {
    positionConfig match {
      case PositionConfig.Fixed(x, y) => Point(x, y)
      case PositionConfig.Random =>
        val random = new Random(java.lang.System.currentTimeMillis() + index)
        val margin = 50.0 // Margin from edges
        Point(
          x = margin + random.nextDouble() * (pageDimensions.width - 2 * margin),
          y = margin + random.nextDouble() * (pageDimensions.height - 2 * margin)
        )
    }
  }

  /**
   * Generate angle based on configuration.
   */
  private def generateAngle(orientationConfig: OrientationConfig, index: Int): Double = {
    orientationConfig match {
      case OrientationConfig.Fixed(angle) => angle
      case OrientationConfig.Random =>
        val random = new Random(java.lang.System.currentTimeMillis() + index + 1000)
        random.nextDouble() * 360.0
    }
  }

  /**
   * Generate font size based on configuration.
   */
  private def generateFontSize(fontSizeConfig: FontSizeConfig, index: Int): Double = {
    fontSizeConfig match {
      case FontSizeConfig.Fixed(size) => size
      case FontSizeConfig.Random(min, max) =>
        val random = new Random(java.lang.System.currentTimeMillis() + index + 2000)
        min + random.nextDouble() * (max - min)
    }
  }

  /**
   * Generate color based on configuration.
   */
  private def generateColor(colorConfig: ColorConfig, text: String, index: Int): Color = {
    colorConfig match {
      case ColorConfig.Fixed(color) => color
      case ColorConfig.RandomPerLetter =>
        // For now, return a single random color per watermark
        // Individual letter coloring will be implemented in Phase 3
        val random = new Random(java.lang.System.currentTimeMillis() + index + 3000)
        new Color(random.nextFloat(), random.nextFloat(), random.nextFloat())
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