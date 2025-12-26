package com.github.rccccat.ideacallgraph.ui.toolwindow

import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.intellij.openapi.project.Project

/** Configuration for tree view display. Replaces hardcoded values with configurable settings. */
data class TreeConfiguration(
    val maxDisplayDepth: Int,
    val maxChildrenPerNode: Int,
    val initialExpandDepth: Int,
) {
  companion object {
    /** Creates TreeConfiguration from project settings. */
    fun fromSettings(settings: CallGraphProjectSettings): TreeConfiguration =
        TreeConfiguration(
            maxDisplayDepth = settings.treeMaxDisplayDepth,
            maxChildrenPerNode = settings.treeMaxChildrenPerNode,
            initialExpandDepth = settings.treeInitialExpandDepth,
        )

    /** Creates TreeConfiguration from project. */
    fun fromProject(project: Project): TreeConfiguration =
        fromSettings(CallGraphProjectSettings.getInstance(project))
  }
}
