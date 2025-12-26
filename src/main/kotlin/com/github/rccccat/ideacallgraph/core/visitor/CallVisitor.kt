package com.github.rccccat.ideacallgraph.core.visitor

import com.github.rccccat.ideacallgraph.core.resolver.InterfaceResolver
import com.github.rccccat.ideacallgraph.core.resolver.TypeResolver
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Information about a resolved call target. */
data class CallTargetInfo(
    val target: PsiElement,
    val resolvedImplementations: List<ImplementationInfo>? = null,
)

/** Information about an interface implementation. */
data class ImplementationInfo(
    val implementationMethod: PsiElement,
    val implementingClass: String,
    val isSpringComponent: Boolean,
    val isProjectCode: Boolean,
)

/** Context for visitors, providing access to shared services and resolvers. */
class VisitorContext(
    val project: Project,
    val settings: CallGraphProjectSettings,
    val typeResolver: TypeResolver,
    val interfaceResolver: InterfaceResolver,
    val springAnalyzer: SpringAnalyzer,
)

/**
 * Visitor interface for finding call targets in code elements. Implementations handle different
 * languages (Java, Kotlin).
 */
interface CallVisitor {
  /** Checks if this visitor can handle the given element. */
  fun canVisit(element: PsiElement): Boolean

  /** Finds all call targets within the given element. */
  fun findCallTargets(element: PsiElement, context: VisitorContext): List<CallTargetInfo>
}
