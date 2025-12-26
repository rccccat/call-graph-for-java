package com.github.rccccat.ideacallgraph.util

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifierListOwner

fun matchesQualifiedName(
    qualifiedName: String?,
    annotations: Set<String>,
): Boolean {
  if (qualifiedName == null) return false
  return annotations.any { name -> qualifiedName == name || qualifiedName.endsWith(name) }
}

fun hasAnyAnnotation(
    owner: PsiModifierListOwner?,
    annotations: Set<String>,
): Boolean {
  val modifierList = owner?.modifierList ?: return false
  return modifierList.annotations.any { annotation ->
    matchesAnnotationName(annotation, annotations)
  }
}

fun hasAnyAnnotation(
    annotation: PsiAnnotation?,
    annotations: Set<String>,
): Boolean = annotation != null && matchesAnnotationName(annotation, annotations)

fun hasAnyAnnotationOrMeta(
    owner: PsiModifierListOwner?,
    annotations: Set<String>,
): Boolean {
  val modifierList = owner?.modifierList ?: return false
  return modifierList.annotations.any { annotation ->
    hasAnyAnnotationOrMeta(annotation, annotations)
  }
}

fun hasAnyAnnotationOrMeta(
    annotation: PsiAnnotation?,
    annotations: Set<String>,
): Boolean {
  if (annotation == null) return false
  if (matchesAnnotationName(annotation, annotations)) return true
  val annotationClass =
      annotation.resolveAnnotationType()
          ?: (annotation.nameReferenceElement?.resolve() as? PsiClass)
          ?: return false
  return hasMetaAnnotation(annotationClass, annotations, mutableSetOf())
}

private fun matchesAnnotationName(
    annotation: PsiAnnotation,
    annotations: Set<String>,
): Boolean {
  val qualifiedName = annotation.qualifiedName
  if (matchesQualifiedName(qualifiedName, annotations)) return true
  val simpleName = annotation.nameReferenceElement?.referenceName
  return matchesQualifiedName(simpleName, annotations)
}

private fun hasMetaAnnotation(
    annotationClass: PsiClass,
    annotations: Set<String>,
    visited: MutableSet<String>,
): Boolean {
  val qualifiedName = annotationClass.qualifiedName ?: annotationClass.name ?: return false
  if (!visited.add(qualifiedName)) return false
  val modifierList = annotationClass.modifierList ?: return false
  for (metaAnnotation in modifierList.annotations) {
    if (matchesQualifiedName(metaAnnotation.qualifiedName, annotations)) return true
    val metaClass = metaAnnotation.resolveAnnotationType() ?: continue
    if (hasMetaAnnotation(metaClass, annotations, visited)) return true
  }
  return false
}

fun extractStringValues(attributeValue: PsiAnnotationMemberValue): List<String> =
    when (attributeValue) {
      is PsiLiteralExpression -> {
        val value = attributeValue.value as? String
        if (value == null) emptyList() else listOf(value)
      }

      is PsiArrayInitializerMemberValue -> {
        attributeValue.initializers.mapNotNull { initializer ->
          (initializer as? PsiLiteralExpression)?.value as? String
        }
      }

      else -> {
        emptyList()
      }
    }

fun extractFirstStringValue(attributeValue: PsiAnnotationMemberValue): String? =
    extractStringValues(attributeValue).firstOrNull()
