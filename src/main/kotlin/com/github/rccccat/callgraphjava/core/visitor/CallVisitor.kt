package com.github.rccccat.callgraphjava.core.visitor

import com.github.rccccat.callgraphjava.core.resolver.InterfaceResolver
import com.github.rccccat.callgraphjava.core.resolver.TypeResolver
import com.github.rccccat.callgraphjava.framework.spring.SpringAnalyzer
import com.github.rccccat.callgraphjava.settings.CallGraphProjectSettings
import com.github.rccccat.callgraphjava.util.ExcludePatternMatcher
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression

/** Information about a resolved call target. */
data class CallTargetInfo(
    val target: PsiElement,
    val resolvedImplementations: List<ImplementationInfo>? = null,
    /**  */
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
