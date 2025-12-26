package com.github.rccccat.ideacallgraph.framework.spring

import com.github.rccccat.ideacallgraph.util.SpringAnnotations
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotationOrMeta
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

internal fun hasMappingOnMethodOrSuper(method: PsiMethod): Boolean {
  if (hasMappingIndicator(method)) return true
  if (method.findSuperMethods(true).any { superMethod -> hasMappingIndicator(superMethod) }) {
    return true
  }
  return hasInterfaceMapping(method)
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

private fun hasInterfaceMapping(method: PsiMethod): Boolean {
  val containingClass = method.containingClass ?: return false
  val paramCount = method.parameterList.parametersCount
  val interfaceClasses = collectAllInterfaces(containingClass)
  if (interfaceClasses.any { interfaceClass ->
    hasInterfaceMethodMapping(interfaceClass, method.name, paramCount)
  }) {
    return true
  }
  return hasInterfaceMappingByName(containingClass, method.name, paramCount)
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
  val scope = GlobalSearchScope.allScope(project)
  val javaPsiFacade = JavaPsiFacade.getInstance(project)
  val packageName =
      (psiClass.containingFile as? PsiJavaFile)?.packageName?.takeIf { it.isNotBlank() }
  val shortNamesCache = PsiShortNamesCache.getInstance(project)

  return psiClass.implementsListTypes.mapNotNull { type ->
    type.resolve()
        ?: run {
          val name = type.className ?: return@run null
          val qualifiedCandidate =
              if (name.contains(".")) name else packageName?.let { "$it.$name" } ?: name
          javaPsiFacade.findClass(qualifiedCandidate, scope)
              ?: shortNamesCache.getClassesByName(name, scope).firstOrNull()
        }
  }
}

private fun hasInterfaceMappingByName(
    psiClass: PsiClass,
    methodName: String,
    paramCount: Int,
): Boolean {
  val interfaceNames = collectInterfaceTypeNames(psiClass)
  if (interfaceNames.isEmpty()) return false
  val scope = GlobalSearchScope.allScope(psiClass.project)
  val shortNamesCache = PsiShortNamesCache.getInstance(psiClass.project)
  return shortNamesCache.getMethodsByName(methodName, scope).any { candidate ->
    val owner = candidate.containingClass ?: return@any false
    if (!owner.isInterface) return@any false
    if (!matchesInterfaceName(owner, interfaceNames)) return@any false
    candidate.parameterList.parametersCount == paramCount && hasMappingIndicator(candidate)
  }
}

private fun collectInterfaceTypeNames(psiClass: PsiClass): Set<String> {
  val names = LinkedHashSet<String>()
  psiClass.implementsList?.referenceElements?.forEach { reference ->
    reference.qualifiedName?.let { names.add(it) }
    reference.referenceName?.let { names.add(it) }
  }
  psiClass.superTypes.forEach { type ->
    val canonical = type.canonicalText.substringBefore("<").trim()
    if (canonical.isNotBlank()) {
      names.add(canonical)
      names.add(canonical.substringAfterLast("."))
    }
  }
  return names
}

private fun matchesInterfaceName(
    owner: PsiClass,
    interfaceNames: Set<String>,
): Boolean {
  val qualified = owner.qualifiedName
  val simple = owner.name
  return (qualified != null && interfaceNames.contains(qualified)) ||
      (simple != null && interfaceNames.contains(simple))
}

private fun hasInterfaceMethodMapping(
    interfaceClass: PsiClass,
    name: String,
    paramCount: Int,
): Boolean {
  return interfaceClass.findMethodsByName(name, true).any { interfaceMethod ->
    interfaceMethod.parameterList.parametersCount == paramCount &&
        hasMappingIndicator(interfaceMethod)
  }
}

private fun hasMappingIndicator(method: PsiMethod): Boolean {
  return hasAnyAnnotationOrMeta(method, SpringAnnotations.mappingAnnotations) ||
      hasMappingNameFallback(method) ||
      hasMappingTextFallback(method)
}

private fun hasMappingTextFallback(method: PsiMethod): Boolean {
  val text = method.text
  return text.contains("@RequestMapping") || Regex("@\\w+Mapping\\b").containsMatchIn(text)
}
