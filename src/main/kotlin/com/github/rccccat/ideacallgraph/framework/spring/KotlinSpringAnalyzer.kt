package com.github.rccccat.ideacallgraph.framework.spring

import com.github.rccccat.ideacallgraph.util.SpringAnnotations
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotationOrMeta
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Spring analyzer implementation for Kotlin functions. */
class KotlinSpringAnalyzer : SpringElementAnalyzer<KtNamedFunction> {
  override fun canAnalyze(element: PsiElement): Boolean = element is KtNamedFunction

  override fun analyze(element: KtNamedFunction): SpringMethodInfo {
    return ReadAction.compute<SpringMethodInfo, Exception> {
      val containingClass =
          element.parent?.parent as? KtClass ?: return@compute SpringMethodInfo.EMPTY

      val isController =
          hasKtAnnotationOrMeta(containingClass, SpringAnnotations.controllerAnnotations)
      val isService = hasKtAnnotationOrMeta(containingClass, SpringAnnotations.serviceAnnotations)
      val isEndpoint = isController && hasMappingOnKotlinFunction(element)

      SpringMethodInfo(
          isController = isController,
          isService = isService,
          isEndpoint = isEndpoint,
      )
    }
  }

  override fun isSpringComponent(element: KtNamedFunction): Boolean {
    val containingClass = element.parent?.parent as? KtClass ?: return false
    return hasKtAnnotationOrMeta(containingClass, SpringAnnotations.componentAnnotations)
  }

  private fun hasKtAnnotationOrMeta(
      element: KtAnnotated,
      annotations: Set<String>,
  ): Boolean {
    if (hasDirectKtAnnotation(element, annotations)) return true

    val lightClass = (element as? KtClass)?.toLightClass()
    if (lightClass != null && hasAnyAnnotationOrMeta(lightClass, annotations)) {
      return true
    }

    return element.annotationEntries.any { annotation ->
      val resolved = annotation.typeReference?.references?.firstOrNull()?.resolve() as? PsiClass
      resolved != null && hasAnyAnnotationOrMeta(resolved, annotations)
    }
  }

  private fun hasMappingOnKotlinFunction(function: KtNamedFunction): Boolean {
    if (function.annotationEntries.any { annotation ->
      SpringAnnotations.mappingAnnotations.contains(annotation.shortName?.asString())
    }) {
      return true
    }
    val lightMethods = function.toLightMethods()
    return lightMethods.any { method -> hasMappingOnMethodOrSuper(method) }
  }

  private fun hasDirectKtAnnotation(
      element: KtAnnotated,
      annotations: Set<String>,
  ): Boolean =
      element.annotationEntries.any { annotation ->
        val name = annotation.shortName?.asString()
        name != null && annotations.contains(name)
      }
}
