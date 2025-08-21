package com.pdfwatermarks.cli

import com.pdfwatermarks.domain.*
import zio.*
import java.awt.Color
import java.io.File

/**
 * Command-line interface argument parsing for the PDF Watermarking Application.
 * 
 * Provides parsing for command-line arguments to configure watermark operations
 * with comprehensive validation and user-friendly error messages.
 */

// ========== CLI Configuration Models ==========

/**
 * Configuration parsed from command-line arguments.
 */
case class CliConfig(
  inputFile: File,
  outputFile: File,
  watermarkConfig: WatermarkConfig,
  verbose: Boolean = false,
  help: Boolean = false
)

/**
 * CLI parsing result.
 */
enum CliParseResult:
  case Success(config: CliConfig)
  case Error(message: String)
  case Help(helpText: String)

// ========== CLI Parser Implementation ==========

object CliParser {

  /**
   * Parse command-line arguments into a CLI configuration.
   * 
   * @param args Array of command-line arguments
   * @return Parsing result with configuration or error
   */
  def parseArgs(args: Array[String]): CliParseResult = {
    if (args.isEmpty || args.contains("--help") || args.contains("-h")) {
      return CliParseResult.Help(generateHelpText())
    }

    try {
      val argMap = parseArgumentMap(args)
      
      // Check for required arguments
      val inputPath = argMap.getOrElse("--input", argMap.get("-i").getOrElse(""))
      val outputPath = argMap.getOrElse("--output", argMap.get("-o").getOrElse(""))
      val text = argMap.getOrElse("--text", argMap.get("-t").getOrElse(""))

      if (inputPath.isEmpty) {
        return CliParseResult.Error("Input file path is required. Use --input or -i")
      }
      if (outputPath.isEmpty) {
        return CliParseResult.Error("Output file path is required. Use --output or -o")
      }
      if (text.isEmpty) {
        return CliParseResult.Error("Watermark text is required. Use --text or -t")
      }

      val inputFile = new File(inputPath)
      val outputFile = new File(outputPath)

      // Validate input file
      if (!inputFile.exists()) {
        return CliParseResult.Error(s"Input file does not exist: $inputPath")
      }
      if (!inputFile.canRead()) {
        return CliParseResult.Error(s"Input file is not readable: $inputPath")
      }
      if (!inputPath.toLowerCase.endsWith(".pdf")) {
        return CliParseResult.Error(s"Input file must be a PDF: $inputPath")
      }

      // Parse optional arguments with defaults
      val positionConfig = parsePosition(argMap)
      val orientationConfig = parseOrientation(argMap)
      val fontSizeConfig = parseFontSize(argMap)
      val colorConfig = parseColor(argMap)
      val quantity = parseQuantity(argMap)
      val verbose = argMap.contains("--verbose") || argMap.contains("-v")

      val watermarkConfig = WatermarkConfig(
        text = text,
        position = positionConfig,
        orientation = orientationConfig,
        fontSize = fontSizeConfig,
        color = colorConfig,
        quantity = quantity
      )

      val cliConfig = CliConfig(
        inputFile = inputFile,
        outputFile = outputFile,
        watermarkConfig = watermarkConfig,
        verbose = verbose
      )

      CliParseResult.Success(cliConfig)

    } catch {
      case e: Exception =>
        CliParseResult.Error(s"Error parsing arguments: ${e.getMessage}")
    }
  }

  /**
   * Parse arguments into a key-value map.
   */
  private def parseArgumentMap(args: Array[String]): Map[String, String] = {
    val map = scala.collection.mutable.Map[String, String]()
    var i = 0
    
    while (i < args.length) {
      val arg = args(i)
      if (arg.startsWith("--") || arg.startsWith("-")) {
        if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
          map += arg -> args(i + 1)
          i += 2
        } else {
          // Flag without value
          map += arg -> "true"
          i += 1
        }
      } else {
        i += 1
      }
    }
    
    map.toMap
  }

  /**
   * Parse position configuration from arguments.
   */
  private def parsePosition(argMap: Map[String, String]): PositionConfig = {
    argMap.get("--position").orElse(argMap.get("-p")) match {
      case Some("random") => PositionConfig.Random
      case Some(coords) =>
        coords.split(",") match {
          case Array(x, y) =>
            try {
              PositionConfig.Fixed(x.trim.toDouble, y.trim.toDouble)
            } catch {
              case _: NumberFormatException =>
                throw new IllegalArgumentException(s"Invalid position coordinates: $coords. Use format 'x,y'")
            }
          case _ =>
            throw new IllegalArgumentException(s"Invalid position format: $coords. Use 'x,y' or 'random'")
        }
      case None => PositionConfig.Fixed(300.0, 400.0) // Default center position
    }
  }

  /**
   * Parse orientation configuration from arguments.
   */
  private def parseOrientation(argMap: Map[String, String]): OrientationConfig = {
    argMap.get("--angle").orElse(argMap.get("-a")) match {
      case Some("random") => OrientationConfig.Random
      case Some(angleStr) =>
        try {
          val angle = angleStr.toDouble
          if (angle < 0.0 || angle > 360.0) {
            throw new IllegalArgumentException(s"Angle must be between 0 and 360 degrees: $angle")
          }
          OrientationConfig.Fixed(angle)
        } catch {
          case _: NumberFormatException =>
            throw new IllegalArgumentException(s"Invalid angle: $angleStr. Use a number or 'random'")
        }
      case None => OrientationConfig.Fixed(45.0) // Default 45-degree angle
    }
  }

  /**
   * Parse font size configuration from arguments.
   */
  private def parseFontSize(argMap: Map[String, String]): FontSizeConfig = {
    argMap.get("--font-size").orElse(argMap.get("-s")) match {
      case Some(sizeStr) =>
        if (sizeStr.contains("-")) {
          // Range format: "min-max"
          sizeStr.split("-") match {
            case Array(minStr, maxStr) =>
              try {
                val min = minStr.trim.toDouble
                val max = maxStr.trim.toDouble
                if (min >= max) {
                  throw new IllegalArgumentException(s"Font size minimum must be less than maximum: $min-$max")
                }
                if (min < ConfigConstraints.MinFontSize || max > ConfigConstraints.MaxFontSize) {
                  throw new IllegalArgumentException(s"Font size must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}")
                }
                FontSizeConfig.Random(min, max)
              } catch {
                case _: NumberFormatException =>
                  throw new IllegalArgumentException(s"Invalid font size range: $sizeStr. Use format 'min-max'")
              }
            case _ =>
              throw new IllegalArgumentException(s"Invalid font size range format: $sizeStr. Use 'min-max'")
          }
        } else {
          // Fixed size
          try {
            val size = sizeStr.toDouble
            if (size < ConfigConstraints.MinFontSize || size > ConfigConstraints.MaxFontSize) {
              throw new IllegalArgumentException(s"Font size must be between ${ConfigConstraints.MinFontSize} and ${ConfigConstraints.MaxFontSize}: $size")
            }
            FontSizeConfig.Fixed(size)
          } catch {
            case _: NumberFormatException =>
              throw new IllegalArgumentException(s"Invalid font size: $sizeStr")
          }
        }
      case None => FontSizeConfig.Fixed(24.0) // Default font size
    }
  }

  /**
   * Parse color configuration from arguments.
   */
  private def parseColor(argMap: Map[String, String]): ColorConfig = {
    argMap.get("--color").orElse(argMap.get("-c")) match {
      case Some("random") => ColorConfig.RandomPerLetter
      case Some(colorStr) =>
        parseColorValue(colorStr) match {
          case Some(color) => ColorConfig.Fixed(color)
          case None => throw new IllegalArgumentException(s"Invalid color: $colorStr. Use color name, hex code, or 'random'")
        }
      case None => ColorConfig.Fixed(Color.BLACK) // Default black color
    }
  }

  /**
   * Parse a color value from string (color name or hex code).
   */
  private def parseColorValue(colorStr: String): Option[Color] = {
    val lowerColor = colorStr.toLowerCase.trim
    
    // Try predefined colors first
    val predefinedColors = Map(
      "black" -> Color.BLACK,
      "white" -> Color.WHITE,
      "red" -> Color.RED,
      "green" -> Color.GREEN,
      "blue" -> Color.BLUE,
      "yellow" -> Color.YELLOW,
      "orange" -> Color.ORANGE,
      "pink" -> Color.PINK,
      "cyan" -> Color.CYAN,
      "magenta" -> Color.MAGENTA,
      "gray" -> Color.GRAY,
      "grey" -> Color.GRAY,
      "lightgray" -> Color.LIGHT_GRAY,
      "lightgrey" -> Color.LIGHT_GRAY,
      "darkgray" -> Color.DARK_GRAY,
      "darkgrey" -> Color.DARK_GRAY
    )
    
    predefinedColors.get(lowerColor).orElse {
      // Try hex color parsing
      if (colorStr.startsWith("#") && colorStr.length == 7) {
        try {
          Some(Color.decode(colorStr))
        } catch {
          case _: NumberFormatException => None
        }
      } else {
        None
      }
    }
  }

  /**
   * Parse quantity from arguments.
   */
  private def parseQuantity(argMap: Map[String, String]): Int = {
    argMap.get("--quantity").orElse(argMap.get("-q")) match {
      case Some(qtyStr) =>
        try {
          val quantity = qtyStr.toInt
          if (quantity <= 0 || quantity > ConfigConstraints.MaxWatermarkQuantity) {
            throw new IllegalArgumentException(s"Quantity must be between 1 and ${ConfigConstraints.MaxWatermarkQuantity}: $quantity")
          }
          quantity
        } catch {
          case _: NumberFormatException =>
            throw new IllegalArgumentException(s"Invalid quantity: $qtyStr. Must be a positive integer")
        }
      case None => 1 // Default single watermark
    }
  }

  /**
   * Generate help text for CLI usage.
   */
  private def generateHelpText(): String = {
    """
PDF Watermarking Application - Command Line Interface

USAGE:
    pdf-watermarks [OPTIONS] --input INPUT_FILE --output OUTPUT_FILE --text WATERMARK_TEXT

REQUIRED ARGUMENTS:
    -i, --input <FILE>       Input PDF file path
    -o, --output <FILE>      Output PDF file path  
    -t, --text <TEXT>        Watermark text

OPTIONAL ARGUMENTS:
    -p, --position <POS>     Watermark position: 'x,y' coordinates or 'random' (default: 300,400)
    -a, --angle <ANGLE>      Watermark rotation angle: 0-360 degrees or 'random' (default: 45)
    -s, --font-size <SIZE>   Font size: number or 'min-max' range (default: 24)
    -c, --color <COLOR>      Text color: color name, hex code, or 'random' (default: black)
    -q, --quantity <NUM>     Number of watermarks: 1-100 (default: 1)
    -v, --verbose            Enable verbose output
    -h, --help               Show this help message

EXAMPLES:
    # Basic watermark
    pdf-watermarks --input document.pdf --output watermarked.pdf --text "CONFIDENTIAL"
    
    # Custom position and angle
    pdf-watermarks -i input.pdf -o output.pdf -t "DRAFT" -p 100,200 -a 30
    
    # Random positioning with multiple watermarks
    pdf-watermarks -i input.pdf -o output.pdf -t "SAMPLE" -p random -q 5
    
    # Custom color and font size range
    pdf-watermarks -i input.pdf -o output.pdf -t "COPY" -c red -s 16-32

SUPPORTED COLORS:
    black, white, red, green, blue, yellow, orange, pink, cyan, magenta, gray, lightgray, darkgray
    Hex codes: #FF0000, #00FF00, etc.
    """
  }
}