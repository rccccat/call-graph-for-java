package com.github.rccccat.ideacallgraph.core.visitor

import com.github.rccccat.ideacallgraph.core.resolver.InterfaceResolver
import com.github.rccccat.ideacallgraph.core.resolver.TypeResolver
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.github.rccccat.ideacallgraph.util.ExcludePatternMatcher
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression

/** Information about a resolved call target. */
data class CallTargetInfo(
    val target: PsiElement,
    val resolvedImplementations: List<ImplementationInfo>? = null,
    /**
     * The call expression that triggered this target resolution. Used for parameter usage analysis.
     */
    val callExpression: PsiExpression? = null,
)

/** Information about an interface implementation. */
data class ImplementationInfo(
    val implementationMethod: PsiElement,
)

/** Context for visitors, providing access to shared services and resolvers. */
class VisitorContext(
    val project: Project,
    val settings: CallGraphProjectSettings,
    val typeResolver: TypeResolver,
    val interfaceResolver: InterfaceResolver,
    val springAnalyzer: SpringAnalyzer,
    val excludePatternMatcher: ExcludePatternMatcher,
)
