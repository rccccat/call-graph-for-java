package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.cache.CallGraphCacheManager
import com.github.rccccat.ideacallgraph.framework.spring.hasMappingOnMethodOrSuper
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.github.rccccat.ideacallgraph.util.SpringAnnotations
import com.github.rccccat.ideacallgraph.util.findAnnotatedClasses
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotationOrMeta
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch

/**
 * Scanner for finding all Spring API endpoints in the project. Scans @Controller/@RestController
 * classes and extracts methods with @RequestMapping annotations.
 */
class SpringApiScanner(
    private val project: Project,
    private val cacheManager: CallGraphCacheManager,
) {
    private val scanIndicator = ThreadLocal<ProgressIndicator?>()
    private val controllerAnnotationClassesCache =
        cacheManager.createCachedValue {
            val javaPsiFacade = JavaPsiFacade.getInstance(project)
            collectControllerAnnotationClasses(
                javaPsiFacade,
                GlobalSearchScope.projectScope(project),
                resolveIndicator(),
            )
        }
    private val fullScanBatchSize = 200

    private val controllerClassesCache =
        cacheManager.createCachedValue {
            val scope = GlobalSearchScope.projectScope(project)
            collectControllerClasses(
                controllerAnnotationClassesCache.value,
                scope,
                resolveIndicator(),
            )
        }
    private val endpointsCache =
        cacheManager.createCachedValue { scanAllEndpointsInternal(resolveIndicator()) }

    /**
     * Scans the project for all Spring API endpoint methods.
     *
     * @param indicator Progress indicator for showing progress and supporting cancellation
     * @return List of PsiMethod objects representing API endpoints
     */
    fun scanAllEndpoints(indicator: ProgressIndicator): List<PsiMethod> {
        scanIndicator.set(indicator)
        try {
            val endpoints = endpointsCache.value
            indicator.text = "Found ${endpoints.size} API endpoints"
            indicator.fraction = 1.0
            return endpoints
        } finally {
            scanIndicator.remove()
        }
    }

    private fun resolveIndicator(): ProgressIndicator = scanIndicator.get() ?: EmptyProgressIndicator()

    private fun forEachClassInBatches(
        scope: GlobalSearchScope,
        indicator: ProgressIndicator,
        process: (PsiClass) -> Unit,
    ) {
        val query = AllClassesSearch.search(scope, project)
        val iterator = ReadAction.compute<Iterator<PsiClass>, Exception> { query.iterator() }
        indicator.isIndeterminate = true

        while (true) {
            val processedInBatch =
                ReadAction.compute<Int, Exception> {
                    var count = 0
                    while (iterator.hasNext() && count < fullScanBatchSize) {
                        process(iterator.next())
                        count++
                    }
                    count
                }
            if (processedInBatch == 0) break
            indicator.checkCanceled()
        }

        indicator.isIndeterminate = false
    }

    private fun scanAllEndpointsInternal(indicator: ProgressIndicator): List<PsiMethod> {
        val endpoints = mutableListOf<PsiMethod>()
        val controllerClasses = controllerClassesCache.value
        indicator.text = "Scanning Spring controller methods..."
        indicator.isIndeterminate = false

        for ((index, controllerClass) in controllerClasses.withIndex()) {
            indicator.checkCanceled()
            indicator.fraction =
                if (controllerClasses.isNotEmpty()) {
                    0.6 + (index + 1).toDouble() / controllerClasses.size * 0.4
                } else {
                    0.6
                }
            ReadAction.compute<Unit, Exception> {
                extractEndpointsFromController(controllerClass, endpoints)
            }
        }

        indicator.text = "Found ${endpoints.size} API endpoints"
        indicator.fraction = 1.0

        return endpoints
    }

    /** Extracts all API endpoint methods from a controller class. */
    private fun extractEndpointsFromController(
        controllerClass: PsiClass,
        endpoints: MutableList<PsiMethod>,
    ) {
        for (method in controllerClass.methods) {
            if (hasMappingOnMethodOrSuper(method)) {
                endpoints.add(method)
            }
        }
    }

    private fun collectControllerAnnotationClasses(
        javaPsiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
        indicator: ProgressIndicator,
    ): List<PsiClass> {
        val result = LinkedHashSet<PsiClass>()
        val queue = ArrayDeque<PsiClass>()

        for (
        (index, annotationQualifiedName) in
        SpringAnnotations.controllerAnnotationQualifiedNames.withIndex()
        ) {
            indicator.checkCanceled()
            indicator.fraction =
                index.toDouble() / SpringAnnotations.controllerAnnotationQualifiedNames.size * 0.2

            val annotationClass =
                ReadAction.compute<PsiClass?, Exception> {
                    // Use allScope to find annotation classes defined in Spring libraries
                    javaPsiFacade.findClass(
                        annotationQualifiedName,
                        GlobalSearchScope.allScope(project),
                    )
                } ?: continue
            if (result.add(annotationClass)) {
                queue.add(annotationClass)
            }
        }

        while (queue.isNotEmpty()) {
            indicator.checkCanceled()
            val current = queue.removeFirst()
            val annotatedClasses = findAnnotatedClasses(current, scope)
            for (candidate in annotatedClasses) {
                if (!candidate.isAnnotationType) continue
                if (result.add(candidate)) {
                    queue.add(candidate)
                }
            }
        }

        val settings = CallGraphProjectSettings.getInstance(project)
        if (settings.springEnableFullScan) {
            indicator.text = "Full scan: searching Spring controller annotations..."
            forEachClassInBatches(scope, indicator) { candidate ->
                if (!candidate.isAnnotationType) return@forEachClassInBatches
                if (hasAnyAnnotationOrMeta(candidate, SpringAnnotations.controllerAnnotations)) {
                    result.add(candidate)
                }
            }
        }

        return result.toList()
    }

    private fun collectControllerClasses(
        controllerAnnotationClasses: List<PsiClass>,
        scope: GlobalSearchScope,
        indicator: ProgressIndicator,
    ): List<PsiClass> {
        val result = LinkedHashSet<PsiClass>()
        indicator.text = "Scanning Spring controllers..."
        indicator.isIndeterminate = false
        for ((index, annotationClass) in controllerAnnotationClasses.withIndex()) {
            indicator.checkCanceled()
            indicator.fraction =
                if (controllerAnnotationClasses.isNotEmpty()) {
                    index.toDouble() / controllerAnnotationClasses.size * 0.6
                } else {
                    0.0
                }

            val annotatedClasses = findAnnotatedClasses(annotationClass, scope)
            for (candidate in annotatedClasses) {
                if (!candidate.isAnnotationType) {
                    result.add(candidate)
                }
            }
        }

        val settings = CallGraphProjectSettings.getInstance(project)
        if (settings.springEnableFullScan) {
            indicator.text = "Full scan: searching Spring controller classes..."
            forEachClassInBatches(scope, indicator) { candidate ->
                if (candidate.isAnnotationType) return@forEachClassInBatches
                if (hasAnyAnnotationOrMeta(candidate, SpringAnnotations.controllerAnnotations)) {
                    result.add(candidate)
                }
            }
        }
        return result.toList()
    }
}
