package com.github.rccccat.callgraphjava.framework.spring

import com.github.rccccat.callgraphjava.util.SpringAnnotations
import com.github.rccccat.callgraphjava.util.extractFirstStringValue
import com.github.rccccat.callgraphjava.util.hasAnyAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter

/** Analyzer for Spring dependency injection patterns. */
class SpringInjectionAnalyzer {
  /** Checks if an element has Spring injection annotations. */
  fun hasInjectionAnnotation(element: PsiModifierListOwner): Boolean =
      hasAnyAnnotation(element, SpringAnnotations.injectionAnnotations)

  /** Analyzes Spring injection for the given injection point and implementations. */
  fun analyze(
      injectionPoint: PsiElement,
      implementations: List<PsiClass>,
  ): SpringInjectionResult {
    val qualifierValue = extractQualifier(injectionPoint)

    val injectionType = determineInjectionType(injectionPoint)
    val filteredImplementations =
        filterImplementationsByPriority(implementations, qualifierValue, injectionType)

    return SpringInjectionResult(
        selectedImplementations = filteredImplementations,
        injectionType = injectionType,
        reason = buildResolutionReason(filteredImplementations, qualifierValue, injectionType),
    )
  }

  private fun extractQualifier(element: PsiElement): String? =
      when (element) {
        is PsiField -> extractQualifierFromAnnotations(element.modifierList)
        is PsiParameter -> extractQualifierFromAnnotations(element.modifierList)
        is PsiMethod -> extractQualifierFromAnnotations(element.modifierList)
        else -> null
      }

  private fun extractQualifierFromAnnotations(modifierList: PsiModifierList?): String? {
    if (modifierList == null) return null

    val qualifierAnnotation =
        modifierList.annotations.find {
          hasAnyAnnotation(it, SpringAnnotations.qualifierAnnotations)
        }

    if (qualifierAnnotation != null) {
      return qualifierAnnotation.findAttributeValue("value")?.let { extractFirstStringValue(it) }
    }

    val resourceAnnotation =
        modifierList.annotations.find {
          hasAnyAnnotation(it, SpringAnnotations.resourceAnnotations)
        }

    if (resourceAnnotation != null) {
      return resourceAnnotation.findAttributeValue("name")?.let { extractFirstStringValue(it) }
          ?: resourceAnnotation.findAttributeValue("value")?.let { extractFirstStringValue(it) }
    }

    return null
  }

  private fun determineInjectionType(element: PsiElement): InjectionType {
    val type =
        when (element) {
          is PsiField -> element.type
          is PsiParameter -> element.type
          else -> return InjectionType.SINGLE
        }

    val typeText = type.canonicalText

    return when {
      typeText.startsWith("java.util.List<") || typeText.startsWith("List<") -> InjectionType.LIST

      typeText.startsWith("java.util.Map<java.lang.String,") || typeText.startsWith("Map<") ->
          InjectionType.MAP

      typeText.startsWith("java.util.Set<") || typeText.startsWith("Set<") -> InjectionType.SET

      else -> InjectionType.SINGLE
    }
  }

  private fun filterImplementationsByPriority(
      implementations: List<PsiClass>,
      qualifierValue: String?,
      injectionType: InjectionType,
  ): List<PsiClass> {
    if (injectionType.isCollection) {
      if (qualifierValue != null) {
        val qualifierMatches =
            implementations.filter { impl -> matchesQualifier(impl, qualifierValue) }
        if (qualifierMatches.isNotEmpty()) {
          return qualifierMatches
        }
      }
      return implementations // Return all for collection injection
    }

    if (qualifierValue != null) {
      val qualifierMatches =
          implementations.filter { impl -> matchesQualifier(impl, qualifierValue) }
      if (qualifierMatches.isNotEmpty()) {
        return qualifierMatches
      }
    }

    val primaryImplementations =
        implementations.filter { impl -> hasAnnotation(impl, SpringAnnotations.primaryAnnotations) }

    if (primaryImplementations.isNotEmpty()) {
      return primaryImplementations
    }

    return implementations
  }

  private fun matchesQualifier(
      implementation: PsiClass,
      qualifierValue: String,
  ): Boolean {
    val implQualifier =
        implementation.modifierList
            ?.annotations
            ?.find { hasAnyAnnotation(it, SpringAnnotations.qualifierAnnotations) }
            ?.findAttributeValue("value")
            ?.let { extractFirstStringValue(it) }

    if (implQualifier == qualifierValue) {
      return true
    }

    val componentBeanName =
        implementation.modifierList
            ?.annotations
            ?.find { annotation ->
              SpringAnnotations.componentAnnotations.any {
                annotation.qualifiedName?.endsWith(it) == true
              }
            }
            ?.findAttributeValue("value")
            ?.let { extractFirstStringValue(it) }

    if (componentBeanName != null && componentBeanName == qualifierValue) {
      return true
    }

    val beanName =
        implementation.name?.let { name -> name.substring(0, 1).lowercase() + name.substring(1) }

    return beanName == qualifierValue
  }

  private fun hasAnnotation(
      implementation: PsiClass,
      annotations: Set<String>,
  ): Boolean {
    return hasAnyAnnotation(implementation, annotations)
  }

  private fun buildResolutionReason(
      implementations: List<PsiClass>,
      qualifierValue: String?,
      injectionType: InjectionType,
  ): String =
      when {
        implementations.isEmpty() -> {
          "No matching implementations found"
        }

        qualifierValue != null -> {
          if (injectionType.isCollection) {
            "Collection injection resolved by @Qualifier(\"$qualifierValue\")"
          } else {
            "Resolved by @Qualifier(\"$qualifierValue\")"
          }
        }

        injectionType.isCollection -> {
          "Collection injection - all implementations included"
        }

        implementations.any { hasAnnotation(it, SpringAnnotations.primaryAnnotations) } -> {
          "Resolved by @Primary annotation"
        }

        implementations.size == 1 -> {
          "Single implementation available"
        }

        else -> {
          "Multiple implementations - using all (ambiguous injection)"
        }
      }
}
