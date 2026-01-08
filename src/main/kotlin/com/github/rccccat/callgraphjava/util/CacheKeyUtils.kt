package com.github.rccccat.callgraphjava.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor

/** 规避匿名/局部类冲突的键生成工具。 */

/**
 * 1. qualifiedName（如果存在）
 * 2. filePath:offset（匿名/局部类）
 * 3. filePath:className
 * 4. className 或 "AnonymousClass"
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

/** 遵循 buildClassKey 规则。 */
fun buildMethodKey(method: PsiMethod): String {
  val classKey = method.containingClass?.let { buildClassKey(it) } ?: "UnknownClass"
  val signature = method.getSignature(PsiSubstitutor.EMPTY).toString()
  return "$classKey#$signature"
}

/** 生成带参数类型的稳定键。 */
fun buildMethodKeyWithParams(method: PsiMethod): String {
  val classKey = method.containingClass?.let { buildClassKey(it) } ?: "UnknownClass"
  val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
  return "$classKey#${method.name}($paramTypes)"
}

/** 构建不含文件路径的类键。 */
fun buildQualifiedClassKeyWithoutPath(psiClass: PsiClass): String {
  psiClass.qualifiedName?.let {
    return it
  }
  findNearestQualifiedOuterClass(psiClass)?.let {
    return it
  }
  return psiClass.name ?: "AnonymousClass"
}

/** 构建不含文件路径的调用图节点 ID。 */
fun buildCallGraphNodeId(
    method: PsiMethod,
    lineNumber: Int,
    offset: Int,
): String {
  val classKey =
      method.containingClass?.let { buildQualifiedClassKeyWithoutPath(it) } ?: "UnknownClass"
  val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
  val anchor =
      when {
        lineNumber > 0 -> "L$lineNumber"
        offset >= 0 -> "O$offset"
        else -> "O-1"
      }
  return "$classKey#${method.name}($paramTypes)@$anchor"
}

private fun findNearestQualifiedOuterClass(psiClass: PsiClass): String? {
  var current = psiClass.containingClass
  while (current != null) {
    current.qualifiedName?.let {
      return it
    }
    current = current.containingClass
  }
  return null
}
