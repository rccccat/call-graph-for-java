package com.github.rccccat.callgraphjava.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/** application-level default is used. */
@State(
    name = "CallGraphProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
@Service(Service.Level.PROJECT)
class CallGraphProjectSettings : PersistentStateComponent<CallGraphProjectSettings.State> {
  data class State(
      var projectMaxDepth: Int? = null,
      var thirdPartyMaxDepth: Int? = null,
      var excludePackagePatterns: MutableList<String>? = null,
      var includeGettersSetters: Boolean? = null,
      var includeToString: Boolean? = null,
      var includeHashCodeEquals: Boolean? = null,
      var resolveInterfaceImplementations: Boolean? = null,
      var mybatisScanAllXml: Boolean? = null,
      var springEnableFullScan: Boolean? = null,
      var treeMaxDisplayDepth: Int? = null,
      var treeMaxChildrenPerNode: Int? = null,
      var treeInitialExpandDepth: Int? = null,
  )

  private var myState = State()

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  val projectMaxDepth: Int
    get() = myState.projectMaxDepth ?: CallGraphAppSettings.getInstance().projectMaxDepth

  val thirdPartyMaxDepth: Int
    get() = myState.thirdPartyMaxDepth ?: CallGraphAppSettings.getInstance().thirdPartyMaxDepth

  val excludePackagePatterns: List<String>
    get() =
        myState.excludePackagePatterns ?: CallGraphAppSettings.getInstance().excludePackagePatterns

  val includeGettersSetters: Boolean
    get() =
        myState.includeGettersSetters ?: CallGraphAppSettings.getInstance().includeGettersSetters

  val includeToString: Boolean
    get() = myState.includeToString ?: CallGraphAppSettings.getInstance().includeToString

  val includeHashCodeEquals: Boolean
    get() =
        myState.includeHashCodeEquals ?: CallGraphAppSettings.getInstance().includeHashCodeEquals

  val resolveInterfaceImplementations: Boolean
    get() =
        myState.resolveInterfaceImplementations
            ?: CallGraphAppSettings.getInstance().resolveInterfaceImplementations

  val mybatisScanAllXml: Boolean
    get() = myState.mybatisScanAllXml ?: CallGraphAppSettings.getInstance().mybatisScanAllXml

  val springEnableFullScan: Boolean
    get() = myState.springEnableFullScan ?: CallGraphAppSettings.getInstance().springEnableFullScan

  val treeMaxDisplayDepth: Int
    get() = myState.treeMaxDisplayDepth ?: CallGraphAppSettings.getInstance().treeMaxDisplayDepth

  val treeMaxChildrenPerNode: Int
    get() =
        myState.treeMaxChildrenPerNode ?: CallGraphAppSettings.getInstance().treeMaxChildrenPerNode

  val treeInitialExpandDepth: Int
    get() =
        myState.treeInitialExpandDepth ?: CallGraphAppSettings.getInstance().treeInitialExpandDepth

  fun setProjectMaxDepth(value: Int?) {
    myState.projectMaxDepth = value
  }

  fun setThirdPartyMaxDepth(value: Int?) {
    myState.thirdPartyMaxDepth = value
  }

  fun setExcludePackagePatterns(patterns: List<String>?) {
    myState.excludePackagePatterns = patterns?.toMutableList()
  }

  fun setIncludeGettersSetters(value: Boolean?) {
    myState.includeGettersSetters = value
  }

  fun setIncludeToString(value: Boolean?) {
    myState.includeToString = value
  }

  fun setIncludeHashCodeEquals(value: Boolean?) {
    myState.includeHashCodeEquals = value
  }

  fun setResolveInterfaceImplementations(value: Boolean?) {
    myState.resolveInterfaceImplementations = value
  }

  fun setMybatisScanAllXml(value: Boolean?) {
    myState.mybatisScanAllXml = value
  }

  fun setSpringEnableFullScan(value: Boolean?) {
    myState.springEnableFullScan = value
  }

  companion object {
    fun getInstance(project: Project): CallGraphProjectSettings =
        project.getService(CallGraphProjectSettings::class.java)
  }
}
