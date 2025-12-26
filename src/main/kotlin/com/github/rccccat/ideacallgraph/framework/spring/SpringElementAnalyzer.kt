package com.github.rccccat.ideacallgraph.framework.spring

import com.intellij.psi.PsiElement

/**
 * Strategy interface for Spring element analysis. Implementations handle different PSI element
 * types (Java methods, Kotlin functions).
 */
interface SpringElementAnalyzer<T : PsiElement> {
  /** Checks if this analyzer can handle the given element. */
  fun canAnalyze(element: PsiElement): Boolean

  /** Analyzes the element for Spring annotations and returns method info. */
  fun analyze(element: T): SpringMethodInfo

  /** Checks if the element belongs to a Spring component class. */
  fun isSpringComponent(element: T): Boolean
}
