package com.github.rccccat.ideacallgraph.analysis

import com.github.rccccat.ideacallgraph.model.CallGraphNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Factory for creating CallGraphNode instances from PSI elements
 */
class CallGraphNodeFactory(private val project: Project) {

    private val springAnalyzer = SpringCallGraphAnalyzer()

    /**
     * Creates a CallGraphNode from a PSI element (method or function)
     */
    fun createNode(element: PsiElement): CallGraphNode? {
        return when (element) {
            is PsiMethod -> createFromJavaMethod(element)
            is KtNamedFunction -> createFromKotlinFunction(element)
            else -> null
        }
    }

    private fun createFromJavaMethod(method: PsiMethod): CallGraphNode {
        val containingClass = method.containingClass
        val className = containingClass?.name
        val qualifiedClassName = containingClass?.qualifiedName
        
        val signature = buildJavaSignature(method)
        val id = "${qualifiedClassName ?: "Unknown"}#${method.name}(${method.parameterList.parameters.joinToString(",") { it.type.presentableText }})"
        
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method as PsiElement)
        
        // Check if this is a Spring endpoint
        val springInfo = springAnalyzer.analyzeMethod(method)
        
        val nodeType = when {
            springInfo.isController -> CallGraphNode.NodeType.SPRING_CONTROLLER_METHOD
            springInfo.isService -> CallGraphNode.NodeType.SPRING_SERVICE_METHOD
            else -> CallGraphNode.NodeType.JAVA_METHOD
        }
        
        // Check if this is project code
        val isProjectCode = isProjectCode(method)
        
        return CallGraphNode(
            id = id,
            name = method.name,
            className = className,
            signature = signature,
            elementPointer = pointer,
            nodeType = nodeType,
            isSpringEndpoint = springInfo.isEndpoint,
            springMapping = springInfo.mapping,
            httpMethods = springInfo.httpMethods,
            isProjectCode = isProjectCode
        )
    }

    private fun createFromKotlinFunction(function: KtNamedFunction): CallGraphNode {
        val containingClass = function.containingClass()
        val className = containingClass?.name
        val qualifiedClassName = containingClass?.fqName?.asString()
        
        val signature = buildKotlinSignature(function)
        val functionName = function.name ?: "anonymous"
        val id = "${qualifiedClassName ?: "Unknown"}#$functionName"
        
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(function as PsiElement)
        
        // Check if this is project code
        val isProjectCode = isProjectCode(function)
        
        // TODO: Add Spring annotation support for Kotlin
        
        return CallGraphNode(
            id = id,
            name = functionName,
            className = className,
            signature = signature,
            elementPointer = pointer,
            nodeType = CallGraphNode.NodeType.KOTLIN_FUNCTION,
            isProjectCode = isProjectCode
        )
    }

    private fun buildJavaSignature(method: PsiMethod): String {
        val returnType = method.returnType?.presentableText ?: "void"
        val parameters = method.parameterList.parameters.joinToString(", ") { param ->
            "${param.type.presentableText} ${param.name}"
        }
        return "$returnType ${method.name}($parameters)"
    }

    private fun buildKotlinSignature(function: KtNamedFunction): String {
        val functionName = function.name ?: "anonymous"
        val parameters = function.valueParameters.joinToString(", ") { param ->
            val paramName = param.name ?: ""
            val paramType = param.typeReference?.text ?: ""
            "$paramName: $paramType"
        }
        val returnType = function.typeReference?.text ?: "Unit"
        return "fun $functionName($parameters): $returnType"
    }

    private fun isProjectCode(element: PsiElement): Boolean {
        val containingFile = element.containingFile
        if (containingFile == null) return false
        
        val virtualFile = containingFile.virtualFile
        if (virtualFile == null) return false
        
        // Check if it's in project source roots
        val fileIndex = ProjectFileIndex.getInstance(project)
        
        // It's project code if it's in source content or test content
        return fileIndex.isInSourceContent(virtualFile) || fileIndex.isInTestSourceContent(virtualFile)
    }
}