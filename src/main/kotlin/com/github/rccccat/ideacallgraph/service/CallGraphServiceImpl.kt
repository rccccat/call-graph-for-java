package com.github.rccccat.ideacallgraph.service

import com.github.rccccat.ideacallgraph.api.CallGraphService
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.core.CallGraphBuilder
import com.github.rccccat.ideacallgraph.core.visitor.CallVisitor
import com.github.rccccat.ideacallgraph.core.visitor.JavaCallVisitor
import com.github.rccccat.ideacallgraph.core.visitor.KotlinCallVisitor
import com.github.rccccat.ideacallgraph.export.CodeExtractor
import com.github.rccccat.ideacallgraph.export.JsonExporter
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraph
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Project-level implementation of CallGraphService. */
@Service(Service.Level.PROJECT)
class CallGraphServiceImpl(
    private val project: Project,
) : CallGraphService {

  private val visitors: List<CallVisitor> =
      listOf(
          JavaCallVisitor(),
          KotlinCallVisitor(),
      )
  private val jsonExporter = JsonExporter()
  private val codeExtractor = CodeExtractor()

  override fun buildCallGraph(startElement: PsiElement): IdeCallGraph? {
    val registry = AnalyzerRegistry.getInstance()
    val builder =
        CallGraphBuilder(
            project = project,
            springAnalyzer = registry.springAnalyzer,
            visitors = visitors,
        )
    return builder.build(startElement)
  }

  override fun exportToJson(callGraph: IdeCallGraph): String {
    val codeMap = codeExtractor.extractCode(callGraph)
    return jsonExporter.exportToJson(callGraph.data, codeMap)
  }

  override fun exportToJsonCompact(callGraph: IdeCallGraph): String {
    val codeMap = codeExtractor.extractCode(callGraph)
    return jsonExporter.exportToJsonCompact(callGraph.data, codeMap)
  }

  override fun getData(callGraph: IdeCallGraph): CallGraphData {
    return callGraph.data
  }

  companion object {
    fun getInstance(project: Project): CallGraphServiceImpl =
        project.getService(CallGraphServiceImpl::class.java)
  }
}
