package com.github.rccccat.callgraphjava.ide.model

import com.github.rccccat.callgraphjava.api.model.CallGraphData
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/** IDE-specific call graph that supports navigation via PSI pointers. */
data class IdeCallGraph(
    val data: CallGraphData,
    val nodePointers: Map<String, SmartPsiElementPointer<PsiElement>>,
) {
  val rootNode: IdeCallGraphNode?
    get() = getNode(data.rootNodeId)

  val nodes: Set<IdeCallGraphNode>
    get() = data.nodes.keys.mapNotNull { getNode(it) }.toSet()

  fun getNode(id: String): IdeCallGraphNode? {
    val nodeData = data.nodes[id] ?: return null
    val pointer = nodePointers[id] ?: return null
    return IdeCallGraphNode(nodeData, pointer)
  }

  fun getCallTargets(node: IdeCallGraphNode): List<IdeCallGraphNode> =
      data.getCallTargets(node.id).mapNotNull { getNode(it.id) }
}
