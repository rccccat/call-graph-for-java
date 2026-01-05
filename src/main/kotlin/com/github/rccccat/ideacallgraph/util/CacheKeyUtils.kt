package com.github.rccccat.ideacallgraph.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor

/**
 * Unified cache key utilities for PSI elements. Ensures consistent key generation across all caches
 * to avoid collisions for anonymous/local classes.
 */

/**
 * Builds a unique key for a PsiClass. Priority order:
 * 1. qualifiedName (if available)
 * 2. filePath:offset (for anonymous/local classes)
 * 3. filePath:className
 * 4. className or "AnonymousClass"
 */
fun buildClassKey(psiClass: PsiClass): String {
  psiClass.qualifiedName?.let {
    return it
  }
  val filePath = psiClass.containingFile?.virtualFile?.path
  val offset = psiClass.textRange?.startOffset ?: -1
  if (filePath != null && offset >= 0) {
    return "$filePath:$offset"
  }
  val className = psiClass.name ?: "AnonymousClass"
  return if (filePath != null) "$filePath:$className" else className
}

/**
 * Builds a unique key for a PsiMethod. Format: classKey#methodSignature Priority for classKey
 * follows buildClassKey rules.
 */
fun buildMethodKey(method: PsiMethod): String {
  val classKey = method.containingClass?.let { buildClassKey(it) } ?: "UnknownClass"
  val signature = method.getSignature(PsiSubstitutor.EMPTY).toString()
  return "$classKey#$signature"
}

/**
 * Builds a unique key for a PsiMethod with parameter types. Format: classKey#methodName(paramTypes)
 * Useful for MyBatis-style caching where canonical param types are needed.
 */
fun buildMethodKeyWithParams(method: PsiMethod): String {
  val classKey = method.containingClass?.let { buildClassKey(it) } ?: "UnknownClass"
  val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
  return "$classKey#${method.name}($paramTypes)"
}
