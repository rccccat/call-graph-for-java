package com.github.rccccat.ideacallgraph.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings - provides global default values. These defaults are used when
 * project-specific settings are not configured.
 */
@State(name = "CallGraphAppSettings", storages = [Storage("call-graph-app-settings.xml")])
@Service(Service.Level.APP)
class CallGraphAppSettings : PersistentStateComponent<CallGraphAppSettings.State> {

  data class State(
      var projectMaxDepth: Int = 5,
      var thirdPartyMaxDepth: Int = 1,
      var excludePackagePatterns: MutableList<String> =
          mutableListOf(
              "java\\..*",
              "javax\\..*",
              "kotlin\\..*",
              "kotlinx\\..*",
              "org\\.springframework\\..*",
              "org\\.apache\\..*",
          ),
      var includeGettersSetters: Boolean = false,
      var includeToString: Boolean = false,
      var includeHashCodeEquals: Boolean = false,
      var resolveInterfaceImplementations: Boolean = true,
      var traverseAllImplementations: Boolean = false,
      var mybatisScanAllXml: Boolean = false,
      // UI defaults
      var treeMaxDisplayDepth: Int = 5,
      var treeMaxChildrenPerNode: Int = 20,
      var treeInitialExpandDepth: Int = 2,
  )

  private var myState = State()

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  // Getters
  val projectMaxDepth: Int
    get() = myState.projectMaxDepth

  val thirdPartyMaxDepth: Int
    get() = myState.thirdPartyMaxDepth

  val excludePackagePatterns: List<String>
    get() = myState.excludePackagePatterns

  val includeGettersSetters: Boolean
    get() = myState.includeGettersSetters

  val includeToString: Boolean
    get() = myState.includeToString

  val includeHashCodeEquals: Boolean
    get() = myState.includeHashCodeEquals

  val resolveInterfaceImplementations: Boolean
    get() = myState.resolveInterfaceImplementations

  val traverseAllImplementations: Boolean
    get() = myState.traverseAllImplementations

  val mybatisScanAllXml: Boolean
    get() = myState.mybatisScanAllXml

  val treeMaxDisplayDepth: Int
    get() = myState.treeMaxDisplayDepth

  val treeMaxChildrenPerNode: Int
    get() = myState.treeMaxChildrenPerNode

  val treeInitialExpandDepth: Int
    get() = myState.treeInitialExpandDepth

  // Setters
  fun setProjectMaxDepth(value: Int) {
    myState.projectMaxDepth = value
  }

  fun setThirdPartyMaxDepth(value: Int) {
    myState.thirdPartyMaxDepth = value
  }

  fun setExcludePackagePatterns(patterns: List<String>) {
    myState.excludePackagePatterns = patterns.toMutableList()
  }

  fun setIncludeGettersSetters(value: Boolean) {
    myState.includeGettersSetters = value
  }

  fun setIncludeToString(value: Boolean) {
    myState.includeToString = value
  }

  fun setIncludeHashCodeEquals(value: Boolean) {
    myState.includeHashCodeEquals = value
  }

  fun setResolveInterfaceImplementations(value: Boolean) {
    myState.resolveInterfaceImplementations = value
  }

  fun setTraverseAllImplementations(value: Boolean) {
    myState.traverseAllImplementations = value
  }

  fun setMybatisScanAllXml(value: Boolean) {
    myState.mybatisScanAllXml = value
  }

  companion object {
    fun getInstance(): CallGraphAppSettings =
        ApplicationManager.getApplication().getService(CallGraphAppSettings::class.java)
  }
}
