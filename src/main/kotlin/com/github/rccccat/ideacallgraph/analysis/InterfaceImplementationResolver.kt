package com.github.rccccat.ideacallgraph.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Service to resolve interface implementations for better call graph analysis
 */
class InterfaceImplementationResolver(private val project: Project) {

    private val springAnalyzer = SpringCallGraphAnalyzer()

    /**
     * Enhanced method resolution with Spring @Primary/@Qualifier support
     */
    fun resolveInterfaceImplementationsAdvanced(
        interfaceMethod: PsiMethod,
        injectionPoint: PsiElement? = null
    ): AdvancedImplementationResult {
        return ReadAction.compute<AdvancedImplementationResult, Exception> {
            val containingClass = interfaceMethod.containingClass ?: 
                return@compute AdvancedImplementationResult(emptyList(), "No containing interface")
            
            if (!containingClass.isInterface) {
                return@compute AdvancedImplementationResult(emptyList(), "Not an interface method")
            }

            // Find all implementing classes
            val implementingClasses = findImplementingClasses(containingClass)
            
            if (implementingClasses.isEmpty()) {
                return@compute AdvancedImplementationResult(emptyList(), "No implementations found")
            }

            // Use Spring analyzer for priority resolution if injection point is available
            val result = if (injectionPoint != null) {
                springAnalyzer.analyzeSpringInjection(injectionPoint, containingClass, implementingClasses)
            } else {
                SpringCallGraphAnalyzer.SpringInjectionResult(
                    implementingClasses,
                    SpringCallGraphAnalyzer.InjectionType.SINGLE,
                    "No injection context available"
                )
            }

            // Convert to implementation info
            val implementations = result.selectedImplementations.mapNotNull { implementingClass ->
                val implementationMethod = findImplementationMethod(implementingClass, interfaceMethod)
                if (implementationMethod != null) {
                    ImplementationInfo(
                        implementingClass = implementingClass,
                        implementationMethod = implementationMethod,
                        isSpringComponent = isSpringComponent(implementingClass),
                        isProjectCode = isProjectCode(implementingClass),
                        injectionType = result.injectionType,
                        resolutionReason = result.reason
                    )
                } else null
            }
            
            AdvancedImplementationResult(implementations, result.reason)
        }
    }
    fun resolveInterfaceImplementations(interfaceMethod: PsiMethod): List<ImplementationInfo> {
        return ReadAction.compute<List<ImplementationInfo>, Exception> {
            val implementations = mutableListOf<ImplementationInfo>()
            val containingClass = interfaceMethod.containingClass ?: return@compute implementations
            
            if (!containingClass.isInterface) {
                return@compute implementations
            }

            // Find all classes that implement this interface
            val implementingClasses = findImplementingClasses(containingClass)
            
            for (implementingClass in implementingClasses) {
                val implementationMethod = findImplementationMethod(implementingClass, interfaceMethod)
                if (implementationMethod != null) {
                    implementations.add(
                        ImplementationInfo(
                            implementingClass = implementingClass,
                            implementationMethod = implementationMethod,
                            isSpringComponent = isSpringComponent(implementingClass),
                            isProjectCode = isProjectCode(implementingClass)
                        )
                    )
                }
            }
            
            implementations
        }
    }

    /**
     * Find all Kotlin interface implementations
     */
    fun resolveKotlinInterfaceImplementations(interfaceClass: KtClass): List<KotlinImplementationInfo> {
        return ReadAction.compute<List<KotlinImplementationInfo>, Exception> {
            val implementations = mutableListOf<KotlinImplementationInfo>()
            
            if (!interfaceClass.isInterface()) {
                return@compute implementations
            }

            // Find implementing classes using Kotlin-specific search
            val scope = GlobalSearchScope.projectScope(project)
            val implementingClasses = findKotlinImplementingClasses(interfaceClass, scope)
            
            for (implementingClass in implementingClasses) {
                implementations.add(
                    KotlinImplementationInfo(
                        implementingClass = implementingClass,
                        isSpringComponent = isKotlinSpringComponent(implementingClass),
                        isProjectCode = isProjectCode(implementingClass)
                    )
                )
            }
            
            implementations
        }
    }

    private fun findImplementingClasses(interfaceClass: PsiClass): List<PsiClass> {
        val scope = GlobalSearchScope.projectScope(project)
        return ClassInheritorsSearch.search(interfaceClass, scope, true)
            .findAll()
            .filter { !it.isInterface && !it.hasModifierProperty(PsiModifier.ABSTRACT) }
    }

    private fun findKotlinImplementingClasses(interfaceClass: KtClass, scope: GlobalSearchScope): List<KtClass> {
        val implementations = mutableListOf<KtClass>()
        val interfaceName = interfaceClass.name ?: return implementations
        
        // Search for classes that might implement this interface
        val possibleClasses = KotlinClassShortNameIndex.getAllKeys(project)
        
        for (className in possibleClasses) {
            val classes = KotlinClassShortNameIndex[className, project, scope]
            for (cls in classes) {
                if (cls is KtClass && !cls.isInterface() && implementsInterface(cls, interfaceClass)) {
                    implementations.add(cls)
                }
            }
        }
        
        return implementations
    }

    private fun implementsInterface(implementingClass: KtClass, interfaceClass: KtClass): Boolean {
        val superTypeListEntries = implementingClass.superTypeListEntries
        return superTypeListEntries.any { entry ->
            val typeReference = entry.typeReference
            typeReference?.text?.contains(interfaceClass.name ?: "") == true
        }
    }

    private fun findImplementationMethod(implementingClass: PsiClass, interfaceMethod: PsiMethod): PsiMethod? {
        return implementingClass.findMethodsByName(interfaceMethod.name, false)
            .find { method ->
                // Check if method signature matches
                methodSignaturesMatch(method, interfaceMethod)
            }
    }

    private fun methodSignaturesMatch(method1: PsiMethod, method2: PsiMethod): Boolean {
        if (method1.name != method2.name) return false
        if (method1.parameterList.parametersCount != method2.parameterList.parametersCount) return false
        
        val params1 = method1.parameterList.parameters
        val params2 = method2.parameterList.parameters
        
        return params1.zip(params2).all { (p1, p2) ->
            p1.type.canonicalText == p2.type.canonicalText
        }
    }

    private fun isSpringComponent(psiClass: PsiClass): Boolean {
        val modifierList = psiClass.modifierList ?: return false
        return modifierList.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName != null && (
                qualifiedName.endsWith("Component") ||
                qualifiedName.endsWith("Service") ||
                qualifiedName.endsWith("Repository") ||
                qualifiedName.endsWith("Controller") ||
                qualifiedName.endsWith("RestController")
            )
        }
    }

    private fun isKotlinSpringComponent(ktClass: KtClass): Boolean {
        return ktClass.annotationEntries.any { annotation ->
            val annotationName = annotation.shortName?.asString()
            annotationName in setOf("Component", "Service", "Repository", "Controller", "RestController")
        }
    }

    private fun isProjectCode(element: PsiElement): Boolean {
        val containingFile = element.containingFile ?: return false
        val virtualFile = containingFile.virtualFile ?: return false
        return project.projectFile?.parent?.findFileByRelativePath("src")?.let { srcRoot ->
            virtualFile.path.startsWith(srcRoot.path)
        } ?: false
    }

    data class ImplementationInfo(
        val implementingClass: PsiClass,
        val implementationMethod: PsiMethod,
        val isSpringComponent: Boolean,
        val isProjectCode: Boolean,
        val injectionType: SpringCallGraphAnalyzer.InjectionType = SpringCallGraphAnalyzer.InjectionType.SINGLE,
        val resolutionReason: String = "Standard resolution"
    )

    data class AdvancedImplementationResult(
        val implementations: List<ImplementationInfo>,
        val resolutionSummary: String
    )

    data class KotlinImplementationInfo(
        val implementingClass: KtClass,
        val isSpringComponent: Boolean,
        val isProjectCode: Boolean
    )
}