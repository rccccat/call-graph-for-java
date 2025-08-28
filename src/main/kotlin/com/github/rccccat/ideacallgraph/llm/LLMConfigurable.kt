package com.github.rccccat.ideacallgraph.llm

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

/**
 * Configuration panel for LLM settings
 */
class LLMConfigurable : Configurable {

    private val settings = LLMSettings.getInstance()
    
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var baseUrlField: JBTextField
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var modelField: JBTextField
    private lateinit var systemPromptArea: JBTextArea
    private lateinit var timeoutField: JBTextField

    override fun getDisplayName(): String = "LLM Analysis"

    override fun createComponent(): JComponent {
        return panel {
            group("LLM Configuration") {
                row {
                    enabledCheckBox = checkBox("Enable LLM Analysis")
                        .bindSelected(settings::enabled, settings::setEnabled)
                        .component
                }
                
                separator()
                
                row("Base URL:") {
                    baseUrlField = textField()
                        .bindText(settings::baseUrl, settings::setBaseUrl)
                        .columns(50)
                        .comment("API endpoint URL (e.g., https://api.openai.com/v1 or custom endpoint)")
                        .component
                }
                
                row("API Key:") {
                    apiKeyField = JBPasswordField()
                    cell(apiKeyField)
                        .columns(50)
                        .comment("Your API key for the LLM service")
                }
                
                row("Model:") {
                    modelField = textField()
                        .bindText(settings::model, settings::setModel)
                        .columns(30)
                        .comment("Model name (e.g., gpt-4, gpt-3.5-turbo)")
                        .component
                }
                
                row("Timeout (seconds):") {
                    timeoutField = textField()
                        .columns(10)
                        .comment("Request timeout in seconds")
                        .component
                }
            }
            
            group("System Prompt") {
                row {
                    systemPromptArea = textArea()
                        .bindText(settings::systemPrompt, settings::setSystemPrompt)
                        .rows(15)
                        .columns(80)
                        .comment("Customize the system prompt to guide the LLM analysis")
                        .component
                }
                
                row {
                    button("Reset to Default") {
                        systemPromptArea.text = LLMSettings.DEFAULT_SYSTEM_PROMPT
                    }
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return enabledCheckBox.isSelected != settings.enabled ||
                baseUrlField.text != settings.baseUrl ||
                String(apiKeyField.password) != settings.apiKey ||
                modelField.text != settings.model ||
                systemPromptArea.text != settings.systemPrompt ||
                timeoutField.text != settings.timeout.toString()
    }

    override fun apply() {
        settings.setEnabled(enabledCheckBox.isSelected)
        settings.setBaseUrl(baseUrlField.text.trim())
        settings.setApiKey(String(apiKeyField.password))
        settings.setModel(modelField.text.trim())
        settings.setSystemPrompt(systemPromptArea.text.trim())
        
        try {
            val timeout = timeoutField.text.toIntOrNull() ?: 30
            settings.setTimeout(timeout.coerceIn(5, 300)) // 5-300 seconds
        } catch (e: NumberFormatException) {
            settings.setTimeout(30)
        }
    }

    override fun reset() {
        enabledCheckBox.isSelected = settings.enabled
        baseUrlField.text = settings.baseUrl
        apiKeyField.text = settings.apiKey
        modelField.text = settings.model
        systemPromptArea.text = settings.systemPrompt
        timeoutField.text = settings.timeout.toString()
    }
}