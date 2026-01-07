package com.github.rccccat.callgraphjava.settings

import com.github.rccccat.callgraphjava.util.ExcludePatternMatcher
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
  private lateinit var springEnableFullScanCheckBox: JBCheckBox
  private lateinit var mybatisScanAllXmlCheckBox: JBCheckBox

  override fun getDisplayName(): String = "Call Graph"

  override fun createComponent(): JComponent {
    val appSettings = CallGraphAppSettings.getInstance()
    val projectSettings = CallGraphProjectSettings.getInstance(project)
    val projectState = projectSettings.state

    mainPanel = JPanel(GridBagLayout())
    val constraints = GridBagConstraints()

    constraints.gridx = 0
    constraints.gridy = 0
    constraints.gridwidth = 2
    constraints.fill = GridBagConstraints.HORIZONTAL
    constraints.insets = Insets(0, 0, 20, 0)
    mainPanel!!.add(JBLabel("<html><h3>调用图配置</h3></html>"), constraints)

    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(0, 0, 10, 0)
    useDefaultsCheckBox =
        JBCheckBox(
            "使用应用默认值（清除项目覆盖）",
            isUsingAppDefaults(projectState),
        )
    useDefaultsCheckBox.addActionListener { updateFieldsEnabled() }
    mainPanel!!.add(useDefaultsCheckBox, constraints)

    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(0, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>递归深度设置</b></html>"), constraints)

    constraints.gridy++
    constraints.gridwidth = 1
    constraints.insets = Insets(5, 20, 5, 10)
    mainPanel!!.add(JBLabel("项目代码最大深度:"), constraints)

    constraints.gridx = 1
    constraints.fill = GridBagConstraints.HORIZONTAL
    constraints.insets = Insets(5, 0, 5, 0)
    projectDepthField =
        JBTextField((projectState.projectMaxDepth ?: appSettings.projectMaxDepth).toString(), 5)
    mainPanel!!.add(projectDepthField, constraints)

    constraints.gridx = 0
    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 10)
    mainPanel!!.add(JBLabel("第三方库最大深度:"), constraints)

    constraints.gridx = 1
    constraints.insets = Insets(5, 0, 5, 0)
    thirdPartyDepthField =
        JBTextField(
            (projectState.thirdPartyMaxDepth ?: appSettings.thirdPartyMaxDepth).toString(),
            5,
        )
    mainPanel!!.add(thirdPartyDepthField, constraints)

    constraints.gridx = 0
    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>排除过滤（正则模式）</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    mainPanel!!.add(
        JBLabel("排除匹配这些模式的包/类/方法/签名（每行一个）:"),
        constraints,
    )

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

    constraints.gridy++
    constraints.gridwidth = 2
    constraints.weighty = 0.0
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>方法过滤选项</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    includeGettersSettersCheckBox =
        JBCheckBox(
            "包含 getter/setter 方法",
            projectState.includeGettersSetters ?: appSettings.includeGettersSetters,
        )
    mainPanel!!.add(includeGettersSettersCheckBox, constraints)

    constraints.gridy++
    includeToStringCheckBox =
        JBCheckBox(
            "包含 toString() 方法",
            projectState.includeToString ?: appSettings.includeToString,
        )
    mainPanel!!.add(includeToStringCheckBox, constraints)

    constraints.gridy++
    includeHashCodeEqualsCheckBox =
        JBCheckBox(
            "包含 hashCode()/equals() 方法",
            projectState.includeHashCodeEquals ?: appSettings.includeHashCodeEquals,
        )
    mainPanel!!.add(includeHashCodeEqualsCheckBox, constraints)

    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>接口实现解析</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    resolveInterfaceImplementationsCheckBox =
        JBCheckBox(
            "解析接口实现（Spring @Autowired/@Resource）",
            projectState.resolveInterfaceImplementations
                ?: appSettings.resolveInterfaceImplementations,
        )
    mainPanel!!.add(resolveInterfaceImplementationsCheckBox, constraints)

    constraints.gridy++

    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>Spring API 扫描</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    springEnableFullScanCheckBox =
        JBCheckBox(
            "启用完整 Spring API 扫描（较慢）",
            projectState.springEnableFullScan ?: appSettings.springEnableFullScan,
        )
    mainPanel!!.add(springEnableFullScanCheckBox, constraints)

    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(20, 0, 10, 0)
    mainPanel!!.add(JBLabel("<html><b>MyBatis 扫描</b></html>"), constraints)

    constraints.gridy++
    constraints.insets = Insets(5, 20, 5, 0)
    mybatisScanAllXmlCheckBox =
        JBCheckBox(
            "扫描所有 XML 文件查找 MyBatis 映射器（较慢）",
            projectState.mybatisScanAllXml ?: appSettings.mybatisScanAllXml,
        )
    mainPanel!!.add(mybatisScanAllXmlCheckBox, constraints)

    constraints.gridy++
    constraints.gridwidth = 2
    constraints.insets = Insets(30, 0, 0, 0)
    val helpText =
        """
        <html>
        <small>
        <b>提示:</b><br/>
        • 模式必须以 pkg:、class:、method: 或 sig: 开头（也可不加前缀）<br/>
        • 签名格式: com.example.Foo#bar(java.lang.String,int)<br/>
        • 更高的递归深度提供更完整的调用图，但可能影响性能<br/>
        • 方法过滤有助于减少调用图中的噪音<br/>
        • 接口解析对于 Spring 依赖注入模式特别有用<br/>
        • 使用应用默认值可清除项目特定的覆盖设置
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
        springEnableFullScanCheckBox.isSelected !=
            (projectState.springEnableFullScan ?: appSettings.springEnableFullScan) ||
        mybatisScanAllXmlCheckBox.isSelected !=
            (projectState.mybatisScanAllXml ?: appSettings.mybatisScanAllXml)
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
      settings.setSpringEnableFullScan(null)
      settings.setMybatisScanAllXml(null)
      return
    }

    val projectDepth = projectDepthField.text.toIntOrNull()
    val thirdPartyDepth = thirdPartyDepthField.text.toIntOrNull()

    if (projectDepth == null || projectDepth < 1 || projectDepth > 20) {
      Messages.showErrorDialog("项目深度必须在 1 到 20 之间", "配置无效")
      return
    }

    if (thirdPartyDepth == null || thirdPartyDepth < 0 || thirdPartyDepth > 10) {
      Messages.showErrorDialog("第三方库深度必须在 0 到 10 之间", "配置无效")
      return
    }

    settings.setProjectMaxDepth(projectDepth)
    settings.setThirdPartyMaxDepth(thirdPartyDepth)

    val patterns = excludePatternsArea.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    for (pattern in patterns) {
      val error = ExcludePatternMatcher.validatePattern(pattern)
      if (error != null) {
        Messages.showErrorDialog(
            "无效的排除模式: $pattern\n错误: $error",
            "配置无效",
        )
        return
      }
    }

    settings.setExcludePackagePatterns(patterns)
    settings.setIncludeGettersSetters(includeGettersSettersCheckBox.isSelected)
    settings.setIncludeToString(includeToStringCheckBox.isSelected)
    settings.setIncludeHashCodeEquals(includeHashCodeEqualsCheckBox.isSelected)
    settings.setResolveInterfaceImplementations(resolveInterfaceImplementationsCheckBox.isSelected)
    settings.setSpringEnableFullScan(springEnableFullScanCheckBox.isSelected)
    settings.setMybatisScanAllXml(mybatisScanAllXmlCheckBox.isSelected)
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
    springEnableFullScanCheckBox.isSelected =
        projectState.springEnableFullScan ?: appSettings.springEnableFullScan
    mybatisScanAllXmlCheckBox.isSelected =
        projectState.mybatisScanAllXml ?: appSettings.mybatisScanAllXml
    updateFieldsEnabled()
  }

  private fun isUsingAppDefaults(state: CallGraphProjectSettings.State): Boolean =
      state.projectMaxDepth == null &&
          state.thirdPartyMaxDepth == null &&
          state.excludePackagePatterns == null &&
          state.includeGettersSetters == null &&
          state.includeToString == null &&
          state.includeHashCodeEquals == null &&
          state.resolveInterfaceImplementations == null &&
          state.springEnableFullScan == null &&
          state.mybatisScanAllXml == null

  private fun updateFieldsEnabled() {
    val enabled = !useDefaultsCheckBox.isSelected
    projectDepthField.isEnabled = enabled
    thirdPartyDepthField.isEnabled = enabled
    excludePatternsArea.isEnabled = enabled
    includeGettersSettersCheckBox.isEnabled = enabled
    includeToStringCheckBox.isEnabled = enabled
    includeHashCodeEqualsCheckBox.isEnabled = enabled
    resolveInterfaceImplementationsCheckBox.isEnabled = enabled
    springEnableFullScanCheckBox.isEnabled = enabled
    mybatisScanAllXmlCheckBox.isEnabled = enabled
  }
}
