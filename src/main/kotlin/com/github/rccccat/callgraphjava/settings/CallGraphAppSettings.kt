package com.github.rccccat.callgraphjava.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker

/** project-specific settings are not configured. */
@State(name = "CallGraphAppSettings", storages = [Storage("call-graph-app-settings.xml")])
@Service(Service.Level.APP)
class CallGraphAppSettings : PersistentStateComponent<CallGraphAppSettings.State> {
  data class State(
      var projectMaxDepth: Int = 5,
      var thirdPartyMaxDepth: Int = 1,
      var excludePackagePatterns: MutableList<String> =
          mutableListOf(
              """pkg:java\..*""",
              """pkg:javax\..*""",
              """pkg:kotlin\..*""",
              """pkg:kotlinx\..*""",
              """pkg:org\.springframework\..*""",
              """pkg:org\.apache\..*""",
              """pkg:org\.slf4j\..*""",
              """pkg:ch\.qos\.logback\..*""",
              """pkg:com\.google\.common\..*""",
              """pkg:com\.fasterxml\..*""",
              """pkg:.*exception.*""",
              """pkg:com\.kuaishou\.framework\.util""",
              """method:(toString|hashCode|equals|clone|finalize|getClass|notify|notifyAll|wait)""",
              """method:(log|debug|info|error|warn|trace|fatal|perf)""",
              """method:(printStackTrace|print|printf|println)""",
              """method:(nanoTime|currentTimeMillis|elapsedTime|startTimer|stopTimer)""",
              """method:(size|isEmpty|iterator|hasNext|next|stream|parallelStream)""",
              """method:(length|charAt|substring|indexOf|trim|split|concat|replace)""",
              """method:(build|builder|newBuilder)""",
              """method:(toBuilder|mergeFrom|parseFrom|writeTo|getDefaultInstance)""",
              """class:.*Builder""",
              """method:(assert.*|validate)""",
              """method:(close|dispose|destroy|shutdown|cleanup)""",
          ),
      var includeGettersSetters: Boolean = false,
      var includeToString: Boolean = false,
      var includeHashCodeEquals: Boolean = false,
      var resolveInterfaceImplementations: Boolean = true,
      var mybatisScanAllXml: Boolean = false,
      var springEnableFullScan: Boolean = false,
      var treeMaxDisplayDepth: Int = 5,
      var treeMaxChildrenPerNode: Int = 20,
      var treeInitialExpandDepth: Int = 2,
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

  val mybatisScanAllXml: Boolean
    get() = myState.mybatisScanAllXml

  val springEnableFullScan: Boolean
    get() = myState.springEnableFullScan

  val treeMaxDisplayDepth: Int
    get() = myState.treeMaxDisplayDepth

  val treeMaxChildrenPerNode: Int
    get() = myState.treeMaxChildrenPerNode

  val treeInitialExpandDepth: Int
    get() = myState.treeInitialExpandDepth

  fun setProjectMaxDepth(value: Int) {
    if (myState.projectMaxDepth == value) return
    myState.projectMaxDepth = value
    markModified()
  }

  fun setThirdPartyMaxDepth(value: Int) {
    if (myState.thirdPartyMaxDepth == value) return
    myState.thirdPartyMaxDepth = value
    markModified()
  }

  fun setExcludePackagePatterns(patterns: List<String>) {
    val newPatterns = patterns.toMutableList()
    if (myState.excludePackagePatterns == newPatterns) return
    myState.excludePackagePatterns = newPatterns
    markModified()
  }

  fun setIncludeGettersSetters(value: Boolean) {
    if (myState.includeGettersSetters == value) return
    myState.includeGettersSetters = value
    markModified()
  }

  fun setIncludeToString(value: Boolean) {
    if (myState.includeToString == value) return
    myState.includeToString = value
    markModified()
  }

  fun setIncludeHashCodeEquals(value: Boolean) {
    if (myState.includeHashCodeEquals == value) return
    myState.includeHashCodeEquals = value
    markModified()
  }

  fun setResolveInterfaceImplementations(value: Boolean) {
    if (myState.resolveInterfaceImplementations == value) return
    myState.resolveInterfaceImplementations = value
    markModified()
  }

  fun setMybatisScanAllXml(value: Boolean) {
    if (myState.mybatisScanAllXml == value) return
    myState.mybatisScanAllXml = value
    markModified()
  }

  fun setSpringEnableFullScan(value: Boolean) {
    if (myState.springEnableFullScan == value) return
    myState.springEnableFullScan = value
    markModified()
  }

  private fun markModified() {
    settingsModificationTracker.incModificationCount()
  }

  companion object {
    fun getInstance(): CallGraphAppSettings =
        ApplicationManager.getApplication().getService(CallGraphAppSettings::class.java)
  }
}
