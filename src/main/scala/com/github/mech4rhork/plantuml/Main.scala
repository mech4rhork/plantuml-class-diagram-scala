package com.github.mech4rhork.plantuml

import java.io.File
import java.nio.file.{Files, Paths}

/**
  * https://github.com/maji-KY/sbt-plantuml-class-diagram
  */
object Main {

  /**
    * specify root package to find classes
    */
  val genClassDiagramPackage = ""

  /**
    * ignore implicit parameter
    */
  val genClassDiagramIgnoreImplicit = true

  /**
    * specify class name Regex to filter classes
    */
  val genClassDiagramNameFilter = None

  /**
    * generate class diagram
    */
  val genClassDiagram = ""

  val sepChar = File.separatorChar.toString

  // Task.
  def doTask(pClassDir: String, pOutputPath: String): Unit = {

    // More settings.
    val rootPackage = genClassDiagramPackage
    val outputPath = new File(pOutputPath)
    val outputDir = new File(new File(new File(pOutputPath).getAbsolutePath).getParent)
    println(s"[DEBUG] outputDir=$outputDir") // DEBUG
    val packageRootPath = rootPackage.replace('.', File.separatorChar)
    val classDir = new File(pClassDir).getAbsoluteFile
    println(s"[DEBUG] classDir=$classDir") // DEBUG
    val packageRootDir = new File(classDir + sepChar + packageRootPath)

    Files.createDirectories(Paths.get(outputDir.toURI))
    val bw = Files.newBufferedWriter(Paths.get(outputPath.toURI))
    val setting = GenerateSetting(
      rootPackage, genClassDiagramIgnoreImplicit, ignoreClassNameReg = genClassDiagramNameFilter)
    try {
      ClassDiagramGenerator.generate(packageRootDir, setting) { output =>
        bw.append(output)
      }
    } finally {
      bw.close()
    }
  }

  def main(args: Array[String]): Unit = {
    println("[DEBUG]  ---  Begin  ---") // DEBUG

    // Arguments parsing.
    val usage = """
      Usage: JAR <class_dir> <output_path>
    """
    args.map {x => s"[DEBUG][args] $x"}.foreach(println) // DEBUG
    if (args.length != 2) {
      println(usage)
      System.exit(1)
    }
    val classDir = new File(args(0)).toString
    val outputPath = new File(args(1)).toString
    println(s"[DEBUG][args] classDir=$classDir") // DEBUG
    println(s"[DEBUG][args] outputPath=$outputPath") // DEBUG

    // Task.
    doTask(classDir, outputPath)

    println("[DEBUG]  ---   End   ---0149") // DEBUG
  }
}
