package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.framework.spring.hasMappingOnMethodOrSuper
import com.github.rccccat.ideacallgraph.util.SpringAnnotations
import com.github.rccccat.ideacallgraph.util.findAnnotatedClasses
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotationOrMeta
import com.intellij.openapi.application.ReadAction
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
) {
  /**
   * Scans the project for all Spring API endpoint methods.
   *
   * @param indicator Progress indicator for showing progress and supporting cancellation
   * @return List of PsiMethod objects representing API endpoints
   */
  fun scanAllEndpoints(indicator: ProgressIndicator): List<PsiMethod> {
    return ReadAction.compute<List<PsiMethod>, Exception> {
      val endpoints = mutableListOf<PsiMethod>()
      val scope = GlobalSearchScope.projectScope(project)
      val javaPsiFacade = JavaPsiFacade.getInstance(project)
      val controllerClasses = LinkedHashSet<PsiClass>()

      indicator.text = "Scanning Spring controllers..."
      indicator.isIndeterminate = false

      val controllerAnnotationClasses =
          collectControllerAnnotationClasses(
              javaPsiFacade,
              GlobalSearchScope.projectScope(project),
              indicator,
          )
      for ((index, annotationClass) in controllerAnnotationClasses.withIndex()) {
        indicator.checkCanceled()
        indicator.fraction =
            if (controllerAnnotationClasses.isNotEmpty()) {
              index.toDouble() / controllerAnnotationClasses.size * 0.6
            } else {
              0.0
            }

        val annotationQualifiedName = annotationClass.qualifiedName ?: continue
        val annotatedClasses = findAnnotatedClasses(javaPsiFacade, annotationQualifiedName, scope)
        for (candidate in annotatedClasses) {
          if (!candidate.isAnnotationType) {
            controllerClasses.add(candidate)
          }
        }
      }

      controllerClasses.addAll(collectControllerClassesByMeta(scope))

      for (controllerClass in controllerClasses) {
        indicator.checkCanceled()
        extractEndpointsFromController(controllerClass, endpoints)
      }

      indicator.text = "Found ${endpoints.size} API endpoints"
      indicator.fraction = 1.0

      endpoints
    }
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

    for ((index, annotationQualifiedName) in
        SpringAnnotations.controllerAnnotationQualifiedNames.withIndex()) {
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
      val qualifiedName = current.qualifiedName ?: continue

      val annotatedClasses = findAnnotatedClasses(javaPsiFacade, qualifiedName, scope)
      for (candidate in annotatedClasses) {
        if (!candidate.isAnnotationType) continue
        if (result.add(candidate)) {
          queue.add(candidate)
        }
      }
    }

    return result.toList()
  }

  private fun collectControllerClassesByMeta(
      scope: GlobalSearchScope,
  ): List<PsiClass> {
    val result = LinkedHashSet<PsiClass>()
    AllClassesSearch.search(scope, project).forEach { psiClass ->
      if (psiClass.isAnnotationType) return@forEach
      if (hasAnyAnnotationOrMeta(psiClass, SpringAnnotations.controllerAnnotations)) {
        result.add(psiClass)
      }
    }
    return result.toList()
  }
}
