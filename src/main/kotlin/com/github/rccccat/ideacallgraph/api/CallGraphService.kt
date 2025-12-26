package com.github.rccccat.ideacallgraph.api

import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraph
import com.intellij.psi.PsiElement

/** Service interface for call graph operations. */
interface CallGraphService {
  /** Builds a call graph starting from the given element. */
  fun buildCallGraph(startElement: PsiElement): IdeCallGraph?

  /** Exports the call graph to JSON format. */
  fun exportToJson(callGraph: IdeCallGraph): String

  /** Exports the call graph to compact JSON format. */
  fun exportToJsonCompact(callGraph: IdeCallGraph): String

  /** Gets the pure data model from an IDE call graph. */
  fun getData(callGraph: IdeCallGraph): CallGraphData
}
