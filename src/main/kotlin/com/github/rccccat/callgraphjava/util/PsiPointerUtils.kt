package com.github.rccccat.callgraphjava.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

fun <T : PsiElement> toSmartPointer(
    project: Project,
    element: T,
): SmartPsiElementPointer<T> {
  val manager = SmartPointerManager.getInstance(project)
  return manager.createSmartPsiElementPointer(element)
}

fun <T : PsiElement> toSmartPointerList(
    project: Project,
    elements: Collection<T>,
): List<SmartPsiElementPointer<T>> {
  val manager = SmartPointerManager.getInstance(project)
  return elements.map { element -> manager.createSmartPsiElementPointer(element) }
}

fun <T : PsiElement> resolveValidPointers(
    pointers: Collection<SmartPsiElementPointer<T>>
): List<T> = pointers.mapNotNull { pointer -> pointer.element }
