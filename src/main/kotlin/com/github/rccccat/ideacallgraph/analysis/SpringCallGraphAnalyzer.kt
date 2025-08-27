package com.github.rccccat.ideacallgraph.analysis

import com.github.rccccat.ideacallgraph.model.CallGraphEdge
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Analyzer for Spring Framework specific patterns and annotations
 */
class SpringCallGraphAnalyzer {

    companion object {
        private val CONTROLLER_ANNOTATIONS = setOf(
            "Controller", "RestController",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController"
        )

        private val SERVICE_ANNOTATIONS = setOf(
            "Service", "Component", "Repository",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Repository"
        )

        private val MAPPING_ANNOTATIONS = setOf(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", 
            "DeleteMapping", "PatchMapping",
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
        )

        private val INJECTION_ANNOTATIONS = setOf(
            "Autowired", "Inject", "Resource",
            "org.springframework.beans.factory.annotation.Autowired",
            "javax.inject.Inject",
            "jakarta.inject.Inject",
            "javax.annotation.Resource",
            "jakarta.annotation.Resource"
        )
    }

    /**
     * Analyzes a Java method for Spring-specific patterns
     */
    fun analyzeMethod(method: PsiMethod): SpringMethodInfo {
        val containingClass = method.containingClass ?: return SpringMethodInfo()
        
        val isController = hasAnyAnnotation(containingClass, CONTROLLER_ANNOTATIONS)
        val isService = hasAnyAnnotation(containingClass, SERVICE_ANNOTATIONS)
        
        val mappingInfo = if (isController) {
            analyzeMappingAnnotations(method, containingClass)
        } else {
            MappingInfo()
        }
        
        return SpringMethodInfo(
            isController = isController,
            isService = isService,
            isEndpoint = mappingInfo.isEndpoint,
            mapping = mappingInfo.path,
            httpMethods = mappingInfo.httpMethods
        )
    }

    /**
     * Analyzes a method call for Spring-specific call types
     */
    fun analyzeCallType(expression: PsiMethodCallExpression, targetMethod: PsiMethod): CallGraphEdge.CallType? {
        // Check if the call is through Spring dependency injection
        val qualifierExpr = expression.methodExpression.qualifierExpression
        if (qualifierExpr is PsiReferenceExpression) {
            val resolved = qualifierExpr.resolve()
            if (resolved is PsiField && hasAnyAnnotation(resolved, INJECTION_ANNOTATIONS)) {
                return CallGraphEdge.CallType.SPRING_INJECTION
            }
        }
        
        return null
    }

    private fun analyzeMappingAnnotations(method: PsiMethod, containingClass: PsiClass): MappingInfo {
        val methodMappings = findMappingAnnotations(method)
        val classMappings = findMappingAnnotations(containingClass)
        
        if (methodMappings.isEmpty()) {
            return MappingInfo()
        }
        
        val fullPath = buildFullPath(classMappings, methodMappings)
        val httpMethods = extractHttpMethods(methodMappings)
        
        return MappingInfo(
            isEndpoint = true,
            path = fullPath,
            httpMethods = httpMethods
        )
    }

    private fun findMappingAnnotations(element: PsiModifierListOwner): List<PsiAnnotation> {
        val modifierList = element.modifierList ?: return emptyList()
        return modifierList.annotations.filter { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName != null && MAPPING_ANNOTATIONS.any { 
                qualifiedName.endsWith(it) || qualifiedName == it 
            }
        }
    }

    private fun buildFullPath(classMappings: List<PsiAnnotation>, methodMappings: List<PsiAnnotation>): String {
        val classPath = extractPath(classMappings.firstOrNull()) ?: ""
        val methodPath = extractPath(methodMappings.firstOrNull()) ?: ""
        
        val normalizedClassPath = classPath.removeSuffix("/")
        val normalizedMethodPath = if (methodPath.startsWith("/")) methodPath else "/$methodPath"
        
        return normalizedClassPath + normalizedMethodPath
    }

    private fun extractPath(annotation: PsiAnnotation?): String? {
        if (annotation == null) return null
        
        // Try to get value from "value" attribute
        val valueAttr = annotation.findAttributeValue("value")
        if (valueAttr != null) {
            return extractStringValue(valueAttr)
        }
        
        // Try to get value from "path" attribute
        val pathAttr = annotation.findAttributeValue("path")
        if (pathAttr != null) {
            return extractStringValue(pathAttr)
        }
        
        return null
    }

    private fun extractStringValue(attributeValue: PsiAnnotationMemberValue): String? {
        return when (attributeValue) {
            is PsiLiteralExpression -> attributeValue.value as? String
            is PsiArrayInitializerMemberValue -> {
                // Take the first element if it's an array
                val firstElement = attributeValue.initializers.firstOrNull()
                if (firstElement is PsiLiteralExpression) {
                    firstElement.value as? String
                } else null
            }
            else -> null
        }
    }

    private fun extractHttpMethods(annotations: List<PsiAnnotation>): List<String> {
        val httpMethods = mutableListOf<String>()
        
        for (annotation in annotations) {
            val annotationName = annotation.qualifiedName?.split(".")?.lastOrNull() ?: continue
            
            when (annotationName) {
                "GetMapping" -> httpMethods.add("GET")
                "PostMapping" -> httpMethods.add("POST")
                "PutMapping" -> httpMethods.add("PUT")
                "DeleteMapping" -> httpMethods.add("DELETE")
                "PatchMapping" -> httpMethods.add("PATCH")
                "RequestMapping" -> {
                    // Extract method from RequestMapping annotation
                    val methodAttr = annotation.findAttributeValue("method")
                    if (methodAttr != null) {
                        extractRequestMethods(methodAttr).forEach { httpMethods.add(it) }
                    } else {
                        // If no method specified, assume all methods
                        httpMethods.addAll(listOf("GET", "POST", "PUT", "DELETE", "PATCH"))
                    }
                }
            }
        }
        
        return httpMethods.distinct()
    }

    private fun extractRequestMethods(attributeValue: PsiAnnotationMemberValue): List<String> {
        // This would parse RequestMethod.GET, RequestMethod.POST, etc.
        // Simplified implementation
        val text = attributeValue.text
        return when {
            text.contains("GET") -> listOf("GET")
            text.contains("POST") -> listOf("POST")
            text.contains("PUT") -> listOf("PUT")
            text.contains("DELETE") -> listOf("DELETE")
            text.contains("PATCH") -> listOf("PATCH")
            else -> listOf("GET") // default
        }
    }

    private fun hasAnyAnnotation(element: PsiModifierListOwner, annotations: Set<String>): Boolean {
        val modifierList = element.modifierList ?: return false
        return modifierList.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName != null && annotations.any { 
                qualifiedName.endsWith(it) || qualifiedName == it 
            }
        }
    }

    data class SpringMethodInfo(
        val isController: Boolean = false,
        val isService: Boolean = false,
        val isEndpoint: Boolean = false,
        val mapping: String? = null,
        val httpMethods: List<String> = emptyList()
    )

    private data class MappingInfo(
        val isEndpoint: Boolean = false,
        val path: String? = null,
        val httpMethods: List<String> = emptyList()
    )
}