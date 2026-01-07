package com.github.rccccat.callgraphjava.actions

import com.github.rccccat.callgraphjava.ide.model.IdeCallGraph
import com.github.rccccat.callgraphjava.service.CallGraphServiceImpl
import com.github.rccccat.callgraphjava.toolWindow.CallGraphToolWindowContent
import com.github.rccccat.callgraphjava.util.isProjectCode
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

class GenerateCallGraphAction : AnAction("生成调用图") {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

    if (DumbService.isDumb(project)) {
      showNotification(project, "索引尚未就绪，请等待索引完成。", NotificationType.WARNING)
      return
    }

    val offset = editor.caretModel.offset

    val targetElement =
        ReadAction.compute<PsiElement?, Exception> {
          val element = psiFile.findElementAt(offset) ?: return@compute null
          findTargetElement(element)
        }

    if (targetElement == null) {
      showNotification(project, "请将光标放在方法上", NotificationType.WARNING)
      return
    }

    if (!isProjectCode(project, targetElement)) {
      showNotification(project, "仅支持从项目源码方法生成调用图", NotificationType.WARNING)
      return
    }

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "正在生成调用图", true) {
              override fun run(indicator: ProgressIndicator) {
                indicator.text = "正在分析方法: ${getElementName(targetElement)}"
                indicator.fraction = 0.2

                val service = CallGraphServiceImpl.getInstance(project)
                val callGraph = service.buildCallGraph(targetElement)

                indicator.fraction = 0.8

                if (callGraph == null) {
                  ApplicationManager.getApplication().invokeLater {
                    showNotification(project, "生成调用图失败", NotificationType.ERROR)
                  }
                  return
                }

                indicator.fraction = 1.0

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
      e.presentation.text = "正在索引"
      e.presentation.isEnabled = false
    } else {
      e.presentation.text = "生成调用图 (Ctrl+Alt+G)"
    }
  }

  private fun findTargetElement(element: PsiElement): PsiElement? =
      PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)

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
