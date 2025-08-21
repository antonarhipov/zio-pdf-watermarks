package com.pdfwatermarks

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File

/**
 * Utility for generating test PDF files with various characteristics.
 * Used to create sample PDFs for testing PDF processing functionality.
 */
object TestPdfGenerator {

  /**
   * Generate a simple single-page PDF for basic testing.
   */
  def generateSimplePdf(outputFile: File, title: String = "Test Document"): Unit = {
    val document = new PDDocument()
    try {
      val page = new PDPage()
      document.addPage(page)
      
      val contentStream = new PDPageContentStream(document, page)
      try {
        contentStream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
        contentStream.beginText()
        contentStream.newLineAtOffset(100, 700)
        contentStream.showText(title)
        contentStream.newLineAtOffset(0, -20)
        contentStream.showText("This is a test PDF document.")
        contentStream.newLineAtOffset(0, -20)
        contentStream.showText("Generated for unit testing purposes.")
        contentStream.endText()
      } finally {
        contentStream.close()
      }
      
      document.save(outputFile)
    } finally {
      document.close()
    }
  }

  /**
   * Generate a multi-page PDF for testing pagination.
   */
  def generateMultiPagePdf(outputFile: File, pageCount: Int = 3): Unit = {
    val document = new PDDocument()
    try {
      (1 to pageCount).foreach { pageNumber =>
        val page = new PDPage()
        document.addPage(page)
        
        val contentStream = new PDPageContentStream(document, page)
        try {
          contentStream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14)
          contentStream.beginText()
          contentStream.newLineAtOffset(100, 700)
          contentStream.showText(s"Page $pageNumber of $pageCount")
          contentStream.newLineAtOffset(0, -30)
          contentStream.showText("This is a multi-page test document.")
          contentStream.newLineAtOffset(0, -20)
          contentStream.showText("Each page has unique content for testing.")
          contentStream.newLineAtOffset(0, -40)
          contentStream.showText(s"Content specific to page $pageNumber:")
          contentStream.newLineAtOffset(0, -20)
          contentStream.showText("Lorem ipsum dolor sit amet, consectetur adipiscing elit.")
          contentStream.newLineAtOffset(0, -20)
          contentStream.showText("Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
          contentStream.endText()
        } finally {
          contentStream.close()
        }
      }
      
      document.save(outputFile)
    } finally {
      document.close()
    }
  }

  /**
   * Generate a large PDF with many pages for performance testing.
   */
  def generateLargePdf(outputFile: File, pageCount: Int = 50): Unit = {
    val document = new PDDocument()
    try {
      (1 to pageCount).foreach { pageNumber =>
        val page = new PDPage()
        document.addPage(page)
        
        val contentStream = new PDPageContentStream(document, page)
        try {
          contentStream.setFont(PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 10)
          contentStream.beginText()
          contentStream.newLineAtOffset(50, 750)
          contentStream.showText(s"Large Test Document - Page $pageNumber")
          contentStream.newLineAtOffset(0, -20)
          
          // Add substantial content to make the file larger
          val lines = List(
            "This is a large PDF document generated for performance testing.",
            "It contains multiple pages with substantial text content.",
            "The purpose is to test how the application handles larger files.",
            "",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod",
            "tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim",
            "veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea",
            "commodo consequat. Duis aute irure dolor in reprehenderit in voluptate",
            "velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint",
            "occaecat cupidatat non proident, sunt in culpa qui officia deserunt",
            "mollit anim id est laborum.",
            "",
            "Sed ut perspiciatis unde omnis iste natus error sit voluptatem",
            "accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae",
            "ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt",
            "explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut",
            "odit aut fugit, sed quia consequuntur magni dolores eos qui ratione",
            "voluptatem sequi nesciunt."
          )
          
          lines.foreach { line =>
            contentStream.showText(line)
            contentStream.newLineAtOffset(0, -15)
          }
          
          contentStream.endText()
        } finally {
          contentStream.close()
        }
      }
      
      document.save(outputFile)
    } finally {
      document.close()
    }
  }

  /**
   * Generate an empty PDF (no pages) for error testing.
   */
  def generateEmptyPdf(outputFile: File): Unit = {
    val document = new PDDocument()
    try {
      // Don't add any pages - this creates an empty PDF
      document.save(outputFile)
    } finally {
      document.close()
    }
  }

  /**
   * Generate all test PDF files in the specified directory.
   */
  def generateAllTestFiles(testPdfDir: String): Unit = {
    val dir = new File(testPdfDir)
    if (!dir.exists()) {
      dir.mkdirs()
    }

    // Generate various test PDFs
    generateSimplePdf(new File(s"$testPdfDir/simple.pdf"))
    generateMultiPagePdf(new File(s"$testPdfDir/multi-page.pdf"), 5)
    generateLargePdf(new File(s"$testPdfDir/large.pdf"), 10)
    generateEmptyPdf(new File(s"$testPdfDir/empty.pdf"))
    
    // Generate PDFs with specific characteristics
    generateSimplePdf(new File(s"$testPdfDir/test-sample.pdf"), "Sample Test Document")
    generateSimplePdf(new File(s"$testPdfDir/watermark-test.pdf"), "Watermark Test Document")
  }
}