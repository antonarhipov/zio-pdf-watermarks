package com.pdfwatermarks.services

import com.pdfwatermarks.domain.*
import zio.*
import java.io.File

/**
 * Service interfaces for the PDF Watermarking Application.
 * 
 * These services define the core business logic interfaces using
 * ZIO service pattern for dependency injection and testability.
 */

// ========== PDF Processing Service ==========

/**
 * Service for PDF document processing operations.
 */
trait PdfProcessingService {
  /**
   * Load and validate a PDF document from a file.
   */
  def loadPdf(file: File): IO[DomainError, PdfDocument]
  
  /**
   * Apply watermarks to a PDF document according to the configuration.
   */
  def applyWatermarks(
    document: PdfDocument, 
    config: WatermarkConfig
  ): IO[DomainError, File]
  
  /**
   * Get the number of pages in a PDF document.
   */
  def getPageCount(file: File): IO[DomainError, Int]
  
  /**
   * Get page dimensions for a specific page.
   */
  def getPageDimensions(file: File, pageNumber: Int): IO[DomainError, PageDimensions]
}

object PdfProcessingService {
  def loadPdf(file: File): ZIO[PdfProcessingService, DomainError, PdfDocument] =
    ZIO.serviceWithZIO[PdfProcessingService](_.loadPdf(file))
    
  def applyWatermarks(
    document: PdfDocument, 
    config: WatermarkConfig
  ): ZIO[PdfProcessingService, DomainError, File] =
    ZIO.serviceWithZIO[PdfProcessingService](_.applyWatermarks(document, config))
    
  def getPageCount(file: File): ZIO[PdfProcessingService, DomainError, Int] =
    ZIO.serviceWithZIO[PdfProcessingService](_.getPageCount(file))
    
  def getPageDimensions(file: File, pageNumber: Int): ZIO[PdfProcessingService, DomainError, PageDimensions] =
    ZIO.serviceWithZIO[PdfProcessingService](_.getPageDimensions(file, pageNumber))
}

// ========== Watermark Rendering Service ==========

/**
 * Service for watermark layout calculation and rendering.
 */
trait WatermarkRenderingService {
  /**
   * Generate watermark layout for a single page.
   */
  def generatePageLayout(
    pageDimensions: PageDimensions,
    config: WatermarkConfig
  ): IO[DomainError, PageWatermarkLayout]
  
  /**
   * Calculate optimal positioning to avoid overlaps.
   */
  def calculateNonOverlappingPositions(
    pageDimensions: PageDimensions,
    watermarkCount: Int,
    fontSize: Double
  ): IO[DomainError, List[Point]]
  
  /**
   * Generate random color for each character in watermark text.
   */
  def generateRandomColors(text: String): IO[Nothing, List[java.awt.Color]]
}

object WatermarkRenderingService {
  def generatePageLayout(
    pageDimensions: PageDimensions,
    config: WatermarkConfig
  ): ZIO[WatermarkRenderingService, DomainError, PageWatermarkLayout] =
    ZIO.serviceWithZIO[WatermarkRenderingService](_.generatePageLayout(pageDimensions, config))
    
  def calculateNonOverlappingPositions(
    pageDimensions: PageDimensions,
    watermarkCount: Int,
    fontSize: Double
  ): ZIO[WatermarkRenderingService, DomainError, List[Point]] =
    ZIO.serviceWithZIO[WatermarkRenderingService](_.calculateNonOverlappingPositions(pageDimensions, watermarkCount, fontSize))
    
  def generateRandomColors(text: String): ZIO[WatermarkRenderingService, Nothing, List[java.awt.Color]] =
    ZIO.serviceWithZIO[WatermarkRenderingService](_.generateRandomColors(text))
}

// ========== File Management Service ==========

/**
 * Service for file upload, temporary storage, and cleanup operations.
 */
trait FileManagementService {
  /**
   * Store uploaded file in temporary location.
   */
  def storeUploadedFile(uploadInfo: UploadInfo): IO[DomainError, File]
  
  /**
   * Validate file format and size.
   */
  def validateFile(file: File): IO[DomainError, Unit]
  
  /**
   * Clean up temporary files.
   */
  def cleanupTempFiles(files: List[File]): UIO[Unit]
  
  /**
   * Generate unique filename for processed files.
   */
  def generateProcessedFilename(originalFilename: String): UIO[String]
}

object FileManagementService {
  def storeUploadedFile(uploadInfo: UploadInfo): ZIO[FileManagementService, DomainError, File] =
    ZIO.serviceWithZIO[FileManagementService](_.storeUploadedFile(uploadInfo))
    
  def validateFile(file: File): ZIO[FileManagementService, DomainError, Unit] =
    ZIO.serviceWithZIO[FileManagementService](_.validateFile(file))
    
  def cleanupTempFiles(files: List[File]): ZIO[FileManagementService, Nothing, Unit] =
    ZIO.serviceWithZIO[FileManagementService](_.cleanupTempFiles(files))
    
  def generateProcessedFilename(originalFilename: String): ZIO[FileManagementService, Nothing, String] =
    ZIO.serviceWithZIO[FileManagementService](_.generateProcessedFilename(originalFilename))
}

// ========== Session Management Service ==========

/**
 * Service for managing user sessions and workflow state.
 */
trait SessionManagementService {
  /**
   * Create a new user session.
   */
  def createSession(): UIO[UserSession]
  
  /**
   * Get session by ID.
   */
  def getSession(sessionId: String): IO[DomainError, UserSession]
  
  /**
   * Update session with uploaded document.
   */
  def updateSessionWithDocument(
    sessionId: String, 
    document: PdfDocument
  ): IO[DomainError, UserSession]
  
  /**
   * Update session with watermark configuration.
   */
  def updateSessionWithConfig(
    sessionId: String, 
    config: WatermarkConfig
  ): IO[DomainError, UserSession]
  
  /**
   * Clean up expired sessions.
   */
  def cleanupExpiredSessions(): UIO[Unit]
}

object SessionManagementService {
  def createSession(): ZIO[SessionManagementService, Nothing, UserSession] =
    ZIO.serviceWithZIO[SessionManagementService](_.createSession())
    
  def getSession(sessionId: String): ZIO[SessionManagementService, DomainError, UserSession] =
    ZIO.serviceWithZIO[SessionManagementService](_.getSession(sessionId))
    
  def updateSessionWithDocument(
    sessionId: String, 
    document: PdfDocument
  ): ZIO[SessionManagementService, DomainError, UserSession] =
    ZIO.serviceWithZIO[SessionManagementService](_.updateSessionWithDocument(sessionId, document))
    
  def updateSessionWithConfig(
    sessionId: String, 
    config: WatermarkConfig
  ): ZIO[SessionManagementService, DomainError, UserSession] =
    ZIO.serviceWithZIO[SessionManagementService](_.updateSessionWithConfig(sessionId, config))
    
  def cleanupExpiredSessions(): ZIO[SessionManagementService, Nothing, Unit] =
    ZIO.serviceWithZIO[SessionManagementService](_.cleanupExpiredSessions())
}

// ========== Validation Service ==========

/**
 * Service for validating configurations and inputs.
 */
trait ValidationService {
  /**
   * Validate watermark configuration.
   */
  def validateWatermarkConfig(config: WatermarkConfig): IO[DomainError, Unit]
  
  /**
   * Validate positioning coordinates against page boundaries.
   */
  def validatePosition(
    position: Point, 
    pageDimensions: PageDimensions
  ): IO[DomainError, Unit]
  
  /**
   * Validate file upload parameters.
   */
  def validateUpload(uploadInfo: UploadInfo): IO[DomainError, Unit]
}

object ValidationService {
  def validateWatermarkConfig(config: WatermarkConfig): ZIO[ValidationService, DomainError, Unit] =
    ZIO.serviceWithZIO[ValidationService](_.validateWatermarkConfig(config))
    
  def validatePosition(
    position: Point, 
    pageDimensions: PageDimensions
  ): ZIO[ValidationService, DomainError, Unit] =
    ZIO.serviceWithZIO[ValidationService](_.validatePosition(position, pageDimensions))
    
  def validateUpload(uploadInfo: UploadInfo): ZIO[ValidationService, DomainError, Unit] =
    ZIO.serviceWithZIO[ValidationService](_.validateUpload(uploadInfo))
}