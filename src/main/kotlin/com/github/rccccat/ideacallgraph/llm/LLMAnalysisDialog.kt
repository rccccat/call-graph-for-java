package com.github.rccccat.ideacallgraph.llm

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog to display LLM analysis results
 */
class LLMAnalysisDialog(
    private val project: Project,
    private val analysisResult: String,
    private val callGraphName: String
) : DialogWrapper(project) {

    private lateinit var textArea: JBTextArea

    init {
        title = "LLM Analysis - $callGraphName"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        textArea = JBTextArea(analysisResult)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.font = JBUI.Fonts.create("JetBrains Mono", 12)
        
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(800, 600)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }

    override fun createActions() = arrayOf(okAction)

    override fun getOKAction() = super.getOKAction().apply {
        putValue(Action.NAME, "Close")
    }

    /**
     * Updates the content of the dialog
     */
    fun updateContent(newContent: String) {
        textArea.text = newContent
        textArea.caretPosition = 0
    }

    /**
     * Appends content to the existing text
     */
    fun appendContent(additionalContent: String) {
        textArea.text = textArea.text + "\n\n" + additionalContent
    }
}