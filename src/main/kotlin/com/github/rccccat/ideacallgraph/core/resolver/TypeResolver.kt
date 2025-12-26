package com.github.rccccat.ideacallgraph.core.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

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

  /** Finds a class by name, with package context awareness. */
  fun findClassByName(
      qualifiedName: String,
      contextElement: PsiElement?,
  ): PsiClass? {
    val scope = GlobalSearchScope.allScope(project)
    if (qualifiedName.contains(".")) {
      return javaPsiFacade.findClass(qualifiedName, scope)
    }

    val contextPackage =
        (contextElement?.containingFile as? PsiJavaFile)?.packageName?.takeIf { it.isNotBlank() }

    if (contextPackage != null) {
      javaPsiFacade.findClass("$contextPackage.$qualifiedName", scope)?.let {
        return it
      }
    }

    return PsiShortNamesCache.getInstance(project)
        .getClassesByName(qualifiedName, scope)
        .firstOrNull()
  }

  private fun extractTypeArgumentFromText(
      typeText: String,
      rawTypeName: String,
  ): String? {
    val typeArgs = typeText.substringAfter("<", "").substringBeforeLast(">", "")
    if (typeArgs.isBlank()) return null

    val parts = typeArgs.split(",").map { it.trim() }
    return when (rawTypeName) {
      "java.util.Map",
      "Map",
      -> parts.getOrNull(1)

      else -> parts.firstOrNull()
    }
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
