package com.pdfwatermarks.pdf

import com.pdfwatermarks.BaseTestSpec
import com.pdfwatermarks.domain.{DomainError, DocumentStatus}
import zio.*
import java.io.File

/**
 * Tests for PDF loading and validation functionality.
 * Tests the PdfProcessor module using both positive and negative test cases.
 */
class PdfProcessorTest extends BaseTestSpec {

  "PdfProcessor.loadPdf" should "successfully load a valid simple PDF" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.loadPdf(pdfFile))
    
    result.filename shouldBe "simple.pdf"
    result.pageCount shouldBe 1
    result.originalSize shouldBe pdfFile.length()
    result.status shouldBe DocumentStatus.Uploaded
    result.id should not be empty
  }

  it should "successfully load a multi-page PDF" in {
    val pdfFile = getTestPdfFile("multi-page.pdf")
    
    val result = runSync(PdfProcessor.loadPdf(pdfFile))
    
    result.filename shouldBe "multi-page.pdf"
    result.pageCount shouldBe 5
    result.originalSize shouldBe pdfFile.length()
    result.status shouldBe DocumentStatus.Uploaded
  }

  it should "successfully load a large PDF" in {
    val pdfFile = getTestPdfFile("large.pdf")
    
    val result = runSync(PdfProcessor.loadPdf(pdfFile))
    
    result.filename shouldBe "large.pdf"
    result.pageCount shouldBe 10
    result.originalSize shouldBe pdfFile.length()
    result.status shouldBe DocumentStatus.Uploaded
  }

  it should "fail to load a non-existent file" in {
    val nonExistentFile = new File("non-existent.pdf")
    
    val result = runSync(PdfProcessor.loadPdf(nonExistentFile).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.InvalidFileFormat]
    result.swap.getOrElse(fail("Expected Left")).asInstanceOf[DomainError.InvalidFileFormat].message should include("not exist")
  }

  it should "fail to load a file with wrong extension" in {
    val txtFile = createTempFile("test", ".txt")
    
    val result = runSync(PdfProcessor.loadPdf(txtFile).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.InvalidFileFormat]
    result.swap.getOrElse(fail("Expected Left")).asInstanceOf[DomainError.InvalidFileFormat].message should include("Only PDF files are supported")
  }

  it should "fail to load an empty PDF file" in {
    val emptyPdfFile = getTestPdfFile("empty.pdf")
    
    val result = runSync(PdfProcessor.loadPdf(emptyPdfFile).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.PdfProcessingError]
    result.swap.getOrElse(fail("Expected Left")).asInstanceOf[DomainError.PdfProcessingError].message should include("Failed to read PDF page count")
  }

  "PdfProcessor.getPageCount" should "return correct page count for single-page PDF" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.getPageCount(pdfFile))
    
    result shouldBe 1
  }

  it should "return correct page count for multi-page PDF" in {
    val pdfFile = getTestPdfFile("multi-page.pdf")
    
    val result = runSync(PdfProcessor.getPageCount(pdfFile))
    
    result shouldBe 5
  }

  it should "fail for empty PDF" in {
    val emptyPdfFile = getTestPdfFile("empty.pdf")
    
    val result = runSync(PdfProcessor.getPageCount(emptyPdfFile).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.PdfProcessingError]
  }

  "PdfProcessor.getPageDimensions" should "return valid dimensions for a PDF page" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.getPageDimensions(pdfFile, 1))
    
    result.width should be > 0.0
    result.height should be > 0.0
    // Standard letter size is approximately 612 x 792 points
    result.width shouldBe 612.0 +- 1.0
    result.height shouldBe 792.0 +- 1.0
  }

  it should "fail for invalid page number (too high)" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.getPageDimensions(pdfFile, 999).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.PdfProcessingError]
  }

  it should "fail for invalid page number (zero)" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.getPageDimensions(pdfFile, 0).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.PdfProcessingError]
  }

  "PdfProcessor.getAllPageDimensions" should "return dimensions for all pages" in {
    val pdfFile = getTestPdfFile("multi-page.pdf")
    
    val result = runSync(PdfProcessor.getAllPageDimensions(pdfFile))
    
    result should have length 5
    result.foreach { dimensions =>
      dimensions.width should be > 0.0
      dimensions.height should be > 0.0
    }
  }

  "PdfProcessor.validatePdfIntegrity" should "pass for valid PDF files" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.validatePdfIntegrity(pdfFile).either)
    
    result.isRight shouldBe true
  }

  it should "fail for empty PDF" in {
    val emptyPdfFile = getTestPdfFile("empty.pdf")
    
    val result = runSync(PdfProcessor.validatePdfIntegrity(emptyPdfFile).either)
    
    result.isLeft shouldBe true
    result.swap.getOrElse(fail("Expected Left")) shouldBe a[DomainError.InvalidFileFormat]
  }

  "PdfProcessor.getPdfMetadata" should "extract metadata from PDF files" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.getPdfMetadata(pdfFile))
    
    result shouldBe a[Map[String, String]]
    // The test PDF may or may not have metadata - that's fine
    // Just verify we get a Map back without errors
  }

  "PdfProcessor.isPdfFile" should "return true for valid PDF files" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.isPdfFile(pdfFile))
    
    result shouldBe true
  }

  it should "return false for non-PDF files" in {
    val txtFile = createTempFile("test", ".txt")
    txtFile.writeText("This is not a PDF")
    
    val result = runSync(PdfProcessor.isPdfFile(txtFile))
    
    result shouldBe false
  }

  "PdfProcessor.getPdfVersion" should "return version for PDF files" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.getPdfVersion(pdfFile))
    
    result should not be "Unknown"
    result should fullyMatch regex "\\d\\.\\d"
  }

  "PdfProcessor.estimateMemoryRequirement" should "return reasonable estimates" in {
    val pdfFile = getTestPdfFile("large.pdf")
    
    val result = runSync(PdfProcessor.estimateMemoryRequirement(pdfFile))
    
    result should be > 0L
    // Should be more than file size due to processing overhead
    result should be > pdfFile.length()
  }

  "PdfProcessor.checkMemoryAvailability" should "pass for normal-sized files" in {
    val pdfFile = getTestPdfFile("simple.pdf")
    
    val result = runSync(PdfProcessor.checkMemoryAvailability(pdfFile).either)
    
    result.isRight shouldBe true
  }

  // Helper extension for writing text to files
  implicit class FileOps(file: File) {
    def writeText(content: String): Unit = {
      val writer = new java.io.FileWriter(file)
      try writer.write(content)
      finally writer.close()
    }
  }
}