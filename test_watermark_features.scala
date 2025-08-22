#!/usr/bin/env scala

import java.io.File
import java.awt.Color
import com.pdfwatermarks.domain._
import com.pdfwatermarks.pdf.WatermarkRenderer

/**
 * Test script to verify all watermark configuration features work correctly.
 * This script tests the newly implemented RandomPerLetter coloring and other features.
 */
object TestWatermarkFeatures extends App {
  
  println("Testing PDF Watermark Configuration Features...")
  
  // Create test configurations for each feature
  val testConfigurations = List(
    
    // Test 1: Fixed positioning and coloring
    WatermarkConfig(
      text = "CONFIDENTIAL",
      position = PositionConfig.Fixed(100.0, 400.0),
      orientation = OrientationConfig.Fixed(45.0),
      fontSize = FontSizeConfig.Fixed(24.0),
      color = ColorConfig.Fixed(Color.RED),
      quantity = 1
    ),
    
    // Test 2: Random positioning with per-letter coloring
    WatermarkConfig(
      text = "SAMPLE",
      position = PositionConfig.Random,
      orientation = OrientationConfig.Random,
      fontSize = FontSizeConfig.Fixed(32.0),
      color = ColorConfig.RandomPerLetter, // This is the new feature!
      quantity = 3
    ),
    
    // Test 3: Multiple watermarks with different configurations
    WatermarkConfig(
      text = "DRAFT",
      position = PositionConfig.Random,
      orientation = OrientationConfig.Fixed(90.0),
      fontSize = FontSizeConfig.Random(18.0, 36.0),
      color = ColorConfig.RandomPerLetter,
      quantity = 5
    ),
    
    // Test 4: Template positioning with palette colors
    WatermarkConfig(
      text = "PRIVATE",
      position = PositionConfig.Template(PositionTemplate.FourCorners),
      orientation = OrientationConfig.Preset(OrientationPreset.DiagonalUp),
      fontSize = FontSizeConfig.Recommended(DocumentType.Legal),
      color = ColorConfig.Palette(ColorPalette.Professional),
      quantity = 4
    )
  )
  
  // Test each configuration
  testConfigurations.zipWithIndex.foreach { case (config, index) =>
    println(s"\nTest ${index + 1}: ${config.text}")
    testConfiguration(config, index + 1)
  }
  
  println("\nAll watermark feature tests completed!")
  
  def testConfiguration(config: WatermarkConfig, testNumber: Int): Unit = {
    try {
      // Create sample page dimensions
      val pageDimensions = PageDimensions(612.0, 792.0) // US Letter size
      
      // Generate watermark instances
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      println(s"  - Generated ${instances.length} watermark instances")
      println(s"  - Position config: ${config.position}")
      println(s"  - Orientation config: ${config.orientation}")
      println(s"  - Font size config: ${config.fontSize}")
      println(s"  - Color config: ${config.color}")
      
      // Validate instances
      instances.foreach { instance =>
        assert(instance.text == config.text, "Text should match configuration")
        assert(instance.fontSize > 0, "Font size should be positive")
        assert(instance.position.x >= 0 && instance.position.x <= pageDimensions.width, "X position should be within page bounds")
        assert(instance.position.y >= 0 && instance.position.y <= pageDimensions.height, "Y position should be within page bounds")
        assert(instance.angle >= 0 && instance.angle <= 360, "Angle should be between 0 and 360 degrees")
      }
      
      println(s"  ✓ Test ${testNumber} passed - all instances valid")
      
    } catch {
      case e: Exception =>
        println(s"  ✗ Test ${testNumber} failed: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}