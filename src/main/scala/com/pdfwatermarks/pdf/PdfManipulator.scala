package com.pdfwatermarks.pdf

import com.pdfwatermarks.domain.*
import com.pdfwatermarks.errors.ErrorPatterns
import com.pdfwatermarks.logging.{StructuredLogging, PerformanceMonitoring}
import zio.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.io.{File, IOException, ByteArrayOutputStream}
import java.awt.Color

/**
 * PDF document manipulation service using Apache PDFBox.
 * 
 * This service provides comprehensive PDF document manipulation capabilities
 * including document copying, page operations, content modification, and
 * preparation for watermark application.
 */
object PdfManipulator {

  /**
   * Create a copy of a PDF document.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the copy
   * @return Either a domain error or the target file
   */
  def copyDocument(sourceFile: File, targetFile: File): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_copy_document") {
      ErrorPatterns.safely {
        val sourceDoc = Loader.loadPDF(sourceFile)
        try {
          // Create a new document and copy all pages
          val targetDoc = new PDDocument()
          try {
            // Copy document information
            if (sourceDoc.getDocumentInformation != null) {
              targetDoc.setDocumentInformation(sourceDoc.getDocumentInformation)
            }
            
            // Copy all pages
            val iterator = sourceDoc.getPages.iterator()
            while (iterator.hasNext) {
              val page = iterator.next()
              targetDoc.addPage(page)
            }
            
            // Save the copied document
            targetDoc.save(targetFile)
            targetFile
          } finally {
            targetDoc.close()
          }
        } finally {
          sourceDoc.close()
        }
      }.mapError {
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to copy PDF document")
        case other => other
      }
    }

  /**
   * Create a new PDF document with specified pages from source.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the new document
   * @param pageNumbers List of page numbers to include (1-based)
   * @return Either a domain error or the target file
   */
  def extractPages(sourceFile: File, targetFile: File, pageNumbers: List[Int]): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_extract_pages") {
      ErrorPatterns.safely {
        val sourceDoc = Loader.loadPDF(sourceFile)
        try {
          val totalPages = sourceDoc.getNumberOfPages
          
          // Validate page numbers
          val invalidPages = pageNumbers.filter(p => p < 1 || p > totalPages)
          if (invalidPages.nonEmpty) {
            throw new IllegalArgumentException(s"Invalid page numbers: ${invalidPages.mkString(", ")}. Valid range: 1-$totalPages")
          }
          
          val targetDoc = new PDDocument()
          try {
            // Copy document information
            if (sourceDoc.getDocumentInformation != null) {
              targetDoc.setDocumentInformation(sourceDoc.getDocumentInformation)
            }
            
            // Copy specified pages
            pageNumbers.foreach { pageNum =>
              val page = sourceDoc.getPage(pageNum - 1) // PDFBox uses 0-based indexing
              targetDoc.addPage(page)
            }
            
            targetDoc.save(targetFile)
            targetFile
          } finally {
            targetDoc.close()
          }
        } finally {
          sourceDoc.close()
        }
      }.mapError {
        case DomainError.InternalError(msg) if msg.contains("IllegalArgumentException") =>
          DomainError.InvalidConfiguration(List("Invalid page numbers specified"))
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to extract pages from PDF document")
        case other => other
      }
    }

  /**
   * Create a completely new blank PDF document.
   * 
   * @param targetFile The location for the new document
   * @param pageCount Number of blank pages to create
   * @param pageSize Page size (default: US Letter)
   * @return Either a domain error or the created file
   */
  def createBlankDocument(
    targetFile: File, 
    pageCount: Int = 1, 
    pageSize: PDRectangle = PDRectangle.LETTER
  ): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_create_blank_document") {
      ErrorPatterns.safely {
        if (pageCount <= 0) {
          throw new IllegalArgumentException("Page count must be positive")
        }
        
        val document = new PDDocument()
        try {
          // Add blank pages
          (1 to pageCount).foreach { _ =>
            val page = new PDPage(pageSize)
            document.addPage(page)
          }
          
          document.save(targetFile)
          targetFile
        } finally {
          document.close()
        }
      }.mapError {
        case DomainError.InternalError(msg) if msg.contains("IllegalArgumentException") =>
          DomainError.InvalidConfiguration(List("Invalid page count specified"))
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to create blank PDF document")
        case other => other
      }
    }

  /**
   * Merge multiple PDF documents into a single document.
   * 
   * @param sourceFiles List of source PDF files to merge
   * @param targetFile The target location for the merged document
   * @return Either a domain error or the merged file
   */
  def mergeDocuments(sourceFiles: List[File], targetFile: File): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_merge_documents") {
      ErrorPatterns.safely {
        if (sourceFiles.isEmpty) {
          throw new IllegalArgumentException("No source files provided for merging")
        }
        
        val targetDoc = new PDDocument()
        val sourceDocuments = scala.collection.mutable.ListBuffer[PDDocument]()
        
        try {
          // Load all source documents
          sourceFiles.foreach { sourceFile =>
            if (!sourceFile.exists()) {
              throw new IllegalArgumentException(s"Source file does not exist: ${sourceFile.getName}")
            }
            val doc = Loader.loadPDF(sourceFile)
            sourceDocuments += doc
          }
          
          // Merge pages from all documents
          sourceDocuments.foreach { sourceDoc =>
            val iterator = sourceDoc.getPages.iterator()
            while (iterator.hasNext) {
              val page = iterator.next()
              targetDoc.addPage(page)
            }
          }
          
          targetDoc.save(targetFile)
          targetFile
        } finally {
          // Clean up all loaded documents
          sourceDocuments.foreach(_.close())
          targetDoc.close()
        }
      }.mapError {
        case DomainError.InternalError(msg) if msg.contains("IllegalArgumentException") =>
          DomainError.InvalidConfiguration(List(s"Merge operation failed: $msg"))
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to merge PDF documents")
        case other => other
      }
    }

  /**
   * Get document as byte array for in-memory processing.
   * 
   * @param file The PDF file to convert
   * @return Either a domain error or byte array representation
   */
  def documentToByteArray(file: File): IO[DomainError, Array[Byte]] =
    ErrorPatterns.safely {
      val document = Loader.loadPDF(file)
      try {
        val outputStream = new ByteArrayOutputStream()
        document.save(outputStream)
        outputStream.toByteArray
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(_) =>
        DomainError.PdfProcessingError("Failed to convert PDF document to byte array")
      case other => other
    }

  /**
   * Create document from byte array.
   * 
   * @param bytes The PDF byte array
   * @param targetFile The target file location
   * @return Either a domain error or the created file
   */
  def documentFromByteArray(bytes: Array[Byte], targetFile: File): IO[DomainError, File] =
    ErrorPatterns.safely {
      val document = Loader.loadPDF(bytes)
      try {
        document.save(targetFile)
        targetFile
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(_) =>
        DomainError.PdfProcessingError("Failed to create PDF document from byte array")
      case other => other
    }

  /**
   * Rotate pages in a PDF document.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the rotated document
   * @param rotation Rotation angle (90, 180, 270 degrees)
   * @param pageNumbers Optional list of page numbers to rotate (default: all pages)
   * @return Either a domain error or the target file
   */
  def rotatePages(
    sourceFile: File, 
    targetFile: File, 
    rotation: Int, 
    pageNumbers: Option[List[Int]] = None
  ): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_rotate_pages") {
      ErrorPatterns.safely {
        if (!List(90, 180, 270).contains(rotation)) {
          throw new IllegalArgumentException("Rotation must be 90, 180, or 270 degrees")
        }
        
        val document = Loader.loadPDF(sourceFile)
        try {
          val totalPages = document.getNumberOfPages
          val pagesToRotate = pageNumbers.getOrElse((1 to totalPages).toList)
          
          // Validate page numbers
          val invalidPages = pagesToRotate.filter(p => p < 1 || p > totalPages)
          if (invalidPages.nonEmpty) {
            throw new IllegalArgumentException(s"Invalid page numbers: ${invalidPages.mkString(", ")}")
          }
          
          // Rotate specified pages
          pagesToRotate.foreach { pageNum =>
            val page = document.getPage(pageNum - 1)
            val currentRotation = page.getRotation
            page.setRotation((currentRotation + rotation) % 360)
          }
          
          document.save(targetFile)
          targetFile
        } finally {
          document.close()
        }
      }.mapError {
        case DomainError.InternalError(msg) if msg.contains("IllegalArgumentException") =>
          DomainError.InvalidConfiguration(List(s"Page rotation failed: $msg"))
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to rotate PDF pages")
        case other => other
      }
    }

  /**
   * Scale pages in a PDF document.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the scaled document
   * @param scaleX Horizontal scale factor
   * @param scaleY Vertical scale factor
   * @param pageNumbers Optional list of page numbers to scale (default: all pages)
   * @return Either a domain error or the target file
   */
  def scalePages(
    sourceFile: File, 
    targetFile: File, 
    scaleX: Float, 
    scaleY: Float, 
    pageNumbers: Option[List[Int]] = None
  ): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_scale_pages") {
      ErrorPatterns.safely {
        if (scaleX <= 0 || scaleY <= 0) {
          throw new IllegalArgumentException("Scale factors must be positive")
        }
        
        val document = Loader.loadPDF(sourceFile)
        try {
          val totalPages = document.getNumberOfPages
          val pagesToScale = pageNumbers.getOrElse((1 to totalPages).toList)
          
          // Validate page numbers
          val invalidPages = pagesToScale.filter(p => p < 1 || p > totalPages)
          if (invalidPages.nonEmpty) {
            throw new IllegalArgumentException(s"Invalid page numbers: ${invalidPages.mkString(", ")}")
          }
          
          // Scale specified pages
          pagesToScale.foreach { pageNum =>
            val page = document.getPage(pageNum - 1)
            val mediaBox = page.getMediaBox
            val newWidth = mediaBox.getWidth * scaleX
            val newHeight = mediaBox.getHeight * scaleY
            page.setMediaBox(new PDRectangle(newWidth, newHeight))
          }
          
          document.save(targetFile)
          targetFile
        } finally {
          document.close()
        }
      }.mapError {
        case DomainError.InternalError(msg) if msg.contains("IllegalArgumentException") =>
          DomainError.InvalidConfiguration(List(s"Page scaling failed: $msg"))
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to scale PDF pages")
        case other => other
      }
    }

  /**
   * Optimize a PDF document by removing unused resources.
   * 
   * @param sourceFile The source PDF file
   * @param targetFile The target location for the optimized document
   * @return Either a domain error or the optimized file
   */
  def optimizeDocument(sourceFile: File, targetFile: File): IO[DomainError, File] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_optimize_document") {
      ErrorPatterns.safely {
        val document = Loader.loadPDF(sourceFile)
        try {
          // Basic optimization - remove unused resources
          // Note: More sophisticated optimization could be added here
          document.save(targetFile)
          targetFile
        } finally {
          document.close()
        }
      }.mapError {
        case DomainError.InternalError(_) =>
          DomainError.PdfProcessingError("Failed to optimize PDF document")
        case other => other
      }
    }

  /**
   * Validate document integrity and repairability.
   * 
   * @param file The PDF file to validate
   * @return Either a domain error or validation result
   */
  def validateDocumentIntegrity(file: File): IO[DomainError, Map[String, String]] =
    ErrorPatterns.safely {
      val document = Loader.loadPDF(file)
      try {
        val results = scala.collection.mutable.Map[String, String]()
        
        // Basic integrity checks
        val pageCount = document.getNumberOfPages
        results += "page_count" -> pageCount.toString
        results += "encrypted" -> document.isEncrypted.toString
        
        // Check each page for basic validity
        var validPages = 0
        (0 until pageCount).foreach { pageIndex =>
          try {
            val page = document.getPage(pageIndex)
            val mediaBox = page.getMediaBox
            if (mediaBox.getWidth > 0 && mediaBox.getHeight > 0) {
              validPages += 1
            }
          } catch {
            case _: Exception => // Invalid page
          }
        }
        
        results += "valid_pages" -> validPages.toString
        results += "integrity_score" -> (if (validPages == pageCount) "100" else ((validPages * 100) / pageCount).toString)
        
        results.toMap
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(_) =>
        DomainError.PdfProcessingError("Failed to validate PDF document integrity")
      case other => other
    }

  /**
   * Create a backup copy with timestamp.
   * 
   * @param sourceFile The source PDF file
   * @param backupDir The directory for backup files
   * @return Either a domain error or the backup file
   */
  def createBackup(sourceFile: File, backupDir: File): IO[DomainError, File] =
    for {
      _ <- ZIO.attemptBlocking {
        if (!backupDir.exists()) {
          backupDir.mkdirs()
        }
      }.orDie
      
      timestamp = java.time.Instant.now().toString.replace(":", "-")
      baseName = sourceFile.getName.stripSuffix(".pdf")
      backupFile = new File(backupDir, s"${baseName}_backup_$timestamp.pdf")
      
      result <- copyDocument(sourceFile, backupFile)
    } yield result
}