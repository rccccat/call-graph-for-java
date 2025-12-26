package com.github.rccccat.ideacallgraph.core.traversal

import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.api.model.CallGraphEdgeData
import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.intellij.openapi.progress.ProgressManager

/** Depth-first traversal implementation for call graph building. */
class DepthFirstTraverser : GraphTraverser {

  override fun traverse(
      rootNode: CallGraphNodeData,
      findCallTargets: (nodeId: String) -> List<TraversalTarget>,
      settings: CallGraphProjectSettings,
  ): CallGraphData {
    val nodes = mutableMapOf(rootNode.id to rootNode)
    val edges = mutableListOf<CallGraphEdgeData>()
    val visitedDepth = mutableMapOf<String, Int>()

    traverseRecursive(
        currentNode = rootNode,
        nodes = nodes,
        edges = edges,
        visitedDepth = visitedDepth,
        remainingDepth = settings.projectMaxDepth,
        isProjectCode = true,
        findCallTargets = findCallTargets,
        settings = settings,
    )

    return CallGraphData(
        rootNodeId = rootNode.id,
        nodes = nodes.toMap(),
        edges = edges.toList(),
    )
  }

  private fun traverseRecursive(
      currentNode: CallGraphNodeData,
      nodes: MutableMap<String, CallGraphNodeData>,
      edges: MutableList<CallGraphEdgeData>,
      visitedDepth: MutableMap<String, Int>,
      remainingDepth: Int,
      isProjectCode: Boolean,
      findCallTargets: (nodeId: String) -> List<TraversalTarget>,
      settings: CallGraphProjectSettings,
  ) {
    ProgressManager.checkCanceled()

    val lastDepth = visitedDepth[currentNode.id]
    if (remainingDepth <= 0 || (lastDepth != null && lastDepth >= remainingDepth)) return

    visitedDepth[currentNode.id] = remainingDepth

    val callTargets = findCallTargets(currentNode.id)

    for (target in callTargets) {
      ProgressManager.checkCanceled()

      addNodeAndEdge(
          fromId = currentNode.id,
          target = target,
          nodes = nodes,
          edges = edges,
      )

      // Handle interface implementations
      if (settings.resolveInterfaceImplementations && target.implementations != null) {
        for (impl in target.implementations) {
          addNodeAndEdge(
              fromId = target.node.id,
              target = impl,
              nodes = nodes,
              edges = edges,
          )
          traverseIfNeeded(
              targetNode = impl.node,
              nodes = nodes,
              edges = edges,
              visitedDepth = visitedDepth,
              remainingDepth = remainingDepth,
              currentIsProjectCode = isProjectCode,
              findCallTargets = findCallTargets,
              settings = settings,
          )
        }
      }

      // Continue traversal for the target
      traverseIfNeeded(
          targetNode = target.node,
          nodes = nodes,
          edges = edges,
          visitedDepth = visitedDepth,
          remainingDepth = remainingDepth,
          currentIsProjectCode = isProjectCode,
          findCallTargets = findCallTargets,
          settings = settings,
      )
    }
  }

  private fun addNodeAndEdge(
      fromId: String,
      target: TraversalTarget,
      nodes: MutableMap<String, CallGraphNodeData>,
      edges: MutableList<CallGraphEdgeData>,
  ) {
    nodes[target.node.id] = target.node
    edges.add(
        CallGraphEdgeData(
            fromId = fromId,
            toId = target.node.id,
        ))
  }

  private fun traverseIfNeeded(
      targetNode: CallGraphNodeData,
      nodes: MutableMap<String, CallGraphNodeData>,
      edges: MutableList<CallGraphEdgeData>,
      visitedDepth: MutableMap<String, Int>,
      remainingDepth: Int,
      currentIsProjectCode: Boolean,
      findCallTargets: (nodeId: String) -> List<TraversalTarget>,
      settings: CallGraphProjectSettings,
  ) {
    val nextDepth =
        computeNextDepth(
            currentIsProjectCode = currentIsProjectCode,
            calleeIsProjectCode = targetNode.isProjectCode,
            remainingDepth = remainingDepth,
            settings = settings,
        )

    if (nextDepth > 0) {
      traverseRecursive(
          currentNode = targetNode,
          nodes = nodes,
          edges = edges,
          visitedDepth = visitedDepth,
          remainingDepth = nextDepth,
          isProjectCode = targetNode.isProjectCode,
          findCallTargets = findCallTargets,
          settings = settings,
      )
    }
  }

  private fun computeNextDepth(
      currentIsProjectCode: Boolean,
      calleeIsProjectCode: Boolean,
      remainingDepth: Int,
      settings: CallGraphProjectSettings,
  ): Int =
      if (calleeIsProjectCode) {
        if (currentIsProjectCode) remainingDepth - 1 else settings.projectMaxDepth - 1
      } else {
        if (currentIsProjectCode) settings.thirdPartyMaxDepth - 1 else remainingDepth - 1
      }
}
