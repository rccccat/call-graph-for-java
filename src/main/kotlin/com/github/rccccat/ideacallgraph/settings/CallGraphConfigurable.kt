package com.github.rccccat.ideacallgraph.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Plugin settings configurable panel
 */
class CallGraphConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private lateinit var projectDepthField: JBTextField
    private lateinit var thirdPartyDepthField: JBTextField
    private lateinit var excludePatternsArea: JBTextArea
    private lateinit var includeGettersSettersCheckBox: JBCheckBox
    private lateinit var includeToStringCheckBox: JBCheckBox
    private lateinit var includeHashCodeEqualsCheckBox: JBCheckBox

    override fun getDisplayName(): String = "Call Graph"

    override fun createComponent(): JComponent {
        val settings = CallGraphSettings.getInstance()
        
        mainPanel = JPanel(GridBagLayout())
        val constraints = GridBagConstraints()

        // Title
        constraints.gridx = 0
        constraints.gridy = 0
        constraints.gridwidth = 2
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.insets = Insets(0, 0, 20, 0)
        mainPanel!!.add(JBLabel("<html><h3>Call Graph Configuration</h3></html>"), constraints)

        // Recursion Depth Section
        constraints.gridy++
        constraints.gridwidth = 2
        constraints.insets = Insets(0, 0, 10, 0)
        mainPanel!!.add(JBLabel("<html><b>Recursion Depth Settings</b></html>"), constraints)

        // Project Max Depth
        constraints.gridy++
        constraints.gridwidth = 1
        constraints.insets = Insets(5, 20, 5, 10)
        mainPanel!!.add(JBLabel("Project Code Max Depth:"), constraints)

        constraints.gridx = 1
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.insets = Insets(5, 0, 5, 0)
        projectDepthField = JBTextField(settings.projectMaxDepth.toString(), 5)
        mainPanel!!.add(projectDepthField, constraints)

        // Third Party Max Depth  
        constraints.gridx = 0
        constraints.gridy++
        constraints.insets = Insets(5, 20, 5, 10)
        mainPanel!!.add(JBLabel("Third-party Library Max Depth:"), constraints)

        constraints.gridx = 1
        constraints.insets = Insets(5, 0, 5, 0)
        thirdPartyDepthField = JBTextField(settings.thirdPartyMaxDepth.toString(), 5)
        mainPanel!!.add(thirdPartyDepthField, constraints)

        // Package Filter Section
        constraints.gridx = 0
        constraints.gridy++
        constraints.gridwidth = 2
        constraints.insets = Insets(20, 0, 10, 0)
        mainPanel!!.add(JBLabel("<html><b>Package Filtering (Regex Patterns)</b></html>"), constraints)

        constraints.gridy++
        constraints.insets = Insets(5, 20, 5, 0)
        mainPanel!!.add(JBLabel("Exclude packages matching these patterns (one per line):"), constraints)

        constraints.gridy++
        constraints.fill = GridBagConstraints.BOTH
        constraints.weightx = 1.0
        constraints.weighty = 0.3
        constraints.insets = Insets(5, 20, 10, 0)
        excludePatternsArea = JBTextArea(10, 50)
        excludePatternsArea.text = settings.excludePackagePatterns.joinToString("\n")
        val scrollPane = JBScrollPane(excludePatternsArea)
        mainPanel!!.add(scrollPane, constraints)

        // Method Filtering Section
        constraints.gridy++
        constraints.gridwidth = 2
        constraints.weighty = 0.0
        constraints.insets = Insets(20, 0, 10, 0)
        mainPanel!!.add(JBLabel("<html><b>Method Filtering Options</b></html>"), constraints)

        // Include Getters/Setters
        constraints.gridy++
        constraints.insets = Insets(5, 20, 5, 0)
        includeGettersSettersCheckBox = JBCheckBox("Include getter/setter methods", settings.includeGettersSetters)
        mainPanel!!.add(includeGettersSettersCheckBox, constraints)

        // Include toString
        constraints.gridy++
        includeToStringCheckBox = JBCheckBox("Include toString() methods", settings.includeToString)
        mainPanel!!.add(includeToStringCheckBox, constraints)

        // Include hashCode/equals
        constraints.gridy++
        includeHashCodeEqualsCheckBox = JBCheckBox("Include hashCode()/equals() methods", settings.includeHashCodeEquals)
        mainPanel!!.add(includeHashCodeEqualsCheckBox, constraints)

        // Help text
        constraints.gridy++
        constraints.gridwidth = 2
        constraints.insets = Insets(30, 0, 0, 0)
        val helpText = """
            <html>
            <small>
            <b>Tips:</b><br/>
            • Use Java regex patterns for package filtering (e.g., "java\\..*" matches all java packages)<br/>
            • Higher recursion depths provide more complete call graphs but may impact performance<br/>
            • Method filtering helps reduce noise in the call graph
            </small>
            </html>
        """.trimIndent()
        mainPanel!!.add(JBLabel(helpText), constraints)

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = CallGraphSettings.getInstance()
        
        return projectDepthField.text.toIntOrNull() != settings.projectMaxDepth ||
               thirdPartyDepthField.text.toIntOrNull() != settings.thirdPartyMaxDepth ||
               excludePatternsArea.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() } != settings.excludePackagePatterns ||
               includeGettersSettersCheckBox.isSelected != settings.includeGettersSetters ||
               includeToStringCheckBox.isSelected != settings.includeToString ||
               includeHashCodeEqualsCheckBox.isSelected != settings.includeHashCodeEquals
    }

    override fun apply() {
        val settings = CallGraphSettings.getInstance()
        
        // Validate and apply depth settings
        val projectDepth = projectDepthField.text.toIntOrNull()
        val thirdPartyDepth = thirdPartyDepthField.text.toIntOrNull()
        
        if (projectDepth == null || projectDepth < 1 || projectDepth > 20) {
            Messages.showErrorDialog("Project depth must be between 1 and 20", "Invalid Configuration")
            return
        }
        
        if (thirdPartyDepth == null || thirdPartyDepth < 0 || thirdPartyDepth > 10) {
            Messages.showErrorDialog("Third-party depth must be between 0 and 10", "Invalid Configuration")
            return
        }
        
        settings.setProjectMaxDepth(projectDepth)
        settings.setThirdPartyMaxDepth(thirdPartyDepth)
        
        // Apply package patterns
        val patterns = excludePatternsArea.text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // Validate regex patterns
        for (pattern in patterns) {
            try {
                Regex(pattern)
            } catch (e: Exception) {
                Messages.showErrorDialog("Invalid regex pattern: $pattern\nError: ${e.message}", "Invalid Configuration")
                return
            }
        }
        
        settings.setExcludePackagePatterns(patterns)
        settings.setIncludeGettersSetters(includeGettersSettersCheckBox.isSelected)
        settings.setIncludeToString(includeToStringCheckBox.isSelected)
        settings.setIncludeHashCodeEquals(includeHashCodeEqualsCheckBox.isSelected)
    }

    override fun reset() {
        val settings = CallGraphSettings.getInstance()
        
        projectDepthField.text = settings.projectMaxDepth.toString()
        thirdPartyDepthField.text = settings.thirdPartyMaxDepth.toString()
        excludePatternsArea.text = settings.excludePackagePatterns.joinToString("\n")
        includeGettersSettersCheckBox.isSelected = settings.includeGettersSetters
        includeToStringCheckBox.isSelected = settings.includeToString
        includeHashCodeEqualsCheckBox.isSelected = settings.includeHashCodeEquals
    }
}