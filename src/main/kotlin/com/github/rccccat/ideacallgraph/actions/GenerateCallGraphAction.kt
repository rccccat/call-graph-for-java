package com.github.rccccat.ideacallgraph.actions

import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraph
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.toolWindow.CallGraphToolWindowContent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/** Action to generate call graph from selected method */
class GenerateCallGraphAction : AnAction("Generate Call Graph") {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

    // Check if indexes are ready
    if (DumbService.isDumb(project)) {
      showNotification(
          project,
          "Indexes are not ready yet. Please wait for indexing to complete.",
          NotificationType.WARNING,
      )
      return
    }

    val offset = editor.caretModel.offset

    // Find target element with ReadAction protection
    val targetElement =
        ReadAction.compute<PsiElement?, Exception> {
          val element = psiFile.findElementAt(offset) ?: return@compute null
          findTargetElement(element)
        }

    if (targetElement == null) {
      showNotification(project, "Please place cursor on a method", NotificationType.WARNING)
      return
    }

    // Generate call graph in background
    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "Generating call graph", true) {
              override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing method: ${getElementName(targetElement)}"
                indicator.fraction = 0.2

                val service = CallGraphServiceImpl.getInstance(project)
                val callGraph = service.buildCallGraph(targetElement)

                indicator.fraction = 0.8

                if (callGraph == null) {
                  ApplicationManager.getApplication().invokeLater {
                    showNotification(
                        project, "Failed to generate call graph", NotificationType.ERROR)
                  }
                  return
                }

                indicator.fraction = 1.0

                // Show the call graph in the tool window (must be done on EDT)
                ApplicationManager.getApplication().invokeLater {
                  showCallGraph(project, callGraph)
                }
              }
            },
        )
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    val psiFile = e.getData(CommonDataKeys.PSI_FILE)

    val isAvailable =
        project != null && editor != null && psiFile != null && psiFile.name.endsWith(".java")

    e.presentation.isEnabledAndVisible = isAvailable

    if (project != null && DumbService.isDumb(project)) {
      e.presentation.text = "Indexing"
      e.presentation.isEnabled = false
    } else {
      e.presentation.text = "Generate Call Graph (Ctrl+Alt+G)"
    }
  }

  private fun findTargetElement(element: PsiElement): PsiElement? {
    // Look for Java method
    return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
  }

  private fun getElementName(element: PsiElement): String =
      when (element) {
        is PsiMethod -> element.name
        else -> "element"
      }

  private fun showCallGraph(
      project: Project,
      callGraph: IdeCallGraph,
  ) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow("Call Graph")

    if (toolWindow != null) {
      val content = toolWindow.contentManager.getContent(0)
      val callGraphContent = content?.component as? CallGraphToolWindowContent
      callGraphContent?.updateCallGraph(callGraph)

      toolWindow.activate(null)
    }
  }

  private fun showNotification(
      project: Project,
      message: String,
      type: NotificationType,
  ) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Call Graph")
        .createNotification(message, type)
        .notify(project)
  }
}
