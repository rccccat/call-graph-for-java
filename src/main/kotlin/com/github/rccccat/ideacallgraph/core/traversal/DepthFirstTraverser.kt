package com.github.rccccat.ideacallgraph.core.traversal

import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.intellij.openapi.progress.ProgressManager

/** Depth-first traversal implementation for call graph building. */
class DepthFirstTraverser {
  fun traverse(
      rootNode: CallGraphNodeData,
      findCallTargets: (nodeId: String) -> List<TraversalTarget>,
      settings: CallGraphProjectSettings,
  ): CallGraphData {
    val nodes = mutableMapOf(rootNode.id to rootNode)
    val outgoingByFromId = mutableMapOf<String, MutableSet<String>>()
    val visitedDepth = mutableMapOf<String, Int>()

    traverseRecursive(
        currentNode = rootNode,
        nodes = nodes,
        outgoingByFromId = outgoingByFromId,
        visitedDepth = visitedDepth,
        remainingDepth = settings.projectMaxDepth,
        isProjectCode = true,
        findCallTargets = findCallTargets,
        settings = settings,
    )

    return CallGraphData(
        rootNodeId = rootNode.id,
        nodes = nodes.toMap(),
        outgoingByFromId = outgoingByFromId.mapValues { it.value.toList() },
    )
  }

  private fun traverseRecursive(
      currentNode: CallGraphNodeData,
      nodes: MutableMap<String, CallGraphNodeData>,
      outgoingByFromId: MutableMap<String, MutableSet<String>>,
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
          outgoingByFromId = outgoingByFromId,
      )

      // Continue traversal for the target
      traverseIfNeeded(
          targetNode = target.node,
          nodes = nodes,
          outgoingByFromId = outgoingByFromId,
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
      outgoingByFromId: MutableMap<String, MutableSet<String>>,
  ) {
    nodes[target.node.id] = target.node
    outgoingByFromId.getOrPut(fromId) { LinkedHashSet() }.add(target.node.id)
  }

  private fun traverseIfNeeded(
      targetNode: CallGraphNodeData,
      nodes: MutableMap<String, CallGraphNodeData>,
      outgoingByFromId: MutableMap<String, MutableSet<String>>,
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
          outgoingByFromId = outgoingByFromId,
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
