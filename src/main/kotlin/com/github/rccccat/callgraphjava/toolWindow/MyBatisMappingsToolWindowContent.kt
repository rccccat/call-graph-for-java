package com.github.rccccat.callgraphjava.toolWindow

import com.github.rccccat.callgraphjava.api.model.NodeType
import com.github.rccccat.callgraphjava.export.MyBatisMapperScanner
import com.github.rccccat.callgraphjava.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.github.rccccat.callgraphjava.service.CallGraphServiceImpl
import com.github.rccccat.callgraphjava.settings.CallGraphProjectSettings
import com.github.rccccat.callgraphjava.ui.CallGraphNodeNavigator
import com.github.rccccat.callgraphjava.ui.CallGraphNodeText
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
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
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JToolBar
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class MyBatisMappingsToolWindowContent(
    private val project: Project,
) : JBPanel<MyBatisMappingsToolWindowContent>(BorderLayout()) {
  private val listModel = javax.swing.DefaultListModel<IdeCallGraphNode>()
  private val list = JBList(listModel)
  private val emptyLabel = JLabel("点击刷新扫描 MyBatis 映射器")
  private val scrollPane = JBScrollPane(list)
  private val service = CallGraphServiceImpl.getInstance(project)
  private val myBatisAnalyzer: MyBatisAnalyzer = service.getMyBatisAnalyzer()
  private val searchField = SearchTextField()
  private var allNodes: List<IdeCallGraphNode> = emptyList()

  init {
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = MyBatisListRenderer()
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

    val actionGroup = DefaultActionGroup().apply { add(RefreshAction()) }
    val actionToolbar =
        ActionManager.getInstance().createActionToolbar("MyBatisToolWindow", actionGroup, true)
    actionToolbar.targetComponent = this

    toolbar.add(actionToolbar.component)
    toolbar.addSeparator()
    toolbar.add(searchField)
    return toolbar
  }

  private fun configureSearchField() {
    searchField.textEditor.emptyText.text = "搜索映射器 / SQL / 方法"
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
            object : Task.Backgroundable(project, "正在扫描 MyBatis 映射器", true) {
              override fun run(indicator: ProgressIndicator) {
                val settings = CallGraphProjectSettings.getInstance(project)
                val scanner = MyBatisMapperScanner(project, settings.mybatisScanAllXml)
                val nodes =
                    try {
                      val methods = scanner.scanAllMapperMethods(indicator)
                      val results = mutableListOf<IdeCallGraphNode>()

                      for (method in methods) {
                        indicator.checkCanceled()
                        val mybatisInfo = myBatisAnalyzer.analyzeMapperMethod(method)
                        if (!mybatisInfo.isMapperMethod) continue

                        if (mybatisInfo.sqlType != null) {
                          myBatisAnalyzer.createSqlNode(method, mybatisInfo)?.let {
                            results.add(it)
                          }
                        }
                      }

                      results.sortedWith(
                          compareBy({ nodeTypeOrder(it) }, { it.className ?: "" }, { it.name }))
                    } catch (e: Exception) {
                      ApplicationManager.getApplication().invokeLater {
                        showNotification("扫描 MyBatis 映射器出错: ${e.message}", NotificationType.ERROR)
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
      emptyLabel.text = "未找到 MyBatis 映射器"
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
                node.sqlStatement,
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

  private fun nodeTypeOrder(node: IdeCallGraphNode): Int =
      when (node.nodeType) {
        NodeType.MYBATIS_MAPPER_METHOD -> 0
        NodeType.MYBATIS_SQL_STATEMENT -> 1
        else -> 2
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

  private inner class RefreshAction : AnAction("刷新", "重新扫描 MyBatis 映射器", null) {
    override fun actionPerformed(e: AnActionEvent) {
      refreshData()
    }
  }

  private class MyBatisListRenderer : ColoredListCellRenderer<IdeCallGraphNode>() {
    override fun customizeCellRenderer(
        list: JList<out IdeCallGraphNode>,
        value: IdeCallGraphNode?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
      if (value == null) return
      append(CallGraphNodeText.format(value))

      val detail =
          when (value.nodeType) {
            NodeType.MYBATIS_SQL_STATEMENT -> {
              value.sqlStatement?.let { simplifySql(it) }
            }

            else -> {
              value.signature
            }
          }

      if (!detail.isNullOrBlank()) {
        append("  $detail", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
      }
    }

    private fun simplifySql(sql: String): String {
      val normalized = sql.replace(Regex("\\s+"), " ").trim()
      return if (normalized.length > 120) {
        normalized.substring(0, 117) + "..."
      } else {
        normalized
      }
    }
  }
}
