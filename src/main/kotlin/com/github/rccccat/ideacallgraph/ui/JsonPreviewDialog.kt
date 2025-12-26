package com.github.rccccat.ideacallgraph.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** Dialog for previewing and copying JSON export */
class JsonPreviewDialog(
    private val project: Project,
    private val jsonContent: String,
    private val methodName: String,
) : DialogWrapper(project) {
  init {
    title = "Call Graph JSON Export - $methodName"
    isModal = false
    setOKButtonText("Copy to Clipboard")
    setCancelButtonText("Close")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())

    // Create text area with JSON content
    val textArea = JBTextArea(jsonContent)
    textArea.isEditable = false
    textArea.font = textArea.font.deriveFont(12f)

    // Add scroll pane
    val scrollPane = JBScrollPane(textArea)
    scrollPane.preferredSize = Dimension(800, 600)

    // Add header label
    val headerLabel =
        JLabel(
            "<html><b>Call Graph JSON for: $methodName</b><br/>You can copy this JSON to clipboard or select parts of it.</html>")
    headerLabel.border = BorderFactory.createEmptyBorder(5, 5, 10, 5)

    panel.add(headerLabel, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)

    // Add stats panel
    val statsPanel = JPanel()
    val lines = jsonContent.lines().size
    val chars = jsonContent.length
    statsPanel.add(JLabel("Lines: $lines | Characters: $chars"))
    panel.add(statsPanel, BorderLayout.SOUTH)

    return panel
  }

  override fun doOKAction() {
    // Copy JSON to clipboard
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val selection = StringSelection(jsonContent)
    clipboard.setContents(selection, null)

    // Show notification
    com.intellij.notification.NotificationGroupManager.getInstance()
        .getNotificationGroup("Call Graph")
        .createNotification(
            "JSON copied to clipboard", com.intellij.notification.NotificationType.INFORMATION)
        .notify(project)

    super.doOKAction()
  }
}
