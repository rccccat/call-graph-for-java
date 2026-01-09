package com.github.rccccat.callgraphjava.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

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

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
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

  fun setMybatisScanAllXml(value: Boolean) {
    myState.mybatisScanAllXml = value
  }

  fun setSpringEnableFullScan(value: Boolean) {
    myState.springEnableFullScan = value
  }

  companion object {
    fun getInstance(): CallGraphAppSettings =
        ApplicationManager.getApplication().getService(CallGraphAppSettings::class.java)
  }
}
