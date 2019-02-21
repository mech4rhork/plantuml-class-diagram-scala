package com.github.mech4rhork.plantuml

object ClassSymbolLabel extends Enumeration {
  type ClassSymbolLabel = Value
  val ABSTRACT_CLASS = Value("<<(C,#8cd3ec) abstract>>")
  val CASE_CLASS = Value("<<(C,#a1c8ed)>>")
  val DEFAULT = Value("<<(C,#8cd3ec)>>")
  val OBJECT = Value("<<(O,#f8cf8b) singleton>>")
  val TRAIT = Value("<<(T,#a1d38e) interface>>")
}
