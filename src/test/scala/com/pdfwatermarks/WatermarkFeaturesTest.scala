package com.pdfwatermarks

import zio._
import zio.test._
import zio.test.Assertion._
import java.awt.Color
import com.pdfwatermarks.domain._
import com.pdfwatermarks.pdf.WatermarkRenderer

/**
 * Test suite to verify all watermark configuration features work correctly.
 * This includes testing the newly implemented RandomPerLetter coloring feature.
 */
object WatermarkFeaturesTest extends ZIOSpecDefault {

  def spec = suite("Watermark Configuration Features")(
    test("should generate watermark instances with fixed positioning and coloring") {
      val config = WatermarkConfig(
        text = "CONFIDENTIAL",
        position = PositionConfig.Fixed(100.0, 400.0),
        orientation = OrientationConfig.Fixed(45.0),
        fontSize = FontSizeConfig.Fixed(24.0),
        color = ColorConfig.Fixed(Color.RED),
        quantity = 1
      )
      
      val pageDimensions = PageDimensions(612.0, 792.0)
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      assert(instances.length)(equalTo(1)) &&
      assert(instances.head.text)(equalTo("CONFIDENTIAL")) &&
      assert(instances.head.position)(equalTo(Point(100.0, 400.0))) &&
      assert(instances.head.angle)(equalTo(45.0)) &&
      assert(instances.head.fontSize)(equalTo(24.0)) &&
      assert(instances.head.color)(equalTo(Color.RED))
    },

    test("should generate multiple watermark instances with random positioning") {
      val config = WatermarkConfig(
        text = "SAMPLE",
        position = PositionConfig.Random,
        orientation = OrientationConfig.Random,
        fontSize = FontSizeConfig.Fixed(32.0),
        color = ColorConfig.RandomPerLetter, // Test the new feature!
        quantity = 3
      )
      
      val pageDimensions = PageDimensions(612.0, 792.0)
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      assert(instances.length)(equalTo(3)) &&
      assert(instances.forall(_.text == "SAMPLE"))(isTrue) &&
      assert(instances.forall(_.fontSize == 32.0))(isTrue) &&
      assert(instances.forall(i => i.position.x >= 50.0 && i.position.x <= 562.0))(isTrue) &&
      assert(instances.forall(i => i.position.y >= 50.0 && i.position.y <= 742.0))(isTrue) &&
      assert(instances.forall(i => i.angle >= 0.0 && i.angle <= 360.0))(isTrue)
    },

    test("should generate watermarks with random font sizes") {
      val config = WatermarkConfig(
        text = "DRAFT",
        position = PositionConfig.Fixed(200.0, 300.0),
        orientation = OrientationConfig.Fixed(0.0),
        fontSize = FontSizeConfig.Random(18.0, 36.0),
        color = ColorConfig.RandomPerLetter,
        quantity = 5
      )
      
      val pageDimensions = PageDimensions(612.0, 792.0)
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      assert(instances.length)(equalTo(5)) &&
      assert(instances.forall(_.text == "DRAFT"))(isTrue) &&
      assert(instances.forall(i => i.fontSize >= 18.0 && i.fontSize <= 36.0))(isTrue) &&
      assert(instances.forall(_.angle == 0.0))(isTrue)
    },

    test("should generate watermarks with template positioning") {
      val config = WatermarkConfig(
        text = "PRIVATE",
        position = PositionConfig.Template(PositionTemplate.Center),
        orientation = OrientationConfig.Preset(OrientationPreset.DiagonalUp),
        fontSize = FontSizeConfig.Recommended(DocumentType.Legal),
        color = ColorConfig.Palette(ColorPalette.Professional),
        quantity = 1
      )
      
      val pageDimensions = PageDimensions(612.0, 792.0)
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      assert(instances.length)(equalTo(1)) &&
      assert(instances.head.text)(equalTo("PRIVATE")) &&
      assert(instances.head.angle)(equalTo(45.0)) &&
      assert(instances.head.fontSize)(isGreaterThan(0.0))
    },

    test("should handle ColorConfig.RandomPerLetter correctly") {
      val config = WatermarkConfig(
        text = "TEST",
        position = PositionConfig.Fixed(100.0, 100.0),
        orientation = OrientationConfig.Fixed(0.0),
        fontSize = FontSizeConfig.Fixed(24.0),
        color = ColorConfig.RandomPerLetter,
        quantity = 2
      )
      
      val pageDimensions = PageDimensions(612.0, 792.0)
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      // When using RandomPerLetter, instances should still be generated successfully
      // The actual per-letter coloring happens in the rendering phase
      assert(instances.length)(equalTo(2)) &&
      assert(instances.forall(_.text == "TEST"))(isTrue) &&
      assert(instances.forall(_.fontSize == 24.0))(isTrue)
    },

    test("should validate watermark quantities within limits") {
      val config = WatermarkConfig(
        text = "LIMIT_TEST",
        position = PositionConfig.Random,
        orientation = OrientationConfig.Random,
        fontSize = FontSizeConfig.Fixed(20.0),
        color = ColorConfig.Fixed(Color.BLUE),
        quantity = 50
      )
      
      val pageDimensions = PageDimensions(612.0, 792.0)
      val instances = WatermarkRenderer.generateWatermarkInstances(pageDimensions, config)
      
      assert(instances.length)(equalTo(50)) &&
      assert(instances.forall(_.text == "LIMIT_TEST"))(isTrue) &&
      assert(ConfigConstraints.isValidQuantity(config.quantity))(isTrue)
    }
  )
}