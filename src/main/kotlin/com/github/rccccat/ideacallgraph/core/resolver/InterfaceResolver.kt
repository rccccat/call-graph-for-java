package com.github.rccccat.ideacallgraph.core.resolver

import com.github.rccccat.ideacallgraph.core.visitor.ImplementationInfo
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiModificationTracker
import java.util.concurrent.ConcurrentHashMap

/** Resolver for interface implementations and class overrides with Spring awareness. */
class InterfaceResolver(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
) {
  private sealed interface MethodLookupResult {
    data class Found(val method: PsiMethod) : MethodLookupResult

    object Missing : MethodLookupResult
  }

  private val inheritingClassesCache = ConcurrentHashMap<String, List<PsiClass>>()
  private val implementationMethodCache = ConcurrentHashMap<String, MethodLookupResult>()
  private var lastModificationCount: Long = -1

  /**
   * Resolves interface implementations and class overrides with Spring DI awareness. Filters based
   * on @Primary, @Qualifier, and Spring component status.
   */
  fun resolveMethodImplementationsAdvanced(
      baseMethod: PsiMethod,
      injectionPoint: PsiElement? = null,
  ): List<ImplementationInfo> {
    ensureCacheFresh()
    val containingClass = baseMethod.containingClass ?: return emptyList()
    if (!shouldResolveOverrides(baseMethod, containingClass)) return emptyList()

    val implementations = findInheritingClasses(containingClass)
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
      findImplementationMethod(implClass, baseMethod)?.let { implMethod ->
        ImplementationInfo(
            implementationMethod = implMethod,
        )
      }
    }
  }

  private fun shouldResolveOverrides(
      method: PsiMethod,
      containingClass: PsiClass,
  ): Boolean {
    if (method.isConstructor) return false
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) return false
    if (method.hasModifierProperty(PsiModifier.FINAL)) return false
    if (!containingClass.isInterface && containingClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false
    }
    return true
  }

  private fun findInheritingClasses(baseClass: PsiClass): List<PsiClass> {
    val key = baseClass.qualifiedName ?: return emptyList()

    return inheritingClassesCache.getOrPut(key) {
      ClassInheritorsSearch.search(
              baseClass,
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
      baseMethod: PsiMethod,
  ): PsiMethod? {
    val baseOwner = baseMethod.containingClass?.qualifiedName ?: "Unknown"
    val signature = baseMethod.getSignature(PsiSubstitutor.EMPTY).toString()
    val key = "${implClass.qualifiedName}#$baseOwner#$signature"

    val lookupResult =
        implementationMethodCache.computeIfAbsent(key) {
          implClass
              .findMethodsByName(baseMethod.name, false)
              .firstOrNull { method -> methodSignaturesMatch(method, baseMethod) }
              ?.let { MethodLookupResult.Found(it) } ?: MethodLookupResult.Missing
        }

    return when (lookupResult) {
      is MethodLookupResult.Found -> lookupResult.method
      MethodLookupResult.Missing -> null
    }
  }

  private fun methodSignaturesMatch(
      implMethod: PsiMethod,
      baseMethod: PsiMethod,
  ): Boolean {
    if (implMethod.parameterList.parametersCount != baseMethod.parameterList.parametersCount) {
      return false
    }
    if (implMethod.isEquivalentTo(baseMethod)) return true
    return implMethod.findSuperMethods(true).any { superMethod ->
      superMethod.isEquivalentTo(baseMethod)
    }
  }

  private fun ensureCacheFresh() {
    val currentCount = PsiModificationTracker.getInstance(project).modificationCount
    if (currentCount != lastModificationCount) {
      inheritingClassesCache.clear()
      implementationMethodCache.clear()
      lastModificationCount = currentCount
    }
  }
}
