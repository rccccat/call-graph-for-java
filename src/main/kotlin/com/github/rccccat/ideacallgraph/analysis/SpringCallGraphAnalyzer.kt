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

        private val QUALIFIER_ANNOTATIONS = setOf(
            "Qualifier",
            "org.springframework.beans.factory.annotation.Qualifier"
        )

        private val PRIMARY_ANNOTATIONS = setOf(
            "Primary",
            "org.springframework.context.annotation.Primary"
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
        
        // Check if this is a call to an interface method that might be implemented by a Spring component
        val containingClass = targetMethod.containingClass
        if (containingClass?.isInterface == true) {
            return CallGraphEdge.CallType.INTERFACE_CALL
        }
        
        return null
    }

    /**
     * Enhanced method to detect Spring injection patterns including constructor and setter injection
     */
    fun detectInjectionPattern(method: PsiMethod, targetClass: PsiClass): InjectionPattern? {
        // Check for field injection
        val fields = targetClass.fields
        for (field in fields) {
            if (hasAnyAnnotation(field, INJECTION_ANNOTATIONS)) {
                val fieldType = field.type
                if (isCompatibleType(fieldType, method.containingClass)) {
                    return InjectionPattern.FIELD_INJECTION
                }
            }
        }
        
        // Check for constructor injection
        val constructors = targetClass.constructors
        for (constructor in constructors) {
            if (hasAnyAnnotation(constructor, INJECTION_ANNOTATIONS) || 
                (constructors.size == 1 && constructor.parameterList.parametersCount > 0)) {
                val parameters = constructor.parameterList.parameters
                for (param in parameters) {
                    if (isCompatibleType(param.type, method.containingClass)) {
                        return InjectionPattern.CONSTRUCTOR_INJECTION
                    }
                }
            }
        }
        
        // Check for setter injection
        val methods = targetClass.methods
        for (setterMethod in methods) {
            if (setterMethod.name.startsWith("set") && 
                hasAnyAnnotation(setterMethod, INJECTION_ANNOTATIONS)) {
                val parameters = setterMethod.parameterList.parameters
                if (parameters.size == 1 && isCompatibleType(parameters[0].type, method.containingClass)) {
                    return InjectionPattern.SETTER_INJECTION
                }
            }
        }
        
        return null
    }

    private fun isCompatibleType(type: PsiType, targetClass: PsiClass?): Boolean {
        if (targetClass == null) return false
        
        val typeClassName = type.canonicalText
        val targetClassName = targetClass.qualifiedName
        
        // Direct type match
        if (typeClassName == targetClassName) return true
        
        // Interface compatibility check
        if (targetClass.isInterface) {
            return typeClassName == targetClassName
        }
        
        // Check if target class implements the type interface
        val interfaces = targetClass.interfaces
        return interfaces.any { it.qualifiedName == typeClassName }
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

    enum class InjectionPattern {
        FIELD_INJECTION,
        CONSTRUCTOR_INJECTION,
        SETTER_INJECTION
    }

    /**
     * Enhanced Spring injection analysis with @Primary/@Qualifier support
     */
    fun analyzeSpringInjection(
        injectionPoint: PsiElement,
        targetInterface: PsiClass,
        implementations: List<PsiClass>
    ): SpringInjectionResult {
        // Extract qualifier from injection point
        val qualifierValue = extractQualifier(injectionPoint)
        
        // Check for collection injection
        val injectionType = determineInjectionType(injectionPoint)
        if (injectionType.isCollection) {
            return SpringInjectionResult(
                selectedImplementations = implementations,
                injectionType = injectionType,
                reason = "Collection injection - all implementations included"
            )
        }
        
        // Filter implementations based on qualifier and primary
        val filteredImplementations = filterImplementationsByPriority(
            implementations, 
            qualifierValue
        )
        
        return SpringInjectionResult(
            selectedImplementations = filteredImplementations,
            injectionType = injectionType,
            reason = buildResolutionReason(filteredImplementations, qualifierValue)
        )
    }

    private fun extractQualifier(element: PsiElement): String? {
        return when (element) {
            is PsiField -> extractQualifierFromAnnotations(element.modifierList)
            is PsiParameter -> extractQualifierFromAnnotations(element.modifierList)
            is PsiMethod -> extractQualifierFromAnnotations(element.modifierList)
            else -> null
        }
    }

    private fun extractQualifierFromAnnotations(modifierList: PsiModifierList?): String? {
        if (modifierList == null) return null
        
        return modifierList.annotations
            .find { hasAnyAnnotation(it, QUALIFIER_ANNOTATIONS) }
            ?.findAttributeValue("value")
            ?.let { extractStringValue(it) }
    }

    private fun determineInjectionType(element: PsiElement): InjectionType {
        val type = when (element) {
            is PsiField -> element.type
            is PsiParameter -> element.type
            else -> return InjectionType.SINGLE
        }
        
        val typeText = type.canonicalText
        
        return when {
            typeText.startsWith("java.util.List<") || typeText.startsWith("kotlin.collections.List<") -> 
                InjectionType.LIST
            typeText.startsWith("java.util.Map<java.lang.String,") || typeText.startsWith("kotlin.collections.Map<kotlin.String,") -> 
                InjectionType.MAP
            typeText.startsWith("java.util.Set<") || typeText.startsWith("kotlin.collections.Set<") -> 
                InjectionType.SET
            else -> InjectionType.SINGLE
        }
    }

    private fun filterImplementationsByPriority(
        implementations: List<PsiClass>,
        qualifierValue: String?
    ): List<PsiClass> {
        // If qualifier is specified, find matching implementation
        if (qualifierValue != null) {
            val qualifierMatches = implementations.filter { impl ->
                matchesQualifier(impl, qualifierValue)
            }
            if (qualifierMatches.isNotEmpty()) {
                return qualifierMatches
            }
        }
        
        // Find @Primary implementations
        val primaryImplementations = implementations.filter { impl ->
            hasAnyAnnotation(impl, PRIMARY_ANNOTATIONS)
        }
        
        if (primaryImplementations.isNotEmpty()) {
            return primaryImplementations
        }
        
        // Return all if no specific priority found
        return implementations
    }

    private fun matchesQualifier(implementation: PsiClass, qualifierValue: String): Boolean {
        // Check if the implementation has a matching @Qualifier
        val implQualifier = implementation.modifierList?.annotations
            ?.find { hasAnyAnnotation(it, QUALIFIER_ANNOTATIONS) }
            ?.findAttributeValue("value")
            ?.let { extractStringValue(it) }
        
        if (implQualifier == qualifierValue) {
            return true
        }
        
        // Check if the bean name matches (simple class name in camelCase)
        val beanName = implementation.name?.let { name ->
            name.substring(0, 1).lowercase() + name.substring(1)
        }
        
        return beanName == qualifierValue
    }

    private fun buildResolutionReason(
        implementations: List<PsiClass>,
        qualifierValue: String?
    ): String {
        return when {
            implementations.isEmpty() -> "No matching implementations found"
            qualifierValue != null -> "Resolved by @Qualifier(\"$qualifierValue\")"
            implementations.any { hasAnyAnnotation(it, PRIMARY_ANNOTATIONS) } -> "Resolved by @Primary annotation"
            implementations.size == 1 -> "Single implementation available"
            else -> "Multiple implementations - using all (ambiguous injection)"
        }
    }

    private fun hasAnyAnnotation(element: PsiElement, annotations: Set<String>): Boolean {
        return when (element) {
            is PsiModifierListOwner -> hasAnyAnnotation(element, annotations)
            is PsiAnnotation -> {
                val qualifiedName = element.qualifiedName
                qualifiedName != null && annotations.any { 
                    qualifiedName.endsWith(it) || qualifiedName == it 
                }
            }
            else -> false
        }
    }

    data class SpringInjectionResult(
        val selectedImplementations: List<PsiClass>,
        val injectionType: InjectionType,
        val reason: String
    )

    enum class InjectionType(val isCollection: Boolean) {
        SINGLE(false),
        LIST(true),
        SET(true),
        MAP(true)
    }
}