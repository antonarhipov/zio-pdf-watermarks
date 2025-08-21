ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "com.pdfwatermarks"

lazy val root = (project in file("."))
  .settings(
    name := "pdf-watermarks",
    description := "PDF Watermarking Application with ZIO",
    
    // Scala compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:higherKinds",
      "-language:implicitConversions"
    ),
    
    // JVM options
    javaOptions ++= Seq(
      "-Xmx2G",
      "-XX:+UseG1GC"
    ),
    
    // Test configuration
    Test / fork := true,
    Test / javaOptions += "-Xmx1G",
    
    // Dependencies
    libraryDependencies ++= Seq(
      // ZIO Core
      "dev.zio" %% "zio" % "2.1.9",
      
      // ZIO HTTP for web server
      "dev.zio" %% "zio-http" % "3.0.0",
      
      // Apache PDFBox for PDF processing
      "org.apache.pdfbox" % "pdfbox" % "3.0.3",
      
      // Testing dependencies
      "dev.zio" %% "zio-test" % "2.1.9" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.9" % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      
      // Logging
      "dev.zio" %% "zio-logging" % "2.3.1",
      "dev.zio" %% "zio-logging-slf4j2" % "2.3.1",
      "ch.qos.logback" % "logback-classic" % "1.4.14"
    )
  )
