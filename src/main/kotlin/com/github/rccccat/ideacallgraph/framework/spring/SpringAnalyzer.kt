package com.github.rccccat.ideacallgraph.framework.spring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Unified Spring analyzer facade - single entry point for Spring analysis. Delegates to Java and
 * Kotlin specific analyzers as needed.
 */
class SpringAnalyzer {
  private val javaAnalyzer = JavaSpringAnalyzer()
  private val kotlinAnalyzer = KotlinSpringAnalyzer()
  private val injectionAnalyzer = SpringInjectionAnalyzer()

  /**
   * Analyzes an element for Spring-specific patterns. Works with both Java methods and Kotlin
   * functions.
   */
  fun analyzeMethod(element: PsiElement): SpringMethodInfo =
      when {
        javaAnalyzer.canAnalyze(element) -> javaAnalyzer.analyze(element as PsiMethod)
        kotlinAnalyzer.canAnalyze(element) -> kotlinAnalyzer.analyze(element as KtNamedFunction)
        else -> SpringMethodInfo.EMPTY
      }

  /** Checks if an element has Spring injection annotations. */
  fun hasInjectionAnnotation(element: PsiModifierListOwner): Boolean =
      injectionAnalyzer.hasInjectionAnnotation(element)

  /** Analyzes Spring injection for the given injection point and implementations. */
  fun analyzeInjection(
      injectionPoint: PsiElement,
      implementations: List<PsiClass>,
  ): SpringInjectionResult = injectionAnalyzer.analyze(injectionPoint, implementations)

}
