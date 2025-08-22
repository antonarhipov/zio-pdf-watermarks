package com.pdfwatermarks.services

import com.pdfwatermarks.domain.*
import zio.*

/**
 * ZIO layer configuration for dependency injection.
 * 
 * This object provides layer composition and service implementations
 * for the PDF Watermarking Application using ZIO's dependency injection
 * system. Layers are organized to support both production and test environments.
 */
object Layers {

  /**
   * Complete application layer containing all services.
   * This is the main layer used by the application.
   */
  val appLayer: ZLayer[Any, Nothing, 
    PdfProcessingService & 
    WatermarkRenderingService & 
    FileManagementService & 
    SessionManagementService & 
    ValidationService &
    TempFileManagementService &
    DownloadTrackingService &
    com.pdfwatermarks.config.ApplicationConfig
  ] = {
    import com.pdfwatermarks.config.{ApplicationConfig, TempFileConfig}
  
    val configLayer = ApplicationConfig.layer.orDie
  
    val tempFileConfigLayer = configLayer >>> ZLayer.fromFunction((config: ApplicationConfig) => config.tempFiles)
    
    val pdfProcessingLayer = ZLayer.succeed(PdfProcessingServiceLive())
    val watermarkRenderingLayer = ZLayer.succeed(WatermarkRenderingServiceLive()) 
    val fileManagementLayer = ZLayer.succeed(FileManagementServiceLive())
    val sessionManagementLayer = ZLayer.succeed(SessionManagementServiceLive())
    val validationLayer = ZLayer.succeed(ValidationServiceLive())
    val tempFileManagementLayer = tempFileConfigLayer >>> TempFileManagementService.layer
    val downloadTrackingLayer = ZLayer.succeed(DownloadTrackingServiceLive())
  
    configLayer ++
    pdfProcessingLayer ++
    watermarkRenderingLayer ++
    fileManagementLayer ++
    sessionManagementLayer ++
    validationLayer ++
    tempFileManagementLayer ++
    downloadTrackingLayer
  }

  /**
   * Test layer with mock implementations for testing.
   */
  val testLayer: ZLayer[Any, Nothing,
    PdfProcessingService &
    WatermarkRenderingService &
    FileManagementService &
    SessionManagementService &
    ValidationService &
    TempFileManagementService &
    DownloadTrackingService
  ] = {
    import com.pdfwatermarks.config.TempFileConfig
    
    val tempFileConfig = TempFileConfig(
      baseDir = "./test-tmp",
      maxAgeHours = 1,
      maxTotalSizeMb = 10,
      cleanupIntervalMinutes = 5,
      uploadPrefix = "test-upload-",
      processedPrefix = "test-processed-"
    )
    
    val pdfProcessingTestLayer = ZLayer.succeed(PdfProcessingServiceTest())
    val watermarkRenderingTestLayer = ZLayer.succeed(WatermarkRenderingServiceTest())
    val fileManagementTestLayer = ZLayer.succeed(FileManagementServiceTest())
    val sessionManagementTestLayer = ZLayer.succeed(SessionManagementServiceTest())
    val validationTestLayer = ZLayer.succeed(ValidationServiceTest())
    val tempFileManagementTestLayer = ZLayer.succeed(tempFileConfig) >>> TempFileManagementService.layer
    val downloadTrackingTestLayer = ZLayer.succeed(DownloadTrackingServiceTest())
    
    pdfProcessingTestLayer ++
    watermarkRenderingTestLayer ++
    fileManagementTestLayer ++
    sessionManagementTestLayer ++
    validationTestLayer ++
    tempFileManagementTestLayer ++
    downloadTrackingTestLayer
  }

  // ========== Live Service Implementations ==========
  // Note: These are placeholder implementations that will be fully developed in later tasks

  /**
   * Live implementation of PDF processing service using Apache PDFBox.
   */
  case class PdfProcessingServiceLive() extends PdfProcessingService {
    import com.pdfwatermarks.pdf.PdfProcessor

    override def loadPdf(file: java.io.File): IO[DomainError, PdfDocument] =
      PdfProcessor.loadPdf(file)

    override def applyWatermarks(
      document: PdfDocument, 
      config: WatermarkConfig
    ): IO[DomainError, java.io.File] = {
      import com.pdfwatermarks.pdf.WatermarkRenderer
      
      for {
        // Get the actual source file path from the document
        sourceFile <- ZIO.attemptBlocking {
          // Try to find the uploaded file in the temp directory
          val possiblePaths = List(
            new java.io.File(s"./tmp/${document.id}.pdf"),
            new java.io.File(s"./tmp/upload-${document.id}"),
            new java.io.File(s"./tmp/${document.filename}"),
            new java.io.File(s"tmp/${document.filename}"),
            new java.io.File(document.filename) // Last resort - direct filename
          )
          
          possiblePaths.find(_.exists()).getOrElse {
            throw new java.io.FileNotFoundException(s"Could not find uploaded file for document ${document.id}")
          }
        }.mapError(err => DomainError.InternalError(s"Failed to locate source file: ${err.getMessage}"))
        
        // Create a temporary file for the watermarked output
        outputFile <- ZIO.attemptBlocking {
          val tempDir = new java.io.File("./tmp")
          tempDir.mkdirs() // Ensure directory exists
          val timestamp = java.lang.System.currentTimeMillis()
          val uniqueId = java.util.UUID.randomUUID().toString.take(8)
          new java.io.File(tempDir, s"processed-${timestamp}-${uniqueId}-${document.filename}")
        }.mapError(err => DomainError.InternalError(s"Failed to create temp output file: ${err.getMessage}"))
        
        _ <- ZIO.logInfo(s"Applying watermarks from ${sourceFile.getAbsolutePath} to ${outputFile.getAbsolutePath}")
        
        // Apply watermarks using the actual WatermarkRenderer implementation
        result <- WatermarkRenderer.applyWatermarks(sourceFile, outputFile, config)
        
        _ <- ZIO.logInfo(s"Watermark application completed successfully for ${document.filename}")
        
      } yield result
    }

    override def getPageCount(file: java.io.File): IO[DomainError, Int] =
      PdfProcessor.getPageCount(file)

    override def getPageDimensions(file: java.io.File, pageNumber: Int): IO[DomainError, PageDimensions] =
      PdfProcessor.getPageDimensions(file, pageNumber)
  }

  /**
   * Live implementation of watermark rendering service.
   * TODO: Implement watermark layout algorithms (Task 66-100)
   */
  case class WatermarkRenderingServiceLive() extends WatermarkRenderingService {
    import scala.util.Random
    
    override def generatePageLayout(
      pageDimensions: PageDimensions,
      config: WatermarkConfig
    ): IO[DomainError, PageWatermarkLayout] = {
      for {
        // Validate configuration
        _ <- validateWatermarkConfig(config, pageDimensions)
        
        // Generate watermark instances based on configuration
        watermarks <- generateWatermarkInstances(pageDimensions, config)
        
        layout = PageWatermarkLayout(
          pageNumber = 1, // Will be updated by caller for specific pages
          pageDimensions = pageDimensions,
          watermarks = watermarks
        )
      } yield layout
    }

    private def generateWatermarkInstances(
      pageDimensions: PageDimensions,
      config: WatermarkConfig
    ): IO[DomainError, List[WatermarkInstance]] = {
      for {
        // Generate positions based on configuration
        positions <- generatePositions(pageDimensions, config)
        
        // Generate orientations
        orientations <- generateOrientations(config, positions.length)
        
        // Generate font sizes
        fontSizes <- generateFontSizes(config, pageDimensions, positions.length)
        
        // Generate colors
        colors <- generateColors(config, positions.length)
        
        // Create watermark instances
        instances = positions.zip(orientations).zip(fontSizes).zip(colors).map {
          case (((pos, angle), fontSize), color) =>
            val boundingBox = calculateBoundingBox(pos, config.text, fontSize, angle)
            WatermarkInstance(
              text = config.text,
              position = pos,
              angle = angle,
              fontSize = fontSize,
              color = color,
              boundingBox = boundingBox
            )
        }
      } yield instances
    }

    private def generatePositions(
      pageDimensions: PageDimensions,
      config: WatermarkConfig
    ): IO[DomainError, List[Point]] = {
      config.position match {
        case PositionConfig.Fixed(x, y) =>
          // For fixed position, replicate the position for the quantity specified
          val position = Point(x, y)
          ZIO.succeed(List.fill(config.quantity)(position))
          
        case PositionConfig.Random =>
          // Generate random non-overlapping positions
          calculateNonOverlappingPositions(
            pageDimensions, 
            config.quantity,
            // Use average font size for spacing calculations
            config.fontSize match {
              case FontSizeConfig.Fixed(size) => size
              case FontSizeConfig.Random(min, max) => (min + max) / 2
              case FontSizeConfig.DynamicScale(baseSize, _) => baseSize
              case FontSizeConfig.Recommended(_) => 24.0 // Use default size for spacing calculations
            }
          )
          
        case PositionConfig.Template(template) =>
          generateTemplatePositions(pageDimensions, template, config.quantity)
      }
    }

    private def generateTemplatePositions(
      pageDimensions: PageDimensions,
      template: PositionTemplate,
      requestedQuantity: Int
    ): IO[DomainError, List[Point]] = {
      ZIO.attempt {
        val margin = 50.0 // Margin from edges
        val width = pageDimensions.width
        val height = pageDimensions.height
        
        template match {
          case PositionTemplate.Center =>
            List(Point(width / 2, height / 2))
            
          case PositionTemplate.TopLeft =>
            List(Point(margin, margin))
            
          case PositionTemplate.TopRight =>
            List(Point(width - margin, margin))
            
          case PositionTemplate.BottomLeft =>
            List(Point(margin, height - margin))
            
          case PositionTemplate.BottomRight =>
            List(Point(width - margin, height - margin))
            
          case PositionTemplate.TopCenter =>
            List(Point(width / 2, margin))
            
          case PositionTemplate.BottomCenter =>
            List(Point(width / 2, height - margin))
            
          case PositionTemplate.LeftCenter =>
            List(Point(margin, height / 2))
            
          case PositionTemplate.RightCenter =>
            List(Point(width - margin, height / 2))
            
          case PositionTemplate.FourCorners =>
            List(
              Point(margin, margin),
              Point(width - margin, margin),
              Point(margin, height - margin),
              Point(width - margin, height - margin)
            )
            
          case PositionTemplate.Grid(rows, cols) =>
            val xStep = (width - 2 * margin) / (cols - 1)
            val yStep = (height - 2 * margin) / (rows - 1)
            
            for {
              row <- (0 until rows).toList
              col <- (0 until cols).toList
            } yield Point(
              margin + col * xStep,
              margin + row * yStep
            )
            
          case PositionTemplate.Diagonal =>
            val count = math.min(requestedQuantity, 10) // Limit diagonal positions
            val xStep = (width - 2 * margin) / (count - 1)
            val yStep = (height - 2 * margin) / (count - 1)
            
            (0 until count).map { i =>
              Point(margin + i * xStep, margin + i * yStep)
            }.toList
            
          case PositionTemplate.Border =>
            val spacing = 100.0
            
            // Generate top edge positions
            val topPositions = {
              val count = ((width - 2 * margin) / spacing).toInt
              (0 to count).map(i => Point(margin + i * spacing, margin))
            }
            
            // Generate bottom edge positions
            val bottomPositions = {
              val count = ((width - 2 * margin) / spacing).toInt
              (0 to count).map(i => Point(margin + i * spacing, height - margin))
            }
            
            // Generate left edge positions
            val leftPositions = {
              val count = ((height - 2 * margin - spacing) / spacing).toInt
              (1 to count).map(i => Point(margin, margin + i * spacing))
            }
            
            // Generate right edge positions
            val rightPositions = {
              val count = ((height - 2 * margin - spacing) / spacing).toInt
              (1 to count).map(i => Point(width - margin, margin + i * spacing))
            }
            
            (topPositions ++ bottomPositions ++ leftPositions ++ rightPositions).toList
        }
      }.map(positions => 
        // If template generates more positions than requested, take first N
        // If template generates fewer, repeat the pattern
        if (positions.length >= requestedQuantity) {
          positions.take(requestedQuantity)
        } else if (positions.nonEmpty) {
          val repetitions = (requestedQuantity + positions.length - 1) / positions.length
          List.fill(repetitions)(positions).flatten.take(requestedQuantity)
        } else {
          List.empty
        }
      ).mapError(ex => DomainError.InternalError(s"Failed to generate template positions: ${ex.getMessage}"))
    }

    private def generateOrientations(
      config: WatermarkConfig,
      count: Int
    ): IO[Nothing, List[Double]] = {
      config.orientation match {
        case OrientationConfig.Fixed(angle) =>
          ZIO.succeed(List.fill(count)(angle))
          
        case OrientationConfig.Random =>
          ZIO.succeed {
            (1 to count).map(_ => Random.nextDouble() * 360.0).toList
          }
          
        case OrientationConfig.Preset(preset) =>
          val angle = presetToAngle(preset)
          ZIO.succeed(List.fill(count)(angle))
      }
    }

    private def presetToAngle(preset: OrientationPreset): Double = {
      preset match {
        case OrientationPreset.Horizontal        => 0.0
        case OrientationPreset.DiagonalUp        => 45.0
        case OrientationPreset.Vertical          => 90.0
        case OrientationPreset.DiagonalDown      => 135.0
        case OrientationPreset.UpsideDown        => 180.0
        case OrientationPreset.DiagonalUpReverse => 225.0
        case OrientationPreset.VerticalReverse   => 270.0
        case OrientationPreset.DiagonalDownReverse => 315.0
      }
    }

    private def generateFontSizes(
      config: WatermarkConfig,
      pageDimensions: PageDimensions,
      count: Int
    ): IO[Nothing, List[Double]] = {
      config.fontSize match {
        case FontSizeConfig.Fixed(size) =>
          ZIO.succeed(List.fill(count)(size))
          
        case FontSizeConfig.Random(min, max) =>
          ZIO.succeed {
            (1 to count).map(_ => min + Random.nextDouble() * (max - min)).toList
          }
          
        case FontSizeConfig.DynamicScale(baseSize, scaleFactor) =>
          val scaledSize = FontScaling.applyDynamicScaling(baseSize, scaleFactor, pageDimensions)
          ZIO.succeed(List.fill(count)(scaledSize))
          
        case FontSizeConfig.Recommended(documentType) =>
          val recommendedSize = FontScaling.getRecommendedSize(pageDimensions, documentType)
          ZIO.succeed(List.fill(count)(recommendedSize))
      }
    }

    private def generateColors(
      config: WatermarkConfig,
      count: Int
    ): IO[Nothing, List[java.awt.Color]] = {
      config.color match {
        case ColorConfig.Fixed(color) =>
          ZIO.succeed(List.fill(count)(color))
          
        case ColorConfig.RandomPerLetter =>
          generateRandomColors(config.text * count) // Repeat text for each instance
            .map(_.take(count)) // Take only what we need
            
        case ColorConfig.Palette(palette) =>
          generatePaletteColors(palette, count)
      }
    }

    private def generatePaletteColors(palette: ColorPalette, count: Int): IO[Nothing, List[java.awt.Color]] = {
      ZIO.succeed {
        val paletteColors = getPaletteColors(palette)
        if (paletteColors.isEmpty) {
          List.fill(count)(java.awt.Color.BLACK) // Fallback
        } else {
          // Cycle through palette colors
          (0 until count).map(i => paletteColors(i % paletteColors.length)).toList
        }
      }
    }

    private def getPaletteColors(palette: ColorPalette): List[java.awt.Color] = {
      import java.awt.Color
      
      palette match {
        case ColorPalette.Professional =>
          List(
            new Color(47, 79, 79),   // Dark slate gray
            new Color(25, 25, 112),  // Midnight blue
            new Color(105, 105, 105), // Dim gray
            new Color(72, 61, 139),  // Dark slate blue
            new Color(47, 79, 79)    // Dark slate gray
          )
          
        case ColorPalette.Vibrant =>
          List(
            new Color(255, 69, 0),   // Red orange
            new Color(50, 205, 50),  // Lime green
            new Color(30, 144, 255), // Dodger blue
            new Color(255, 20, 147), // Deep pink
            new Color(255, 215, 0)   // Gold
          )
          
        case ColorPalette.Pastel =>
          List(
            new Color(255, 182, 193), // Light pink
            new Color(173, 216, 230), // Light blue
            new Color(144, 238, 144), // Light green
            new Color(255, 218, 185), // Peach puff
            new Color(221, 160, 221)  // Plum
          )
          
        case ColorPalette.Monochrome =>
          List(
            new Color(0, 0, 0),       // Black
            new Color(64, 64, 64),    // Dark gray
            new Color(128, 128, 128), // Gray
            new Color(192, 192, 192), // Light gray
            new Color(255, 255, 255)  // White
          )
          
        case ColorPalette.Warm =>
          List(
            new Color(220, 20, 60),   // Crimson
            new Color(255, 140, 0),   // Dark orange
            new Color(255, 215, 0),   // Gold
            new Color(255, 69, 0),    // Orange red
            new Color(255, 99, 71)    // Tomato
          )
          
        case ColorPalette.Cool =>
          List(
            new Color(70, 130, 180),  // Steel blue
            new Color(32, 178, 170),  // Light sea green
            new Color(123, 104, 238), // Medium slate blue
            new Color(0, 191, 255),   // Deep sky blue
            new Color(72, 209, 204)   // Medium turquoise
          )
          
        case ColorPalette.Earth =>
          List(
            new Color(139, 69, 19),   // Saddle brown
            new Color(34, 139, 34),   // Forest green
            new Color(160, 82, 45),   // Sienna
            new Color(107, 142, 35),  // Olive drab
            new Color(210, 180, 140)  // Tan
          )
          
        case ColorPalette.Custom(colors) =>
          colors
      }
    }

    private def calculateBoundingBox(
      position: Point,
      text: String,
      fontSize: Double,
      angle: Double
    ): BoundingBox = {
      // Approximate text dimensions (this is a simplified calculation)
      val textWidth = text.length * fontSize * 0.6 // Rough character width estimation
      val textHeight = fontSize * 1.2 // Include line spacing
      
      // For simplicity, ignore rotation effects on bounding box for now
      // In a complete implementation, we'd calculate rotated bounds
      BoundingBox(
        topLeft = Point(position.x, position.y),
        bottomRight = Point(position.x + textWidth, position.y + textHeight)
      )
    }

    override def calculateNonOverlappingPositions(
      pageDimensions: PageDimensions,
      watermarkCount: Int,
      fontSize: Double
    ): IO[DomainError, List[Point]] = {
      ZIO.attempt {
        val positions = scala.collection.mutable.ListBuffer[Point]()
        val margin = fontSize * 2 // Minimum spacing between watermarks
        val maxAttempts = watermarkCount * 50 // Prevent infinite loops
        
        var attempts = 0
        while (positions.length < watermarkCount && attempts < maxAttempts) {
          attempts += 1
          
          val x = Random.nextDouble() * (pageDimensions.width - fontSize * 10) + fontSize * 2
          val y = Random.nextDouble() * (pageDimensions.height - fontSize * 2) + fontSize
          val candidate = Point(x, y)
          
          // Check if position conflicts with existing positions
          val conflicts = positions.exists { existing =>
            val distance = math.sqrt(
              math.pow(candidate.x - existing.x, 2) + 
              math.pow(candidate.y - existing.y, 2)
            )
            distance < margin
          }
          
          if (!conflicts) {
            positions += candidate
          }
        }
        
        positions.toList
      }.mapError(ex => DomainError.InternalError(s"Failed to generate positions: ${ex.getMessage}"))
    }

    override def generateRandomColors(text: String): IO[Nothing, List[java.awt.Color]] = {
      ZIO.succeed {
        text.map { _ =>
          new java.awt.Color(
            Random.nextFloat(),
            Random.nextFloat(), 
            Random.nextFloat()
          )
        }.toList
      }
    }

    private def validateWatermarkConfig(
      config: WatermarkConfig,
      pageDimensions: PageDimensions
    ): IO[DomainError, Unit] = {
      val errors = scala.collection.mutable.ListBuffer[String]()
      
      // Validate quantity
      if (!ConfigConstraints.isValidQuantity(config.quantity)) {
        errors += s"Watermark quantity must be between 1 and ${ConfigConstraints.MaxWatermarkQuantity}"
      }
      
      // Validate fixed position is within page bounds
      config.position match {
        case PositionConfig.Fixed(x, y) =>
          if (x < 0 || x > pageDimensions.width || y < 0 || y > pageDimensions.height) {
            errors += s"Fixed position ($x, $y) is outside page dimensions (${pageDimensions.width} x ${pageDimensions.height})"
          }
        case _ => // Random position is always valid
      }
      
      // Validate fixed angle
      config.orientation match {
        case OrientationConfig.Fixed(angle) =>
          if (!ConfigConstraints.isValidAngle(angle)) {
            errors += s"Angle must be between 0 and 360 degrees, got $angle"
          }
        case _ => // Random orientation is always valid
      }
      
      // Validate font sizes
      config.fontSize match {
        case FontSizeConfig.Fixed(size) =>
          if (!ConfigConstraints.isValidFontSize(size)) {
            errors += s"Font size must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}"
          }
        case FontSizeConfig.Random(min, max) =>
          if (!ConfigConstraints.isValidFontSize(min) || !ConfigConstraints.isValidFontSize(max)) {
            errors += s"Font size range must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}"
          }
          if (min >= max) {
            errors += "Minimum font size must be less than maximum font size"
          }
        case FontSizeConfig.DynamicScale(baseSize, scaleFactor) =>
          if (!ConfigConstraints.isValidFontSize(baseSize)) {
            errors += s"Base font size must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}"
          }
          if (scaleFactor <= 0.0) {
            errors += "Scale factor must be positive"
          }
        case FontSizeConfig.Recommended(_) =>
          // Recommended font sizes are always valid as they are calculated dynamically
          ()
      }
      
      if (errors.nonEmpty) {
        ZIO.fail(DomainError.InvalidConfiguration(errors.toList))
      } else {
        ZIO.unit
      }
    }
  }

  /**
   * Live implementation of file management service.
   * TODO: Implement file handling operations (Task 36-40, 57-61)
   */
  case class FileManagementServiceLive() extends FileManagementService {
    override def storeUploadedFile(uploadInfo: UploadInfo): IO[DomainError, java.io.File] =
      ZIO.succeed(new java.io.File(uploadInfo.tempPath))

    override def validateFile(file: java.io.File): IO[DomainError, Unit] =
      if (file.getName.toLowerCase.endsWith(".pdf")) ZIO.unit
      else ZIO.fail(DomainError.InvalidFileFormat("Only PDF files are supported"))

    override def cleanupTempFiles(files: List[java.io.File]): UIO[Unit] =
      ZIO.succeed(()) // TODO: Implement file cleanup

    override def generateProcessedFilename(originalFilename: String): UIO[String] =
      ZIO.succeed(s"watermarked_$originalFilename")
  }

  /**
   * Live implementation of session management service.
   * TODO: Implement session storage and management (Task 62-65)
   */
  case class SessionManagementServiceLive() extends SessionManagementService {
    private val sessions = scala.collection.concurrent.TrieMap[String, UserSession]()

    override def createSession(): UIO[UserSession] =
      ZIO.succeed {
        val session = UserSession(
          sessionId = java.util.UUID.randomUUID().toString,
          uploadedDocument = None,
          watermarkConfig = None,
          createdAt = java.time.Instant.now(),
          lastActivity = java.time.Instant.now()
        )
        sessions.put(session.sessionId, session)
        session
      }

    override def getSession(sessionId: String): IO[DomainError, UserSession] =
      ZIO.fromOption(sessions.get(sessionId))
        .orElseFail(DomainError.SessionNotFound(sessionId))

    override def updateSessionWithDocument(
      sessionId: String, 
      document: PdfDocument
    ): IO[DomainError, UserSession] =
      for {
        session <- getSession(sessionId)
        updated = session.copy(
          uploadedDocument = Some(document),
          lastActivity = java.time.Instant.now()
        )
        _ <- ZIO.succeed(sessions.put(sessionId, updated))
      } yield updated

    override def updateSessionWithConfig(
      sessionId: String, 
      config: WatermarkConfig
    ): IO[DomainError, UserSession] =
      for {
        session <- getSession(sessionId)
        updated = session.copy(
          watermarkConfig = Some(config),
          lastActivity = java.time.Instant.now()
        )
        _ <- ZIO.succeed(sessions.put(sessionId, updated))
      } yield updated

    override def updateDocumentStatus(
      sessionId: String,
      newStatus: DocumentStatus
    ): IO[DomainError, UserSession] =
      for {
        session <- getSession(sessionId)
        document <- ZIO.fromOption(session.uploadedDocument)
          .orElseFail(DomainError.InvalidConfiguration(List("No document in session to update status")))
        updatedDocument = document.copy(status = newStatus)
        updated = session.copy(
          uploadedDocument = Some(updatedDocument),
          lastActivity = java.time.Instant.now()
        )
        _ <- ZIO.succeed(sessions.put(sessionId, updated))
      } yield updated

    override def updateDocumentProcessedFilePath(
      sessionId: String,
      processedFilePath: String
    ): IO[DomainError, UserSession] =
      for {
        session <- getSession(sessionId)
        document <- ZIO.fromOption(session.uploadedDocument)
          .orElseFail(DomainError.InvalidConfiguration(List("No document in session to update processed file path")))
        updatedDocument = document.copy(processedFilePath = Some(processedFilePath))
        updated = session.copy(
          uploadedDocument = Some(updatedDocument),
          lastActivity = java.time.Instant.now()
        )
        _ <- ZIO.succeed(sessions.put(sessionId, updated))
      } yield updated

    override def cleanupExpiredSessions(): UIO[Unit] =
      ZIO.succeed(()) // TODO: Implement session cleanup
  }

  /**
   * Live implementation of preview service.
   * Real-time watermark preview functionality (Tasks 91-95)
   */
  case class PreviewServiceLive() extends PreviewService {
    override def generatePreview(
      pageDimensions: PageDimensions,
      config: WatermarkConfig,
      previewConfig: PreviewConfig
    ): IO[DomainError, PreviewRender] = {
      for {
        startTime <- ZIO.clockWith(_.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        
        // Generate watermark layout using existing service
        layout <- WatermarkRenderingServiceLive().generatePageLayout(pageDimensions, config)
        
        endTime <- ZIO.clockWith(_.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        renderTime = endTime - startTime
        
        previewRender = PreviewRender(
          watermarkInstances = layout.watermarks,
          pageDimensions = pageDimensions,
          previewConfig = previewConfig,
          renderTime = renderTime
        )
      } yield previewRender
    }

    override def updateZoom(
      currentRender: PreviewRender,
      newZoomLevel: Double
    ): IO[DomainError, PreviewRender] = {
      val updatedConfig = currentRender.previewConfig.copy(zoomLevel = newZoomLevel)
      ZIO.succeed(currentRender.copy(previewConfig = updatedConfig))
    }

    override def updatePan(
      currentRender: PreviewRender,
      newPanOffset: Point
    ): IO[DomainError, PreviewRender] = {
      val updatedConfig = currentRender.previewConfig.copy(panOffset = newPanOffset)
      ZIO.succeed(currentRender.copy(previewConfig = updatedConfig))
    }

    override def optimizePreview(
      config: WatermarkConfig,
      previewConfig: PreviewConfig
    ): IO[Nothing, PreviewConfig] = {
      ZIO.succeed {
        val watermarkCount = config.quantity
        val canvasArea = previewConfig.canvasWidth * previewConfig.canvasHeight
        
        // Performance optimization based on complexity
        val optimizedConfig = if (watermarkCount > 50 || canvasArea > 1920 * 1080) {
          // High complexity - reduce quality for performance
          previewConfig.copy(
            showGrid = false,
            showRulers = false
          )
        } else if (watermarkCount > 20 || canvasArea > 1280 * 720) {
          // Medium complexity - moderate optimization
          previewConfig.copy(
            showGrid = previewConfig.showGrid, // Keep user preference
            showRulers = false // Disable rulers for performance
          )
        } else {
          // Low complexity - keep all features
          previewConfig
        }
        
        optimizedConfig
      }
    }

    override def getPerformanceMetrics(
      previewRender: PreviewRender
    ): IO[Nothing, PreviewPerformance] = {
      ZIO.succeed {
        val runtime = java.lang.Runtime.getRuntime
        val memoryUsage = runtime.totalMemory() - runtime.freeMemory()
        
        PreviewPerformance(
          renderTimeMs = previewRender.renderTime,
          watermarkCount = previewRender.watermarkInstances.length,
          canvasSize = Point(
            previewRender.previewConfig.canvasWidth.toDouble,
            previewRender.previewConfig.canvasHeight.toDouble
          ),
          frameRate = if (previewRender.renderTime > 0) 1000.0 / previewRender.renderTime else 60.0,
          memoryUsage = memoryUsage
        )
      }
    }

    override def shouldUpdatePreview(
      trigger: PreviewUpdateTrigger,
      currentConfig: WatermarkConfig
    ): IO[Nothing, Boolean] = {
      ZIO.succeed {
        trigger match {
          case PreviewUpdateTrigger.ConfigurationChange(field) =>
            // Always update for configuration changes
            true
            
          case PreviewUpdateTrigger.ZoomChange(oldZoom, newZoom) =>
            // Update if zoom change is significant (> 10%)
            math.abs(newZoom - oldZoom) / oldZoom > 0.1
            
          case PreviewUpdateTrigger.PanChange(oldOffset, newOffset) =>
            // Update if pan change is significant
            val distance = math.sqrt(
              math.pow(newOffset.x - oldOffset.x, 2) + 
              math.pow(newOffset.y - oldOffset.y, 2)
            )
            distance > 10.0 // Update if moved more than 10 pixels
            
          case PreviewUpdateTrigger.ManualRefresh =>
            // Always update for manual refresh
            true
        }
      }
    }
  }

  /**
   * Live implementation of enhanced UI service.
   * Enhanced user interface functionality (Tasks 96-100)
   */
  case class EnhancedUIServiceLive() extends EnhancedUIService {
    private val allSteps = List(
      NavigationStep.Upload,
      NavigationStep.BasicConfig,
      NavigationStep.AdvancedPositioning,
      NavigationStep.OrientationSettings,
      NavigationStep.FontConfiguration,
      NavigationStep.ColorSettings,
      NavigationStep.MultipleWatermarks,
      NavigationStep.Preview,
      NavigationStep.Review,
      NavigationStep.Download
    )

    override def getNavigationConfig(
      currentStep: NavigationStep,
      watermarkConfig: Option[WatermarkConfig]
    ): IO[Nothing, NavigationConfig] = {
      ZIO.succeed {
        val currentIndex = allSteps.indexOf(currentStep)
        val availableSteps = getAvailableSteps(watermarkConfig)
        
        NavigationConfig(
          mode = NavigationMode.Wizard, // Default to wizard mode
          currentStep = currentIndex,
          totalSteps = allSteps.length,
          availableSteps = availableSteps,
          canNavigateBack = currentIndex > 0,
          canNavigateForward = currentIndex < allSteps.length - 1 && 
                               watermarkConfig.isDefined && 
                               isStepComplete(currentStep, watermarkConfig.get)
        )
      }
    }

    private def getAvailableSteps(watermarkConfig: Option[WatermarkConfig]): List[NavigationStep] = {
      if (watermarkConfig.isEmpty) {
        List(NavigationStep.Upload, NavigationStep.BasicConfig)
      } else {
        allSteps // All steps available once basic config exists
      }
    }

    private def isStepComplete(step: NavigationStep, config: WatermarkConfig): Boolean = {
      step match {
        case NavigationStep.Upload => true // Always complete if config exists
        case NavigationStep.BasicConfig => config.text.trim.nonEmpty
        case NavigationStep.AdvancedPositioning => true // Optional step
        case NavigationStep.OrientationSettings => true // Optional step
        case NavigationStep.FontConfiguration => true // Optional step
        case NavigationStep.ColorSettings => true // Optional step
        case NavigationStep.MultipleWatermarks => true // Optional step
        case NavigationStep.Preview => true // Optional step
        case NavigationStep.Review => true // Optional step
        case NavigationStep.Download => true // Final step
      }
    }

    override def navigateToStep(
      currentStep: NavigationStep,
      targetStep: NavigationStep,
      watermarkConfig: Option[WatermarkConfig]
    ): IO[DomainError, NavigationConfig] = {
      for {
        currentConfig <- getNavigationConfig(currentStep, watermarkConfig)
        targetIndex = allSteps.indexOf(targetStep)
        _ <- if (targetIndex >= 0 && targetIndex < allSteps.length) {
               ZIO.unit
             } else {
               ZIO.fail(DomainError.InvalidConfiguration(List(s"Invalid navigation target: $targetStep")))
             }
        newConfig <- getNavigationConfig(targetStep, watermarkConfig)
      } yield newConfig
    }

    override def getFormSectionConfigs(
      watermarkConfig: WatermarkConfig
    ): IO[Nothing, List[FormSectionConfig]] = {
      ZIO.succeed {
        List(
          FormSectionConfig(
            section = FormSection.TextInput,
            visible = true,
            expanded = true,
            required = true,
            dependencies = List.empty,
            helpText = Some("Enter the text to be used as watermark")
          ),
          FormSectionConfig(
            section = FormSection.PositionConfig,
            visible = true,
            expanded = watermarkConfig.position != PositionConfig.Random,
            required = false,
            dependencies = List("text"),
            helpText = Some("Configure watermark positioning")
          ),
          FormSectionConfig(
            section = FormSection.OrientationConfig,
            visible = true,
            expanded = watermarkConfig.orientation match {
              case OrientationConfig.Fixed(_) => true
              case OrientationConfig.Preset(_) => true
              case _ => false
            },
            required = false,
            dependencies = List("text"),
            helpText = Some("Set watermark rotation angle")
          ),
          FormSectionConfig(
            section = FormSection.FontSizeConfig,
            visible = true,
            expanded = watermarkConfig.fontSize match {
              case FontSizeConfig.Random(_, _) => true
              case FontSizeConfig.DynamicScale(_, _) => true
              case _ => false
            },
            required = false,
            dependencies = List("text"),
            helpText = Some("Configure watermark font size")
          ),
          FormSectionConfig(
            section = FormSection.ColorConfig,
            visible = true,
            expanded = watermarkConfig.color match {
              case ColorConfig.Fixed(_) => true
              case ColorConfig.Palette(_) => true
              case _ => false
            },
            required = false,
            dependencies = List("text"),
            helpText = Some("Choose watermark color scheme")
          ),
          FormSectionConfig(
            section = FormSection.QuantityConfig,
            visible = watermarkConfig.quantity > 1,
            expanded = watermarkConfig.quantity > 10,
            required = false,
            dependencies = List("text", "position"),
            helpText = Some("Set number of watermark instances")
          ),
          FormSectionConfig(
            section = FormSection.PreviewConfig,
            visible = true,
            expanded = false,
            required = false,
            dependencies = List("text"),
            helpText = Some("Preview watermark appearance")
          ),
          FormSectionConfig(
            section = FormSection.AdvancedOptions,
            visible = true,
            expanded = false,
            required = false,
            dependencies = List("text"),
            helpText = Some("Advanced configuration options")
          )
        )
      }
    }

    override def generateConfigurationSummary(
      watermarkConfig: WatermarkConfig,
      pageDimensions: PageDimensions
    ): IO[Nothing, ConfigurationSummary] = {
      ZIO.succeed {
        val positionSummary = watermarkConfig.position match {
          case PositionConfig.Fixed(x, y) => s"Fixed position ($x, $y)"
          case PositionConfig.Random => "Random positioning"
          case PositionConfig.Template(template) => s"Template: ${template.toString}"
        }
        
        val orientationSummary = watermarkConfig.orientation match {
          case OrientationConfig.Fixed(angle) => s"Fixed angle: ${angle}°"
          case OrientationConfig.Random => "Random rotation"
          case OrientationConfig.Preset(preset) => s"Preset: ${preset.toString}"
        }
        
        val fontSizeSummary = watermarkConfig.fontSize match {
          case FontSizeConfig.Fixed(size) => s"Fixed size: ${size}pt"
          case FontSizeConfig.Random(min, max) => s"Random size: ${min}pt - ${max}pt"
          case FontSizeConfig.DynamicScale(base, factor) => s"Dynamic scaling: ${base}pt × ${factor}"
          case FontSizeConfig.Recommended(docType) => s"Recommended for ${docType.toString}"
        }
        
        val colorSummary = watermarkConfig.color match {
          case ColorConfig.Fixed(color) => s"Fixed color: RGB(${color.getRed}, ${color.getGreen}, ${color.getBlue})"
          case ColorConfig.RandomPerLetter => "Random colors per letter"
          case ColorConfig.Palette(palette) => s"Color palette: ${palette.toString}"
        }
        
        val quantitySummary = s"${watermarkConfig.quantity} watermark${if (watermarkConfig.quantity > 1) "s" else ""}"
        
        // Estimate processing time based on complexity
        val complexity = watermarkConfig.quantity * (if (watermarkConfig.color == ColorConfig.RandomPerLetter) 2 else 1)
        val estimatedTime = math.max(1, complexity / 10) // Rough estimation
        
        val warnings = scala.collection.mutable.ListBuffer[String]()
        val recommendations = scala.collection.mutable.ListBuffer[String]()
        
        // Generate warnings
        if (watermarkConfig.quantity > 50) {
          warnings += "High watermark quantity may impact performance"
        }
        
        // Generate recommendations
        if (watermarkConfig.fontSize.isInstanceOf[FontSizeConfig.Fixed] && 
            watermarkConfig.fontSize.asInstanceOf[FontSizeConfig.Fixed].size < 12) {
          recommendations += "Consider larger font size for better visibility"
        }
        
        ConfigurationSummary(
          watermarkText = watermarkConfig.text,
          positionSummary = positionSummary,
          orientationSummary = orientationSummary,
          fontSizeSummary = fontSizeSummary,
          colorSummary = colorSummary,
          quantitySummary = quantitySummary,
          estimatedProcessingTime = estimatedTime,
          warnings = warnings.toList,
          recommendations = recommendations.toList
        )
      }
    }

    override def getUserGuidance(
      currentStep: NavigationStep,
      formSection: Option[FormSection]
    ): IO[Nothing, UserGuidance] = {
      ZIO.succeed {
        val tooltips = Map(
          "watermark-text" -> "Enter the text that will appear as a watermark on your PDF",
          "position-fixed" -> "Specify exact X,Y coordinates for watermark placement",
          "position-random" -> "Watermarks will be placed at random positions",
          "position-template" -> "Use predefined positioning patterns",
          "orientation-fixed" -> "Set a specific rotation angle (0-360 degrees)",
          "orientation-random" -> "Each watermark will have a random rotation",
          "font-size-fixed" -> "All watermarks will use the same font size",
          "font-size-random" -> "Font sizes will vary within the specified range",
          "color-fixed" -> "All watermarks will use the same color",
          "color-palette" -> "Colors will be selected from the chosen palette",
          "quantity" -> "Number of watermark instances to apply"
        )
        
        val helpMessages = Map(
          "positioning" -> "Choose how watermarks are placed on your document. Fixed positions offer precision, while random placement provides security.",
          "orientation" -> "Rotation can make watermarks less obtrusive or more secure. Try different angles to see what works best.",
          "font-sizing" -> "Font size affects visibility and document readability. Consider your document type when choosing sizes.",
          "coloring" -> "Color choice impacts both visibility and aesthetics. Consider contrast with your document background.",
          "quantity" -> "More watermarks provide better security but may affect readability. Find the right balance for your needs."
        )
        
        val quickTips = currentStep match {
          case NavigationStep.BasicConfig => 
            List("Start with descriptive text", "Keep it concise for better appearance")
          case NavigationStep.AdvancedPositioning =>
            List("Use templates for quick setup", "Preview changes before applying")
          case NavigationStep.Preview =>
            List("Use zoom to see details", "Pan around to check positioning")
          case _ =>
            List("Use keyboard shortcuts for faster navigation", "Hover over fields for helpful tips")
        }
        
        val shortcuts = Map(
          "Ctrl+P" -> "Generate Preview",
          "Ctrl+S" -> "Save Configuration", 
          "Ctrl+Z" -> "Undo Last Change",
          "Tab" -> "Next Field",
          "Shift+Tab" -> "Previous Field",
          "Enter" -> "Apply Changes",
          "Escape" -> "Cancel Current Action"
        )
        
        UserGuidance(
          tooltips = tooltips,
          helpMessages = helpMessages,
          quickTips = quickTips,
          keyboardShortcuts = shortcuts
        )
      }
    }

    override def getKeyboardShortcuts(
      currentStep: NavigationStep
    ): IO[Nothing, List[KeyboardShortcut]] = {
      ZIO.succeed {
        val commonShortcuts = List(
          KeyboardShortcut("P", Some("Ctrl"), "preview", "Generate Preview", enabled = true),
          KeyboardShortcut("S", Some("Ctrl"), "save", "Save Configuration", enabled = true),
          KeyboardShortcut("Z", Some("Ctrl"), "undo", "Undo Last Change", enabled = true),
          KeyboardShortcut("Tab", None, "next-field", "Next Field", enabled = true),
          KeyboardShortcut("Tab", Some("Shift"), "prev-field", "Previous Field", enabled = true)
        )
        
        val stepSpecificShortcuts = currentStep match {
          case NavigationStep.Preview =>
            List(
              KeyboardShortcut("+", None, "zoom-in", "Zoom In", enabled = true),
              KeyboardShortcut("-", None, "zoom-out", "Zoom Out", enabled = true),
              KeyboardShortcut("R", None, "refresh", "Refresh Preview", enabled = true)
            )
          case NavigationStep.BasicConfig =>
            List(
              KeyboardShortcut("Enter", None, "apply", "Apply Text Changes", enabled = true)
            )
          case _ => List.empty
        }
        
        commonShortcuts ++ stepSpecificShortcuts
      }
    }

    override def validateFormDependencies(
      watermarkConfig: WatermarkConfig,
      sectionConfigs: List[FormSectionConfig]
    ): IO[Nothing, List[String]] = {
      ZIO.succeed {
        val messages = scala.collection.mutable.ListBuffer[String]()
        
        // Check text dependency
        if (watermarkConfig.text.trim.isEmpty) {
          messages += "Watermark text is required before configuring other options"
        }
        
        // Check quantity vs position compatibility
        if (watermarkConfig.quantity > 1 && watermarkConfig.position.isInstanceOf[PositionConfig.Fixed]) {
          messages += "Multiple watermarks with fixed position will overlap. Consider using random or template positioning."
        }
        
        // Check performance warnings
        if (watermarkConfig.quantity > 100) {
          messages += "Very high watermark quantity may cause performance issues"
        }
        
        messages.toList
      }
    }
  }

  /**
   * Live implementation of validation service.
   * TODO: Implement comprehensive validation (Task 101-105, 111-116)
   */
  case class ValidationServiceLive() extends ValidationService {
    override def validateWatermarkConfig(config: WatermarkConfig): IO[DomainError, Unit] = {
      val errors = scala.collection.mutable.ListBuffer[String]()
      
      if (config.text.trim.isEmpty) {
        errors += "Watermark text cannot be empty"
      }
      
      if (!ConfigConstraints.isValidQuantity(config.quantity)) {
        errors += s"Watermark quantity must be between 1 and ${ConfigConstraints.MaxWatermarkQuantity}"
      }
      
      config.fontSize match {
        case FontSizeConfig.Fixed(size) =>
          if (!ConfigConstraints.isValidFontSize(size)) {
            errors += s"Font size must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}"
          }
        case FontSizeConfig.Random(min, max) =>
          if (!ConfigConstraints.isValidFontSize(min) || !ConfigConstraints.isValidFontSize(max) || min >= max) {
            errors += "Invalid font size range"
          }
        case FontSizeConfig.DynamicScale(baseSize, scaleFactor) =>
          if (!ConfigConstraints.isValidFontSize(baseSize)) {
            errors += s"Base font size must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}"
          }
          if (scaleFactor <= 0.0) {
            errors += "Scale factor must be positive"
          }
        case FontSizeConfig.Recommended(_) =>
          // Recommended font sizes are always valid as they are calculated dynamically
          ()
      }
      
      config.orientation match {
        case OrientationConfig.Fixed(angle) =>
          if (!ConfigConstraints.isValidAngle(angle)) {
            errors += "Angle must be between 0 and 360 degrees"
          }
        case _ => // Random is always valid
      }
      
      if (errors.nonEmpty) {
        ZIO.fail(DomainError.InvalidConfiguration(errors.toList))
      } else {
        ZIO.unit
      }
    }

    override def validatePosition(
      position: Point, 
      pageDimensions: PageDimensions
    ): IO[DomainError, Unit] =
      if (position.x >= 0 && position.x <= pageDimensions.width &&
          position.y >= 0 && position.y <= pageDimensions.height) {
        ZIO.unit
      } else {
        ZIO.fail(DomainError.InvalidConfiguration(List("Position coordinates are outside page boundaries")))
      }

    override def validateUpload(uploadInfo: UploadInfo): IO[DomainError, Unit] = {
      val errors = scala.collection.mutable.ListBuffer[String]()
      
      if (uploadInfo.size > ConfigConstraints.MaxFileSizeBytes) {
        errors += s"File size exceeds maximum allowed size of ${ConfigConstraints.MaxFileSizeBytes} bytes"
      }
      
      val extension = uploadInfo.filename.toLowerCase.takeRight(4)
      if (!ConfigConstraints.SupportedFileExtensions.contains(extension)) {
        errors += "Only PDF files are supported"
      }
      
      if (errors.nonEmpty) {
        ZIO.fail(DomainError.InvalidConfiguration(errors.toList))
      } else {
        ZIO.unit
      }
    }
  }

  /**
   * Live implementation of download tracking service (Task 60).
   */
  case class DownloadTrackingServiceLive() extends DownloadTrackingService {
    private val downloadSessions = scala.collection.concurrent.TrieMap[String, DownloadSession]()

    override def createDownloadSession(
      sessionId: String,
      documentId: String,
      filename: String,
      fileSize: Long
    ): UIO[DownloadSession] =
      ZIO.succeed {
        val downloadSession = DownloadSession(
          sessionId = sessionId,
          documentId = documentId,
          filename = filename,
          fileSize = fileSize,
          bytesTransferred = 0L,
          startedAt = java.time.Instant.now(),
          lastActivity = java.time.Instant.now(),
          status = DownloadStatus.Starting
        )
        downloadSessions.put(sessionId, downloadSession)
        downloadSession
      }

    override def getDownloadSession(sessionId: String): IO[DomainError, DownloadSession] =
      ZIO.fromOption(downloadSessions.get(sessionId))
        .orElseFail(DomainError.SessionNotFound(s"Download session not found: $sessionId"))

    override def updateDownloadProgress(
      sessionId: String,
      bytesTransferred: Long
    ): IO[DomainError, DownloadSession] =
      for {
        session <- getDownloadSession(sessionId)
        updated = session.copy(
          bytesTransferred = bytesTransferred,
          lastActivity = java.time.Instant.now(),
          status = if (bytesTransferred >= session.fileSize) DownloadStatus.Completed else DownloadStatus.InProgress
        )
        _ <- ZIO.succeed(downloadSessions.put(sessionId, updated))
      } yield updated

    override def completeDownload(sessionId: String): IO[DomainError, DownloadSession] =
      for {
        session <- getDownloadSession(sessionId)
        updated = session.copy(
          bytesTransferred = session.fileSize,
          lastActivity = java.time.Instant.now(),
          status = DownloadStatus.Completed
        )
        _ <- ZIO.succeed(downloadSessions.put(sessionId, updated))
      } yield updated

    override def failDownload(sessionId: String, reason: String): IO[DomainError, DownloadSession] =
      for {
        session <- getDownloadSession(sessionId)
        updated = session.copy(
          lastActivity = java.time.Instant.now(),
          status = DownloadStatus.Failed(reason)
        )
        _ <- ZIO.succeed(downloadSessions.put(sessionId, updated))
      } yield updated

    override def getDownloadProgress(sessionId: String): IO[DomainError, DownloadProgressResponse] =
      for {
        session <- getDownloadSession(sessionId)
        progress = if (session.fileSize > 0) ((session.bytesTransferred * 100) / session.fileSize).toInt else 0
        
        // Calculate transfer rate and ETA
        elapsedSeconds = java.time.Duration.between(session.startedAt, session.lastActivity).getSeconds
        transferRate = if (elapsedSeconds > 0) Some(session.bytesTransferred / elapsedSeconds) else None
        remainingBytes = session.fileSize - session.bytesTransferred
        estimatedTimeRemaining = transferRate.filter(_ > 0).map(rate => remainingBytes / rate)
        
        statusText = session.status match {
          case DownloadStatus.Starting => "starting"
          case DownloadStatus.InProgress => "downloading"
          case DownloadStatus.Completed => "completed"
          case DownloadStatus.Failed(_) => "failed"
          case DownloadStatus.Cancelled => "cancelled"
        }
        
        message = session.status match {
          case DownloadStatus.Starting => "Download is starting..."
          case DownloadStatus.InProgress => s"Downloading... ${session.bytesTransferred}/${session.fileSize} bytes"
          case DownloadStatus.Completed => "Download completed successfully"
          case DownloadStatus.Failed(reason) => s"Download failed: $reason"
          case DownloadStatus.Cancelled => "Download was cancelled"
        }
        
      } yield DownloadProgressResponse(
        sessionId = sessionId,
        filename = session.filename,
        fileSize = session.fileSize,
        bytesTransferred = session.bytesTransferred,
        progress = progress,
        status = statusText,
        transferRate = transferRate,
        estimatedTimeRemaining = estimatedTimeRemaining,
        message = message
      )

    override def cleanupDownloadSessions(): UIO[Unit] =
      ZIO.succeed {
        val now = java.time.Instant.now()
        val expiredThreshold = now.minusSeconds(3600) // 1 hour
        
        downloadSessions.filterInPlace { (_, session) =>
          session.status match {
            case DownloadStatus.Completed | DownloadStatus.Failed(_) | DownloadStatus.Cancelled =>
              session.lastActivity.isAfter(expiredThreshold)
            case _ => true // Keep active downloads
          }
        }
      }
  }

  // ========== Test Service Implementations ==========

  /**
   * Test implementation of PDF processing service for unit testing.
   */
  case class PdfProcessingServiceTest() extends PdfProcessingService {
    override def loadPdf(file: java.io.File): IO[DomainError, PdfDocument] =
      ZIO.succeed(PdfDocument(
        id = "test-doc-id",
        filename = "test.pdf",
        originalSize = 1000,
        pageCount = 3,
        uploadedAt = java.time.Instant.parse("2024-01-01T00:00:00Z"),
        status = DocumentStatus.Uploaded
      ))

    override def applyWatermarks(document: PdfDocument, config: WatermarkConfig): IO[DomainError, java.io.File] =
      ZIO.succeed(new java.io.File("/tmp/test-watermarked.pdf"))

    override def getPageCount(file: java.io.File): IO[DomainError, Int] =
      ZIO.succeed(3)

    override def getPageDimensions(file: java.io.File, pageNumber: Int): IO[DomainError, PageDimensions] =
      ZIO.succeed(PageDimensions(612.0, 792.0))
  }

  /**
   * Test implementations for other services (simplified for testing).
   */
  case class WatermarkRenderingServiceTest() extends WatermarkRenderingService {
    override def generatePageLayout(pageDimensions: PageDimensions, config: WatermarkConfig): IO[DomainError, PageWatermarkLayout] =
      ZIO.succeed(PageWatermarkLayout(1, pageDimensions, List.empty))
    override def calculateNonOverlappingPositions(pageDimensions: PageDimensions, watermarkCount: Int, fontSize: Double): IO[DomainError, List[Point]] =
      ZIO.succeed(List(Point(100, 100)))
    override def generateRandomColors(text: String): IO[Nothing, List[java.awt.Color]] =
      ZIO.succeed(List(java.awt.Color.RED))
  }

  case class FileManagementServiceTest() extends FileManagementService {
    override def storeUploadedFile(uploadInfo: UploadInfo): IO[DomainError, java.io.File] =
      ZIO.succeed(new java.io.File("/tmp/test.pdf"))
    override def validateFile(file: java.io.File): IO[DomainError, Unit] = ZIO.unit
    override def cleanupTempFiles(files: List[java.io.File]): UIO[Unit] = ZIO.unit
    override def generateProcessedFilename(originalFilename: String): UIO[String] =
      ZIO.succeed(s"test_$originalFilename")
  }

  case class SessionManagementServiceTest() extends SessionManagementService {
    override def createSession(): UIO[UserSession] =
      ZIO.succeed(UserSession("test-session", None, None, java.time.Instant.now(), java.time.Instant.now()))
    override def getSession(sessionId: String): IO[DomainError, UserSession] =
      ZIO.succeed(UserSession(sessionId, None, None, java.time.Instant.now(), java.time.Instant.now()))
    override def updateSessionWithDocument(sessionId: String, document: PdfDocument): IO[DomainError, UserSession] =
      ZIO.succeed(UserSession(sessionId, Some(document), None, java.time.Instant.now(), java.time.Instant.now()))
    override def updateSessionWithConfig(sessionId: String, config: WatermarkConfig): IO[DomainError, UserSession] =
      ZIO.succeed(UserSession(sessionId, None, Some(config), java.time.Instant.now(), java.time.Instant.now()))
    override def updateDocumentStatus(sessionId: String, newStatus: DocumentStatus): IO[DomainError, UserSession] =
      ZIO.succeed(UserSession(sessionId, Some(PdfDocument("test-doc", "test.pdf", 1000, 1, java.time.Instant.now(), newStatus)), None, java.time.Instant.now(), java.time.Instant.now()))
    override def updateDocumentProcessedFilePath(sessionId: String, processedFilePath: String): IO[DomainError, UserSession] =
      ZIO.succeed(UserSession(sessionId, Some(PdfDocument("test-doc", "test.pdf", 1000, 1, java.time.Instant.now(), DocumentStatus.Completed, Some(processedFilePath))), None, java.time.Instant.now(), java.time.Instant.now()))
    override def cleanupExpiredSessions(): UIO[Unit] = ZIO.unit
  }

  case class ValidationServiceTest() extends ValidationService {
    override def validateWatermarkConfig(config: WatermarkConfig): IO[DomainError, Unit] = ZIO.unit
    override def validatePosition(position: Point, pageDimensions: PageDimensions): IO[DomainError, Unit] = ZIO.unit
    override def validateUpload(uploadInfo: UploadInfo): IO[DomainError, Unit] = ZIO.unit
  }

  case class DownloadTrackingServiceTest() extends DownloadTrackingService {
    private val testDownloadSession = DownloadSession(
      sessionId = "test-session-id",
      documentId = "test-doc-id",
      filename = "test.pdf",
      fileSize = 1024L,
      bytesTransferred = 512L,
      startedAt = java.time.Instant.now(),
      lastActivity = java.time.Instant.now(),
      status = DownloadStatus.InProgress
    )

    override def createDownloadSession(sessionId: String, documentId: String, filename: String, fileSize: Long): UIO[DownloadSession] =
      ZIO.succeed(testDownloadSession.copy(sessionId = sessionId, documentId = documentId, filename = filename, fileSize = fileSize))

    override def getDownloadSession(sessionId: String): IO[DomainError, DownloadSession] =
      ZIO.succeed(testDownloadSession.copy(sessionId = sessionId))

    override def updateDownloadProgress(sessionId: String, bytesTransferred: Long): IO[DomainError, DownloadSession] =
      ZIO.succeed(testDownloadSession.copy(sessionId = sessionId, bytesTransferred = bytesTransferred))

    override def completeDownload(sessionId: String): IO[DomainError, DownloadSession] =
      ZIO.succeed(testDownloadSession.copy(sessionId = sessionId, status = DownloadStatus.Completed, bytesTransferred = testDownloadSession.fileSize))

    override def failDownload(sessionId: String, reason: String): IO[DomainError, DownloadSession] =
      ZIO.succeed(testDownloadSession.copy(sessionId = sessionId, status = DownloadStatus.Failed(reason)))

    override def getDownloadProgress(sessionId: String): IO[DomainError, DownloadProgressResponse] =
      ZIO.succeed(DownloadProgressResponse(
        sessionId = sessionId,
        filename = "test.pdf",
        fileSize = 1024L,
        bytesTransferred = 512L,
        progress = 50,
        status = "downloading",
        transferRate = Some(128L),
        estimatedTimeRemaining = Some(4L),
        message = "Test download in progress"
      ))

    override def cleanupDownloadSessions(): UIO[Unit] = ZIO.unit
  }
}