package com.pdfwatermarks.services

import com.pdfwatermarks.domain.*
import com.pdfwatermarks.config.*
import zio.*
import zio.stream.*
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

/**
 * Temporary File Management Service for the PDF Watermarking Application.
 * 
 * This service provides:
 * - Configurable temporary file directory management
 * - Automatic cleanup of old temporary files
 * - Organized file storage with prefixes
 * - Size-based cleanup policies
 * - Thread-safe operations
 */
trait TempFileManagementService {
  
  /**
   * Initialize the temporary file directory structure.
   */
  def initialize(): IO[DomainError, Unit]
  
  /**
   * Create a new temporary file with a unique name.
   */
  def createTempFile(prefix: String, suffix: String): IO[DomainError, File]
  
  /**
   * Create a temporary file for uploaded content.
   */
  def createUploadTempFile(originalFilename: String): IO[DomainError, File]
  
  /**
   * Create a temporary file for processed content.
   */
  def createProcessedTempFile(originalFilename: String): IO[DomainError, File]
  
  /**
   * Store content in a temporary file.
   */
  def storeTempFile(content: Array[Byte], filename: String): IO[DomainError, File]
  
  /**
   * Move a temporary file to another location within temp directory.
   */
  def moveTempFile(source: File, newFilename: String): IO[DomainError, File]
  
  /**
   * Clean up old temporary files based on age and size policies.
   */
  def cleanupOldFiles(): UIO[Int]
  
  /**
   * Clean up specific temporary file.
   */
  def cleanupFile(file: File): UIO[Unit]
  
  /**
   * Clean up multiple temporary files.
   */
  def cleanupFiles(files: List[File]): UIO[Unit]
  
  /**
   * Get current temporary directory size in bytes.
   */
  def getTempDirectorySize(): UIO[Long]
  
  /**
   * Get list of all temporary files with metadata.
   */
  def listTempFiles(): UIO[List[TempFileInfo]]
  
  /**
   * Check if cleanup is needed based on size or age policies.
   */
  def isCleanupNeeded(): UIO[Boolean]
}

/**
 * Information about a temporary file.
 */
case class TempFileInfo(
  file: File,
  size: Long,
  createdAt: Instant,
  lastModified: Instant,
  isUploadFile: Boolean,
  isProcessedFile: Boolean
)

/**
 * Live implementation of TempFileManagementService.
 */
case class TempFileManagementServiceLive(config: TempFileConfig) extends TempFileManagementService {
  
  private val tempDirPath = config.baseDirPath
  
  def initialize(): IO[DomainError, Unit] = {
    for {
      _ <- ZIO.logInfo(s"Initializing temporary file directory: ${tempDirPath}")
      _ <- ZIO.attempt {
        Files.createDirectories(tempDirPath)
      }.mapError(err => DomainError.InternalError(s"Failed to create temp directory: ${err.getMessage}"))
      _ <- ZIO.logInfo(s"Temporary file directory initialized successfully")
    } yield ()
  }
  
  def createTempFile(prefix: String, suffix: String): IO[DomainError, File] = {
    for {
      uniqueId <- ZIO.succeed(UUID.randomUUID().toString.take(8))
      timestamp <- ZIO.succeed(java.lang.System.currentTimeMillis())
      filename = s"${prefix}${timestamp}-${uniqueId}${suffix}"
      filePath = tempDirPath.resolve(filename)
      file <- ZIO.attempt {
        Files.createFile(filePath)
        filePath.toFile
      }.mapError(err => DomainError.InternalError(s"Failed to create temp file: ${err.getMessage}"))
      _ <- ZIO.logDebug(s"Created temporary file: ${file.getAbsolutePath}")
    } yield file
  }
  
  def createUploadTempFile(originalFilename: String): IO[DomainError, File] = {
    val extension = originalFilename.lastIndexOf('.') match {
      case -1 => ""
      case idx => originalFilename.substring(idx)
    }
    createTempFile(config.uploadPrefix, extension)
  }
  
  def createProcessedTempFile(originalFilename: String): IO[DomainError, File] = {
    val extension = originalFilename.lastIndexOf('.') match {
      case -1 => ""
      case idx => originalFilename.substring(idx)
    }
    createTempFile(config.processedPrefix, extension)
  }
  
  def storeTempFile(content: Array[Byte], filename: String): IO[DomainError, File] = {
    for {
      tempFile <- createTempFile("temp-", ".tmp")
      _ <- ZIO.attempt {
        Files.write(tempFile.toPath, content)
      }.mapError(err => DomainError.InternalError(s"Failed to write temp file: ${err.getMessage}"))
      _ <- ZIO.logDebug(s"Stored ${content.length} bytes in temporary file: ${tempFile.getAbsolutePath}")
    } yield tempFile
  }
  
  def moveTempFile(source: File, newFilename: String): IO[DomainError, File] = {
    for {
      targetPath <- ZIO.succeed(tempDirPath.resolve(newFilename))
      targetFile <- ZIO.attempt {
        Files.move(source.toPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        targetPath.toFile
      }.mapError(err => DomainError.InternalError(s"Failed to move temp file: ${err.getMessage}"))
      _ <- ZIO.logDebug(s"Moved temporary file from ${source.getAbsolutePath} to ${targetFile.getAbsolutePath}")
    } yield targetFile
  }
  
  def cleanupOldFiles(): UIO[Int] = {
    val cleanupLogic: ZIO[Any, Throwable, Int] = for {
      _ <- ZIO.logInfo("Starting temporary file cleanup")
      currentTime <- ZIO.succeed(Instant.now())
      maxAge = config.maxAgeDuration
      maxSize = config.maxTotalSizeBytes
      
      // Get all files in temp directory
      files <- ZIO.attempt {
        if (Files.exists(tempDirPath)) {
          Files.list(tempDirPath).iterator().asScala.toList
            .map(_.toFile)
            .filter(_.isFile)
        } else {
          List.empty[File]
        }
      }
      
      // Separate files by age
      (oldFiles, recentFiles) = files.partition { file =>
        val fileAge = java.time.Duration.between(
          Instant.ofEpochMilli(file.lastModified()),
          currentTime
        )
        fileAge.compareTo(maxAge) > 0
      }
      
      // Calculate current size
      currentSize <- ZIO.succeed(files.map(_.length()).sum)
      
      // Determine files to delete
      filesToDelete = if (currentSize > maxSize) {
        // If over size limit, delete oldest files first
        val sortedByAge = recentFiles.sortBy(_.lastModified())
        val sizeToReduce = currentSize - maxSize
        var accumulatedSize = 0L
        val additionalDeletes = sortedByAge.takeWhile { file =>
          if (accumulatedSize < sizeToReduce) {
            accumulatedSize += file.length()
            true
          } else false
        }
        oldFiles ++ additionalDeletes
      } else {
        oldFiles
      }
      
      // Delete files
      deleteCount <- ZIO.foreach(filesToDelete) { file =>
        ZIO.attempt {
          Files.deleteIfExists(file.toPath)
          1
        }.orElse(ZIO.succeed(0))
      }.map(_.sum)
      
      _ <- ZIO.logInfo(s"Cleanup completed: deleted $deleteCount temporary files")
    } yield deleteCount
    
    cleanupLogic.catchAll { error =>
      ZIO.logWarning(s"Temporary file cleanup failed: $error") *> ZIO.succeed(0)
    }
  }
  
  def cleanupFile(file: File): UIO[Unit] = {
    ZIO.attempt {
      Files.deleteIfExists(file.toPath)
      ()
    }.orElse(ZIO.unit)
      .tap(_ => ZIO.logDebug(s"Cleaned up temporary file: ${file.getAbsolutePath}"))
  }
  
  def cleanupFiles(files: List[File]): UIO[Unit] = {
    ZIO.foreach(files)(cleanupFile).unit
  }
  
  def getTempDirectorySize(): UIO[Long] = {
    ZIO.attempt {
      if (Files.exists(tempDirPath)) {
        Files.list(tempDirPath).iterator().asScala
          .filter(Files.isRegularFile(_))
          .map(Files.size(_))
          .sum
      } else {
        0L
      }
    }.orElse(ZIO.succeed(0L))
  }
  
  def listTempFiles(): UIO[List[TempFileInfo]] = {
    ZIO.attempt {
      if (Files.exists(tempDirPath)) {
        Files.list(tempDirPath).iterator().asScala.toList
          .filter(Files.isRegularFile(_))
          .map { path =>
            val file = path.toFile
            val size = Files.size(path)
            val createdAt = Files.readAttributes(path, classOf[BasicFileAttributes]).creationTime().toInstant()
            val lastModified = Instant.ofEpochMilli(file.lastModified())
            val filename = file.getName
            
            TempFileInfo(
              file = file,
              size = size,
              createdAt = createdAt,
              lastModified = lastModified,
              isUploadFile = filename.startsWith(config.uploadPrefix),
              isProcessedFile = filename.startsWith(config.processedPrefix)
            )
          }
      } else {
        List.empty[TempFileInfo]
      }
    }.orElse(ZIO.succeed(List.empty[TempFileInfo]))
  }
  
  def isCleanupNeeded(): UIO[Boolean] = {
    for {
      currentSize <- getTempDirectorySize()
      files <- listTempFiles()
      currentTime <- ZIO.succeed(Instant.now())
      maxAge = config.maxAgeDuration
      
      hasOldFiles = files.exists { info =>
        java.time.Duration.between(info.lastModified, currentTime).compareTo(maxAge) > 0
      }
      isOverSizeLimit = currentSize > config.maxTotalSizeBytes
    } yield hasOldFiles || isOverSizeLimit
  }
}

object TempFileManagementService {
  
  /**
   * Create a ZLayer for TempFileManagementService.
   */
  val layer: ZLayer[TempFileConfig, Nothing, TempFileManagementService] =
    ZLayer.fromFunction(TempFileManagementServiceLive.apply)
  
  /**
   * Initialize temporary file system.
   */
  def initialize(): ZIO[TempFileManagementService, DomainError, Unit] =
    ZIO.serviceWithZIO[TempFileManagementService](_.initialize())
    
  def createTempFile(prefix: String, suffix: String): ZIO[TempFileManagementService, DomainError, File] =
    ZIO.serviceWithZIO[TempFileManagementService](_.createTempFile(prefix, suffix))
    
  def createUploadTempFile(originalFilename: String): ZIO[TempFileManagementService, DomainError, File] =
    ZIO.serviceWithZIO[TempFileManagementService](_.createUploadTempFile(originalFilename))
    
  def createProcessedTempFile(originalFilename: String): ZIO[TempFileManagementService, DomainError, File] =
    ZIO.serviceWithZIO[TempFileManagementService](_.createProcessedTempFile(originalFilename))
    
  def storeTempFile(content: Array[Byte], filename: String): ZIO[TempFileManagementService, DomainError, File] =
    ZIO.serviceWithZIO[TempFileManagementService](_.storeTempFile(content, filename))
    
  def moveTempFile(source: File, newFilename: String): ZIO[TempFileManagementService, DomainError, File] =
    ZIO.serviceWithZIO[TempFileManagementService](_.moveTempFile(source, newFilename))
    
  def cleanupOldFiles(): ZIO[TempFileManagementService, Nothing, Int] =
    ZIO.serviceWithZIO[TempFileManagementService](_.cleanupOldFiles())
    
  def cleanupFile(file: File): ZIO[TempFileManagementService, Nothing, Unit] =
    ZIO.serviceWithZIO[TempFileManagementService](_.cleanupFile(file))
    
  def cleanupFiles(files: List[File]): ZIO[TempFileManagementService, Nothing, Unit] =
    ZIO.serviceWithZIO[TempFileManagementService](_.cleanupFiles(files))
    
  def getTempDirectorySize(): ZIO[TempFileManagementService, Nothing, Long] =
    ZIO.serviceWithZIO[TempFileManagementService](_.getTempDirectorySize())
    
  def listTempFiles(): ZIO[TempFileManagementService, Nothing, List[TempFileInfo]] =
    ZIO.serviceWithZIO[TempFileManagementService](_.listTempFiles())
    
  def isCleanupNeeded(): ZIO[TempFileManagementService, Nothing, Boolean] =
    ZIO.serviceWithZIO[TempFileManagementService](_.isCleanupNeeded())
}