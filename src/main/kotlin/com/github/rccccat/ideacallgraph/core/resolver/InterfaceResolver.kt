package com.github.rccccat.ideacallgraph.core.resolver

import com.github.rccccat.ideacallgraph.cache.CallGraphCacheManager
import com.github.rccccat.ideacallgraph.core.visitor.ImplementationInfo
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.util.resolveValidPointers
import com.github.rccccat.ideacallgraph.util.toSmartPointer
import com.github.rccccat.ideacallgraph.util.toSmartPointerList
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import java.util.concurrent.ConcurrentHashMap

/** Resolver for interface implementations and class overrides with Spring awareness. */
class InterfaceResolver(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
    private val cacheManager: CallGraphCacheManager,
) {
    private sealed interface MethodLookupResult {
        data class Found(
            val methodPointer: SmartPsiElementPointer<PsiMethod>,
        ) : MethodLookupResult

        object Missing : MethodLookupResult
    }

    private val inheritingClassesCache =
        cacheManager.createCachedValue { ConcurrentHashMap<String, List<SmartPsiElementPointer<PsiClass>>>() }
    private val implementationMethodCache =
        cacheManager.createCachedValue { ConcurrentHashMap<String, MethodLookupResult>() }

    /**
     * Resolves interface implementations and class overrides with Spring DI awareness. Filters based
     * on @Primary, @Qualifier, and Spring component status.
     */
    fun resolveMethodImplementationsAdvanced(
        baseMethod: PsiMethod,
        injectionPoint: PsiElement? = null,
    ): List<ImplementationInfo> {
        val containingClass = baseMethod.containingClass ?: return emptyList()
        if (!shouldResolveOverrides(baseMethod, containingClass)) return emptyList()

        val implementations = findInheritingClasses(containingClass)
        if (implementations.isEmpty()) return emptyList()

        // If there's an injection point, use Spring's resolution logic
        val filteredImplementations =
            if (injectionPoint != null) {
                val result = springAnalyzer.analyzeInjection(injectionPoint, implementations)
                result.selectedImplementations
            } else {
                implementations
            }

        return filteredImplementations.mapNotNull { implClass ->
            findImplementationMethod(implClass, baseMethod)?.let { implMethod ->
                ImplementationInfo(
                    implementationMethod = implMethod,
                )
            }
        }
    }

    private fun shouldResolveOverrides(
        method: PsiMethod,
        containingClass: PsiClass,
    ): Boolean {
        if (method.isConstructor) return false
        if (method.hasModifierProperty(PsiModifier.STATIC)) return false
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) return false
        if (method.hasModifierProperty(PsiModifier.FINAL)) return false
        if (!containingClass.isInterface && containingClass.hasModifierProperty(PsiModifier.FINAL)) {
            return false
        }
        return true
    }

    private fun findInheritingClasses(baseClass: PsiClass): List<PsiClass> {
        val key = baseClass.qualifiedName ?: return emptyList()
        val cache = inheritingClassesCache.value
        val pointers =
            cache.computeIfAbsent(key) {
                val fileIndex = ProjectFileIndex.getInstance(project)
                val classes =
                    ClassInheritorsSearch
                        .search(
                            baseClass,
                            GlobalSearchScope.projectScope(project),
                            true,
                        ).findAll()
                        .filter { implClass ->
                            if (implClass.isInterface) {
                                false
                            } else {
                                val virtualFile = implClass.containingFile?.virtualFile
                                if (virtualFile == null) {
                                    true
                                } else {
                                    val orderEntries = fileIndex.getOrderEntriesForFile(virtualFile)
                                    if (orderEntries.any { entry -> entry is LibraryOrderEntry || entry is JdkOrderEntry }) {
                                        return@filter false
                                    }
                                    if (fileIndex.isInLibrary(virtualFile) ||
                                        fileIndex.isInLibrarySource(virtualFile) ||
                                        fileIndex.isInLibraryClasses(virtualFile)
                                    ) {
                                        return@filter false
                                    }
                                    true
                                }
                            }
                        }.toList()
                toSmartPointerList(project, classes)
            }
        return resolveValidPointers(pointers)
    }

    private fun findImplementationMethod(
        implClass: PsiClass,
        baseMethod: PsiMethod,
    ): PsiMethod? {
        val baseOwner = baseMethod.containingClass?.qualifiedName ?: "Unknown"
        val signature = baseMethod.getSignature(PsiSubstitutor.EMPTY).toString()
        val key = "${implClass.qualifiedName}#$baseOwner#$signature"

        val lookupResult =
            implementationMethodCache.value.computeIfAbsent(key) {
                implClass
                    .findMethodsByName(baseMethod.name, true) // true = search in superclasses
                    .firstOrNull { method ->
                        methodSignaturesMatch(method, baseMethod) &&
                            !method.hasModifierProperty(PsiModifier.ABSTRACT)
                    }?.let { MethodLookupResult.Found(toSmartPointer(project, it)) }
                    ?: MethodLookupResult.Missing
            }

        return when (lookupResult) {
            is MethodLookupResult.Found -> lookupResult.methodPointer.element
            MethodLookupResult.Missing -> null
        }
    }

    private fun methodSignaturesMatch(
        implMethod: PsiMethod,
        baseMethod: PsiMethod,
    ): Boolean {
        if (implMethod.parameterList.parametersCount != baseMethod.parameterList.parametersCount) {
            return false
        }
        if (implMethod.isEquivalentTo(baseMethod)) return true
        return implMethod.findSuperMethods(true).any { superMethod ->
            superMethod.isEquivalentTo(baseMethod)
        }
    }
}
