package com.github.rccccat.ideacallgraph.core.resolver

import com.github.rccccat.ideacallgraph.core.visitor.ImplementationInfo
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.util.SpringAnnotations
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotation
import com.github.rccccat.ideacallgraph.util.isProjectCode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiModificationTracker
import java.util.concurrent.ConcurrentHashMap

/** Resolver for interface implementations with Spring awareness. */
class InterfaceResolver(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
) {
  private sealed interface MethodLookupResult {
    data class Found(val method: PsiMethod) : MethodLookupResult

    object Missing : MethodLookupResult
  }

  private val implementingClassesCache = ConcurrentHashMap<String, List<PsiClass>>()
  private val implementationMethodCache = ConcurrentHashMap<String, MethodLookupResult>()
  private var lastModificationCount: Long = -1

  /**
   * Resolves interface implementations with Spring DI awareness. Filters based
   * on @Primary, @Qualifier, and Spring component status.
   */
  fun resolveInterfaceImplementationsAdvanced(
      interfaceMethod: PsiMethod,
      injectionPoint: PsiElement? = null,
  ): List<ImplementationInfo> {
    ensureCacheFresh()
    val containingClass = interfaceMethod.containingClass ?: return emptyList()
    if (!containingClass.isInterface) return emptyList()

    val implementations = findImplementingClasses(containingClass)
    if (implementations.isEmpty()) return emptyList()

    // If there's an injection point, use Spring's resolution logic
    val filteredImplementations =
        if (injectionPoint != null) {
          val result = springAnalyzer.analyzeInjection(injectionPoint, implementations)
          result.selectedImplementations
        } else {
          implementations
        }

    return filteredImplementations.mapNotNull { implClass ->
      findImplementationMethod(implClass, interfaceMethod)?.let { implMethod ->
        ImplementationInfo(
            implementationMethod = implMethod,
            implementingClass = implClass.qualifiedName ?: implClass.name ?: "",
            isSpringComponent = hasAnyAnnotation(implClass, SpringAnnotations.componentAnnotations),
            isProjectCode = isProjectCode(project, implMethod),
        )
      }
    }
  }

  private fun findImplementingClasses(interfaceClass: PsiClass): List<PsiClass> {
    val key = interfaceClass.qualifiedName ?: return emptyList()

    return implementingClassesCache.getOrPut(key) {
      ClassInheritorsSearch.search(
              interfaceClass,
              GlobalSearchScope.allScope(project),
              true,
          )
          .findAll()
          .filter { !it.isInterface }
          .toList()
    }
  }

  private fun findImplementationMethod(
      implClass: PsiClass,
      interfaceMethod: PsiMethod,
  ): PsiMethod? {
    val interfaceOwner = interfaceMethod.containingClass?.qualifiedName ?: "Unknown"
    val signature = interfaceMethod.getSignature(PsiSubstitutor.EMPTY).toString()
    val key =
        "${implClass.qualifiedName}#$interfaceOwner#$signature"

    val lookupResult =
        implementationMethodCache.computeIfAbsent(key) {
          implClass
              .findMethodsByName(interfaceMethod.name, false)
              .firstOrNull { method -> methodSignaturesMatch(method, interfaceMethod) }
              ?.let { MethodLookupResult.Found(it) } ?: MethodLookupResult.Missing
        }

    return when (lookupResult) {
      is MethodLookupResult.Found -> lookupResult.method
      MethodLookupResult.Missing -> null
    }
  }

  private fun methodSignaturesMatch(
      implMethod: PsiMethod,
      interfaceMethod: PsiMethod,
  ): Boolean {
    if (implMethod.parameterList.parametersCount != interfaceMethod.parameterList.parametersCount) {
      return false
    }
    if (implMethod.isEquivalentTo(interfaceMethod)) return true
    return implMethod.findSuperMethods(true).any { superMethod ->
      superMethod.isEquivalentTo(interfaceMethod)
    }
  }

  private fun ensureCacheFresh() {
    val currentCount = PsiModificationTracker.getInstance(project).modificationCount
    if (currentCount != lastModificationCount) {
      implementingClassesCache.clear()
      implementationMethodCache.clear()
      lastModificationCount = currentCount
    }
  }
}
