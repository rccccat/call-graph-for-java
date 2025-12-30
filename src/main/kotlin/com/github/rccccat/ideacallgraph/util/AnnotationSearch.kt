package com.github.rccccat.ideacallgraph.util

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

fun findAnnotatedClasses(
    annotationClass: PsiClass,
    scope: GlobalSearchScope,
): Collection<PsiClass> {
  return ReadAction.compute<Collection<PsiClass>, Exception> {
    AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).findAll()
  }
}
