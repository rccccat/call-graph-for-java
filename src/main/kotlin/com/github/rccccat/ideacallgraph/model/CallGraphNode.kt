package com.github.rccccat.ideacallgraph.model

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Represents a node in the call graph, which can be a Java method or Kotlin function
 */
data class CallGraphNode(
    val id: String,
    val name: String,
    val className: String?,
    val signature: String,
    val elementPointer: SmartPsiElementPointer<PsiElement>,
    val nodeType: NodeType,
    val isSpringEndpoint: Boolean = false,
    val springMapping: String? = null,
    val httpMethods: List<String> = emptyList(),
    val isProjectCode: Boolean = true,
    // MyBatis specific fields
    val sqlType: SqlType? = null,
    val sqlStatement: String? = null,
    val xmlFilePath: String? = null
) {
    enum class NodeType {
        JAVA_METHOD,
        KOTLIN_FUNCTION,
        SPRING_CONTROLLER_METHOD,
        SPRING_SERVICE_METHOD,
        MYBATIS_MAPPER_METHOD,
        MYBATIS_SQL_STATEMENT
    }

    enum class SqlType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallGraphNode) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * Represents an edge (call relationship) in the call graph
 */
data class CallGraphEdge(
    val from: CallGraphNode,
    val to: CallGraphNode,
    val callType: CallType
) {
    enum class CallType {
        DIRECT_CALL,
        REFLECTION_CALL,
        SPRING_INJECTION,
        INTERFACE_CALL,
        MYBATIS_SQL_CALL
    }
}

/**
 * Represents the complete call graph
 */
data class CallGraph(
    val rootNode: CallGraphNode,
    val nodes: Set<CallGraphNode>,
    val edges: Set<CallGraphEdge>
) {
    fun getCallees(node: CallGraphNode): Set<CallGraphNode> {
        return edges.filter { it.from == node }.map { it.to }.toSet()
    }

    fun getCallers(node: CallGraphNode): Set<CallGraphNode> {
        return edges.filter { it.to == node }.map { it.from }.toSet()
    }
}