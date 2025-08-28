package com.github.rccccat.ideacallgraph.llm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

/**
 * Settings for LLM integration
 */
@Service(Service.Level.APP)
@State(name = "LLMSettings", storages = [Storage("CallGraphLLMSettings.xml")])
class LLMSettings : PersistentStateComponent<LLMSettings.State> {

    companion object {
        fun getInstance(): LLMSettings {
            return ApplicationManager.getApplication().getService(LLMSettings::class.java)
        }
        
        const val DEFAULT_SYSTEM_PROMPT = """You are a code analysis expert. I will provide you with a call graph in JSON format that represents method call relationships in a software project.

Please analyze the call graph and provide:

1. **Architecture Overview**: Describe the overall architecture patterns you observe
2. **Key Components**: Identify the main components and their roles
3. **Potential Issues**: Point out any potential problems like:
   - Circular dependencies
   - Deep call chains
   - Heavy coupling between components
   - Missing error handling paths
4. **Optimization Suggestions**: Recommend improvements for:
   - Performance optimization
   - Code organization
   - Maintainability
5. **Security Considerations**: Identify any potential security concerns in the call flow

Please provide your analysis in a clear, structured format with specific examples from the call graph data."""
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    data class State(
        var baseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-4",
        var systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        var enabled: Boolean = false,
        var timeout: Int = 30
    )

    // Getters for easy access
    val baseUrl: String get() = myState.baseUrl
    val apiKey: String get() = myState.apiKey
    val model: String get() = myState.model
    val systemPrompt: String get() = myState.systemPrompt
    val enabled: Boolean get() = myState.enabled
    val timeout: Int get() = myState.timeout

    // Setters
    fun setBaseUrl(url: String) {
        myState.baseUrl = url
    }

    fun setApiKey(key: String) {
        myState.apiKey = key
    }

    fun setModel(modelName: String) {
        myState.model = modelName
    }

    fun setSystemPrompt(prompt: String) {
        myState.systemPrompt = prompt
    }

    fun setEnabled(isEnabled: Boolean) {
        myState.enabled = isEnabled
    }

    fun setTimeout(timeoutSeconds: Int) {
        myState.timeout = timeoutSeconds
    }
}