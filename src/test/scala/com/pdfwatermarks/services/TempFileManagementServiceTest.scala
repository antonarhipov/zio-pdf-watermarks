package com.pdfwatermarks.services

import zio.*
import zio.test.*
import zio.test.Assertion.*
import com.pdfwatermarks.config.*
import com.pdfwatermarks.domain.*
import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import scala.util.Random

/**
 * Comprehensive tests for TempFileManagementService (Task 39).
 * 
 * Tests cover:
 * - Temporary file creation with configurable paths
 * - File cleanup based on age and size policies
 * - Directory initialization and management
 * - File organization with prefixes
 * - Configuration integration
 */
object TempFileManagementServiceTest extends ZIOSpecDefault {

  // Test configuration for temporary files
  val testTempConfig = TempFileConfig(
    baseDir = "./test-tmp",
    maxAgeHours = 1,
    maxTotalSizeMb = 10,
    cleanupIntervalMinutes = 5,
    uploadPrefix = "upload-",
    processedPrefix = "processed-"
  )
  
  val testConfigLayer = ZLayer.succeed(testTempConfig)
  val serviceLayer = testConfigLayer >>> TempFileManagementService.layer
  
  // Helper to create test directory
  def setupTestDirectory(): UIO[Path] = {
    ZIO.attempt {
      val testDir = Paths.get("./test-tmp")
      if (Files.exists(testDir)) {
        Files.walk(testDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.deleteIfExists)
      }
      Files.createDirectories(testDir)
      testDir
    }.orDie
  }
  
  // Helper to cleanup test directory
  def cleanupTestDirectory(): UIO[Unit] = {
    ZIO.attempt {
      val testDir = Paths.get("./test-tmp")
      if (Files.exists(testDir)) {
        Files.walk(testDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.deleteIfExists)
      }
    }.orDie.unit
  }

  def spec = suite("TempFileManagementServiceTest")(
    
    suite("Initialization")(
      test("initialize creates temp directory when it doesn't exist") {
        for {
          _ <- cleanupTestDirectory()
          _ <- TempFileManagementService.initialize()
          exists <- ZIO.attempt(Files.exists(Paths.get("./test-tmp")))
        } yield assertTrue(exists)
      }.provide(serviceLayer),
      
      test("initialize succeeds when temp directory already exists") {
        for {
          _ <- setupTestDirectory()
          result <- TempFileManagementService.initialize()
          exists <- ZIO.attempt(Files.exists(Paths.get("./test-tmp")))
        } yield assertTrue(exists) && assertTrue(result == ())
      }.provide(serviceLayer)
    ),
    
    suite("File Creation")(
      test("createTempFile generates unique files with specified prefix and suffix") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          file1 <- TempFileManagementService.createTempFile("test-", ".txt")
          file2 <- TempFileManagementService.createTempFile("test-", ".txt")
        } yield assertTrue(
          file1.exists(),
          file2.exists(),
          file1.getName.startsWith("test-"),
          file1.getName.endsWith(".txt"),
          file2.getName.startsWith("test-"),
          file2.getName.endsWith(".txt"),
          file1.getName != file2.getName // Should be unique
        )
      }.provide(serviceLayer),
      
      test("createUploadTempFile uses upload prefix and preserves extension") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          pdfFile <- TempFileManagementService.createUploadTempFile("document.pdf")
          txtFile <- TempFileManagementService.createUploadTempFile("readme.txt")
          noExtFile <- TempFileManagementService.createUploadTempFile("noextension")
        } yield assertTrue(
          pdfFile.exists(),
          pdfFile.getName.startsWith("upload-"),
          pdfFile.getName.endsWith(".pdf"),
          txtFile.exists(),
          txtFile.getName.startsWith("upload-"),
          txtFile.getName.endsWith(".txt"),
          noExtFile.exists(),
          noExtFile.getName.startsWith("upload-")
        )
      }.provide(serviceLayer),
      
      test("createProcessedTempFile uses processed prefix and preserves extension") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          pdfFile <- TempFileManagementService.createProcessedTempFile("watermarked.pdf")
        } yield assertTrue(
          pdfFile.exists(),
          pdfFile.getName.startsWith("processed-"),
          pdfFile.getName.endsWith(".pdf")
        )
      }.provide(serviceLayer)
    ),
    
    suite("File Storage")(
      test("storeTempFile creates file with content") {
        val testContent = "Hello, World!".getBytes()
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          file <- TempFileManagementService.storeTempFile(testContent, "test.txt")
          content <- ZIO.attempt(Files.readAllBytes(file.toPath))
        } yield assertTrue(
          file.exists(),
          content.toList == testContent.toList
        )
      }.provide(serviceLayer),
      
      test("moveTempFile relocates file within temp directory") {
        val testContent = "Move me!".getBytes()
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          originalFile <- TempFileManagementService.storeTempFile(testContent, "original.txt")
          movedFile <- TempFileManagementService.moveTempFile(originalFile, "moved.txt")
          originalExists <- ZIO.attempt(originalFile.exists())
          movedContent <- ZIO.attempt(Files.readAllBytes(movedFile.toPath))
        } yield assertTrue(
          !originalExists,
          movedFile.exists(),
          movedFile.getName == "moved.txt",
          movedContent.toList == testContent.toList
        )
      }.provide(serviceLayer)
    ),
    
    suite("Directory Management")(
      test("getTempDirectorySize calculates total size correctly") {
        val content1 = "File 1 content".getBytes()
        val content2 = "File 2 with more content".getBytes()
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          file1 <- TempFileManagementService.storeTempFile(content1, "file1.txt")
          file2 <- TempFileManagementService.storeTempFile(content2, "file2.txt")
          totalSize <- TempFileManagementService.getTempDirectorySize()
          expectedSize = content1.length + content2.length
        } yield assertTrue(totalSize == expectedSize.toLong)
      }.provide(serviceLayer),
      
      test("listTempFiles returns correct file information") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          uploadFile <- TempFileManagementService.createUploadTempFile("test.pdf")
          processedFile <- TempFileManagementService.createProcessedTempFile("result.pdf")
          _ <- ZIO.attempt(Files.write(uploadFile.toPath, "upload content".getBytes()))
          _ <- ZIO.attempt(Files.write(processedFile.toPath, "processed content".getBytes()))
          
          files <- TempFileManagementService.listTempFiles()
          uploadInfo = files.find(_.isUploadFile)
          processedInfo = files.find(_.isProcessedFile)
        } yield assertTrue(
          files.length == 2,
          uploadInfo.isDefined,
          processedInfo.isDefined,
          uploadInfo.get.size > 0,
          processedInfo.get.size > 0
        )
      }.provide(serviceLayer)
    ),
    
    suite("Cleanup Policies")(
      test("cleanupOldFiles removes files older than max age") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          
          // Create old file by creating and modifying timestamp
          oldFile <- TempFileManagementService.createTempFile("old-", ".txt")
          _ <- ZIO.attempt {
            val twoHoursAgo = java.lang.System.currentTimeMillis() - (2 * 60 * 60 * 1000) // 2 hours ago
            oldFile.setLastModified(twoHoursAgo)
          }
          
          // Create new file
          newFile <- TempFileManagementService.createTempFile("new-", ".txt")
          
          // Run cleanup
          deletedCount <- TempFileManagementService.cleanupOldFiles()
          
          oldExists <- ZIO.attempt(oldFile.exists())
          newExists <- ZIO.attempt(newFile.exists())
        } yield assertTrue(
          deletedCount == 1,
          !oldExists, // Old file should be deleted
          newExists   // New file should remain
        )
      }.provide(serviceLayer),
      
      test("isCleanupNeeded detects when cleanup is required") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          
          // Initially no cleanup needed
          initialCheck <- TempFileManagementService.isCleanupNeeded()
          
          // Create old file
          oldFile <- TempFileManagementService.createTempFile("old-", ".txt")
          _ <- ZIO.attempt {
            val twoHoursAgo = java.lang.System.currentTimeMillis() - (2 * 60 * 60 * 1000)
            oldFile.setLastModified(twoHoursAgo)
          }
          
          // Now cleanup should be needed
          afterOldFile <- TempFileManagementService.isCleanupNeeded()
        } yield assertTrue(
          !initialCheck,
          afterOldFile
        )
      }.provide(serviceLayer),
      
      test("cleanupFile removes specific file") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          file <- TempFileManagementService.createTempFile("test-", ".txt")
          existsBefore <- ZIO.attempt(file.exists())
          _ <- TempFileManagementService.cleanupFile(file)
          existsAfter <- ZIO.attempt(file.exists())
        } yield assertTrue(
          existsBefore,
          !existsAfter
        )
      }.provide(serviceLayer),
      
      test("cleanupFiles removes multiple files") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          file1 <- TempFileManagementService.createTempFile("test1-", ".txt")
          file2 <- TempFileManagementService.createTempFile("test2-", ".txt")
          file3 <- TempFileManagementService.createTempFile("test3-", ".txt")
          files = List(file1, file2, file3)
          
          existsBefore <- ZIO.foreach(files)(f => ZIO.attempt(f.exists()))
          _ <- TempFileManagementService.cleanupFiles(files)
          existsAfter <- ZIO.foreach(files)(f => ZIO.attempt(f.exists()))
        } yield assertTrue(
          existsBefore.forall(identity),
          !existsAfter.exists(identity)
        )
      }.provide(serviceLayer)
    ),
    
    suite("Configuration Integration")(
      test("service uses configured base directory") {
        for {
          _ <- setupTestDirectory()
          service <- ZIO.service[TempFileManagementService]
          _ <- service.initialize()
          file <- service.createTempFile("config-test-", ".txt")
          path = file.getAbsolutePath
        } yield assertTrue(
          path.contains("test-tmp") // Should use our configured directory
        )
      }.provide(serviceLayer),
      
      test("service uses configured prefixes") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          uploadFile <- TempFileManagementService.createUploadTempFile("test.pdf")
          processedFile <- TempFileManagementService.createProcessedTempFile("result.pdf")
        } yield assertTrue(
          uploadFile.getName.startsWith("upload-"),    // Uses configured upload prefix
          processedFile.getName.startsWith("processed-") // Uses configured processed prefix
        )
      }.provide(serviceLayer)
    ),
    
    suite("Error Handling")(
      test("handles file creation errors gracefully") {
        val invalidConfig = TempFileConfig(
          baseDir = "/invalid/readonly/path",
          maxAgeHours = 1,
          maxTotalSizeMb = 10,
          cleanupIntervalMinutes = 5,
          uploadPrefix = "upload-",
          processedPrefix = "processed-"
        )
        
        for {
          result <- TempFileManagementService.initialize().exit
        } yield assertTrue(result.isFailure)
      }.provide(ZLayer.succeed(invalidConfig) >>> TempFileManagementService.layer),
      
      test("cleanup operations are safe and don't throw") {
        for {
          _ <- setupTestDirectory()
          _ <- TempFileManagementService.initialize()
          nonExistentFile = new File("./test-tmp/nonexistent.txt")
          
          // These should not fail even with non-existent files
          _ <- TempFileManagementService.cleanupFile(nonExistentFile)
          _ <- TempFileManagementService.cleanupFiles(List(nonExistentFile))
          cleanupCount <- TempFileManagementService.cleanupOldFiles()
        } yield assertTrue(cleanupCount >= 0) // Should succeed and return non-negative count
      }.provide(serviceLayer)
    )
  ) @@ TestAspect.after(cleanupTestDirectory()) @@ TestAspect.before(setupTestDirectory())
}