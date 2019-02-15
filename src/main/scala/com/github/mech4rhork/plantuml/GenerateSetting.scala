package com.github.mech4rhork.plantuml

import scala.util.matching.Regex

case class GenerateSetting(rootPackage: String, ignoreImplicit: Boolean, ignoreClassNameReg: Option[Regex])
