package com.github.rccccat.ideacallgraph.service

import com.github.rccccat.ideacallgraph.api.CallGraphService
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.core.CallGraphBuilder
import com.github.rccccat.ideacallgraph.core.dataflow.ParameterUsageAnalyzer
import com.github.rccccat.ideacallgraph.core.resolver.InterfaceResolver
import com.github.rccccat.ideacallgraph.core.resolver.TypeResolver
import com.github.rccccat.ideacallgraph.core.visitor.JavaCallVisitor
import com.github.rccccat.ideacallgraph.export.CodeExtractor
import com.github.rccccat.ideacallgraph.export.JsonExporter
import com.github.rccccat.ideacallgraph.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraph
import com.github.rccccat.ideacallgraph.ide.psi.PsiNodeFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** Project-level implementation of CallGraphService. */
@Service(Service.Level.PROJECT)
class CallGraphServiceImpl(
    private val project: Project,
) : CallGraphService {

  private val springAnalyzer = SpringAnalyzer()
  private val visitor = JavaCallVisitor()
  private val myBatisAnalyzer = MyBatisAnalyzer(project)
  private val typeResolver = TypeResolver(project)
  private val interfaceResolver = InterfaceResolver(project, springAnalyzer)
  private val parameterUsageAnalyzer = ParameterUsageAnalyzer(project)
  private val nodeFactory = PsiNodeFactory(project, springAnalyzer, myBatisAnalyzer)
  private val jsonExporter = JsonExporter()
  private val codeExtractor = CodeExtractor()

  override fun buildCallGraph(startElement: PsiElement): IdeCallGraph? {
    val builder =
        CallGraphBuilder(
            project = project,
            springAnalyzer = springAnalyzer,
            visitor = visitor,
            typeResolver = typeResolver,
            interfaceResolver = interfaceResolver,
            myBatisAnalyzer = myBatisAnalyzer,
            nodeFactory = nodeFactory,
            parameterUsageAnalyzer = parameterUsageAnalyzer,
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

  fun resetCaches() {
    myBatisAnalyzer.resetCaches()
    interfaceResolver.resetCaches()
    parameterUsageAnalyzer.clearCache()
  }

  fun getMyBatisAnalyzer(): MyBatisAnalyzer = myBatisAnalyzer

  fun getNodeFactory(): PsiNodeFactory = nodeFactory

  companion object {
    fun getInstance(project: Project): CallGraphServiceImpl =
        project.getService(CallGraphServiceImpl::class.java)
  }
}
