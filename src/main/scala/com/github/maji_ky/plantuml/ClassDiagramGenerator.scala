package com.github.maji_ky.plantuml

import java.io.{File, FilenameFilter}
import java.net.{URL, URLClassLoader}

import scala.reflect.runtime.universe

import collection.JavaConverters._

object ClassDiagramGenerator {
  var counter = 0

  /**
    * Java reflection.
    * https://stackoverflow.com/questions/7884393/can-a-directory-be-added-to-the-class-path-at-runtime
    * https://stackoverflow.com/questions/3628019/how-do-i-call-a-field-accessor-in-scala-using-java-reflection
    * @param dir
    * @throws
    */
  @throws[Exception]
  def addPathToClasspath(dir: File): Unit = {
    val url = dir.toURI.toURL
    val urlClassLoader = this.getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val urlClass = classOf[URLClassLoader]
    val method = urlClass.getDeclaredMethod("addURL", classOf[URL])
    method.setAccessible(true)
    method.invoke(urlClassLoader, Array(url):_*)
  }

  /**
    * Scala reflection.
    * https://docs.scala-lang.org/overviews/reflection/environment-universes-mirrors.html
    * @param dir
    */
  def addPathToClasspath2(dir: File): Unit = {
    val url = dir.toURI.toURL
    val ru = scala.reflect.runtime.universe
    val rm = ru.runtimeMirror(getClass.getClassLoader)
    val instanceMirror = rm.reflect(ClassLoader.getSystemClassLoader.asInstanceOf[URLClassLoader])
//    println(s"[DEBUG] in method addPathToClasspath: instanceMirror=${instanceMirror}") // DEBUG
//    println(s"[DEBUG] in method addPathToClasspath: methodSymbol=${ru.typeOf[URLClassLoader].decl(ru.TermName("addUrl"))}") // DEBUG
    val methodSymbol = ru.typeOf[URLClassLoader].decl(ru.TermName("addUrl")).asMethod
    val methodMirror = instanceMirror.reflectMethod(methodSymbol)
    methodMirror(url)
  }

  def _listClassFiles(dir: File): Seq[File] = {
    val dollarFilter = new FilenameFilter {
      override def accept(f: File, name: String): Boolean = {
        !name.contains("$") && (f.isDirectory || name.endsWith(".class"))
      }
    }
    Option(dir.listFiles(dollarFilter)).map(_.toSeq.flatMap {
      case f if f.isDirectory => _listClassFiles(f)
      case f => Seq(f)
    }).getOrElse(Seq.empty)
  }

  /**
    * Function generating the plantuml string.
    * @param rootDir
    * @param setting
    * @param fanout
    */
  def generate(rootDir: File, setting: GenerateSetting)(fanout: String => Unit) = {
    addPathToClasspath(rootDir)

    val dollarFilter = new FilenameFilter {
      override def accept(f: File, name: String): Boolean = {
        !name.contains("$") && (f.isDirectory || name.endsWith(".class"))
      }
    }

    this.getClass.getClassLoader.asInstanceOf[URLClassLoader]
      .getURLs.map{x => s"[DEBUG][URL] $x"}.foreach(println) // DEBUG

    val pathLength = rootDir.getAbsolutePath.length + 1
    val classLength = ".class".length

    val classUrls = Array[URL](rootDir.toURI.toURL)//_listClassFiles(rootDir).map{ x => x.toURI.toURL}.toArray
    val loader = new URLClassLoader(classUrls, getClass.getClassLoader)
//    val loader = ClassLoader.getSystemClassLoader

    fanout("@startuml\n")
    listFiles(rootDir).map{x => s"[DEBUG] $x"}.foreach(println) // DEBUG
    listFiles(rootDir).map(toFQCN).filterNot(x => setting.ignoreClassNameReg.exists(_.findFirstMatchIn(x).nonEmpty)).foreach { cn =>
      counter += 1
      println()
      println(s"[DEBUG] in method generate: cn=$cn ---($counter)---") // DEBUG
      val clazz = loader.loadClass(cn)

      import scala.reflect.runtime.universe
      val runtimeMirror = universe.runtimeMirror(loader)
      val classSymbol = runtimeMirror.classSymbol(clazz)
      println(s"[DEBUG] in method generate: classSymbol=$classSymbol") // DEBUG
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
//        .filter(_.isPublic)
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
          println(s"[DEBUG] in method generate: x=$x, term=$term") // DEBUG
          val res = declToString(term, x.asMethod)
          println(s"[DEBUG] in method generate: res=$res") // DEBUG
          res
        }
      declarations.map{x => s"[DEBUG][declaration] $x"}.foreach(println) // DEBUG

      fanout(
        s"""class $cn${baseClass.map(x => s" extends $x").mkString} {
            |${declarations.mkString("\n")}
            |}
            |""".stripMargin
      )
    }
    fanout("@enduml")

    implicit class RichList[T](self: List[T]) {
      def mkStringIfNonEmpty(start: String, seq: String, end: String): String =
        if (self.nonEmpty) self.mkString(start, seq, end) else ""
    }

    import scala.reflect.runtime.universe.{Symbol => RefSymbol}
    def paramToString(param: RefSymbol): String =
      (if (param.isImplicit) "implicit " else "") +
        param.name +
        ": " +
        param.typeSignature.typeSymbol.name +
        typeToString(param.typeSignature)

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
