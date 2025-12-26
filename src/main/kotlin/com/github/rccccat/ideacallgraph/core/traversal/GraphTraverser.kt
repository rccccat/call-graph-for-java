package com.github.rccccat.ideacallgraph.core.traversal

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData

/** Target discovered during traversal. */
data class TraversalTarget(
    val node: CallGraphNodeData,
)
