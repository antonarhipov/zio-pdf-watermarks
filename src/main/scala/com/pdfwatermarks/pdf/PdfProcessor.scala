package com.pdfwatermarks.pdf

import com.pdfwatermarks.domain.*
import com.pdfwatermarks.errors.ErrorPatterns
import com.pdfwatermarks.logging.{StructuredLogging, PerformanceMonitoring}
import zio.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.{File, IOException}
import java.time.Instant

/**
 * PDF processing implementation using Apache PDFBox.
 * 
 * This module provides concrete implementations for PDF file loading,
 * validation, and basic operations using Apache PDFBox library with
 * proper error handling and performance monitoring.
 */
object PdfProcessor {

  /**
   * Load and validate a PDF document from a file.
   * 
   * @param file The PDF file to load
   * @return Either a domain error or a validated PDF document
   */
  def loadPdf(file: File): IO[DomainError, PdfDocument] =
    PerformanceMonitoring.withPerformanceMonitoring("pdf_load_and_validate") {
      for {
        _ <- ZIO.logInfo(s"Loading PDF file: ${file.getName}")
        _ <- validateFileExists(file)
        _ <- validateFileExtension(file)
        _ <- validateFileSize(file)
        pageCount <- getPageCount(file)
        document = PdfDocument(
          id = java.util.UUID.randomUUID().toString,
          filename = file.getName,
          originalSize = file.length(),
          pageCount = pageCount,
          uploadedAt = Instant.now(),
          status = DocumentStatus.Uploaded
        )
        _ <- ZIO.logInfo(s"Successfully loaded PDF: ${file.getName}, pages: $pageCount, size: ${file.length()} bytes")
      } yield document
    }

  /**
   * Get the number of pages in a PDF document.
   * 
   * @param file The PDF file to analyze
   * @return The number of pages or a domain error
   */
  def getPageCount(file: File): IO[DomainError, Int] =
    ErrorPatterns.safely {
      val document = PDDocument.load(file)
      try {
        val pageCount = document.getNumberOfPages
        if (pageCount <= 0) {
          throw new IllegalStateException("PDF document has no pages")
        }
        pageCount
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(_) => 
        DomainError.PdfProcessingError("Failed to read PDF page count - file may be corrupted")
      case other => other
    }

  /**
   * Get page dimensions for a specific page.
   * 
   * @param file The PDF file
   * @param pageNumber The page number (1-based)
   * @return The page dimensions or a domain error
   */
  def getPageDimensions(file: File, pageNumber: Int): IO[DomainError, PageDimensions] =
    ErrorPatterns.safely {
      val document = PDDocument.load(file)
      try {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages) {
          throw new IllegalArgumentException(s"Page number $pageNumber is out of range (1-${document.getNumberOfPages})")
        }
        
        val page = document.getPage(pageNumber - 1) // PDFBox uses 0-based indexing
        val mediaBox = page.getMediaBox
        PageDimensions(mediaBox.getWidth.toDouble, mediaBox.getHeight.toDouble)
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(msg) if msg.contains("IllegalArgumentException") =>
        DomainError.InvalidConfiguration(List(s"Invalid page number: $pageNumber"))
      case DomainError.InternalError(_) =>
        DomainError.PdfProcessingError("Failed to read PDF page dimensions - file may be corrupted")
      case other => other
    }

  /**
   * Get all page dimensions in a PDF document.
   * 
   * @param file The PDF file
   * @return List of page dimensions or a domain error
   */
  def getAllPageDimensions(file: File): IO[DomainError, List[PageDimensions]] =
    ErrorPatterns.safely {
      val document = PDDocument.load(file)
      try {
        val pages = (0 until document.getNumberOfPages).map { pageIndex =>
          val page = document.getPage(pageIndex)
          val mediaBox = page.getMediaBox
          PageDimensions(mediaBox.getWidth.toDouble, mediaBox.getHeight.toDouble)
        }.toList
        pages
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(_) =>
        DomainError.PdfProcessingError("Failed to read PDF page dimensions - file may be corrupted")
      case other => other
    }

  /**
   * Validate that a PDF file can be opened and read.
   * 
   * @param file The PDF file to validate
   * @return Unit on success, domain error on failure
   */
  def validatePdfIntegrity(file: File): IO[DomainError, Unit] =
    ErrorPatterns.safely {
      val document = PDDocument.load(file)
      try {
        // Basic integrity checks
        val pageCount = document.getNumberOfPages
        if (pageCount <= 0) {
          throw new IllegalStateException("PDF document has no pages")
        }

        // Try to access the first page to ensure the document is readable
        val firstPage = document.getPage(0)
        val mediaBox = firstPage.getMediaBox
        
        if (mediaBox.getWidth <= 0 || mediaBox.getHeight <= 0) {
          throw new IllegalStateException("PDF document has invalid page dimensions")
        }

        // Check if document is encrypted (we don't support encrypted PDFs for now)
        if (document.isEncrypted) {
          throw new IllegalStateException("Encrypted PDF documents are not supported")
        }
        
        ()
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(msg) =>
        if (msg.contains("Encrypted PDF")) {
          DomainError.InvalidFileFormat("Encrypted PDF documents are not supported")
        } else if (msg.contains("no pages") || msg.contains("invalid page dimensions")) {
          DomainError.InvalidFileFormat("PDF document appears to be corrupted or invalid")
        } else {
          DomainError.PdfProcessingError(s"PDF validation failed: $msg")
        }
      case other => other
    }

  /**
   * Get PDF metadata information.
   * 
   * @param file The PDF file
   * @return Map of metadata or domain error
   */
  def getPdfMetadata(file: File): IO[DomainError, Map[String, String]] =
    ErrorPatterns.safely {
      val document = PDDocument.load(file)
      try {
        val info = document.getDocumentInformation
        val metadata = scala.collection.mutable.Map[String, String]()
        
        Option(info.getTitle).foreach(metadata += "title" -> _)
        Option(info.getAuthor).foreach(metadata += "author" -> _)
        Option(info.getSubject).foreach(metadata += "subject" -> _)
        Option(info.getKeywords).foreach(metadata += "keywords" -> _)
        Option(info.getCreator).foreach(metadata += "creator" -> _)
        Option(info.getProducer).foreach(metadata += "producer" -> _)
        Option(info.getCreationDate).foreach(date => metadata += "creation_date" -> date.toString)
        Option(info.getModificationDate).foreach(date => metadata += "modification_date" -> date.toString)
        
        metadata.toMap
      } finally {
        document.close()
      }
    }.mapError {
      case DomainError.InternalError(_) =>
        DomainError.PdfProcessingError("Failed to read PDF metadata")
      case other => other
    }

  // ========== Private Validation Methods ==========

  /**
   * Validate that the file exists and is readable.
   */
  private def validateFileExists(file: File): IO[DomainError, Unit] =
    ZIO.cond(
      file.exists() && file.canRead(),
      (),
      DomainError.InvalidFileFormat("File does not exist or is not readable")
    )

  /**
   * Validate file extension is PDF.
   */
  private def validateFileExtension(file: File): IO[DomainError, Unit] = {
    val fileName = file.getName.toLowerCase
    ZIO.cond(
      fileName.endsWith(".pdf"),
      (),
      DomainError.InvalidFileFormat("Only PDF files are supported")
    )
  }

  /**
   * Validate file size is within acceptable limits.
   */
  private def validateFileSize(file: File): IO[DomainError, Unit] = {
    val fileSize = file.length()
    ZIO.cond(
      fileSize <= ConfigConstraints.MaxFileSizeBytes && fileSize > 0,
      (),
      if (fileSize > ConfigConstraints.MaxFileSizeBytes) {
        DomainError.FileSizeExceeded(fileSize, ConfigConstraints.MaxFileSizeBytes)
      } else {
        DomainError.InvalidFileFormat("File is empty")
      }
    )
  }

  // ========== Utility Methods ==========

  /**
   * Check if a file appears to be a valid PDF by checking its header.
   */
  def isPdfFile(file: File): IO[DomainError, Boolean] =
    ErrorPatterns.safely {
      val fis = new java.io.FileInputStream(file)
      try {
        val buffer = new Array[Byte](4)
        val bytesRead = fis.read(buffer)
        
        if (bytesRead < 4) {
          false
        } else {
          // PDF files start with "%PDF"
          val header = new String(buffer)
          header == "%PDF"
        }
      } finally {
        fis.close()
      }
    }.mapError(_ => DomainError.PdfProcessingError("Failed to check PDF file header"))

  /**
   * Get PDF version from file header.
   */
  def getPdfVersion(file: File): IO[DomainError, String] =
    ErrorPatterns.safely {
      val fis = new java.io.FileInputStream(file)
      try {
        val buffer = new Array[Byte](8)
        val bytesRead = fis.read(buffer)
        
        if (bytesRead < 8) {
          "Unknown"
        } else {
          val header = new String(buffer)
          if (header.startsWith("%PDF-")) {
            header.substring(5, 8) // Extract version like "1.4", "1.7", etc.
          } else {
            "Unknown"
          }
        }
      } finally {
        fis.close()
      }
    }.mapError(_ => DomainError.PdfProcessingError("Failed to read PDF version"))

  /**
   * Estimate memory requirements for processing a PDF.
   */
  def estimateMemoryRequirement(file: File): IO[DomainError, Long] =
    for {
      pageCount <- getPageCount(file)
      fileSize = file.length()
      // Rough estimate: file size * 3 + (page count * 1MB) for processing overhead
      estimatedMemory = (fileSize * 3) + (pageCount * 1024 * 1024)
    } yield estimatedMemory

  /**
   * Check if the system has enough memory to process the PDF.
   */
  def checkMemoryAvailability(file: File): IO[DomainError, Unit] =
    for {
      required <- estimateMemoryRequirement(file)
      available <- ZIO.attemptBlocking(Runtime.getRuntime.freeMemory()).orDie
      _ <- ZIO.cond(
        available > required,
        (),
        DomainError.InternalError(
          s"Insufficient memory to process PDF. Required: ${required / (1024 * 1024)}MB, Available: ${available / (1024 * 1024)}MB"
        )
      )
    } yield ()
}