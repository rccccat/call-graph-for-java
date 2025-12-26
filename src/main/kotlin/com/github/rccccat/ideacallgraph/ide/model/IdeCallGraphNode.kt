package com.github.rccccat.ideacallgraph.ide.model

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/** IDE-specific call graph node that includes PSI pointer for navigation. */
data class IdeCallGraphNode(
    val data: CallGraphNodeData,
    val elementPointer: SmartPsiElementPointer<PsiElement>,
) {
  val id: String
    get() = data.id

  val name: String
    get() = data.name

  val className: String?
    get() = data.className

  val signature: String
    get() = data.signature

  val nodeType
    get() = data.nodeType

  val isProjectCode
    get() = data.isProjectCode

  val isSpringEndpoint
    get() = data.isSpringEndpoint

  val sqlType
    get() = data.sqlType

  val sqlStatement
    get() = data.sqlStatement

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IdeCallGraphNode) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}
