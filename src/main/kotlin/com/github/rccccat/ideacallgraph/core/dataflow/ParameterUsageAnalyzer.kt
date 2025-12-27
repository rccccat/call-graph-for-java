package com.github.rccccat.ideacallgraph.core.dataflow

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import java.util.concurrent.ConcurrentHashMap

/**
 * Analyzes whether method parameters are effectively used within the method body. Uses IntelliJ's
 * Slice Analysis API for data flow tracking with caching for performance.
 */
class ParameterUsageAnalyzer(
    private val project: Project,
) {
  private val sliceAnalyzer = SliceDataFlowAnalyzer(project)
  private val cache = ConcurrentHashMap<String, Boolean>()

  /**
   * Determines if a method call should be included based on parameter usage. Returns true if any
   * passed argument is effectively used in the callee.
   *
   * @param callExpression The method call expression
   * @param calledMethod The method being called
   * @return true if the call is relevant (parameters are used), false otherwise
   */
  fun isCallRelevant(
      @Suppress("UNUSED_PARAMETER") callExpression: PsiMethodCallExpression,
      calledMethod: PsiMethod,
  ): Boolean {
    // Methods with no parameters are always relevant
    if (calledMethod.parameterList.parametersCount == 0) {
      return true
    }

    // Constructors are always relevant
    if (calledMethod.isConstructor) {
      return true
    }

    // Abstract methods have no body - conservatively consider as used
    if (calledMethod.hasModifierProperty(PsiModifier.ABSTRACT) || calledMethod.body == null) {
      return true
    }

    // Check if at least one parameter is effectively used
    return calledMethod.parameterList.parameters.any { param ->
      isParameterEffectivelyUsed(param, calledMethod)
    }
  }

  /**
   * Analyzes a single parameter for effective usage within the method body.
   *
   * @param parameter The parameter to analyze
   * @param method The method containing the parameter
   * @return true if the parameter is effectively used
   */
  private fun isParameterEffectivelyUsed(
      parameter: com.intellij.psi.PsiParameter,
      method: PsiMethod,
  ): Boolean {
    val cacheKey = buildCacheKey(method, parameter)
    return cache.getOrPut(cacheKey) { analyzeParameterUsage(parameter, method) }
  }

  private fun analyzeParameterUsage(
      parameter: com.intellij.psi.PsiParameter,
      method: PsiMethod,
  ): Boolean {
    val body = method.body ?: return true

    // Quick check: if parameter has no references at all, it's definitely unused
    val hasAnyReference =
        ReferencesSearch.search(parameter, LocalSearchScope(body)).findFirst() != null
    if (!hasAnyReference) {
      return false
    }

    // Perform slice analysis to track data flow
    val result = sliceAnalyzer.analyzeParameterUsage(parameter, method)
    return result.isEffectivelyUsed
  }

  private fun buildCacheKey(
      method: PsiMethod,
      parameter: com.intellij.psi.PsiParameter,
  ): String {
    val className = method.containingClass?.qualifiedName ?: "anonymous"
    val methodSignature = buildMethodSignature(method)
    return "$className#$methodSignature#${parameter.name}"
  }

  private fun buildMethodSignature(method: PsiMethod): String {
    val params =
        method.parameterList.parameters.joinToString(",") {
          it.type.canonicalText // Use full canonical text to avoid package collisions
        }
    return "${method.name}($params)"
  }

  /** Clears the analysis cache. Call this when PSI structure changes significantly. */
  fun clearCache() {
    cache.clear()
  }
}
