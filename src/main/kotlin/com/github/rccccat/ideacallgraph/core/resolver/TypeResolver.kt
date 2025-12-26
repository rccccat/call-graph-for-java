package com.github.rccccat.ideacallgraph.core.resolver

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.psi.*

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
    if (qualifiedName.contains(".")) {
      return javaPsiFacade.findClass(qualifiedName, project.allScope())
    }

    val contextPackage =
        (contextElement?.containingFile as? PsiJavaFile)?.packageName?.takeIf { it.isNotBlank() }

    if (contextPackage != null) {
      javaPsiFacade.findClass("$contextPackage.$qualifiedName", project.allScope())?.let {
        return it
      }
    }

    return PsiShortNamesCache.getInstance(project)
        .getClassesByName(qualifiedName, project.allScope())
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

    private val KOTLIN_LIST_TYPES =
        setOf(
            "List",
            "MutableList",
            "Collection",
            "MutableCollection",
            "Set",
            "MutableSet",
            "java.util.List",
            "java.util.Collection",
            "java.util.Set",
            "kotlin.collections.List",
            "kotlin.collections.MutableList",
            "kotlin.collections.Collection",
            "kotlin.collections.MutableCollection",
            "kotlin.collections.Set",
            "kotlin.collections.MutableSet",
        )

    private val KOTLIN_MAP_TYPES =
        setOf(
            "Map",
            "MutableMap",
            "java.util.Map",
            "kotlin.collections.Map",
            "kotlin.collections.MutableMap",
        )
  }

  /** Resolves the receiver class for a Kotlin call expression. */
  fun resolveKotlinReceiverClass(
      function: KtNamedFunction,
      expression: KtCallExpression,
  ): PsiClass? {
    val dotQualified = expression.parent as? KtDotQualifiedExpression ?: return null
    val receiver = dotQualified.receiverExpression
    val packageName = function.containingKtFile.packageFqName.asString()

    val typeName =
        when (receiver) {
          is KtArrayAccessExpression -> {
            resolveKotlinElementTypeName(receiver)
          }

          is KtCallExpression -> {
            receiver.calleeExpression?.text
          }

          is KtThisExpression,
          is KtSuperExpression,
          -> {
            val containingClass = PsiTreeUtil.getParentOfType(function, KtClass::class.java)
            containingClass?.fqName?.asString()
          }

          else -> {
            resolveKotlinTypeName(receiver)
          }
        }

    val normalized = typeName?.let { normalizeTypeName(it) } ?: return null
    val candidates =
        if (normalized.contains(".")) {
          listOf(normalized)
        } else {
          listOf("$packageName.$normalized", normalized)
        }

    for (candidate in candidates) {
      val psiClass = javaPsiFacade.findClass(candidate, project.allScope())
      if (psiClass != null) {
        return psiClass
      }
    }

    return null
  }

  private fun normalizeTypeName(typeName: String): String {
    val trimmed = typeName.trim().removeSuffix("?")
    val withoutGenerics = trimmed.substringBefore("<")
    return withoutGenerics.removeSuffix("()")
  }

  private fun resolveKotlinTypeName(expression: KtExpression?): String? =
      when (expression) {
        is KtNameReferenceExpression -> {
          when (val resolved = expression.references.firstOrNull()?.resolve()) {
            is KtProperty -> resolved.typeReference?.text
            is KtParameter -> resolved.typeReference?.text
            is PsiClass -> resolved.qualifiedName
            else -> null
          }
        }

        is KtDotQualifiedExpression -> {
          resolveKotlinTypeName(expression.receiverExpression)
        }

        is KtSafeQualifiedExpression -> {
          resolveKotlinTypeName(expression.receiverExpression)
        }

        is KtArrayAccessExpression -> {
          resolveKotlinTypeName(expression.arrayExpression)
        }

        is KtCallExpression -> {
          expression.calleeExpression?.text
        }

        else -> {
          null
        }
      }

  private fun resolveKotlinElementTypeName(expression: KtExpression?): String? {
    val typeName = resolveKotlinTypeName(expression) ?: return null
    return extractKotlinCollectionElementType(typeName) ?: typeName
  }

  private fun extractKotlinCollectionElementType(typeName: String): String? {
    val rawName = normalizeTypeName(typeName)
    val typeArgs = typeName.substringAfter("<", "").substringBeforeLast(">", "")
    if (typeArgs.isBlank()) return null

    val parts = typeArgs.split(",").map { it.trim() }
    return when (rawName) {
      in KOTLIN_LIST_TYPES -> parts.firstOrNull()
      in KOTLIN_MAP_TYPES -> parts.getOrNull(1)
      else -> null
    }
  }
}
