package com.github.rccccat.callgraphjava.ui

import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

object CallGraphNodeNavigator {
  fun navigate(
      project: Project,
      node: IdeCallGraphNode,
  ) {
    val navigationInfo =
        ReadAction.compute<NavigationInfo?, Exception> {
          val element = node.elementPointer.element ?: return@compute null

          if (element is Navigatable && element.canNavigate()) {
            return@compute NavigationInfo(element = element, isDirectlyNavigatable = true)
          }

          val containingFile = element.containingFile ?: return@compute null
          val virtualFile = containingFile.virtualFile ?: return@compute null
          val offset = element.textOffset
          val startOffset = element.textRange.startOffset
          val endOffset = element.textRange.endOffset

          NavigationInfo(
              element = element,
              virtualFile = virtualFile,
              offset = offset,
              startOffset = startOffset,
              endOffset = endOffset,
          )
        } ?: return

    ApplicationManager.getApplication().invokeLater {
      if (navigationInfo.isDirectlyNavigatable) {
        (navigationInfo.element as Navigatable).navigate(true)
      } else if (navigationInfo.virtualFile != null) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val editor =
            fileEditorManager.openTextEditor(
                OpenFileDescriptor(project, navigationInfo.virtualFile, navigationInfo.offset),
                true,
            )
        editor?.let {
          it.selectionModel.setSelection(navigationInfo.startOffset, navigationInfo.endOffset)
          it.caretModel.moveToOffset(navigationInfo.startOffset)
        }
      }
    }
  }

  private data class NavigationInfo(
      val element: PsiElement,
      val isDirectlyNavigatable: Boolean = false,
      val virtualFile: VirtualFile? = null,
      val offset: Int = 0,
      val startOffset: Int = 0,
      val endOffset: Int = 0,
  )
}
