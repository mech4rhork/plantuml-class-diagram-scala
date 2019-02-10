package com.github.maji_ky.plantuml

import scala.util.matching.Regex

case class GenerateSetting(rootPackage: String, ignoreImplicit: Boolean, ignoreClassNameReg: Option[Regex])
