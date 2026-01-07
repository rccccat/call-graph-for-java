package com.github.rccccat.callgraphjava.toolWindow

import com.github.rccccat.callgraphjava.cache.CallGraphCacheManager
import com.github.rccccat.callgraphjava.export.SpringApiScanner
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.github.rccccat.callgraphjava.service.CallGraphServiceImpl
import com.github.rccccat.callgraphjava.ui.CallGraphNodeNavigator
import com.github.rccccat.callgraphjava.ui.CallGraphNodeText
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.file.Paths
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JToolBar
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class SpringApisToolWindowContent(
    private val project: Project,
) : JBPanel<SpringApisToolWindowContent>(BorderLayout()) {
  private val listModel = javax.swing.DefaultListModel<IdeCallGraphNode>()
  private val list = JBList(listModel)
  private val emptyLabel = JLabel("点击刷新扫描 Spring API 端点")
  private val scrollPane = JBScrollPane(list)
  private val service = CallGraphServiceImpl.getInstance(project)
  private val cacheManager = CallGraphCacheManager.getInstance(project)
  private val nodeFactory = service.getNodeFactory()
  private val searchField = SearchTextField()
  private var allNodes: List<IdeCallGraphNode> = emptyList()

  init {
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = SpringApiListRenderer()
    list.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2) {
              val node = list.selectedValue ?: return
              CallGraphNodeNavigator.navigate(project, node)
            }
          }
        },
    )

    val toolbar = createToolbar()
    configureSearchField()
    add(toolbar, BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)

    showEmptyState()
    DumbService.getInstance(project).runWhenSmart { refreshData() }
  }

  private fun createToolbar(): JToolBar {
    val toolbar = JToolBar()
    toolbar.isFloatable = false

    val actionGroup =
        DefaultActionGroup().apply {
          add(RefreshAction())
          add(ExportAction())
        }
    val actionToolbar =
        ActionManager.getInstance().createActionToolbar("SpringApisToolWindow", actionGroup, true)
    actionToolbar.targetComponent = this

    toolbar.add(actionToolbar.component)
    toolbar.addSeparator()
    toolbar.add(searchField)
    return toolbar
  }

  private fun configureSearchField() {
    searchField.textEditor.emptyText.text = "搜索映射 / 类 / 方法"
    val preferredHeight = searchField.preferredSize.height
    searchField.preferredSize = Dimension(260, preferredHeight)
    searchField.maximumSize = Dimension(600, preferredHeight)
    searchField.textEditor.document.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            applyFilter()
          }
        },
    )
  }

  private fun refreshData() {
    if (DumbService.isDumb(project)) {
      showNotification("索引尚未就绪，请等待索引完成。", NotificationType.WARNING)
      return
    }

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "正在扫描 Spring API", true) {
              override fun run(indicator: ProgressIndicator) {
                service.resetCaches()
                val scanner = SpringApiScanner(project, cacheManager)
                val nodes =
                    try {
                      ReadAction.compute<List<IdeCallGraphNode>, Exception> {
                        val endpoints = scanner.scanAllEndpoints(indicator)
                        endpoints
                            .mapNotNull { nodeFactory.createIdeNode(it) }
                            .filter { it.isSpringEndpoint }
                      }
                    } catch (e: Exception) {
                      ApplicationManager.getApplication().invokeLater {
                        showNotification("扫描 Spring API 出错: ${e.message}", NotificationType.ERROR)
                      }
                      return
                    }

                ApplicationManager.getApplication().invokeLater { updateList(nodes) }
              }
            },
        )
  }

  private fun updateList(nodes: List<IdeCallGraphNode>) {
    allNodes = nodes
    applyFilter()
  }

  private fun applyFilter() {
    val query = searchField.text.trim().lowercase()
    val filteredNodes =
        if (query.isBlank()) {
          allNodes
        } else {
          allNodes.filter { matchesQuery(it, query) }
        }

    listModel.clear()
    filteredNodes.forEach { listModel.addElement(it) }

    if (allNodes.isEmpty()) {
      emptyLabel.text = "未找到 Spring API 端点"
      showEmptyState()
      return
    }

    if (filteredNodes.isEmpty()) {
      emptyLabel.text = "没有匹配 \"$query\" 的结果"
      showEmptyState()
      return
    }

    showList()
  }

  private fun matchesQuery(
      node: IdeCallGraphNode,
      query: String,
  ): Boolean {
    val combined =
        listOfNotNull(
                node.className,
                node.name,
                node.signature,
            )
            .joinToString(" ")
            .lowercase()
    val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
    return tokens.all { combined.contains(it) }
  }

  private fun showEmptyState() {
    scrollPane.setViewportView(emptyLabel)
  }

  private fun showList() {
    scrollPane.setViewportView(list)
  }

  private fun showNotification(
      message: String,
      type: NotificationType,
  ) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Call Graph")
        .createNotification(message, type)
        .notify(project)
  }

  private inner class RefreshAction : AnAction("刷新", "重新扫描 Spring API", null) {
    override fun actionPerformed(e: AnActionEvent) {
      refreshData()
    }
  }

  private inner class ExportAction : AnAction("导出", "导出筛选的 Spring API 调用图到 JSONL", null) {
    override fun actionPerformed(e: AnActionEvent) {
      exportFilteredCallGraphs()
    }
  }

  private fun exportFilteredCallGraphs() {
    if (DumbService.isDumb(project)) {
      showNotification("索引尚未就绪，请等待索引完成。", NotificationType.WARNING)
      return
    }

    val nodes = getFilteredNodes()
    if (nodes.isEmpty()) {
      showNotification("没有可导出的 Spring API 端点。", NotificationType.WARNING)
      return
    }

    val descriptor = FileSaverDescriptor("导出 Spring API", "选择保存 JSONL 文件的位置", "jsonl")
    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val basePath = project.basePath?.let { Paths.get(it) }
    val fileWrapper = dialog.save(basePath, "spring-apis.jsonl") ?: return
    val outputFile = fileWrapper.file

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "正在导出 Spring API", true) {
              override fun run(indicator: ProgressIndicator) {
                var successCount = 0
                var failCount = 0

                BufferedWriter(FileWriter(outputFile)).use { writer ->
                  nodes.forEachIndexed { index, node ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / nodes.size
                    indicator.text2 = "${node.className ?: "未知"}.${node.name}"

                    val element =
                        ReadAction.compute<PsiElement?, Exception> { node.elementPointer.element }
                    if (element == null) {
                      failCount++
                      return@forEachIndexed
                    }

                    try {
                      val callGraph = service.buildCallGraph(element)
                      if (callGraph != null) {
                        writer.write(service.exportToJsonCompact(callGraph))
                        writer.newLine()
                        successCount++
                      } else {
                        failCount++
                      }
                    } catch (e: Exception) {
                      failCount++
                    }
                  }
                }

                ApplicationManager.getApplication().invokeLater {
                  val message = buildString {
                    append("已导出 $successCount 个 API 端点到 ${outputFile.name}")
                    if (failCount > 0) {
                      append("\n$failCount 个端点导出失败")
                    }
                  }
                  showNotification(
                      message,
                      if (failCount == 0) {
                        NotificationType.INFORMATION
                      } else {
                        NotificationType.WARNING
                      },
                  )
                }
              }
            },
        )
  }

  private fun getFilteredNodes(): List<IdeCallGraphNode> {
    val nodes = ArrayList<IdeCallGraphNode>(listModel.size)
    for (i in 0 until listModel.size) {
      nodes.add(listModel.getElementAt(i))
    }
    return nodes
  }

  private class SpringApiListRenderer : ColoredListCellRenderer<IdeCallGraphNode>() {
    override fun customizeCellRenderer(
        list: JList<out IdeCallGraphNode>,
        value: IdeCallGraphNode?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
      if (value == null) return
      append(CallGraphNodeText.format(value))

      val signature = value.signature
      if (signature.isNotBlank()) {
        append("  $signature", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
      }
    }
  }
}
