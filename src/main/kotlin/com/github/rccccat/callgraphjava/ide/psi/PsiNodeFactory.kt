package com.github.rccccat.callgraphjava.ide.psi

import com.github.rccccat.callgraphjava.api.model.CallGraphNodeData
import com.github.rccccat.callgraphjava.api.model.NodeType
import com.github.rccccat.callgraphjava.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.callgraphjava.framework.spring.SpringAnalyzer
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.github.rccccat.callgraphjava.util.buildCallGraphNodeId
import com.github.rccccat.callgraphjava.util.isProjectCode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager

/** 基于 PSI 元素创建 IDE 节点与纯数据节点的工厂。 */
class PsiNodeFactory(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
    private val myBatisAnalyzer: MyBatisAnalyzer,
) {
  fun createNodeData(element: PsiElement): CallGraphNodeData? =
      when (element) {
        is PsiMethod -> createFromJavaMethod(element)
        else -> null
      }

  fun createIdeNode(element: PsiElement): IdeCallGraphNode? {
    val nodeData = createNodeData(element) ?: return null
    val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element)
    return IdeCallGraphNode(nodeData, pointer)
  }

  private fun createFromJavaMethod(method: PsiMethod): CallGraphNodeData {
    val containingClass = method.containingClass
    val className = containingClass?.name

    val signature = buildJavaSignature(method)

    val springInfo = springAnalyzer.analyzeMethod(method)
    val myBatisInfo = myBatisAnalyzer.analyzeMapperMethod(method)

    val nodeType =
        when {
          springInfo.isController -> NodeType.SPRING_CONTROLLER_METHOD
          springInfo.isService -> NodeType.SPRING_SERVICE_METHOD
          myBatisInfo.isMapperMethod -> NodeType.MYBATIS_MAPPER_METHOD
          else -> NodeType.JAVA_METHOD
        }

    val offset = method.textRange?.startOffset ?: -1
    val lineNumber = calculateLineNumber(method, offset)
    val id = buildCallGraphNodeId(method, lineNumber, offset)

    return CallGraphNodeData(
        id = id,
        name = method.name,
        className = className,
        signature = signature,
        nodeType = nodeType,
        isProjectCode = isProjectCode(project, method),
        isSpringEndpoint = springInfo.isEndpoint,
        sqlType = myBatisInfo.sqlType,
        sqlStatement = myBatisInfo.sqlStatement,
        offset = offset,
        lineNumber = lineNumber,
    )
  }

  private fun buildJavaSignature(method: PsiMethod): String {
    val returnType = method.returnType?.presentableText ?: "void"
    val parameters =
        method.parameterList.parameters.joinToString(", ") { param ->
          "${param.type.presentableText} ${param.name}"
        }
    return "$returnType ${method.name}($parameters)"
  }

  private fun calculateLineNumber(
      element: PsiElement,
      offset: Int,
  ): Int {
    if (offset < 0) return -1
    val file = element.containingFile ?: return -1
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return -1
    return document.getLineNumber(offset) + 1
  }
}
