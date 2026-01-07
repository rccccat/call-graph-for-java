package com.github.rccccat.callgraphjava.toolWindow

import com.github.rccccat.callgraphjava.api.model.NodeType
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraph
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.github.rccccat.callgraphjava.service.CallGraphServiceImpl
import com.github.rccccat.callgraphjava.ui.CallGraphNodeNavigator
import com.github.rccccat.callgraphjava.ui.CallGraphNodeText
import com.github.rccccat.callgraphjava.ui.CallGraphTreeRenderer
import com.github.rccccat.callgraphjava.ui.toolwindow.TreeConfiguration
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JToolBar
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class CallGraphToolWindowContent(
    private val project: Project,
) : JBPanel<CallGraphToolWindowContent>(BorderLayout()) {
  private val tree = Tree()
  private val emptyLabel =
      JLabel(
              "<html><center>右键点击方法选择「生成调用图」来生成调用图<br/>双击节点跳转到代码</center></html>",
          )
          .apply { horizontalAlignment = JLabel.CENTER }
  private val scrollPane = JBScrollPane(tree)
  private val treeConfiguration = TreeConfiguration.fromProject(project)
  private var currentCallGraph: IdeCallGraph? = null

  init {
    tree.cellRenderer = CallGraphTreeRenderer()
    tree.isRootVisible = true
    tree.showsRootHandles = true

    tree.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2) {
              handleTreeDoubleClick(e)
            }
          }
        },
    )

    tree.addMouseListener(
        object : PopupHandler() {
          override fun invokePopup(
              component: java.awt.Component,
              x: Int,
              y: Int,
          ) {
            val path = tree.getPathForLocation(x, y)
            if (path != null) {
              tree.selectionPath = path
              showContextMenu(component, x, y)
            }
          }
        },
    )

    val toolbar = createToolbar()

    add(toolbar, BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)

    showEmptyState()
  }

  private fun createToolbar(): JToolBar {
    val toolbar = JToolBar()
    toolbar.isFloatable = false

    val actionGroup = DefaultActionGroup().apply { add(ExportToJsonAction()) }

    val actionToolbar =
        ActionManager.getInstance().createActionToolbar("CallGraphToolWindow", actionGroup, true)
    actionToolbar.targetComponent = this

    toolbar.add(actionToolbar.component)
    return toolbar
  }

  fun updateCallGraph(callGraph: IdeCallGraph) {
    currentCallGraph = callGraph
    val rootNode =
        callGraph.rootNode
            ?: run {
              showEmptyState()
              return
            }
    val root = createTreeNode(callGraph, rootNode)
    val model = DefaultTreeModel(root)
    tree.model = model

    expandTree(root, treeConfiguration.initialExpandDepth)

    showTree()
  }

  private fun handleTreeDoubleClick(e: MouseEvent) {
    val path = tree.getPathForLocation(e.x, e.y) ?: return
    val selectedNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return
    val treeNode = selectedNode.userObject as? CallGraphTreeNode ?: return

    CallGraphNodeNavigator.navigate(project, treeNode.node)
  }

  private fun showContextMenu(
      component: java.awt.Component,
      x: Int,
      y: Int,
  ) {
    val selectedPath = tree.selectionPath ?: return
    val selectedNode = selectedPath.lastPathComponent as? DefaultMutableTreeNode ?: return
    val treeNode = selectedNode.userObject as? CallGraphTreeNode ?: return

    val actionGroup =
        DefaultActionGroup().apply {
          add(NavigateToCodeAction(treeNode.node))
          add(CopyMethodSignatureAction(treeNode.node))
          add(Separator())
          add(ShowMethodInfoAction(treeNode.node))
        }

    val popupMenu =
        ActionManager.getInstance().createActionPopupMenu("CallGraphToolWindow", actionGroup)
    popupMenu.component.show(component, x, y)
  }

  private inner class NavigateToCodeAction(
      private val node: IdeCallGraphNode,
  ) : AnAction("跳转到代码") {
    override fun actionPerformed(e: AnActionEvent) {
      CallGraphNodeNavigator.navigate(project, node)
    }
  }

  private inner class CopyMethodSignatureAction(
      private val node: IdeCallGraphNode,
  ) : AnAction("复制方法签名") {
    override fun actionPerformed(e: AnActionEvent) {
      val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
      val selection = java.awt.datatransfer.StringSelection(node.signature)
      clipboard.setContents(selection, null)

      Messages.showInfoMessage(project, "方法签名已复制到剪贴板", "复制签名")
    }
  }

  private inner class ShowMethodInfoAction(
      private val node: IdeCallGraphNode,
  ) : AnAction("显示方法信息") {
    override fun actionPerformed(e: AnActionEvent) {
      val info = buildString {
        appendLine("方法: ${node.name}")
        appendLine("类: ${node.className ?: "未知"}")
        appendLine("签名: ${node.signature}")
        appendLine("类型: ${node.nodeType}")
        appendLine("项目代码: ${node.isProjectCode}")
        if (node.isSpringEndpoint) {
          appendLine("Spring 端点: 是")
        }
        if (node.nodeType == NodeType.MYBATIS_MAPPER_METHOD) {
          appendLine("MyBatis Mapper 方法: 是")
          if (node.sqlType != null) {
            appendLine("SQL 类型: ${node.sqlType}")
          }
        }
        if (node.nodeType == NodeType.MYBATIS_SQL_STATEMENT) {
          appendLine("MyBatis SQL 语句: 是")
          appendLine("SQL 类型: ${node.sqlType}")
          if (node.sqlStatement != null) {
            appendLine("SQL: ${node.sqlStatement}")
          }
        }
      }

      Messages.showInfoMessage(project, info, "方法信息")
    }
  }

  private inner class ExportToJsonAction : AnAction("查看 JSON", "以 JSON 格式查看调用图", null) {
    override fun actionPerformed(e: AnActionEvent) {
      val callGraph = currentCallGraph
      if (callGraph == null) {
        Messages.showWarningDialog(project, "没有可导出的调用图", "导出警告")
        return
      }

      val service = CallGraphServiceImpl.getInstance(project)
      val json = service.exportToJson(callGraph)
      val rootNode = callGraph.rootNode
      if (rootNode == null) {
        Messages.showWarningDialog(project, "调用图根节点缺失", "导出警告")
        return
      }

      val dialog =
          com.github.rccccat.callgraphjava.ui.JsonPreviewDialog(
              project, json, "${rootNode.className}.${rootNode.name}")
      dialog.show()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = currentCallGraph != null
    }
  }

  private fun createTreeNode(
      callGraph: IdeCallGraph,
      rootNode: IdeCallGraphNode,
  ): DefaultMutableTreeNode {
    val rootTreeNode = DefaultMutableTreeNode(CallGraphTreeNode(rootNode, 0))

    buildTreeRecursive(
        rootTreeNode, rootNode, callGraph, mutableSetOf(), treeConfiguration.maxDisplayDepth)

    return rootTreeNode
  }

  private fun buildTreeRecursive(
      parentTreeNode: DefaultMutableTreeNode,
      currentNode: IdeCallGraphNode,
      callGraph: IdeCallGraph,
      ancestorPath: MutableSet<String>,
      maxDepth: Int,
  ) {
    if (maxDepth <= 0 || currentNode.id in ancestorPath) return

    ancestorPath.add(currentNode.id)
    val callTargets = callGraph.getCallTargets(currentNode)

    for (callTarget in callTargets.take(treeConfiguration.maxChildrenPerNode)) {
      val callTargetTreeNode =
          DefaultMutableTreeNode(CallGraphTreeNode(callTarget, parentTreeNode.level + 1))
      parentTreeNode.add(callTargetTreeNode)

      buildTreeRecursive(callTargetTreeNode, callTarget, callGraph, ancestorPath, maxDepth - 1)
    }

    ancestorPath.remove(currentNode.id)
  }

  private fun expandTree(
      node: DefaultMutableTreeNode,
      depth: Int,
  ) {
    if (depth <= 0) return

    val path = TreePath(node.path)
    tree.expandPath(path)

    for (i in 0 until node.childCount) {
      val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
      expandTree(child, depth - 1)
    }
  }

  private fun showEmptyState() {
    scrollPane.setViewportView(emptyLabel)
  }

  private fun showTree() {
    scrollPane.setViewportView(tree)
  }

  data class CallGraphTreeNode(
      val node: IdeCallGraphNode,
      val depth: Int,
  ) {
    override fun toString(): String = CallGraphNodeText.format(node)
  }
}
