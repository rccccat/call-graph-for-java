package com.github.rccccat.callgraphjava.framework.spring

import com.intellij.psi.PsiClass

/** Unified Spring method analysis result - used by both Java and Kotlin analyzers. */
data class SpringMethodInfo(
    val isController: Boolean = false,
    val isService: Boolean = false,
    val isEndpoint: Boolean = false,
) {
  companion object {
    val EMPTY = SpringMethodInfo()
  }
}

/** Result of Spring injection analysis. */
data class SpringInjectionResult(
    val selectedImplementations: List<PsiClass>,
    val injectionType: InjectionType,
    val reason: String,
)

/** Type of Spring dependency injection. */
enum class InjectionType(val isCollection: Boolean) {
  SINGLE(false),
  LIST(true),
  SET(true),
  MAP(true),
}
