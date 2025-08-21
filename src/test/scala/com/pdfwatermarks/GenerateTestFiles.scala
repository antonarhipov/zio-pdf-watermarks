package com.pdfwatermarks

/**
 * Utility to generate test PDF files.
 * Run this object to create sample PDF files for testing.
 */
object GenerateTestFiles extends App {
  
  val testPdfDir = "src/test/resources/pdfs"
  
  println("Generating test PDF files...")
  TestPdfGenerator.generateAllTestFiles(testPdfDir)
  println(s"Test PDF files generated in: $testPdfDir")
  
  // List generated files
  val dir = new java.io.File(testPdfDir)
  if (dir.exists()) {
    val files = dir.listFiles().filter(_.getName.endsWith(".pdf"))
    println(s"Generated ${files.length} test PDF files:")
    files.foreach(file => println(s"  - ${file.getName} (${file.length()} bytes)"))
  }
}