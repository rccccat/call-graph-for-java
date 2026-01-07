package com.github.rccccat.callgraphjava.framework.spring

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner

/** specific analyzer. */
class SpringAnalyzer {
  private val javaAnalyzer = JavaSpringAnalyzer()
  private val injectionAnalyzer = SpringInjectionAnalyzer()

  /** Analyzes an element for Spring-specific patterns. Works with Java methods. */
  fun analyzeMethod(element: PsiElement): SpringMethodInfo =
      when {
        javaAnalyzer.canAnalyze(element) -> javaAnalyzer.analyze(element as PsiMethod)
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
