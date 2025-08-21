package com.pdfwatermarks

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import zio.*
import zio.test.*
import java.io.File
import java.nio.file.{Files, Paths}

/**
 * Base test specification providing common utilities and fixtures for PDF watermarking tests.
 * 
 * This class provides:
 * - Common test utilities and helpers
 * - Sample PDF file management
 * - ZIO test runtime setup
 * - Shared test data and fixtures
 */
abstract class BaseTestSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  // Test runtime for ZIO effects
  implicit val runtime: Runtime[Any] = Runtime.default

  // Test data directory
  val testResourcesDir = "src/test/resources"
  val testPdfsDir = s"$testResourcesDir/pdfs"

  // Helper method to run ZIO effects in tests
  def runSync[E, A](effect: ZIO[Any, E, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }

  // Helper method to get test PDF file
  def getTestPdfFile(filename: String): File = {
    val file = new File(s"$testPdfsDir/$filename")
    if (!file.exists()) {
      fail(s"Test PDF file not found: ${file.getAbsolutePath}")
    }
    file
  }

  // Helper method to create temporary test file
  def createTempFile(prefix: String, suffix: String): File = {
    val tempFile = File.createTempFile(prefix, suffix)
    tempFile.deleteOnExit()
    tempFile
  }

  // Cleanup temporary files after each test
  override def afterEach(): Unit = {
    // Cleanup logic can be added here
    super.afterEach()
  }
}

/**
 * ZIO Test base specification for ZIO-specific testing.
 */
abstract class BaseZIOSpec extends ZIOSpecDefault {

  // Test data directory
  val testResourcesDir = "src/test/resources"
  val testPdfsDir = s"$testResourcesDir/pdfs"

  // Helper method to get test PDF file
  def getTestPdfFile(filename: String): File = {
    val file = new File(s"$testPdfsDir/$filename")
    if (!file.exists()) {
      throw new RuntimeException(s"Test PDF file not found: ${file.getAbsolutePath}")
    }
    file
  }

  // Helper method to create temporary test file
  def createTempFile(prefix: String, suffix: String): UIO[File] = 
    ZIO.succeed {
      val tempFile = File.createTempFile(prefix, suffix)
      tempFile.deleteOnExit()
      tempFile
    }
}