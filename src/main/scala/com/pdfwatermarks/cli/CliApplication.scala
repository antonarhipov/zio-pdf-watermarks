package com.pdfwatermarks.cli

import com.pdfwatermarks.domain.*
import com.pdfwatermarks.pdf.{PdfProcessor, WatermarkRenderer}
import com.pdfwatermarks.logging.StructuredLogging
import zio.*

/**
 * Command-line application for PDF watermarking.
 * 
 * Provides a complete CLI interface for applying watermarks to PDF documents
 * with comprehensive error handling and user feedback.
 */
object CliApplication extends ZIOAppDefault {

  /**
   * Main application entry point for CLI usage.
   */
  def run: ZIO[ZIOAppArgs, Any, ExitCode] =
    getArgs.flatMap { args =>
      val cliApp = CliApplication()
      cliApp.runCli(args)
    }

  /**
   * CLI application service implementation.
   */
  case class CliApplication() {

    /**
     * Run the CLI application with command-line arguments.
     */
    def runCli(args: Chunk[String]): ZIO[Any, Nothing, ExitCode] = {
      val program = for {
        _ <- ZIO.logInfo("PDF Watermarking CLI Application started")
        parseResult = CliParser.parseArgs(args.toArray)
        exitCode <- handleParseResult(parseResult)
      } yield exitCode

      program.catchAll { error =>
        for {
          _ <- ZIO.logError(s"Unexpected error: ${error}")
          _ <- Console.printLineError(s"An unexpected error occurred: ${error.getMessage}").orDie
        } yield ExitCode.failure
      }
    }

    /**
     * Handle the CLI parsing result and execute appropriate action.
     */
    private def handleParseResult(parseResult: CliParseResult): ZIO[Any, Throwable, ExitCode] = {
      parseResult match {
        case CliParseResult.Success(config) =>
          processWatermarkRequest(config)

        case CliParseResult.Help(helpText) =>
          for {
            _ <- Console.printLine(helpText).orDie
          } yield ExitCode.success

        case CliParseResult.Error(message) =>
          for {
            _ <- Console.printLineError(s"Error: $message").orDie
            _ <- Console.printLineError("Use --help for usage information.").orDie
          } yield ExitCode.failure
      }
    }

    /**
     * Process watermark application request.
     */
    private def processWatermarkRequest(config: CliConfig): ZIO[Any, Throwable, ExitCode] = {
      val program = for {
        _ <- ZIO.logInfo(s"Processing watermark request for file: ${config.inputFile.getName}")
        _ <- printVerboseInfo(config)
        
        // Load and validate input PDF
        _ <- Console.printLine("Loading PDF document...").orDie
        pdfDocument <- PdfProcessor.loadPdf(config.inputFile)
        _ <- Console.printLine(s"Successfully loaded PDF: ${pdfDocument.pageCount} pages, ${formatFileSize(pdfDocument.originalSize)}").orDie
        
        // Validate PDF integrity
        _ <- Console.printLine("Validating PDF integrity...").orDie
        _ <- PdfProcessor.validatePdfIntegrity(config.inputFile)
        _ <- Console.printLine("PDF validation successful").orDie
        
        // Apply watermarks
        _ <- Console.printLine("Applying watermarks...").orDie
        resultFile <- WatermarkRenderer.applyWatermarks(
          config.inputFile,
          config.outputFile,
          config.watermarkConfig
        )
        
        // Verify output file
        outputSize = resultFile.length()
        _ <- Console.printLine(s"Watermarking completed successfully!").orDie
        _ <- Console.printLine(s"Output file: ${resultFile.getAbsolutePath}").orDie
        _ <- Console.printLine(s"Output size: ${formatFileSize(outputSize)}").orDie
        
        // Print summary
        _ <- printProcessingSummary(config, pdfDocument, outputSize)
        
      } yield ExitCode.success

      program.catchAll { error =>
        handleProcessingError(config, error)
      }
    }

    /**
     * Print verbose information if enabled.
     */
    private def printVerboseInfo(config: CliConfig): ZIO[Any, Nothing, Unit] = {
      if (config.verbose) {
        for {
          _ <- Console.printLine("\n=== Verbose Information ===").orDie
          _ <- Console.printLine(s"Input file: ${config.inputFile.getAbsolutePath}").orDie
          _ <- Console.printLine(s"Output file: ${config.outputFile.getAbsolutePath}").orDie
          _ <- Console.printLine(s"Watermark text: '${config.watermarkConfig.text}'").orDie
          _ <- printWatermarkConfig(config.watermarkConfig)
          _ <- Console.printLine("=============================\n").orDie
        } yield ()
      } else {
        ZIO.unit
      }
    }

    /**
     * Print watermark configuration details.
     */
    private def printWatermarkConfig(config: WatermarkConfig): ZIO[Any, Nothing, Unit] = {
      for {
        _ <- Console.printLine(s"Position: ${formatPositionConfig(config.position)}").orDie
        _ <- Console.printLine(s"Orientation: ${formatOrientationConfig(config.orientation)}").orDie
        _ <- Console.printLine(s"Font size: ${formatFontSizeConfig(config.fontSize)}").orDie
        _ <- Console.printLine(s"Color: ${formatColorConfig(config.color)}").orDie
        _ <- Console.printLine(s"Quantity: ${config.quantity}").orDie
      } yield ()
    }

    /**
     * Print processing summary.
     */
    private def printProcessingSummary(config: CliConfig, pdfDocument: PdfDocument, outputSize: Long): ZIO[Any, Nothing, Unit] = {
      for {
        _ <- Console.printLine("\n=== Processing Summary ===").orDie
        _ <- Console.printLine(s"Pages processed: ${pdfDocument.pageCount}").orDie
        _ <- Console.printLine(s"Watermarks per page: ${config.watermarkConfig.quantity}").orDie
        _ <- Console.printLine(s"Total watermarks applied: ${pdfDocument.pageCount * config.watermarkConfig.quantity}").orDie
        _ <- Console.printLine(s"Original size: ${formatFileSize(pdfDocument.originalSize)}").orDie
        _ <- Console.printLine(s"Final size: ${formatFileSize(outputSize)}").orDie
        _ <- Console.printLine(s"Size increase: ${formatFileSize(outputSize - pdfDocument.originalSize)}").orDie
        _ <- Console.printLine("===========================").orDie
      } yield ()
    }

    /**
     * Handle processing errors with user-friendly messages.
     */
    private def handleProcessingError(config: CliConfig, error: Any): ZIO[Any, Nothing, ExitCode] = {
      val errorMessage = error match {
        case domainError: DomainError =>
          formatDomainError(domainError)
        case throwable: Throwable =>
          s"Processing failed: ${throwable.getMessage}"
        case other =>
          s"Processing failed: ${other.toString}"
      }

      for {
        _ <- ZIO.logError(s"Processing error for ${config.inputFile.getName}: $error")
        _ <- Console.printLineError(s"\nError: $errorMessage").orDie
        _ <- Console.printLineError("\nTroubleshooting suggestions:").orDie
        _ <- printTroubleshootingSuggestions(error)
      } yield ExitCode.failure
    }

    /**
     * Format domain error for user display.
     */
    private def formatDomainError(error: DomainError): String = error match {
      case DomainError.InvalidFileFormat(message) =>
        s"Invalid file format: $message"
      case DomainError.FileSizeExceeded(actualSize, maxSize) =>
        s"File size too large (${formatFileSize(actualSize)}). Maximum allowed: ${formatFileSize(maxSize)}"
      case DomainError.InvalidConfiguration(errors) =>
        s"Invalid configuration: ${errors.mkString(", ")}"
      case DomainError.PdfProcessingError(message) =>
        s"PDF processing error: $message"
      case DomainError.SessionNotFound(sessionId) =>
        s"Session not found: $sessionId"
      case DomainError.DocumentNotFound(documentId) =>
        s"Document not found: $documentId"
      case DomainError.InternalError(message) =>
        s"Internal error: $message"
    }

    /**
     * Print troubleshooting suggestions based on error type.
     */
    private def printTroubleshootingSuggestions(error: Any): ZIO[Any, Nothing, Unit] = {
      val suggestions = error match {
        case _: DomainError.InvalidFileFormat =>
          List(
            "- Ensure the input file is a valid PDF",
            "- Check that the file is not corrupted",
            "- Try with a different PDF file"
          )
        case _: DomainError.FileSizeExceeded =>
          List(
            "- Use a smaller PDF file",
            "- Reduce the number of watermarks",
            "- Consider compressing the PDF first"
          )
        case _: DomainError.PdfProcessingError =>
          List(
            "- Check if the PDF is encrypted (not supported)",
            "- Ensure the PDF is not corrupted",
            "- Try with a different PDF file",
            "- Check available disk space"
          )
        case _ =>
          List(
            "- Check file permissions",
            "- Ensure sufficient disk space",
            "- Try with a different file",
            "- Use --verbose for more details"
          )
      }

      ZIO.foreachDiscard(suggestions)(suggestion => Console.printLineError(suggestion).orDie)
    }

    // ========== Helper Methods ==========

    /**
     * Format file size in human-readable format.
     */
    private def formatFileSize(bytes: Long): String = {
      val kb = bytes / 1024.0
      val mb = kb / 1024.0
      val gb = mb / 1024.0

      if (gb >= 1) f"${gb}%.2f GB"
      else if (mb >= 1) f"${mb}%.2f MB"
      else if (kb >= 1) f"${kb}%.2f KB"
      else s"$bytes bytes"
    }

    /**
     * Format position configuration for display.
     */
    private def formatPositionConfig(config: PositionConfig): String = config match {
      case PositionConfig.Fixed(x, y) => f"Fixed ($x%.1f, $y%.1f)"
      case PositionConfig.Random => "Random"
      case PositionConfig.Template(template) => s"Template: ${template.toString}"
    }

    /**
     * Format orientation configuration for display.
     */
    private def formatOrientationConfig(config: OrientationConfig): String = config match {
      case OrientationConfig.Fixed(angle) => f"Fixed $angle%.1f°"
      case OrientationConfig.Random => "Random"
      case OrientationConfig.Preset(preset) => s"Preset: ${preset.toString}"
    }

    /**
     * Format font size configuration for display.
     */
    private def formatFontSizeConfig(config: FontSizeConfig): String = config match {
      case FontSizeConfig.Fixed(size) => f"Fixed $size%.1f pt"
      case FontSizeConfig.Random(min, max) => f"Random $min%.1f-$max%.1f pt"
      case FontSizeConfig.DynamicScale(baseSize, scaleFactor) => f"Dynamic scaling $baseSize%.1f pt × $scaleFactor%.2f"
      case FontSizeConfig.Recommended(documentType) => s"Recommended for ${documentType.toString}"
    }

    /**
     * Format color configuration for display.
     */
    private def formatColorConfig(config: ColorConfig): String = config match {
      case ColorConfig.Fixed(color) => s"Fixed (${color.getRed}, ${color.getGreen}, ${color.getBlue})"
      case ColorConfig.RandomPerLetter => "Random per letter"
      case ColorConfig.Palette(palette) => s"Color palette: ${palette.toString}"
    }
  }

}