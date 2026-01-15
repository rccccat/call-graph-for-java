package com.github.rccccat.callgraphjava.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker

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
  private val settingsModificationTracker = SimpleModificationTracker()

  val modificationTracker: ModificationTracker
    get() = settingsModificationTracker

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
    markModified()
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
    if (myState.projectMaxDepth == value) return
    myState.projectMaxDepth = value
    markModified()
  }

  fun setThirdPartyMaxDepth(value: Int?) {
    if (myState.thirdPartyMaxDepth == value) return
    myState.thirdPartyMaxDepth = value
    markModified()
  }

  fun setExcludePackagePatterns(patterns: List<String>?) {
    val newPatterns = patterns?.toMutableList()
    if (myState.excludePackagePatterns == newPatterns) return
    myState.excludePackagePatterns = newPatterns
    markModified()
  }

  fun setIncludeGettersSetters(value: Boolean?) {
    if (myState.includeGettersSetters == value) return
    myState.includeGettersSetters = value
    markModified()
  }

  fun setIncludeToString(value: Boolean?) {
    if (myState.includeToString == value) return
    myState.includeToString = value
    markModified()
  }

  fun setIncludeHashCodeEquals(value: Boolean?) {
    if (myState.includeHashCodeEquals == value) return
    myState.includeHashCodeEquals = value
    markModified()
  }

  fun setResolveInterfaceImplementations(value: Boolean?) {
    if (myState.resolveInterfaceImplementations == value) return
    myState.resolveInterfaceImplementations = value
    markModified()
  }

  fun setMybatisScanAllXml(value: Boolean?) {
    if (myState.mybatisScanAllXml == value) return
    myState.mybatisScanAllXml = value
    markModified()
  }

  fun setSpringEnableFullScan(value: Boolean?) {
    if (myState.springEnableFullScan == value) return
    myState.springEnableFullScan = value
    markModified()
  }

  private fun markModified() {
    settingsModificationTracker.incModificationCount()
  }

  companion object {
    fun getInstance(project: Project): CallGraphProjectSettings =
        project.getService(CallGraphProjectSettings::class.java)
  }
}
