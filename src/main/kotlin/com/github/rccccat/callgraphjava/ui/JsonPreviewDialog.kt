package com.github.rccccat.callgraphjava.ui

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

class JsonPreviewDialog(
    private val project: Project,
    private val jsonContent: String,
    private val methodName: String,
) : DialogWrapper(project) {
  init {
    title = "调用图 JSON 导出 - $methodName"
    isModal = false
    setOKButtonText("复制到剪贴板")
    setCancelButtonText("关闭")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())

    val textArea = JBTextArea(jsonContent)
    textArea.isEditable = false
    textArea.font = textArea.font.deriveFont(12f)

    val scrollPane = JBScrollPane(textArea)
    scrollPane.preferredSize = Dimension(800, 600)

    val headerLabel =
        JLabel("<html><b>调用图 JSON: $methodName</b><br/>你可以将此 JSON 复制到剪贴板或选择其中部分内容。</html>")
    headerLabel.border = BorderFactory.createEmptyBorder(5, 5, 10, 5)

    panel.add(headerLabel, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)

    val statsPanel = JPanel()
    val lines = jsonContent.lines().size
    val chars = jsonContent.length
    statsPanel.add(JLabel("行数: $lines | 字符数: $chars"))
    panel.add(statsPanel, BorderLayout.SOUTH)

    return panel
  }

  override fun doOKAction() {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val selection = StringSelection(jsonContent)
    clipboard.setContents(selection, null)

    com.intellij.notification.NotificationGroupManager.getInstance()
        .getNotificationGroup("Call Graph")
        .createNotification("JSON 已复制到剪贴板", com.intellij.notification.NotificationType.INFORMATION)
        .notify(project)

    super.doOKAction()
  }
}
