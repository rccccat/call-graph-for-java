package com.github.rccccat.ideacallgraph.actions

import com.github.rccccat.ideacallgraph.analysis.CallGraphAnalyzer
import com.github.rccccat.ideacallgraph.model.CallGraph
import com.github.rccccat.ideacallgraph.toolWindow.CallGraphToolWindowContent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Action to generate call graph from selected method or function
 */
class GenerateCallGraphAction : AnAction("Generate Call Graph") {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return
        
        // Find the containing method or function
        val targetElement = findTargetElement(element) ?: run {
            showErrorMessage(project, "Please place cursor on a method or function")
            return
        }
        
        generateCallGraph(project, targetElement)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        val isAvailable = project != null && editor != null && psiFile != null &&
                (psiFile.name.endsWith(".java") || psiFile.name.endsWith(".kt"))
        
        e.presentation.isEnabledAndVisible = isAvailable
        
        if (isAvailable) {
            val offset = editor!!.caretModel.offset
            val element = psiFile!!.findElementAt(offset)
            val targetElement = element?.let { findTargetElement(it) }
            
            e.presentation.text = if (targetElement != null) {
                val elementName = when (targetElement) {
                    is PsiMethod -> targetElement.name
                    is KtNamedFunction -> targetElement.name ?: "anonymous"
                    else -> "element"
                }
                "Generate Call Graph for '$elementName'"
            } else {
                "Generate Call Graph"
            }
        }
    }

    private fun findTargetElement(element: PsiElement): PsiElement? {
        // Look for Java method
        PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)?.let { return it }
        
        // Look for Kotlin function
        PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)?.let { return it }
        
        return null
    }

    private fun generateCallGraph(project: Project, element: PsiElement) {
        val analyzer = CallGraphAnalyzer(project)
        val callGraph = analyzer.buildCallGraph(element)
        
        if (callGraph == null) {
            showErrorMessage(project, "Failed to generate call graph")
            return
        }
        
        // Show the call graph in the tool window
        showCallGraph(project, callGraph)
    }

    private fun showCallGraph(project: Project, callGraph: CallGraph) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Call Graph")
        
        if (toolWindow != null) {
            val content = toolWindow.contentManager.getContent(0)
            val callGraphContent = content?.component as? CallGraphToolWindowContent
            callGraphContent?.updateCallGraph(callGraph)
            
            toolWindow.activate(null)
        }
    }

    private fun showErrorMessage(project: Project, message: String) {
        com.intellij.openapi.ui.Messages.showErrorDialog(
            project,
            message,
            "Call Graph Error"
        )
    }
}