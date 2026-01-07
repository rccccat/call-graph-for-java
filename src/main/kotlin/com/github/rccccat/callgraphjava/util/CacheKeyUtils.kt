package com.github.rccccat.callgraphjava.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor

/** to avoid collisions for anonymous/local classes. */

/**
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

/** follows buildClassKey rules. */
fun buildMethodKey(method: PsiMethod): String {
  val classKey = method.containingClass?.let { buildClassKey(it) } ?: "UnknownClass"
  val signature = method.getSignature(PsiSubstitutor.EMPTY).toString()
  return "$classKey#$signature"
}

/**  */
fun buildMethodKeyWithParams(method: PsiMethod): String {
  val classKey = method.containingClass?.let { buildClassKey(it) } ?: "UnknownClass"
  val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
  return "$classKey#${method.name}($paramTypes)"
}
