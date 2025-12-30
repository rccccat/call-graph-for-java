package com.github.rccccat.ideacallgraph.toolWindow

import com.github.rccccat.ideacallgraph.cache.CallGraphCacheManager
import com.github.rccccat.ideacallgraph.export.SpringApiScanner
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.ui.CallGraphNodeNavigator
import com.github.rccccat.ideacallgraph.ui.CallGraphNodeText
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
  private val emptyLabel = JLabel("Click Refresh to scan Spring API endpoints")
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
    // Wait for indexing to complete before auto-refreshing
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
    searchField.textEditor.emptyText.text = "Search mapping / class / method"
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
      showNotification(
          "Indexes are not ready yet. Please wait for indexing to complete.",
          NotificationType.WARNING,
      )
      return
    }

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "Scanning Spring APIs", true) {
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
                        showNotification(
                            "Error scanning Spring APIs: ${e.message}", NotificationType.ERROR)
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
      emptyLabel.text = "No Spring API endpoints found"
      showEmptyState()
      return
    }

    if (filteredNodes.isEmpty()) {
      emptyLabel.text = "No matches for \"$query\""
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

  private inner class RefreshAction : AnAction("Refresh", "Rescan Spring APIs", null) {
    override fun actionPerformed(e: AnActionEvent) {
      refreshData()
    }
  }

  private inner class ExportAction :
      AnAction("Export", "Export filtered Spring API call graphs to JSONL", null) {
    override fun actionPerformed(e: AnActionEvent) {
      exportFilteredCallGraphs()
    }
  }

  private fun exportFilteredCallGraphs() {
    if (DumbService.isDumb(project)) {
      showNotification(
          "Indexes are not ready yet. Please wait for indexing to complete.",
          NotificationType.WARNING,
      )
      return
    }

    val nodes = getFilteredNodes()
    if (nodes.isEmpty()) {
      showNotification("No Spring API endpoints to export.", NotificationType.WARNING)
      return
    }

    val descriptor =
        FileSaverDescriptor("Export Spring APIs", "Choose where to save the JSONL file", "jsonl")
    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val basePath = project.basePath?.let { Paths.get(it) }
    val fileWrapper = dialog.save(basePath, "spring-apis.jsonl") ?: return
    val outputFile = fileWrapper.file

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "Exporting Spring APIs", true) {
              override fun run(indicator: ProgressIndicator) {
                var successCount = 0
                var failCount = 0

                BufferedWriter(FileWriter(outputFile)).use { writer ->
                  nodes.forEachIndexed { index, node ->
                    indicator.checkCanceled()
                    indicator.fraction = index.toDouble() / nodes.size
                    indicator.text2 = "${node.className ?: "Unknown"}.${node.name}"

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
                    append("Exported $successCount API endpoints to ${outputFile.name}")
                    if (failCount > 0) {
                      append("\n$failCount endpoints failed to export")
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
