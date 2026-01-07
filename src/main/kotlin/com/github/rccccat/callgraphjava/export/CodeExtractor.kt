package com.github.rccccat.callgraphjava.export

import com.github.rccccat.callgraphjava.ide.model.IdeCallGraph
import com.intellij.openapi.application.ReadAction

/** Code extractor for IDE-specific call graphs. Extracts source code from PSI elements. */
class CodeExtractor {

  /** Extracts code for all nodes in the call graph. Returns a map of node ID to source code. */
  fun extractCode(callGraph: IdeCallGraph): Map<String, String> {
    val codeMap = mutableMapOf<String, String>()

    for ((nodeId, pointer) in callGraph.nodePointers) {
      val code = extractSelfCode(pointer)
      codeMap[nodeId] = code
    }

    return codeMap
  }

  /** Extracts code for a single node. */
  private fun extractSelfCode(
      pointer: com.intellij.psi.SmartPsiElementPointer<com.intellij.psi.PsiElement>
  ): String {
    return try {
      ReadAction.compute<String, Exception> {
        val element = pointer.element ?: return@compute "// Code not available"

        val text = element.text
        if (text.isNullOrBlank()) {
          "// Code not available"
        } else {
          text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
      }
    } catch (e: Exception) {
      "// Error extracting code: ${e.message}"
    }
  }
}
