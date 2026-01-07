package com.github.rccccat.callgraphjava.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement

fun isProjectCode(
    project: Project,
    element: PsiElement,
): Boolean =
    ReadAction.compute<Boolean, Exception> {
      val containingFile = element.containingFile ?: return@compute false
      val virtualFile = containingFile.virtualFile ?: return@compute false

      val fileIndex = ProjectFileIndex.getInstance(project)
      fileIndex.isInSourceContent(virtualFile) || fileIndex.isInTestSourceContent(virtualFile)
    }
