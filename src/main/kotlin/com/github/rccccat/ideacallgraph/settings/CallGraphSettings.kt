package com.github.rccccat.ideacallgraph.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

/**
 * Plugin configuration state
 */
@State(
    name = "CallGraphSettings",
    storages = [Storage("call-graph-plugin.xml")]
)
@Service(Service.Level.APP)
class CallGraphSettings : PersistentStateComponent<CallGraphSettings.State> {

    data class State(
        var projectMaxDepth: Int = 5,
        var thirdPartyMaxDepth: Int = 1,
        var excludePackagePatterns: MutableList<String> = mutableListOf(
            "java\\..*",
            "javax\\..*",
            "kotlin\\..*", 
            "kotlinx\\..*",
            "org\\.springframework\\..*",
            "org\\.apache\\..*"
        ),
        var includeGettersSetters: Boolean = false,
        var includeToString: Boolean = false,
        var includeHashCodeEquals: Boolean = false,
        var resolveInterfaceImplementations: Boolean = true,
        var traverseAllImplementations: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    // Getters
    val projectMaxDepth: Int get() = myState.projectMaxDepth
    val thirdPartyMaxDepth: Int get() = myState.thirdPartyMaxDepth  
    val excludePackagePatterns: List<String> get() = myState.excludePackagePatterns
    val includeGettersSetters: Boolean get() = myState.includeGettersSetters
    val includeToString: Boolean get() = myState.includeToString
    val includeHashCodeEquals: Boolean get() = myState.includeHashCodeEquals
    val resolveInterfaceImplementations: Boolean get() = myState.resolveInterfaceImplementations
    val traverseAllImplementations: Boolean get() = myState.traverseAllImplementations

    // Setters
    fun setProjectMaxDepth(depth: Int) {
        myState.projectMaxDepth = depth
    }

    fun setThirdPartyMaxDepth(depth: Int) {
        myState.thirdPartyMaxDepth = depth
    }

    fun setExcludePackagePatterns(patterns: List<String>) {
        myState.excludePackagePatterns = patterns.toMutableList()
    }

    fun setIncludeGettersSetters(include: Boolean) {
        myState.includeGettersSetters = include
    }

    fun setIncludeToString(include: Boolean) {
        myState.includeToString = include
    }

    fun setIncludeHashCodeEquals(include: Boolean) {
        myState.includeHashCodeEquals = include
    }

    fun setResolveInterfaceImplementations(resolve: Boolean) {
        myState.resolveInterfaceImplementations = resolve
    }

    fun setTraverseAllImplementations(traverse: Boolean) {
        myState.traverseAllImplementations = traverse
    }

    companion object {
        fun getInstance(): CallGraphSettings {
            return ApplicationManager.getApplication().getService(CallGraphSettings::class.java)
        }
    }
}