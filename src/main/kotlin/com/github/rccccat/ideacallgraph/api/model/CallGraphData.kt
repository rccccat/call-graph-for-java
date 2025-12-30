package com.github.rccccat.ideacallgraph.api.model

import java.io.Serializable

/**
 * Pure data model for call graph - no IDE dependencies, fully serializable. This represents the
 * complete call graph structure.
 */
data class CallGraphData(
    val rootNodeId: String,
    val nodes: Map<String, CallGraphNodeData>,
    val outgoingByFromId: Map<String, List<String>>,
) : Serializable {
  fun getCallTargets(nodeId: String): List<CallGraphNodeData> =
      getCallTargetIds(nodeId).mapNotNull { nodes[it] }

  fun getCallTargetIds(nodeId: String): List<String> = outgoingByFromId[nodeId].orEmpty()

  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

/**
 * Pure data model for a call graph node - no PSI dependencies. Contains all information needed for
 * display and export.
 */
data class CallGraphNodeData(
    val id: String,
    val name: String,
    val className: String?,
    val signature: String,
    val nodeType: NodeType,
    val isProjectCode: Boolean,
    // Spring specific fields
    val isSpringEndpoint: Boolean = false,
    // MyBatis specific fields
    val sqlType: SqlType? = null,
    val sqlStatement: String? = null,
    // Source location for navigation
    val offset: Int = -1,
    val lineNumber: Int = -1,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

/** Node type enumeration - represents the type of element in the call graph. */
enum class NodeType {
  JAVA_METHOD,
  SPRING_CONTROLLER_METHOD,
  SPRING_SERVICE_METHOD,
  MYBATIS_MAPPER_METHOD,
  MYBATIS_SQL_STATEMENT,
}

/** SQL operation type for MyBatis nodes. */
enum class SqlType {
  SELECT,
  INSERT,
  UPDATE,
  DELETE,
}
