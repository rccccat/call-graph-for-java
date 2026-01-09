package com.github.rccccat.callgraphjava.core.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope

/** Resolver for PSI types and classes. */
class TypeResolver(
    private val project: Project,
) {
  private val javaPsiFacade: JavaPsiFacade = JavaPsiFacade.getInstance(project)

  /** Resolves a PsiType to its corresponding PsiClass. */
  fun resolveClassFromType(
      type: PsiType?,
      contextElement: PsiElement?,
  ): PsiClass? =
      when (type) {
        is PsiClassType -> type.resolve() ?: findClassByName(type.canonicalText, contextElement)
        is PsiArrayType -> resolveClassFromType(type.componentType, contextElement)
        else -> null
      }

  /** Finds a class by name, with import and package context awareness. */
  fun findClassByName(
      qualifiedName: String,
      contextElement: PsiElement?,
  ): PsiClass? {
    val scope = GlobalSearchScope.allScope(project)

    if (qualifiedName.contains(".")) {
      return javaPsiFacade.findClass(qualifiedName, scope)
    }

    val javaFile = contextElement?.containingFile as? PsiJavaFile

    val contextPackage = javaFile?.packageName?.takeIf { it.isNotBlank() }
    if (contextPackage != null) {
      javaPsiFacade.findClass("$contextPackage.$qualifiedName", scope)?.let {
        return it
      }
    }

    if (javaFile != null) {
      val importList = javaFile.importList
      importList?.importStatements?.forEach { importStatement ->
        if (!importStatement.isOnDemand) {
          val importedName = importStatement.qualifiedName
          if (importedName != null && importedName.endsWith(".$qualifiedName")) {
            javaPsiFacade.findClass(importedName, scope)?.let {
              return it
            }
          }
        }
      }

      importList?.importStatements?.forEach { importStatement ->
        if (importStatement.isOnDemand) {
          val packageName = importStatement.qualifiedName
          if (packageName != null) {
            javaPsiFacade.findClass("$packageName.$qualifiedName", scope)?.let {
              return it
            }
          }
        }
      }
    }

    javaPsiFacade.findClass("java.lang.$qualifiedName", scope)?.let {
      return it
    }

    return null
  }

  /** -> ["String", "List<Integer>"] */
  private fun splitTypeArguments(typeArgs: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0

    for (char in typeArgs) {
      when (char) {
        '<' -> {
          depth++
          current.append(char)
        }

        '>' -> {
          depth--
          current.append(char)
        }

        ',' -> {
          if (depth == 0) {
            result.add(current.toString().trim())
            current.clear()
          } else {
            current.append(char)
          }
        }

        else -> {
          current.append(char)
        }
      }
    }

    if (current.isNotBlank()) {
      result.add(current.toString().trim())
    }

    return result
  }
}
