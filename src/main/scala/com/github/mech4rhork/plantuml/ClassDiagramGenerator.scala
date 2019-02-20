package com.github.mech4rhork.plantuml

import java.io.{File, FilenameFilter}
import java.net.{URL, URLClassLoader}

import com.github.mech4rhork.plantuml.ClassSymbolLabel.ClassSymbolLabel

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.ClassSymbol

object ClassDiagramGenerator {

  var counter = 0 // DEBUG
  val addedClasses: ListBuffer[String] = ListBuffer[String]()

  /**
    * Java reflection.
    * https://stackoverflow.com/questions/7884393/can-a-directory-be-added-to-the-class-path-at-runtime
    * https://stackoverflow.com/questions/3628019/how-do-i-call-a-field-accessor-in-scala-using-java-reflection
    */
  def addPathToClasspath(dir: File): Unit = {
    val url = dir.toURI.toURL
    val urlClassLoader = this.getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val urlClass = classOf[URLClassLoader]
    val method = urlClass.getDeclaredMethod("addURL", classOf[URL])
    method.setAccessible(true)
    method.invoke(urlClassLoader, Array(url):_*)
  }

  def getClassSymbolLabel(classSymbol: ClassSymbol): ClassSymbolLabel = {
    val res = classSymbol match {
      case s if s.isCaseClass => ClassSymbolLabel.CASE_CLASS
      case s if s.isTrait => ClassSymbolLabel.TRAIT
      case s if s.isModule => ClassSymbolLabel.OBJECT
      case s if s.isModuleClass => ClassSymbolLabel.OBJECT
      case s if s.isAbstract => ClassSymbolLabel.ABSTRACT_CLASS
      case _ => ClassSymbolLabel.DEFAULT
    }
    res
  }

  def debugClassSymbol(classSymbol: ClassSymbol): Unit = {
    println(s" > classSymbol.isAbstract=${classSymbol.isAbstract}") // DEBUG
    println(s" > classSymbol.isAbstractOverride=${classSymbol.isAbstractOverride}") // DEBUG
    println(s" > classSymbol.isAliasType=${classSymbol.isAliasType}") // DEBUG
    println(s" > classSymbol.isCaseClass=${classSymbol.isCaseClass}") // DEBUG
    println(s" > classSymbol.isClass=${classSymbol.isClass}") // DEBUG
    println(s" > classSymbol.isConstructor=${classSymbol.isConstructor}") // DEBUG
    println(s" > classSymbol.isContravariant=${classSymbol.isContravariant}") // DEBUG
    println(s" > classSymbol.isCovariant=${classSymbol.isCovariant}") // DEBUG
    println(s" > classSymbol.isDerivedValueClass=${classSymbol.isDerivedValueClass}") // DEBUG
    println(s" > classSymbol.isModule=${classSymbol.isModule}") // DEBUG
    println(s" > classSymbol.isModuleClass=${classSymbol.isModuleClass}") // DEBUG
    println(s" > classSymbol.isPackage=${classSymbol.isPackage}") // DEBUG
    println(s" > classSymbol.isPackageClass=${classSymbol.isPackageClass}") // DEBUG
    println(s" > classSymbol.isSynthetic=${classSymbol.isSynthetic}") // DEBUG
    println(s" > classSymbol.isTerm=${classSymbol.isTerm}") // DEBUG
    println(s" > classSymbol.isTrait=${classSymbol.isTrait}") // DEBUG
    println(s" > classSymbol.isType=${classSymbol.isType}") // DEBUG
  }

  /**
    * class <- 0 $
    * object <- 1 $ at the end
    * anon/gen <- _
    */
  def isNotGenerated(inputString: String): Boolean = {
    val s = inputString.replace(".class", "")
    println(s">>> isNotGenerated( $inputString )") // DEBUG
    val dollarCount = s.count(_ == '$')
    val endsWithDollar = s.endsWith("$")

    val res =
      s match {
        case _ if dollarCount == 0 => true
        case _ if dollarCount == 1 && endsWithDollar => true
        case _ => false
      }

    res
  }

  /**
    * Function generating the plantuml string.
    */
  def generate(rootDir: File, dependencyClasspath: File, setting: GenerateSetting)(fanout: String => Unit) = {

    //
    val dependencyURLs = Source.fromFile(dependencyClasspath).getLines.toList.sorted.map(x => new File(x))
    dependencyURLs.map{x => s"[DEBUG][dependency URL] $x"}.foreach(println) // DEBUG
    dependencyURLs.foreach(addPathToClasspath)

    addPathToClasspath(rootDir) // Add URL to class path.

    val dollarFilter = new FilenameFilter {
      override def accept(f: File, name: String): Boolean = f.isDirectory || name.endsWith(".class")
    }

    this.getClass.getClassLoader.asInstanceOf[URLClassLoader].getURLs.map{x => s"[DEBUG][URL] $x"}.foreach(println) // DEBUG

    val pathLength = rootDir.getAbsolutePath.length + 1
    val classLength = ".class".length

    val classUrls = Array[URL](rootDir.toURI.toURL)
    val loader = new URLClassLoader(classUrls, this.getClass.getClassLoader)

    fanout("@startuml\n")
    fanout("skinparam stereotypeCBackgroundColor #8cd3ec\n") // Default background color.

    listFiles(rootDir).filter(x => isNotGenerated(x.toString)).map{x => s"[DEBUG] $x"}.foreach(println) // DEBUG
    listFiles(rootDir)
      .filter(x => isNotGenerated(x.toString))
      .map(toFQCN).filterNot(x => setting.ignoreClassNameReg.exists(_.findFirstMatchIn(x).nonEmpty)).foreach { cn =>

      try {

        counter += 1
        println()
        println(s"[DEBUG] in method generate: cn=$cn ${"-"*10}($counter)${"-"*60}") // DEBUG
        val clazz = loader.loadClass(cn)
        println(s"[DEBUG] in method generate: clazz=$clazz") // DEBUG

        val runtimeMirror = universe.runtimeMirror(loader)
        val classSymbol = runtimeMirror.classSymbol(clazz)
        debugClassSymbol(classSymbol) // DEBUG
        println(s"[DEBUG] in method generate: classSymbol=${classSymbol.toString}") // DEBUG
        val baseClass = classSymbol.baseClasses.drop(1)
          .takeWhile(x => x.name.toString != "Object" && x.name.toString != "Serializable").take(1)
          .map(x => x.fullName).headOption
        println(s"[DEBUG] in method generate: baseClass=$baseClass") // DEBUG

        val typ = classSymbol.asType.toType
        println(s"[DEBUG] in method generate: typ=$typ") // DEBUG
        val valList = typ.decls.filter(_.isTerm).filter(_.asTerm.isVal).map(_.name.toString.trim).toList
        println(s"[DEBUG] in method generate: valList=$valList") // DEBUG
        val varList = typ.decls.filter(_.isTerm).filter(_.asTerm.isVar).map(_.name.toString.trim).toList
        println(s"[DEBUG] in method generate: varList=$varList") // DEBUG
        val declarations = typ.decls
          .filter(_.isMethod)
          .filter(_.isPublic)
          .filterNot(_.isSynthetic)
          .filterNot(_.name.toString == "<init>")
          .filterNot(_.name.toString == "$init$")
          .filterNot(_.name.toString.endsWith("_$eq"))
          .filterNot(_.isJava)
          .map { x =>
            val term = x.name.toString match {
              case a if valList.contains(a) => "val "
              case a if varList.contains(a) => "var "
              case _ => "def "
            }
            //          println(s"[DEBUG] in method generate: x=$x, term=$term") // DEBUG
            val res = declToString(term, x.asMethod)
            //          println(s"[DEBUG] in method generate: res=$res") // DEBUG
            res
          }
        declarations.map { x => s"[DEBUG][declaration] $x" }.foreach(println) // DEBUG

        val label = getClassSymbolLabel(classSymbol)
        println(s"[DEBUG] in method generate: getClassSymbolLabel(classSymbol)=$label") // DEBUG

        // Skipping case class object occurrence "CaseClass$".
        val isSyntheticCaseClass = classSymbol.isSynthetic && classSymbol.isModuleClass
        println(s"[DEBUG] >> isSyntheticCaseClass=$isSyntheticCaseClass") // DEBUG

        val objectAlreadyAdded = addedClasses.contains(cn.replace("$", if (classSymbol.isCaseClass) "$" else ""))
        println(s"[DEBUG] >> objectAlreadyAdded=$objectAlreadyAdded") // DEBUG

        if (!isSyntheticCaseClass && !objectAlreadyAdded) {
          fanout(
            s"""class $cn $label ${baseClass.map(x => s"extends $x").mkString} {
               |${declarations.mkString("\n")}
               |}
               |""".stripMargin
          )
        }

        if (!isSyntheticCaseClass)
          addedClasses.append(cn.replace("$", ""))

      } catch {
        case e: Throwable =>
          println(s"An exception was thrown (class: $cn).")
          e.printStackTrace()
      }
    }

    println(s"[DEBUG] in method generate: addedClasses=${addedClasses.mkString("\n")}") // DEBUG

    fanout("@enduml")

    implicit class RichList[T](self: List[T]) {
      def mkStringIfNonEmpty(start: String, seq: String, end: String): String =
        if (self.nonEmpty) self.mkString(start, seq, end) else ""
    }

    import scala.reflect.runtime.universe.{Symbol => RefSymbol}
    def paramToString(param: RefSymbol): String = {
      var res = (if (param.isImplicit) "implicit " else "") + param.name + ": " + param.typeSignature.typeSymbol.name
      try {
        res += typeToString(param.typeSignature)
      } catch {
        case e: Throwable =>
          println(s"An exception was thrown (res = res.concat(typeToString(param.typeSignature))).")
          e.printStackTrace()
          res += "[?]"
      }
      res
    }

    import scala.reflect.runtime.universe.MethodSymbol
    def declToString(term: String, method: MethodSymbol): String =
      term +
        method.name.decodedName +
        method.typeParams.map(_.name).mkStringIfNonEmpty("[", ",", "]") +
        method.paramLists.filterNot(setting.ignoreImplicit && _.headOption.exists(_.isImplicit)).map(_.map(paramToString).mkString(", ")).mkStringIfNonEmpty("(", ")(", ")") +
        ": " +
        method.returnType.typeSymbol.name +
        typeToString(method.returnType)

    def typeToString(typ: universe.Type, from: Option[universe.Type] = None): String = {
      val typeSymbol = typ.typeSymbol
      val seenFromType = from.getOrElse(typ)
      if (typeSymbol.isClass) typeSymbol.asClass.typeParams.map { x =>
        val paramType = x.asType.toType.asSeenFrom(seenFromType, typeSymbol.asClass)
        paramType.typeSymbol.name + typeToString(paramType)
      }.mkStringIfNonEmpty("[", ",", "]")
      else
        ""
    }

    def listFiles(dir: File): Seq[File] = {
      Option(dir.listFiles(dollarFilter)).map(_.toSeq.flatMap {
        case f if f.isDirectory => listFiles(f)
        case f => Seq(f)
      }).getOrElse(Seq.empty)
    }

    def toFQCN(f: File) = {
      val fragment = f.getPath.drop(pathLength).dropRight(classLength).replace(File.separatorChar, '.')
      println(s"[DEBUG] in method toFQCN: fragement=$fragment") // DEBUG
      s"${setting.rootPackage}.$fragment".replaceAll("^\\.*", "")
    }
  }
}
