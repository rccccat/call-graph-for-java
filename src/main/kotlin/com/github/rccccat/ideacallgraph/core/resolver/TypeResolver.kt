package com.github.rccccat.ideacallgraph.core.resolver

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

  /** Resolves the element type from a collection type. */
  fun resolveCollectionElementClass(
      type: PsiType,
      contextElement: PsiElement?,
  ): PsiClass? {
    val classType = type as? PsiClassType ?: return null
    val resolved = classType.resolve()
    val qualifiedName = resolved?.qualifiedName
    val parameters = classType.parameters

    if (qualifiedName == null) {
      val rawName = classType.canonicalText.substringBefore("<")
      if (rawName !in COLLECTION_TYPES) {
        return null
      }
      val fallbackName = extractTypeArgumentFromText(classType.canonicalText, rawName)
      return fallbackName?.let { findClassByName(it, contextElement) }
    }

    val fallbackTypeName =
        if (parameters.isEmpty()) {
          extractTypeArgumentFromText(classType.canonicalText, qualifiedName)
        } else {
          null
        }
    val fallbackType = fallbackTypeName?.let { findClassByName(it, contextElement) }

    return when (qualifiedName) {
      "java.util.List",
      "java.util.Set",
      "java.util.Collection",
      -> {
        resolveClassFromType(parameters.firstOrNull(), contextElement) ?: fallbackType
      }

      "java.util.Map" -> {
        resolveClassFromType(parameters.getOrNull(1), contextElement) ?: fallbackType
      }

      else -> {
        null
      }
    }
  }

  /** Finds a class by name, with import and package context awareness. */
  fun findClassByName(
      qualifiedName: String,
      contextElement: PsiElement?,
  ): PsiClass? {
    val scope = GlobalSearchScope.allScope(project)

    // Fully qualified name - direct lookup
    if (qualifiedName.contains(".")) {
      return javaPsiFacade.findClass(qualifiedName, scope)
    }

    val javaFile = contextElement?.containingFile as? PsiJavaFile

    // Resolution order per JLS:
    // 1. Current package (highest priority)
    val contextPackage = javaFile?.packageName?.takeIf { it.isNotBlank() }
    if (contextPackage != null) {
      javaPsiFacade.findClass("$contextPackage.$qualifiedName", scope)?.let {
        return it
      }
    }

    // 2. Explicit single-type imports
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

      // 3. On-demand (wildcard) imports
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

    // 4. java.lang is implicitly imported
    javaPsiFacade.findClass("java.lang.$qualifiedName", scope)?.let {
      return it
    }

    return null
  }

  private fun extractTypeArgumentFromText(
      typeText: String,
      rawTypeName: String,
  ): String? {
    val typeArgs = typeText.substringAfter("<", "").substringBeforeLast(">", "")
    if (typeArgs.isBlank()) return null

    // Parse with bracket awareness to handle nested generics
    val parts = splitTypeArguments(typeArgs)
    return when (rawTypeName) {
      "java.util.Map",
      "Map",
      -> parts.getOrNull(1)

      else -> parts.firstOrNull()
    }
  }

  /**
   * Splits type arguments respecting nested generic brackets. For example: "String, List<Integer>"
   * -> ["String", "List<Integer>"]
   */
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

        else -> current.append(char)
      }
    }

    if (current.isNotBlank()) {
      result.add(current.toString().trim())
    }

    return result
  }

  companion object {
    private val COLLECTION_TYPES =
        setOf(
            "java.util.List",
            "java.util.Set",
            "java.util.Collection",
            "java.util.Map",
            "List",
            "Set",
            "Collection",
            "Map",
        )
  }
}
