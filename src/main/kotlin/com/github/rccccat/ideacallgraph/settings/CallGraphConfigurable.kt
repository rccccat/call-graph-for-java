package com.github.rccccat.ideacallgraph.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

/** Plugin settings configurable panel */
class CallGraphConfigurable(
    private val project: Project,
) : Configurable {
  private var mainPanel: JPanel? = null
  private lateinit var useDefaultsCheckBox: JBCheckBox
  private lateinit var projectDepthField: JBTextField
  private lateinit var thirdPartyDepthField: JBTextField
  private lateinit var excludePatternsArea: JBTextArea
  private lateinit var includeGettersSettersCheckBox: JBCheckBox
  private lateinit var includeToStringCheckBox: JBCheckBox
  private lateinit var includeHashCodeEqualsCheckBox: JBCheckBox
  private lateinit var resolveInterfaceImplementationsCheckBox: JBCheckBox
  private lateinit var mybatisScanAllXmlCheckBox: JBCheckBox
  private lateinit var filterByParameterUsageCheckBox: JBCheckBox

  override fun getDisplayName(): String = "Call Graph"

  override fun createComponent(): JComponent {
    val appSettings = CallGraphAppSettings.getInstance()
    val projectSettings = CallGraphProjectSettings.getInstance(project)
    val projectState = projectSettings.state

    mainPanel = JPanel(GridBagLayout())
    val constraints = GridBagConstraints()

    // Title
    constraints.gridx = 0
    constraints.gridy = 0
    constraints.gridwidth = 2
    constraints.fill = GridBagConstraints.HORIZONTAL
    constraints.insets = Insets(0, 0, 20, 0)
    mainPanel!!.add(JBLabel("<html><h3>Call graph configuration</h3></html>"), constraints)

    // Defaults
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(0, 0, 10, 0)
    useDefaultsCheckBox =
        JBCheckBox(
            "Use application defaults (clear project overrides)",
            isUsingAppDefaults(projectState),
        )
    useDefaultsCheckBox.addActionListener { updateFieldsEnabled() }
    mainPanel!!.add(useDefaultsCheckBox, constraints)

    // Recursion Depth Section
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(0, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>Recursion depth settings</b></html>"), constraints)

    // Project Max Depth
    constraints.gridy++
    constraints.gridwidth = 1
    constraints.insets = Insets(5, 20, 5, 10)
    mainPanel!!.add(JBLabel("Project code max depth:"), constraints)

    constraints.gridx = 1
    constraints.fill = GridBagConstraints.HORIZONTAL
    constraints.insets = Insets(5, 0, 5, 0)
    projectDepthField =
        JBTextField((projectState.projectMaxDepth ?: appSettings.projectMaxDepth).toString(), 5)
    mainPanel!!.add(projectDepthField, constraints)

    // Third Party Max Depth
    constraints.gridx = 0
    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 10)
    mainPanel!!.add(JBLabel("Third-party library max depth:"), constraints)

    constraints.gridx = 1
    constraints.insets = Insets(5, 0, 5, 0)
    thirdPartyDepthField =
        JBTextField(
            (projectState.thirdPartyMaxDepth ?: appSettings.thirdPartyMaxDepth).toString(),
            5,
        )
    mainPanel!!.add(thirdPartyDepthField, constraints)

    // Package Filter Section
    constraints.gridx = 0
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>Package filtering (regex patterns)</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    mainPanel!!.add(
        JBLabel("Exclude packages matching these patterns (one per line):"), constraints)

    constraints.gridy++
    constraints.fill = GridBagConstraints.BOTH
    constraints.weightx = 1.0
    constraints.weighty = 0.3
    constraints.insets = Insets(5, 20, 10, 0)
    excludePatternsArea = JBTextArea(10, 50)
    excludePatternsArea.text =
        (projectState.excludePackagePatterns ?: appSettings.excludePackagePatterns).joinToString(
            "\n")
    val scrollPane = JBScrollPane(excludePatternsArea)
    mainPanel!!.add(scrollPane, constraints)

    // Method Filtering Section
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.weighty = 0.0
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>Method filtering options</b></html>"), constraints)

    // Include Getters/Setters
    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    includeGettersSettersCheckBox =
        JBCheckBox(
            "Include getter/setter methods",
            projectState.includeGettersSetters ?: appSettings.includeGettersSetters,
        )
    mainPanel!!.add(includeGettersSettersCheckBox, constraints)

    // Include toString
    constraints.gridy++
    includeToStringCheckBox =
        JBCheckBox(
            "Include toString() methods",
            projectState.includeToString ?: appSettings.includeToString,
        )
    mainPanel!!.add(includeToStringCheckBox, constraints)

    // Include hashCode/equals
    constraints.gridy++
    includeHashCodeEqualsCheckBox =
        JBCheckBox(
            "Include hashCode()/equals() methods",
            projectState.includeHashCodeEquals ?: appSettings.includeHashCodeEquals,
        )
    mainPanel!!.add(includeHashCodeEqualsCheckBox, constraints)

    // Interface Resolution Section
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>Interface implementation resolution</b></html>"), constraints)

    // Resolve Interface Implementations
    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    resolveInterfaceImplementationsCheckBox =
        JBCheckBox(
            "Resolve interface implementations (Spring @Autowired/@Resource)",
            projectState.resolveInterfaceImplementations
                ?: appSettings.resolveInterfaceImplementations,
        )
    mainPanel!!.add(resolveInterfaceImplementationsCheckBox, constraints)

    // MyBatis Section
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>MyBatis scanning</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    mybatisScanAllXmlCheckBox =
        JBCheckBox(
            "Scan all XML files for MyBatis mappers (slower)",
            projectState.mybatisScanAllXml ?: appSettings.mybatisScanAllXml,
        )
    mainPanel!!.add(mybatisScanAllXmlCheckBox, constraints)

    // Advanced Filtering Section
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>Advanced filtering (experimental)</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    filterByParameterUsageCheckBox =
        JBCheckBox(
            "Filter calls where parameters are unused (slower, uses data flow analysis)",
            projectState.filterByParameterUsage ?: appSettings.filterByParameterUsage,
        )
    mainPanel!!.add(filterByParameterUsageCheckBox, constraints)

    // Help text
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(30, 0, 0, 0)
    val helpText =
        """
        <html>
        <small>
        <b>Tips:</b><br/>
        • Use Java regex patterns for package filtering (e.g., "java\\..*" matches all java packages)<br/>
        • Higher recursion depths provide more complete call graphs but may impact performance<br/>
        • Method filtering helps reduce noise in the call graph<br/>
        • Interface resolution is especially useful for Spring dependency injection patterns<br/>
        • Use application defaults to clear project-specific overrides
        </small>
        </html>
        """
            .trimIndent()
    mainPanel!!.add(JBLabel(helpText), constraints)

    updateFieldsEnabled()
    return mainPanel!!
  }

  override fun isModified(): Boolean {
    val appSettings = CallGraphAppSettings.getInstance()
    val projectSettings = CallGraphProjectSettings.getInstance(project)
    val projectState = projectSettings.state

    val usingDefaults = isUsingAppDefaults(projectState)
    if (useDefaultsCheckBox.isSelected != usingDefaults) {
      return true
    }
    if (useDefaultsCheckBox.isSelected) {
      return false
    }

    return projectDepthField.text.toIntOrNull() !=
        (projectState.projectMaxDepth ?: appSettings.projectMaxDepth) ||
        thirdPartyDepthField.text.toIntOrNull() !=
            (projectState.thirdPartyMaxDepth ?: appSettings.thirdPartyMaxDepth) ||
        excludePatternsArea.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() } !=
            (projectState.excludePackagePatterns ?: appSettings.excludePackagePatterns) ||
        includeGettersSettersCheckBox.isSelected !=
            (projectState.includeGettersSetters ?: appSettings.includeGettersSetters) ||
        includeToStringCheckBox.isSelected !=
            (projectState.includeToString ?: appSettings.includeToString) ||
        includeHashCodeEqualsCheckBox.isSelected !=
            (projectState.includeHashCodeEquals ?: appSettings.includeHashCodeEquals) ||
        resolveInterfaceImplementationsCheckBox.isSelected !=
            (projectState.resolveInterfaceImplementations
                ?: appSettings.resolveInterfaceImplementations) ||
        mybatisScanAllXmlCheckBox.isSelected !=
            (projectState.mybatisScanAllXml ?: appSettings.mybatisScanAllXml) ||
        filterByParameterUsageCheckBox.isSelected !=
            (projectState.filterByParameterUsage ?: appSettings.filterByParameterUsage)
  }

  override fun apply() {
    val settings = CallGraphProjectSettings.getInstance(project)

    if (useDefaultsCheckBox.isSelected) {
      settings.setProjectMaxDepth(null)
      settings.setThirdPartyMaxDepth(null)
      settings.setExcludePackagePatterns(null)
      settings.setIncludeGettersSetters(null)
      settings.setIncludeToString(null)
      settings.setIncludeHashCodeEquals(null)
      settings.setResolveInterfaceImplementations(null)
      settings.setMybatisScanAllXml(null)
      settings.setFilterByParameterUsage(null)
      return
    }

    // Validate and apply depth settings
    val projectDepth = projectDepthField.text.toIntOrNull()
    val thirdPartyDepth = thirdPartyDepthField.text.toIntOrNull()

    if (projectDepth == null || projectDepth < 1 || projectDepth > 20) {
      Messages.showErrorDialog("Project depth must be between 1 and 20", "Invalid Configuration")
      return
    }

    if (thirdPartyDepth == null || thirdPartyDepth < 0 || thirdPartyDepth > 10) {
      Messages.showErrorDialog(
          "Third-party depth must be between 0 and 10", "Invalid Configuration")
      return
    }

    settings.setProjectMaxDepth(projectDepth)
    settings.setThirdPartyMaxDepth(thirdPartyDepth)

    // Apply package patterns
    val patterns = excludePatternsArea.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    // Validate regex patterns
    for (pattern in patterns) {
      try {
        Regex(pattern)
      } catch (e: Exception) {
        Messages.showErrorDialog(
            "Invalid regex pattern: $pattern\nError: ${e.message}", "Invalid Configuration")
        return
      }
    }

    settings.setExcludePackagePatterns(patterns)
    settings.setIncludeGettersSetters(includeGettersSettersCheckBox.isSelected)
    settings.setIncludeToString(includeToStringCheckBox.isSelected)
    settings.setIncludeHashCodeEquals(includeHashCodeEqualsCheckBox.isSelected)
    settings.setResolveInterfaceImplementations(resolveInterfaceImplementationsCheckBox.isSelected)
    settings.setMybatisScanAllXml(mybatisScanAllXmlCheckBox.isSelected)
    settings.setFilterByParameterUsage(filterByParameterUsageCheckBox.isSelected)
  }

  override fun reset() {
    val appSettings = CallGraphAppSettings.getInstance()
    val projectSettings = CallGraphProjectSettings.getInstance(project)
    val projectState = projectSettings.state

    useDefaultsCheckBox.isSelected = isUsingAppDefaults(projectState)
    projectDepthField.text =
        (projectState.projectMaxDepth ?: appSettings.projectMaxDepth).toString()
    thirdPartyDepthField.text =
        (projectState.thirdPartyMaxDepth ?: appSettings.thirdPartyMaxDepth).toString()
    excludePatternsArea.text =
        (projectState.excludePackagePatterns ?: appSettings.excludePackagePatterns).joinToString(
            "\n")
    includeGettersSettersCheckBox.isSelected =
        projectState.includeGettersSetters ?: appSettings.includeGettersSetters
    includeToStringCheckBox.isSelected = projectState.includeToString ?: appSettings.includeToString
    includeHashCodeEqualsCheckBox.isSelected =
        projectState.includeHashCodeEquals ?: appSettings.includeHashCodeEquals
    resolveInterfaceImplementationsCheckBox.isSelected =
        projectState.resolveInterfaceImplementations ?: appSettings.resolveInterfaceImplementations
    mybatisScanAllXmlCheckBox.isSelected =
        projectState.mybatisScanAllXml ?: appSettings.mybatisScanAllXml
    filterByParameterUsageCheckBox.isSelected =
        projectState.filterByParameterUsage ?: appSettings.filterByParameterUsage
    updateFieldsEnabled()
  }

  private fun isUsingAppDefaults(state: CallGraphProjectSettings.State): Boolean {
    return state.projectMaxDepth == null &&
        state.thirdPartyMaxDepth == null &&
        state.excludePackagePatterns == null &&
        state.includeGettersSetters == null &&
        state.includeToString == null &&
        state.includeHashCodeEquals == null &&
        state.resolveInterfaceImplementations == null &&
        state.mybatisScanAllXml == null &&
        state.filterByParameterUsage == null
  }

  private fun updateFieldsEnabled() {
    val enabled = !useDefaultsCheckBox.isSelected
    projectDepthField.isEnabled = enabled
    thirdPartyDepthField.isEnabled = enabled
    excludePatternsArea.isEnabled = enabled
    includeGettersSettersCheckBox.isEnabled = enabled
    includeToStringCheckBox.isEnabled = enabled
    includeHashCodeEqualsCheckBox.isEnabled = enabled
    resolveInterfaceImplementationsCheckBox.isEnabled = enabled
    mybatisScanAllXmlCheckBox.isEnabled = enabled
    filterByParameterUsageCheckBox.isEnabled = enabled
  }
}
