package com.github.rccccat.ideacallgraph.core.traversal

import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings

/** Interface for graph traversal strategies. */
interface GraphTraverser {
  /** Traverses the call graph starting from the root node. */
  fun traverse(
      rootNode: CallGraphNodeData,
      findCallTargets: (nodeId: String) -> List<TraversalTarget>,
      settings: CallGraphProjectSettings,
  ): CallGraphData
}

/** Target discovered during traversal. */
data class TraversalTarget(
    val node: CallGraphNodeData,
    val implementations: List<TraversalTarget>? = null,
)
