package com.github.rccccat.callgraphjava.core.traversal

import com.github.rccccat.callgraphjava.api.model.CallGraphNodeData

/** Target discovered during traversal. */
data class TraversalTarget(
    val node: CallGraphNodeData,
)
