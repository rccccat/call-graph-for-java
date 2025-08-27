package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.model.CallGraph
import com.github.rccccat.ideacallgraph.model.CallGraphNode
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.psi.PsiElement

/**
 * JSON export data models and functionality
 */
data class CallGraphJsonNode(
    val entryMethod: String,
    val signature: String,
    val selfCode: String,
    val isApiCenterMethod: Boolean,
    val apiDetail: ApiDetail?,
    val callees: List<CallGraphJsonNode>
)

data class ApiDetail(
    val httpMethod: String?,
    val path: String?,
    val description: String?
)

class CallGraphJsonExporter {

    fun exportToJson(callGraph: CallGraph): String {
        val jsonNode = convertToJsonNode(callGraph.rootNode, callGraph, mutableSetOf())
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(jsonNode)
    }

    private fun convertToJsonNode(
        node: CallGraphNode,
        callGraph: CallGraph,
        visited: MutableSet<String>
    ): CallGraphJsonNode {
        if (node.id in visited) {
            // Avoid infinite recursion - return a simplified node
            return CallGraphJsonNode(
                entryMethod = "${node.className}.${node.name}",
                signature = node.signature,
                selfCode = "// Cyclic reference",
                isApiCenterMethod = node.isSpringEndpoint,
                apiDetail = createApiDetail(node),
                callees = emptyList()
            )
        }

        visited.add(node.id)

        val callees = callGraph.getCallees(node).map { callee ->
            convertToJsonNode(callee, callGraph, visited.toMutableSet())
        }

        val result = CallGraphJsonNode(
            entryMethod = "${node.className}.${node.name}",
            signature = node.signature,
            selfCode = extractSelfCode(node),
            isApiCenterMethod = node.isSpringEndpoint,
            apiDetail = createApiDetail(node),
            callees = callees
        )

        visited.remove(node.id)
        return result
    }

    private fun createApiDetail(node: CallGraphNode): ApiDetail? {
        return if (node.isSpringEndpoint) {
            ApiDetail(
                httpMethod = node.httpMethods.firstOrNull(),
                path = node.springMapping,
                description = null
            )
        } else null
    }

    private fun extractSelfCode(node: CallGraphNode): String {
        val element = node.elementPointer.element ?: return "// Code not available"
        
        return try {
            val text = element.text
            if (text.isNullOrBlank()) {
                "// Code not available"
            } else {
                // Clean up the code for JSON export
                text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            }
        } catch (e: Exception) {
            "// Error extracting code: ${e.message}"
        }
    }
}