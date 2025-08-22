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

// ========== Preview Service ==========

/**
 * Service for real-time watermark preview functionality.
 */
trait PreviewService {
  /**
   * Generate a real-time preview render of watermarks.
   */
  def generatePreview(
    pageDimensions: PageDimensions,
    config: WatermarkConfig,
    previewConfig: PreviewConfig
  ): IO[DomainError, PreviewRender]
  
  /**
   * Update preview with zoom changes.
   */
  def updateZoom(
    currentRender: PreviewRender,
    newZoomLevel: Double
  ): IO[DomainError, PreviewRender]
  
  /**
   * Update preview with pan offset changes.
   */
  def updatePan(
    currentRender: PreviewRender,
    newPanOffset: Point
  ): IO[DomainError, PreviewRender]
  
  /**
   * Optimize preview performance based on configuration complexity.
   */
  def optimizePreview(
    config: WatermarkConfig,
    previewConfig: PreviewConfig
  ): IO[Nothing, PreviewConfig]
  
  /**
   * Get performance metrics for current preview.
   */
  def getPerformanceMetrics(
    previewRender: PreviewRender
  ): IO[Nothing, PreviewPerformance]
  
  /**
   * Check if preview should be updated based on configuration changes.
   */
  def shouldUpdatePreview(
    trigger: PreviewUpdateTrigger,
    currentConfig: WatermarkConfig
  ): IO[Nothing, Boolean]
}

object PreviewService {
  def generatePreview(
    pageDimensions: PageDimensions,
    config: WatermarkConfig,
    previewConfig: PreviewConfig
  ): ZIO[PreviewService, DomainError, PreviewRender] =
    ZIO.serviceWithZIO[PreviewService](_.generatePreview(pageDimensions, config, previewConfig))
    
  def updateZoom(
    currentRender: PreviewRender,
    newZoomLevel: Double
  ): ZIO[PreviewService, DomainError, PreviewRender] =
    ZIO.serviceWithZIO[PreviewService](_.updateZoom(currentRender, newZoomLevel))
    
  def updatePan(
    currentRender: PreviewRender,
    newPanOffset: Point
  ): ZIO[PreviewService, DomainError, PreviewRender] =
    ZIO.serviceWithZIO[PreviewService](_.updatePan(currentRender, newPanOffset))
    
  def optimizePreview(
    config: WatermarkConfig,
    previewConfig: PreviewConfig
  ): ZIO[PreviewService, Nothing, PreviewConfig] =
    ZIO.serviceWithZIO[PreviewService](_.optimizePreview(config, previewConfig))
    
  def getPerformanceMetrics(
    previewRender: PreviewRender
  ): ZIO[PreviewService, Nothing, PreviewPerformance] =
    ZIO.serviceWithZIO[PreviewService](_.getPerformanceMetrics(previewRender))
    
  def shouldUpdatePreview(
    trigger: PreviewUpdateTrigger,
    currentConfig: WatermarkConfig
  ): ZIO[PreviewService, Nothing, Boolean] =
    ZIO.serviceWithZIO[PreviewService](_.shouldUpdatePreview(trigger, currentConfig))
}

// ========== Enhanced UI Service ==========

/**
 * Service for enhanced user interface functionality.
 */
trait EnhancedUIService {
  /**
   * Get navigation configuration for current workflow state.
   */
  def getNavigationConfig(
    currentStep: NavigationStep,
    watermarkConfig: Option[WatermarkConfig]
  ): IO[Nothing, NavigationConfig]
  
  /**
   * Navigate to next or previous step in workflow.
   */
  def navigateToStep(
    currentStep: NavigationStep,
    targetStep: NavigationStep,
    watermarkConfig: Option[WatermarkConfig]
  ): IO[DomainError, NavigationConfig]
  
  /**
   * Get dynamic form section configurations based on current selections.
   */
  def getFormSectionConfigs(
    watermarkConfig: WatermarkConfig
  ): IO[Nothing, List[FormSectionConfig]]
  
  /**
   * Generate configuration summary for review page.
   */
  def generateConfigurationSummary(
    watermarkConfig: WatermarkConfig,
    pageDimensions: PageDimensions
  ): IO[Nothing, ConfigurationSummary]
  
  /**
   * Get user guidance including tooltips and help messages.
   */
  def getUserGuidance(
    currentStep: NavigationStep,
    formSection: Option[FormSection]
  ): IO[Nothing, UserGuidance]
  
  /**
   * Get available keyboard shortcuts for current context.
   */
  def getKeyboardShortcuts(
    currentStep: NavigationStep
  ): IO[Nothing, List[KeyboardShortcut]]
  
  /**
   * Validate form section dependencies and requirements.
   */
  def validateFormDependencies(
    watermarkConfig: WatermarkConfig,
    sectionConfigs: List[FormSectionConfig]
  ): IO[Nothing, List[String]] // Returns list of validation messages
}

object EnhancedUIService {
  def getNavigationConfig(
    currentStep: NavigationStep,
    watermarkConfig: Option[WatermarkConfig]
  ): ZIO[EnhancedUIService, Nothing, NavigationConfig] =
    ZIO.serviceWithZIO[EnhancedUIService](_.getNavigationConfig(currentStep, watermarkConfig))
    
  def navigateToStep(
    currentStep: NavigationStep,
    targetStep: NavigationStep,
    watermarkConfig: Option[WatermarkConfig]
  ): ZIO[EnhancedUIService, DomainError, NavigationConfig] =
    ZIO.serviceWithZIO[EnhancedUIService](_.navigateToStep(currentStep, targetStep, watermarkConfig))
    
  def getFormSectionConfigs(
    watermarkConfig: WatermarkConfig
  ): ZIO[EnhancedUIService, Nothing, List[FormSectionConfig]] =
    ZIO.serviceWithZIO[EnhancedUIService](_.getFormSectionConfigs(watermarkConfig))
    
  def generateConfigurationSummary(
    watermarkConfig: WatermarkConfig,
    pageDimensions: PageDimensions
  ): ZIO[EnhancedUIService, Nothing, ConfigurationSummary] =
    ZIO.serviceWithZIO[EnhancedUIService](_.generateConfigurationSummary(watermarkConfig, pageDimensions))
    
  def getUserGuidance(
    currentStep: NavigationStep,
    formSection: Option[FormSection]
  ): ZIO[EnhancedUIService, Nothing, UserGuidance] =
    ZIO.serviceWithZIO[EnhancedUIService](_.getUserGuidance(currentStep, formSection))
    
  def getKeyboardShortcuts(
    currentStep: NavigationStep
  ): ZIO[EnhancedUIService, Nothing, List[KeyboardShortcut]] =
    ZIO.serviceWithZIO[EnhancedUIService](_.getKeyboardShortcuts(currentStep))
    
  def validateFormDependencies(
    watermarkConfig: WatermarkConfig,
    sectionConfigs: List[FormSectionConfig]
  ): ZIO[EnhancedUIService, Nothing, List[String]] =
    ZIO.serviceWithZIO[EnhancedUIService](_.validateFormDependencies(watermarkConfig, sectionConfigs))
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
   * Update document status in session.
   */
  def updateDocumentStatus(
    sessionId: String,
    newStatus: DocumentStatus
  ): IO[DomainError, UserSession]
  
  /**
   * Update document with processed file path in session.
   */
  def updateDocumentProcessedFilePath(
    sessionId: String,
    processedFilePath: String
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
    
  def updateDocumentStatus(
    sessionId: String,
    newStatus: DocumentStatus
  ): ZIO[SessionManagementService, DomainError, UserSession] =
    ZIO.serviceWithZIO[SessionManagementService](_.updateDocumentStatus(sessionId, newStatus))
    
  def updateDocumentProcessedFilePath(
    sessionId: String,
    processedFilePath: String
  ): ZIO[SessionManagementService, DomainError, UserSession] =
    ZIO.serviceWithZIO[SessionManagementService](_.updateDocumentProcessedFilePath(sessionId, processedFilePath))
    
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

// ========== Download Tracking Service ==========

/**
 * Service for tracking download progress and managing download sessions (Task 60).
 */
trait DownloadTrackingService {
  /**
   * Create a new download session for tracking progress.
   */
  def createDownloadSession(
    sessionId: String,
    documentId: String,
    filename: String,
    fileSize: Long
  ): UIO[DownloadSession]
  
  /**
   * Get download session by ID.
   */
  def getDownloadSession(sessionId: String): IO[DomainError, DownloadSession]
  
  /**
   * Update download progress with bytes transferred.
   */
  def updateDownloadProgress(
    sessionId: String,
    bytesTransferred: Long
  ): IO[DomainError, DownloadSession]
  
  /**
   * Mark download as completed.
   */
  def completeDownload(sessionId: String): IO[DomainError, DownloadSession]
  
  /**
   * Mark download as failed with error reason.
   */
  def failDownload(sessionId: String, reason: String): IO[DomainError, DownloadSession]
  
  /**
   * Get download progress response for HTTP API.
   */
  def getDownloadProgress(sessionId: String): IO[DomainError, DownloadProgressResponse]
  
  /**
   * Clean up completed or expired download sessions.
   */
  def cleanupDownloadSessions(): UIO[Unit]
}

object DownloadTrackingService {
  def createDownloadSession(
    sessionId: String,
    documentId: String,
    filename: String,
    fileSize: Long
  ): ZIO[DownloadTrackingService, Nothing, DownloadSession] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.createDownloadSession(sessionId, documentId, filename, fileSize))
    
  def getDownloadSession(sessionId: String): ZIO[DownloadTrackingService, DomainError, DownloadSession] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.getDownloadSession(sessionId))
    
  def updateDownloadProgress(
    sessionId: String,
    bytesTransferred: Long
  ): ZIO[DownloadTrackingService, DomainError, DownloadSession] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.updateDownloadProgress(sessionId, bytesTransferred))
    
  def completeDownload(sessionId: String): ZIO[DownloadTrackingService, DomainError, DownloadSession] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.completeDownload(sessionId))
    
  def failDownload(sessionId: String, reason: String): ZIO[DownloadTrackingService, DomainError, DownloadSession] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.failDownload(sessionId, reason))
    
  def getDownloadProgress(sessionId: String): ZIO[DownloadTrackingService, DomainError, DownloadProgressResponse] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.getDownloadProgress(sessionId))
    
  def cleanupDownloadSessions(): ZIO[DownloadTrackingService, Nothing, Unit] =
    ZIO.serviceWithZIO[DownloadTrackingService](_.cleanupDownloadSessions())
}