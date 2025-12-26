package com.github.rccccat.ideacallgraph.core.dataflow

import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.slicer.JavaSliceUsage
import com.intellij.slicer.SliceAnalysisParams
import com.intellij.slicer.SliceUsage
import com.intellij.util.Processor

/**
 * Low-level wrapper around IntelliJ's Slice Analysis API. Handles the actual data flow traversal to
 * determine if a parameter is effectively used.
 */
class SliceDataFlowAnalyzer(
    @Suppress("unused") private val project: Project,
) {
  /**
   * Analyzes whether a parameter is effectively used within its method body. Uses forward slice
   * analysis to track where the parameter's data flows.
   *
   * @param parameter The parameter to analyze
   * @param method The method containing the parameter
   * @param maxDepth Maximum depth for slice traversal
   * @return Analysis result indicating if parameter is effectively used
   */
  fun analyzeParameterUsage(
      parameter: PsiParameter,
      method: PsiMethod,
      maxDepth: Int = DEFAULT_SLICE_DEPTH,
  ): ParameterDataFlowResult {
    return ReadAction.compute<ParameterDataFlowResult, Throwable> {
      // Early exit: abstract methods have no body
      if (method.body == null) {
        return@compute ParameterDataFlowResult(parameter, isEffectivelyUsed = true)
      }

      try {
        val hasEffectiveUsage = performSliceAnalysis(parameter, method, maxDepth)
        ParameterDataFlowResult(parameter, hasEffectiveUsage)
      } catch (e: Exception) {
        // On analysis failure, conservatively assume parameter is used
        ParameterDataFlowResult(parameter, isEffectivelyUsed = true)
      }
    }
  }

  private fun performSliceAnalysis(
      parameter: PsiParameter,
      method: PsiMethod,
      maxDepth: Int,
  ): Boolean {
    // Configure slice analysis for forward data flow
    val containingFile = method.containingFile ?: return true
    val params =
        SliceAnalysisParams().apply {
          dataFlowToThis = false // forward: where does data flow TO
          showInstanceDereferences = true
          scope = AnalysisScope(containingFile)
        }

    // Create root usage from parameter
    val rootUsage = JavaSliceUsage.createRootUsage(parameter, params)
    var hasEffectiveUsage = false

    // Traverse data flow graph with depth limit
    traverseSliceUsages(rootUsage, maxDepth, 0) { usage, _ ->
      ProgressManager.checkCanceled()

      val element = usage.element
      if (element != null && isEffectiveConsumption(element)) {
        hasEffectiveUsage = true
        return@traverseSliceUsages false // Stop traversal
      }
      true // Continue traversal
    }

    return hasEffectiveUsage
  }

  private fun traverseSliceUsages(
      usage: SliceUsage,
      maxDepth: Int,
      currentDepth: Int,
      processor: (SliceUsage, Int) -> Boolean,
  ) {
    if (currentDepth >= maxDepth) return
    if (!processor(usage, currentDepth)) return

    usage.processChildren(
        Processor { childUsage ->
          traverseSliceUsages(childUsage, maxDepth, currentDepth + 1, processor)
          true
        },
    )
  }

  /**
   * Determines if an element represents an "effective consumption" of the parameter data. This
   * means the data is actually used in a meaningful way, not just assigned to a variable.
   */
  private fun isEffectiveConsumption(element: PsiElement): Boolean {
    val parent = element.parent ?: return false

    return when {
      // Return statement: data flows out of method
      parent is PsiReturnStatement -> true

      // Method call argument: data passed to another method
      parent is PsiExpressionList && parent.parent is PsiMethodCallExpression -> true

      // Method call qualifier: param.someMethod()
      parent is PsiReferenceExpression && parent.parent is PsiMethodCallExpression -> {
        val call = parent.parent as PsiMethodCallExpression
        call.methodExpression.qualifierExpression == parent
      }

      // Field assignment: data stored in field
      parent is PsiField -> true

      // Lambda capture: data captured by closure
      isLambdaCapture(element) -> true

      else -> false
    }
  }

  private fun isLambdaCapture(element: PsiElement): Boolean {
    var current = element.parent
    while (current != null) {
      if (current is PsiLambdaExpression) return true
      if (current is PsiMethod) break
      current = current.parent
    }
    return false
  }

  companion object {
    const val DEFAULT_SLICE_DEPTH = 5
  }
}
