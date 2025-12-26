package com.github.rccccat.ideacallgraph.ui

import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode

object CallGraphNodeText {
  fun format(node: IdeCallGraphNode): String {
    val prefix =
        when {
          node.isSpringEndpoint -> {
            "[Endpoint] "
          }

          node.nodeType == NodeType.SPRING_SERVICE_METHOD -> {
            "[Service] "
          }

          node.nodeType == NodeType.MYBATIS_MAPPER_METHOD -> {
            "[Mapper] "
          }

          node.nodeType == NodeType.MYBATIS_SQL_STATEMENT -> {
            "[${node.sqlType?.name}] "
          }

          else -> {
            ""
          }
        }

    val suffix =
        if (!node.isProjectCode) {
          " [Third-party]"
        } else {
          ""
        }

    val className = node.className ?: "Unknown"
    return "$prefix$className::${node.name}$suffix"
  }
}
