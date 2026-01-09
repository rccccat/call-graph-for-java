package com.github.rccccat.callgraphjava.framework.spring

import com.github.rccccat.callgraphjava.util.SpringAnnotations
import com.github.rccccat.callgraphjava.util.hasAnyAnnotationOrMeta
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

/** Spring analyzer implementation for Java methods. */
class JavaSpringAnalyzer {
  fun canAnalyze(element: PsiElement): Boolean = element is PsiMethod

  fun analyze(element: PsiMethod): SpringMethodInfo {
    return ReadAction.compute<SpringMethodInfo, Exception> {
      val containingClass = element.containingClass ?: return@compute SpringMethodInfo.EMPTY

      val isController =
          hasAnyAnnotationOrMeta(containingClass, SpringAnnotations.controllerAnnotations)
      val isService = hasAnyAnnotationOrMeta(containingClass, SpringAnnotations.serviceAnnotations)
      val isEndpoint = isController && hasMappingOnMethodOrSuper(element)

      SpringMethodInfo(
          isController = isController,
          isService = isService,
          isEndpoint = isEndpoint,
      )
    }
  }
}
