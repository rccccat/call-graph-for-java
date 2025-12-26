package com.github.rccccat.ideacallgraph.util

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

fun findAnnotatedClasses(
    javaPsiFacade: JavaPsiFacade,
    annotationQualifiedName: String,
    scope: GlobalSearchScope,
): Collection<PsiClass> {
  val annotationClass =
      ReadAction.compute<PsiClass?, Exception> {
        javaPsiFacade.findClass(
            annotationQualifiedName,
            GlobalSearchScope.allScope(javaPsiFacade.project),
        )
      } ?: return emptyList()
  return ReadAction.compute<Collection<PsiClass>, Exception> {
    AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).findAll()
  }
}
