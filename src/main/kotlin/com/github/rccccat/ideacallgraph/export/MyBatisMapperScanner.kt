package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.util.MyBatisAnnotations
import com.github.rccccat.ideacallgraph.util.findAnnotatedClasses
import com.github.rccccat.ideacallgraph.util.matchesQualifiedName
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile

/**
 * Scanner for finding all MyBatis mapper methods in the project. It combines annotation-based
 * detection with XML namespace mappings.
 */
class MyBatisMapperScanner(
    private val project: Project,
    private val scanAllXml: Boolean,
) {
  fun scanAllMapperMethods(indicator: ProgressIndicator): List<PsiMethod> {
    val scope = GlobalSearchScope.allScope(project)
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val mapperClasses = mutableSetOf<PsiClass>()
    val xmlNamespaces = mutableSetOf<String>()

    indicator.isIndeterminate = false
    indicator.text = "Scanning MyBatis XML mappers..."

    val xmlFiles =
        ReadAction.compute<Collection<VirtualFile>, Exception> {
          FilenameIndex.getAllFilesByExt(project, "xml", scope)
        }
    val candidateXmlFiles =
        if (scanAllXml) {
          xmlFiles
        } else {
          xmlFiles.filter { file -> isLikelyMapperXml(file) }
        }

    for ((index, file) in candidateXmlFiles.withIndex()) {
      indicator.checkCanceled()
      indicator.fraction =
          if (candidateXmlFiles.isNotEmpty()) {
            (index + 1).toDouble() / candidateXmlFiles.size * 0.5
          } else {
            0.0
          }

      val namespace = readMapperNamespace(file) ?: continue
      xmlNamespaces.add(namespace)
    }

    indicator.text = "Scanning MyBatis mapper annotations..."
    for ((index, annotationQualifiedName) in
        MyBatisAnnotations.mapperAnnotationQualifiedNames.withIndex()) {
      indicator.checkCanceled()
      indicator.fraction =
          0.5 + index.toDouble() / MyBatisAnnotations.mapperAnnotationQualifiedNames.size * 0.3

      val annotatedClasses = findAnnotatedClasses(javaPsiFacade, annotationQualifiedName, scope)
      mapperClasses.addAll(annotatedClasses)
    }

    indicator.text = "Resolving mapper classes..."
    indicator.isIndeterminate = true

    val resolvedClasses = mutableSetOf<PsiClass>()
    for (namespace in xmlNamespaces) {
      val psiClass =
          ReadAction.compute<PsiClass?, Exception> { javaPsiFacade.findClass(namespace, scope) }
      if (psiClass != null) {
        resolvedClasses.add(psiClass)
      }
    }

    mapperClasses.addAll(resolvedClasses)

    val filteredMapperClasses =
        ReadAction.compute<List<PsiClass>, Exception> {
          mapperClasses.filter { psiClass ->
            val qualifiedName = psiClass.qualifiedName
            val hasXmlMapping = qualifiedName != null && xmlNamespaces.contains(qualifiedName)
            val hasSqlAnnotations = hasSqlAnnotations(psiClass)

            psiClass.isInterface || hasXmlMapping || hasSqlAnnotations
          }
        }

    val methods = mutableSetOf<PsiMethod>()
    for (mapperClass in filteredMapperClasses) {
      indicator.checkCanceled()
      ReadAction.compute<Unit, Exception> { methods.addAll(mapperClass.methods) }
    }

    indicator.text = "Found ${methods.size} mapper methods"
    indicator.fraction = 1.0
    indicator.isIndeterminate = false

    return methods.toList()
  }

  private fun readMapperNamespace(file: VirtualFile): String? {
    return ReadAction.compute<String?, Exception> {
      val psiFile =
          PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null
      val rootTag = psiFile.rootTag ?: return@compute null
      if (rootTag.name != "mapper") return@compute null
      rootTag.getAttributeValue("namespace")
    }
  }

  private fun isLikelyMapperXml(file: VirtualFile): Boolean {
    val name = file.name
    if (name.endsWith("Mapper.xml", ignoreCase = true)) {
      return true
    }
    val normalizedPath = file.path.replace('\\', '/')
    return normalizedPath.contains("/mapper/") || normalizedPath.contains("/mappers/")
  }

  private fun hasSqlAnnotations(psiClass: PsiClass): Boolean =
      psiClass.methods.any { method ->
        val modifierList = method.modifierList
        modifierList.annotations.any { annotation ->
          matchesQualifiedName(annotation.qualifiedName, MyBatisAnnotations.sqlAnnotations)
        }
      }
}
