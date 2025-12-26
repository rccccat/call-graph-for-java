package com.github.rccccat.ideacallgraph.ui

import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.toolWindow.CallGraphToolWindowContent
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/** Custom tree cell renderer for call graph nodes */
class CallGraphTreeRenderer : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
  ) {
    if (value !is DefaultMutableTreeNode) return

    val userObject = value.userObject
    if (userObject !is CallGraphToolWindowContent.CallGraphTreeNode) return

    val node = userObject.node

    // Set icon based on node type
    icon =
        when (node.nodeType) {
          NodeType.SPRING_CONTROLLER_METHOD -> AllIcons.Nodes.Controller
          NodeType.SPRING_SERVICE_METHOD -> AllIcons.Nodes.Services
          NodeType.JAVA_METHOD -> AllIcons.Nodes.Method
          NodeType.MYBATIS_MAPPER_METHOD -> AllIcons.Nodes.Interface
          NodeType.MYBATIS_SQL_STATEMENT -> AllIcons.FileTypes.Config
        }

    if (node.isSpringEndpoint) {
      append("[Endpoint] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

    // Add service indicator for Spring services
    if (node.nodeType == NodeType.SPRING_SERVICE_METHOD) {
      append("[Service] ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    // Add MyBatis indicators
    if (node.nodeType == NodeType.MYBATIS_MAPPER_METHOD) {
      append("[Mapper] ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    if (node.nodeType == NodeType.MYBATIS_SQL_STATEMENT) {
      val sqlType = node.sqlType?.name ?: "SQL"
      append("[$sqlType] ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    }

    // Add class name if available
    if (node.className != null) {
      append("${node.className}::", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    // Add method/function name with underline for clickable appearance
    val nameAttributes =
        if (node.isProjectCode) {
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        } else {
          SimpleTextAttributes.GRAYED_ATTRIBUTES
        }
    append(node.name, nameAttributes)

    // Add third-party library indicator
    if (!node.isProjectCode) {
      append(" [Third-party]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }

    // Add depth indicator for deeply nested calls
    if (userObject.depth > 3) {
      append(" (depth: ${userObject.depth})", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }
  }
}
