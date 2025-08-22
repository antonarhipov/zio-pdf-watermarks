#!/usr/bin/env scala

import java.awt.Color
import com.pdfwatermarks.domain._
import com.pdfwatermarks.pdf.WatermarkRenderer

/**
 * Test script to verify that random parameters are applied independently to each watermark.
 * This demonstrates the fix for the issue where random parameters were not truly independent.
 */
object TestIndependentRandom extends App {
  
  println("Testing Independent Random Parameters for Each Watermark...")
  println("=" * 60)
  
  // Create a configuration with multiple random watermarks
  val config = WatermarkConfig(
    text = "SAMPLE",
    position = PositionConfig.Random,
    orientation = OrientationConfig.Random,
    fontSize = FontSizeConfig.Random(12.0, 48.0),
    color = ColorConfig.RandomPerLetter,
    quantity = 5
  )
  
  val pageDimensions = PageDimensions(612.0, 792.0) // US Letter size
  
  // Generate watermark instances multiple times to verify independence
  println("Generating 5 watermarks 3 times to verify independence:")
  println()
  
  for (run <- 1 to 3) {
    println(s"Run $run:")
    val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
    
    instances.zipWithIndex.foreach { case (instance, index) =>
      println(f"  Watermark ${index + 1}: pos=(${instance.position.x}%6.1f, ${instance.position.y}%6.1f), " +
             f"angle=${instance.angle}%5.1f°, fontSize=${instance.fontSize}%4.1f, " +
             f"color=RGB(${instance.color.getRed}%3d,${instance.color.getGreen}%3d,${instance.color.getBlue}%3d)")
    }
    
    // Verify that positions are different for each watermark
    val positions = instances.map(_.position)
    val uniquePositions = positions.distinct
    val positionsMatch = uniquePositions.length < positions.length
    
    // Verify that angles are different for each watermark
    val angles = instances.map(_.angle)
    val uniqueAngles = angles.distinct
    val anglesMatch = uniqueAngles.length < angles.length
    
    // Verify that font sizes are different for each watermark
    val fontSizes = instances.map(_.fontSize)
    val uniqueFontSizes = fontSizes.distinct
    val fontSizesMatch = uniqueFontSizes.length < fontSizes.length
    
    // Verify that colors are different for each watermark
    val colors = instances.map(i => (i.color.getRed, i.color.getGreen, i.color.getBlue))
    val uniqueColors = colors.distinct
    val colorsMatch = uniqueColors.length < colors.length
    
    println(f"    Independence check: positions=${!positionsMatch} angles=${!anglesMatch} fontSizes=${!fontSizesMatch} colors=${!colorsMatch}")
    
    if (!positionsMatch && !anglesMatch && !fontSizesMatch && !colorsMatch) {
      println("    ✓ All parameters are independent for each watermark")
    } else {
      println("    ⚠ Some parameters may not be fully independent")
    }
    println()
  }
  
  println("Test completed!")
  println()
  println("Key improvements made:")
  println("- Each watermark now gets a unique seed from a base random generator")
  println("- Position, angle, font size, and color use different offsets from the same seed")
  println("- Per-letter coloring uses the watermark-specific seed instead of hash-based seed")
  println("- This ensures truly independent random parameters for each watermark")
}