package com.pdfwatermarks.pdf

import com.pdfwatermarks.BaseTestSpec
import com.pdfwatermarks.domain.*
import zio.*
import java.io.File
import java.awt.Color

/**
 * Tests for watermark rendering functionality.
 * Tests the WatermarkRenderer module with various watermark configurations.
 */
class WatermarkRendererTest extends BaseTestSpec {

  "WatermarkRenderer.applyWatermarks" should "successfully add text watermark to a PDF" in {
    val inputFile = getTestPdfFile("watermark-test.pdf")
    val outputFile = createTempFile("watermarked", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "CONFIDENTIAL",
      position = PositionConfig.Fixed(300.0, 400.0),
      orientation = OrientationConfig.Fixed(0.0),
      fontSize = FontSizeConfig.Fixed(24.0),
      color = ColorConfig.Fixed(Color.RED),
      quantity = 1
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig))
    
    result shouldBe outputFile
    outputFile.exists() shouldBe true
    outputFile.length() should be > inputFile.length() // Watermarked file should be larger
  }

  it should "handle fixed position watermarks" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val outputFile = createTempFile("fixed_position", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "DRAFT",
      position = PositionConfig.Fixed(100.0, 500.0),
      orientation = OrientationConfig.Fixed(45.0),
      fontSize = FontSizeConfig.Fixed(18.0),
      color = ColorConfig.Fixed(Color.BLUE),
      quantity = 1
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
    
    result.isRight shouldBe true
    outputFile.exists() shouldBe true
  }

  it should "handle random position watermarks" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val outputFile = createTempFile("random_position", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "SAMPLE",
      position = PositionConfig.Random,
      orientation = OrientationConfig.Fixed(0.0),
      fontSize = FontSizeConfig.Fixed(16.0),
      color = ColorConfig.Fixed(Color.GRAY),
      quantity = 5
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
    
    result.isRight shouldBe true
    outputFile.exists() shouldBe true
  }

  it should "handle different font sizes" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val fontSizes = List(8.0, 12.0, 18.0, 24.0, 36.0)
    
    fontSizes.foreach { fontSize =>
      val outputFile = createTempFile(s"watermark_size_${fontSize.toInt}", ".pdf")
      val watermarkConfig = WatermarkConfig(
        text = s"SIZE ${fontSize.toInt}",
        position = PositionConfig.Fixed(200.0, 300.0),
        orientation = OrientationConfig.Fixed(0.0),
        fontSize = FontSizeConfig.Fixed(fontSize),
        color = ColorConfig.Fixed(Color.BLACK),
        quantity = 1
      )
      
      val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
      
      result.isRight shouldBe true
      outputFile.exists() shouldBe true
    }
  }

  it should "handle random font sizes" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val outputFile = createTempFile("random_font_size", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "RANDOM SIZE",
      position = PositionConfig.Fixed(200.0, 300.0),
      orientation = OrientationConfig.Fixed(0.0),
      fontSize = FontSizeConfig.Random(10.0, 30.0),
      color = ColorConfig.Fixed(Color.GREEN),
      quantity = 3
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
    
    result.isRight shouldBe true
    outputFile.exists() shouldBe true
  }

  it should "handle different rotation angles" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val angles = List(0.0, 45.0, 90.0, 180.0, 270.0)
    
    angles.foreach { angle =>
      val outputFile = createTempFile(s"watermark_rotation_${angle.toInt}", ".pdf")
      val watermarkConfig = WatermarkConfig(
        text = s"ROTATED ${angle.toInt}°",
        position = PositionConfig.Fixed(300.0, 400.0),
        orientation = OrientationConfig.Fixed(angle),
        fontSize = FontSizeConfig.Fixed(16.0),
        color = ColorConfig.Fixed(Color.MAGENTA),
        quantity = 1
      )
      
      val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
      
      result.isRight shouldBe true
      outputFile.exists() shouldBe true
    }
  }

  it should "handle random orientations" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val outputFile = createTempFile("random_orientation", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "RANDOM ANGLE",
      position = PositionConfig.Fixed(200.0, 300.0),
      orientation = OrientationConfig.Random,
      fontSize = FontSizeConfig.Fixed(20.0),
      color = ColorConfig.Fixed(Color.ORANGE),
      quantity = 4
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
    
    result.isRight shouldBe true
    outputFile.exists() shouldBe true
  }

  it should "apply watermark to multi-page documents" in {
    val inputFile = getTestPdfFile("multi-page.pdf")
    val outputFile = createTempFile("multipage_watermarked", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "CONFIDENTIAL",
      position = PositionConfig.Random,
      orientation = OrientationConfig.Fixed(45.0),
      fontSize = FontSizeConfig.Fixed(30.0),
      color = ColorConfig.Fixed(Color.RED),
      quantity = 2
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig))
    
    result shouldBe outputFile
    outputFile.exists() shouldBe true
    
    // Verify the watermarked PDF has the same number of pages
    val originalPageCount = runSync(PdfProcessor.getPageCount(inputFile))
    val watermarkedPageCount = runSync(PdfProcessor.getPageCount(outputFile))
    watermarkedPageCount shouldBe originalPageCount
  }

  it should "handle multiple watermarks" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val outputFile = createTempFile("multiple_watermarks", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "COPY",
      position = PositionConfig.Random,
      orientation = OrientationConfig.Random,
      fontSize = FontSizeConfig.Random(12.0, 24.0),
      color = ColorConfig.Fixed(Color.LIGHT_GRAY),
      quantity = 10
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, watermarkConfig).either)
    
    result.isRight shouldBe true
    outputFile.exists() shouldBe true
  }

  it should "fail gracefully with invalid input file" in {
    val nonExistentFile = new File("non-existent.pdf")
    val outputFile = createTempFile("output", ".pdf")
    
    val watermarkConfig = WatermarkConfig(
      text = "TEST",
      position = PositionConfig.Fixed(200.0, 300.0),
      orientation = OrientationConfig.Fixed(0.0),
      fontSize = FontSizeConfig.Fixed(12.0),
      color = ColorConfig.Fixed(Color.BLACK),
      quantity = 1
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(nonExistentFile, outputFile, watermarkConfig).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError]
  }

  it should "respect quantity constraints" in {
    val inputFile = getTestPdfFile("simple.pdf")
    val outputFile = createTempFile("quantity_test", ".pdf")
    
    // Test with quantity = 1
    val singleWatermarkConfig = WatermarkConfig(
      text = "SINGLE",
      position = PositionConfig.Fixed(200.0, 300.0),
      orientation = OrientationConfig.Fixed(0.0),
      fontSize = FontSizeConfig.Fixed(16.0),
      color = ColorConfig.Fixed(Color.CYAN),
      quantity = 1
    )
    
    val result = runSync(WatermarkRenderer.applyWatermarks(inputFile, outputFile, singleWatermarkConfig).either)
    
    result.isRight shouldBe true
    outputFile.exists() shouldBe true
  }
}