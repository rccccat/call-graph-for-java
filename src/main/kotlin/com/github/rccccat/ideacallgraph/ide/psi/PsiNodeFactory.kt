package com.github.rccccat.ideacallgraph.ide.psi

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode
import com.github.rccccat.ideacallgraph.util.isProjectCode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Factory for creating IDE nodes and pure node data from PSI elements. */
class PsiNodeFactory(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
    private val myBatisAnalyzer: MyBatisAnalyzer,
) {
  fun createNodeData(element: PsiElement): CallGraphNodeData? =
      when (element) {
        is PsiMethod -> createFromJavaMethod(element)
        is KtNamedFunction -> createFromKotlinFunction(element)
        else -> null
      }

  fun createIdeNode(element: PsiElement): IdeCallGraphNode? {
    val sourceElement = resolveSourceElement(element)
    val nodeData = createNodeData(sourceElement) ?: return null
    val pointer =
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(sourceElement)
    return IdeCallGraphNode(nodeData, pointer)
  }

  private fun resolveSourceElement(element: PsiElement): PsiElement {
    if (element is PsiMethod) {
      val kotlinOrigin = element.navigationElement as? KtNamedFunction
      if (kotlinOrigin != null) {
        return kotlinOrigin
      }
    }
    return element
  }

  private fun createFromJavaMethod(method: PsiMethod): CallGraphNodeData {
    val containingClass = method.containingClass
    val className = containingClass?.name
    val qualifiedClassName = containingClass?.qualifiedName

    val signature = buildJavaSignature(method)
    val id = buildJavaNodeId(method, qualifiedClassName)

    val springInfo = springAnalyzer.analyzeMethod(method)
    val myBatisInfo = myBatisAnalyzer.analyzeMapperMethod(method)

    val nodeType =
        when {
          springInfo.isController -> NodeType.SPRING_CONTROLLER_METHOD
          springInfo.isService -> NodeType.SPRING_SERVICE_METHOD
          myBatisInfo.isMapperMethod -> NodeType.MYBATIS_MAPPER_METHOD
          else -> NodeType.JAVA_METHOD
        }

    val file = method.containingFile?.virtualFile
    val offset = method.textRange?.startOffset ?: -1
    val lineNumber = file?.let { calculateLineNumber(method, offset) } ?: -1

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

  private fun createFromKotlinFunction(function: KtNamedFunction): CallGraphNodeData {
    val containingClass = function.parent?.parent as? KtClassOrObject
    val className = containingClass?.name
    val qualifiedClassName = containingClass?.fqName?.asString()

    val functionName = function.name ?: "anonymous"
    val signature = buildKotlinSignature(function)
    val id = buildKotlinNodeId(function, qualifiedClassName, functionName)

    val springInfo = springAnalyzer.analyzeMethod(function)

    val nodeType =
        when {
          springInfo.isController -> NodeType.SPRING_CONTROLLER_METHOD
          springInfo.isService -> NodeType.SPRING_SERVICE_METHOD
          else -> NodeType.KOTLIN_FUNCTION
        }

    val file = function.containingFile?.virtualFile
    val offset = function.textRange?.startOffset ?: -1
    val lineNumber = file?.let { calculateLineNumber(function, offset) } ?: -1

    return CallGraphNodeData(
        id = id,
        name = functionName,
        className = className,
        signature = signature,
        nodeType = nodeType,
        isProjectCode = isProjectCode(project, function),
        isSpringEndpoint = springInfo.isEndpoint,
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

  private fun buildKotlinSignature(function: KtNamedFunction): String {
    val functionName = function.name ?: "anonymous"
    val parameters =
        function.valueParameters.joinToString(", ") { param ->
          val paramName = param.name ?: ""
          val paramType = param.typeReference?.text ?: ""
          "$paramName: $paramType"
        }
    val returnType = function.typeReference?.text ?: "Unit"
    return "fun $functionName($parameters): $returnType"
  }

  private fun buildJavaNodeId(
      method: PsiMethod,
      qualifiedClassName: String?,
  ): String {
    val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
    val container = qualifiedClassName ?: buildFileContainer(method)
    val anchor = buildFileAnchor(method)
    return "$container#${method.name}($paramTypes)@$anchor"
  }

  private fun buildKotlinNodeId(
      function: KtNamedFunction,
      qualifiedClassName: String?,
      functionName: String,
  ): String {
    val paramTypes =
        function.valueParameters.joinToString(",") { param -> param.typeReference?.text ?: "Any" }
    val receiverType = function.receiverTypeReference?.text?.let { "receiver=$it;" } ?: ""
    val container = qualifiedClassName ?: buildFileContainer(function)
    val anchor = buildFileAnchor(function)
    return "$container#$functionName($receiverType$paramTypes)@$anchor"
  }

  private fun buildFileContainer(element: PsiElement): String {
    val file = element.containingFile?.virtualFile
    return file?.path ?: element.containingFile?.name ?: "UnknownFile"
  }

  private fun buildFileAnchor(element: PsiElement): String {
    val offset = element.textRange?.startOffset ?: -1
    return "${buildFileContainer(element)}:$offset"
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
