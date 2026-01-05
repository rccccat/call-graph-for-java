package com.github.rccccat.ideacallgraph.framework.spring

import com.github.rccccat.ideacallgraph.util.SpringAnnotations
import com.github.rccccat.ideacallgraph.util.buildClassKey
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotationOrMeta
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor

internal fun hasMappingOnMethodOrSuper(method: PsiMethod): Boolean {
  val containingClass = method.containingClass ?: return false
  val cache = SpringMethodCache.getInstance(method.project).mappingIndexCache()
  val classKey = buildClassKey(containingClass)
  val index = cache.computeIfAbsent(classKey) { buildMethodMappingIndex(containingClass) }

  val methodSignature = methodSignatureKey(method)
  if (index.classMappingSignatures.contains(methodSignature)) {
    return true
  }

  val interfaceKey = interfaceMethodKey(method.name, method.parameterList.parametersCount)
  return index.interfaceMappingKeys.contains(interfaceKey)
}

private fun hasMappingNameFallback(method: PsiMethod): Boolean {
  val modifierList = method.modifierList ?: return false
  return modifierList.annotations.any { annotation ->
    val name =
        annotation.qualifiedName
            ?: annotation.nameReferenceElement?.referenceName
            ?: annotation.text.substringAfter("@").substringBefore("(").substringAfterLast(".")
    if (name.isBlank()) {
      false
    } else {
      val simpleName = name.substringAfterLast(".")
      SpringAnnotations.mappingAnnotations.contains(simpleName) || simpleName.endsWith("Mapping")
    }
  }
}

private fun collectAllInterfaces(psiClass: PsiClass): List<PsiClass> {
  val result = LinkedHashSet<PsiClass>()
  val queue = ArrayDeque<PsiClass>()
  val directInterfaces = psiClass.interfaces.toList() + resolveReferencedInterfaces(psiClass)
  directInterfaces.forEach { queue.add(it) }
  psiClass.supers.filter { it.isInterface }.forEach { queue.add(it) }

  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    if (!result.add(current)) continue
    current.interfaces.forEach { queue.add(it) }
    resolveReferencedInterfaces(current).forEach { queue.add(it) }
    current.supers.filter { it.isInterface }.forEach { queue.add(it) }
  }

  return result.filter { it.isInterface }
}

private fun resolveReferencedInterfaces(psiClass: PsiClass): List<PsiClass> {
  val project = psiClass.project
  val scope = psiClass.resolveScope
  val javaPsiFacade = JavaPsiFacade.getInstance(project)
  val packageName =
      (psiClass.containingFile as? PsiJavaFile)?.packageName?.takeIf { it.isNotBlank() }

  return psiClass.implementsListTypes.mapNotNull { type ->
    type.resolve()
        ?: run {
          val name = type.className ?: return@run null
          val qualifiedCandidate =
              if (name.contains(".")) name else packageName?.let { "$it.$name" } ?: name
          javaPsiFacade.findClass(qualifiedCandidate, scope)
        }
  }
}

private fun hasMappingIndicator(method: PsiMethod): Boolean =
    hasAnyAnnotationOrMeta(method, SpringAnnotations.mappingAnnotations) ||
        hasMappingNameFallback(method)

private fun buildMethodMappingIndex(psiClass: PsiClass): MethodMappingIndex {
  val classMappingSignatures = LinkedHashSet<String>()
  for (current in collectClassHierarchy(psiClass)) {
    for (method in current.methods) {
      if (hasMappingIndicator(method)) {
        classMappingSignatures.add(methodSignatureKey(method))
      }
    }
  }

  val interfaceMappingKeys = LinkedHashSet<String>()
  for (interfaceClass in collectAllInterfaces(psiClass)) {
    for (method in interfaceClass.methods) {
      if (hasMappingIndicator(method)) {
        interfaceMappingKeys.add(
            interfaceMethodKey(method.name, method.parameterList.parametersCount),
        )
      }
    }
  }

  return MethodMappingIndex(classMappingSignatures, interfaceMappingKeys)
}

private fun collectClassHierarchy(psiClass: PsiClass): List<PsiClass> {
  val result = ArrayList<PsiClass>()
  var current: PsiClass? = psiClass
  while (current != null) {
    result.add(current)
    current = current.superClass
  }
  return result
}

private fun methodSignatureKey(method: PsiMethod): String =
    method.getSignature(PsiSubstitutor.EMPTY).toString()

private fun interfaceMethodKey(
    name: String,
    paramCount: Int,
): String = "$name#$paramCount"
