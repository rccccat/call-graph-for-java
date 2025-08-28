package com.github.rccccat.ideacallgraph.toolWindow

import com.github.rccccat.ideacallgraph.export.CallGraphJsonExporter
import com.github.rccccat.ideacallgraph.model.CallGraph
import com.github.rccccat.ideacallgraph.model.CallGraphNode
import com.github.rccccat.ideacallgraph.ui.CallGraphTreeRenderer
import com.github.rccccat.ideacallgraph.llm.LLMService
import com.github.rccccat.ideacallgraph.llm.LLMSettings
import com.github.rccccat.ideacallgraph.llm.LLMAnalysisDialog
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.PopupHandler
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JToolBar
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

/**
 * Tool window content for displaying call graphs
 */
class CallGraphToolWindowContent(private val project: Project) : JBPanel<CallGraphToolWindowContent>(BorderLayout()) {

    private val tree: Tree
    private val emptyLabel: JLabel
    private var currentCallGraph: CallGraph? = null

    init {
        emptyLabel = JLabel("<html><center>Generate a call graph by right-clicking on a method and selecting 'Generate Call Graph'<br/>Double-click on nodes to navigate to code</center></html>")
        emptyLabel.horizontalAlignment = JLabel.CENTER
        
        tree = Tree()
        tree.cellRenderer = CallGraphTreeRenderer()
        tree.isRootVisible = true
        tree.showsRootHandles = true
        
        // Add double-click listener for navigation
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    handleTreeDoubleClick(e)
                }
            }
        })
        
        // Add right-click context menu
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(component: java.awt.Component, x: Int, y: Int) {
                val path = tree.getPathForLocation(x, y)
                if (path != null) {
                    tree.selectionPath = path
                    showContextMenu(component, x, y)
                }
            }
        })
        
        val scrollPane = JBScrollPane(tree)
        
        // Create toolbar
        val toolbar = createToolbar()
        
        add(toolbar, BorderLayout.NORTH)
        add(emptyLabel, BorderLayout.CENTER)
        add(scrollPane, BorderLayout.CENTER)
        
        showEmptyState()
    }

    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        
        val actionGroup = DefaultActionGroup().apply {
            add(ExportToJsonAction())
            add(AnalyzeWithLLMAction())
        }
        
        val actionToolbar = ActionManager.getInstance().createActionToolbar(
            "CallGraphToolWindow",
            actionGroup,
            true
        )
        
        toolbar.add(actionToolbar.component)
        return toolbar
    }

    fun updateCallGraph(callGraph: CallGraph) {
        currentCallGraph = callGraph
        val root = createTreeNode(callGraph)
        val model = DefaultTreeModel(root)
        tree.model = model
        
        // Expand the first few levels
        expandTree(root, 2)
        
        showTree()
    }

    private fun handleTreeDoubleClick(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val selectedNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val treeNode = selectedNode.userObject as? CallGraphTreeNode ?: return
        
        navigateToNode(treeNode.node)
    }

    private fun navigateToNode(node: CallGraphNode) {
        val element = node.elementPointer.element ?: return
        
        // Special handling for MyBatis SQL nodes
        if (node.nodeType == CallGraphNode.NodeType.MYBATIS_SQL_STATEMENT) {
            navigateToSqlNode(node, element)
            return
        }
        
        // Check if the element is navigatable
        if (element is Navigatable && element.canNavigate()) {
            element.navigate(true)
        } else {
            // Fallback: try to navigate to the containing file
            val containingFile = element.containingFile
            if (containingFile != null) {
                val virtualFile = containingFile.virtualFile
                if (virtualFile != null) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    
                    // Calculate offset for more precise navigation
                    val offset = element.textOffset
                    
                    // Open the file and navigate to the offset
                    val editor = fileEditorManager.openTextEditor(
                        com.intellij.openapi.fileEditor.OpenFileDescriptor(project, virtualFile, offset),
                        true
                    )
                    
                    // Optional: select the element text for better visibility
                    editor?.let {
                        val startOffset = element.textRange.startOffset
                        val endOffset = element.textRange.endOffset
                        it.selectionModel.setSelection(startOffset, endOffset)
                        it.caretModel.moveToOffset(startOffset)
                    }
                }
            }
        }
    }

    private fun navigateToSqlNode(node: CallGraphNode, element: PsiElement) {
        // For XML-based SQL statements, navigate directly to the XML tag
        if (element is XmlTag) {
            if (element is Navigatable && element.canNavigate()) {
                element.navigate(true)
                return
            }
        }
        
        // Fallback: try to open XML file by path if we have it
        if (node.xmlFilePath != null) {
            try {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(node.xmlFilePath!!)
                if (virtualFile != null) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    
                    // Try to find the specific SQL tag and navigate to it
                    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                    if (psiFile is XmlFile) {
                        val rootTag = psiFile.rootTag
                        if (rootTag != null) {
                            // Extract method name from node name (remove sql type prefix)
                            val methodName = node.name.substringAfter("_")
                            val sqlTag = findSqlTagById(rootTag, methodName)
                            
                            if (sqlTag != null && sqlTag is Navigatable && sqlTag.canNavigate()) {
                                sqlTag.navigate(true)
                                return
                            }
                        }
                    }
                    
                    // Fallback: just open the XML file
                    fileEditorManager.openFile(virtualFile, true)
                }
            } catch (e: Exception) {
                // Log error and fallback to default navigation
            }
        }
        
        // Final fallback: navigate to the original mapper method
        if (element is Navigatable && element.canNavigate()) {
            element.navigate(true)
        }
    }

    private fun findSqlTagById(rootTag: XmlTag, methodId: String): XmlTag? {
        val sqlTags = listOf("select", "insert", "update", "delete")
        
        for (child in rootTag.subTags) {
            if (child.name.lowercase() in sqlTags) {
                val id = child.getAttributeValue("id")
                if (id == methodId) {
                    return child
                }
            }
        }
        
        return null
    }

    private fun showContextMenu(component: java.awt.Component, x: Int, y: Int) {
        val selectedPath = tree.selectionPath ?: return
        val selectedNode = selectedPath.lastPathComponent as? DefaultMutableTreeNode ?: return
        val treeNode = selectedNode.userObject as? CallGraphTreeNode ?: return
        
        val actionGroup = DefaultActionGroup().apply {
            add(NavigateToCodeAction(treeNode.node))
            add(CopyMethodSignatureAction(treeNode.node))
            add(Separator())
            add(ShowMethodInfoAction(treeNode.node))
        }
        
        val popupMenu = ActionManager.getInstance().createActionPopupMenu("CallGraphToolWindow", actionGroup)
        popupMenu.component.show(component, x, y)
    }

    // Action classes for context menu
    private inner class NavigateToCodeAction(private val node: CallGraphNode) : 
        AnAction("Navigate to Code") {
        
        override fun actionPerformed(e: AnActionEvent) {
            navigateToNode(node)
        }
    }

    private inner class CopyMethodSignatureAction(private val node: CallGraphNode) : 
        AnAction("Copy Method Signature") {
        
        override fun actionPerformed(e: AnActionEvent) {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(node.signature)
            clipboard.setContents(selection, null)
            
            Messages.showInfoMessage(
                project,
                "Method signature copied to clipboard",
                "Copy Signature"
            )
        }
    }

    private inner class ShowMethodInfoAction(private val node: CallGraphNode) : 
        AnAction("Show Method Info") {
        
        override fun actionPerformed(e: AnActionEvent) {
            val info = buildString {
                appendLine("Method: ${node.name}")
                appendLine("Class: ${node.className ?: "Unknown"}")
                appendLine("Signature: ${node.signature}")
                appendLine("Type: ${node.nodeType}")
                appendLine("Project Code: ${node.isProjectCode}")
                if (node.isSpringEndpoint) {
                    appendLine("Spring Endpoint: ${node.springMapping}")
                    appendLine("HTTP Methods: ${node.httpMethods.joinToString(", ")}")
                }
                if (node.nodeType == CallGraphNode.NodeType.MYBATIS_MAPPER_METHOD) {
                    appendLine("MyBatis Mapper Method: true")
                    if (node.sqlType != null) {
                        appendLine("SQL Type: ${node.sqlType}")
                    }
                    if (node.xmlFilePath != null) {
                        appendLine("XML File: ${node.xmlFilePath}")
                    }
                }
                if (node.nodeType == CallGraphNode.NodeType.MYBATIS_SQL_STATEMENT) {
                    appendLine("MyBatis SQL Statement: true")
                    appendLine("SQL Type: ${node.sqlType}")
                    if (node.sqlStatement != null) {
                        appendLine("SQL: ${node.sqlStatement}")
                    }
                    if (node.xmlFilePath != null) {
                        appendLine("XML File: ${node.xmlFilePath}")
                    }
                }
            }
            
            Messages.showInfoMessage(project, info, "Method Information")
        }
    }

    // Export JSON Action
    private inner class ExportToJsonAction : AnAction("View JSON", "View call graph in JSON format", null) {
        
        override fun actionPerformed(e: AnActionEvent) {
            val callGraph = currentCallGraph
            if (callGraph == null) {
                Messages.showWarningDialog(
                    project,
                    "No call graph available to export",
                    "Export Warning"
                )
                return
            }
            
            val exporter = CallGraphJsonExporter()
            val json = exporter.exportToJson(callGraph)
            
            // Show JSON in preview dialog
            val dialog = com.github.rccccat.ideacallgraph.ui.JsonPreviewDialog(
                project,
                json,
                "${callGraph.rootNode.className}.${callGraph.rootNode.name}"
            )
            dialog.show()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = currentCallGraph != null
        }
    }

    // Analyze with LLM Action
    private inner class AnalyzeWithLLMAction : AnAction("AI Analysis", "Analyze call graph using AI", null) {
        
        override fun actionPerformed(e: AnActionEvent) {
            val callGraph = currentCallGraph
            if (callGraph == null) {
                Messages.showWarningDialog(
                    project,
                    "No call graph available to analyze",
                    "Analysis Warning"
                )
                return
            }

            val llmSettings = LLMSettings.getInstance()
            val llmService = LLMService.getInstance()
            
            // Validate settings
            val validationErrors = llmService.validateSettings()
            if (validationErrors.isNotEmpty()) {
                Messages.showWarningDialog(
                    project,
                    "LLM configuration issues:\n\n${validationErrors.joinToString("\n")}",
                    "Configuration Error"
                )
                return
            }

            // Perform analysis
            llmService.analyzeCallGraphWithProgress(
                project = project,
                callGraph = callGraph,
                onSuccess = { analysis ->
                    val dialog = LLMAnalysisDialog(
                        project,
                        analysis,
                        "${callGraph.rootNode.className}.${callGraph.rootNode.name}"
                    )
                    dialog.show()
                },
                onError = { error ->
                    Messages.showErrorDialog(
                        project,
                        "Failed to analyze call graph with LLM:\n\n${error.message}",
                        "Analysis Error"
                    )
                }
            )
        }

        override fun update(e: AnActionEvent) {
            val hasCallGraph = currentCallGraph != null
            val llmEnabled = LLMSettings.getInstance().enabled
            
            e.presentation.isEnabled = hasCallGraph && llmEnabled
            
            if (!llmEnabled) {
                e.presentation.text = "AI Analysis (Disabled)"
                e.presentation.description = "Enable LLM analysis in settings to use this feature"
            } else {
                e.presentation.text = "AI Analysis"
                e.presentation.description = "Analyze call graph using AI"
            }
        }
    }

    private fun createTreeNode(callGraph: CallGraph): DefaultMutableTreeNode {
        val rootTreeNode = DefaultMutableTreeNode(CallGraphTreeNode(callGraph.rootNode, 0))
        
        buildTreeRecursive(rootTreeNode, callGraph.rootNode, callGraph, mutableSetOf(), 5)
        
        return rootTreeNode
    }

    private fun buildTreeRecursive(
        parentTreeNode: DefaultMutableTreeNode,
        currentNode: CallGraphNode,
        callGraph: CallGraph,
        visited: MutableSet<String>,
        maxDepth: Int
    ) {
        if (maxDepth <= 0 || currentNode.id in visited) return
        
        visited.add(currentNode.id)
        val callees = callGraph.getCallees(currentNode)
        
        for (callee in callees.take(20)) { // Limit to 20 to avoid too large trees
            val calleeTreeNode = DefaultMutableTreeNode(CallGraphTreeNode(callee, parentTreeNode.level + 1))
            parentTreeNode.add(calleeTreeNode)
            
            buildTreeRecursive(calleeTreeNode, callee, callGraph, visited, maxDepth - 1)
        }
    }

    private fun expandTree(node: TreeNode, depth: Int) {
        if (depth <= 0) return
        
        tree.expandPath(tree.getPathForRow(0))
        
        for (i in 0 until node.childCount) {
            expandTree(node.getChildAt(i), depth - 1)
        }
    }

    private fun showEmptyState() {
        emptyLabel.isVisible = true
        tree.isVisible = false
    }

    private fun showTree() {
        emptyLabel.isVisible = false
        tree.isVisible = true
    }

    /**
     * Wrapper class for call graph nodes in the tree
     */
    data class CallGraphTreeNode(
        val node: CallGraphNode,
        val depth: Int
    ) {
        override fun toString(): String {
            val prefix = when {
                node.isSpringEndpoint -> "[${node.httpMethods.joinToString(",")}] "
                node.nodeType == CallGraphNode.NodeType.SPRING_SERVICE_METHOD -> "[Service] "
                node.nodeType == CallGraphNode.NodeType.MYBATIS_MAPPER_METHOD -> "[Mapper] "
                node.nodeType == CallGraphNode.NodeType.MYBATIS_SQL_STATEMENT -> "[${node.sqlType?.name}] "
                else -> ""
            }
            
            val suffix = when {
                node.springMapping != null -> " -> ${node.springMapping}"
                !node.isProjectCode -> " [Third-party]"
                else -> ""
            }
            
            return "$prefix${node.className}::${node.name}$suffix"
        }
    }
}