package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.google.gson.GsonBuilder
import java.io.Serializable

/** JSON export model - pure data, no IDE dependencies. */
data class CallGraphJsonExport(
    val rootId: String,
    val nodes: Map<String, CallGraphJsonNode>,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

data class CallGraphJsonNode(
    val entryMethod: String,
    val signature: String,
    val selfCode: String,
    val isApiCenterMethod: Boolean,
    val callTargets: List<String>,
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}

/** JSON exporter for call graph data. Works with pure data models, no IDE dependencies. */
class JsonExporter {
  private val prettyGson = GsonBuilder().setPrettyPrinting().create()
  private val compactGson = GsonBuilder().create()

  /** Exports call graph data to JSON string with code extraction. */
  fun exportToJson(
      callGraph: CallGraphData,
      codeMap: Map<String, String>,
  ): String {
    val jsonExport = convertToJsonExport(callGraph, codeMap)
    return prettyGson.toJson(jsonExport)
  }

  /** Exports call graph data to compact JSON string with code extraction. */
  fun exportToJsonCompact(
      callGraph: CallGraphData,
      codeMap: Map<String, String>,
  ): String {
    val jsonExport = convertToJsonExport(callGraph, codeMap)
    return compactGson.toJson(jsonExport)
  }

  /** Converts CallGraphData to JSON export structure. */
  fun convertToJsonExport(
      callGraph: CallGraphData,
      codeMap: Map<String, String>,
  ): CallGraphJsonExport {
    val nodes = LinkedHashMap<String, CallGraphJsonNode>()
    val queue = ArrayDeque<String>()
    val visited = HashSet<String>()

    queue.add(callGraph.rootNodeId)

    while (queue.isNotEmpty()) {
      val nodeId = queue.removeFirst()
      if (!visited.add(nodeId)) continue

      val node = callGraph.nodes[nodeId] ?: continue
      val callTargets = callGraph.getCallTargets(nodeId).sortedBy { it.id }
      val callTargetIds = callTargets.map { it.id }

      nodes[nodeId] =
          CallGraphJsonNode(
              entryMethod = "${node.className ?: "Unknown"}.${node.name}",
              signature = node.signature,
              selfCode = codeMap[nodeId] ?: "// Code not available",
              isApiCenterMethod = node.isSpringEndpoint,
              callTargets = callTargetIds,
          )

      callTargets.forEach { callTarget ->
        if (!visited.contains(callTarget.id)) {
          queue.add(callTarget.id)
        }
      }
    }

    return CallGraphJsonExport(rootId = callGraph.rootNodeId, nodes = nodes)
  }
}
