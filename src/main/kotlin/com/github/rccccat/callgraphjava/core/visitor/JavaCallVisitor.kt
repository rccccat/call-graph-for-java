package com.github.rccccat.callgraphjava.core.visitor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.util.PsiTreeUtil

/** Visitor for finding call targets in Java code. */
class JavaCallVisitor {
  fun canVisit(element: PsiElement): Boolean = element is PsiMethod

  fun findCallTargets(
      element: PsiElement,
      context: VisitorContext,
  ): List<CallTargetInfo> {
    val method = element as? PsiMethod ?: return emptyList()
    val callTargets = mutableListOf<CallTargetInfo>()

    method.accept(
        object : JavaRecursiveElementVisitor() {
          override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            ProgressManager.checkCanceled()
            super.visitMethodCallExpression(expression)

            val isSuperCall = expression.methodExpression.qualifierExpression is PsiSuperExpression

            val injectionPoint = if (isSuperCall) null else findInjectionPoint(expression, context)
            val resolveResult = expression.resolveMethodGenerics()
            val resolvedMethod =
                if (resolveResult.isValidResult) {
                  resolveResult.element as? PsiMethod
                } else {
                  null
                } ?: expression.resolveMethod()
            val targetMethod = resolvedMethod

            if (targetMethod != null) {
              val implementations =
                  if (isSuperCall) {
                    null
                  } else {
                    resolveOverrideImplementationsIfNeeded(targetMethod, injectionPoint, context)
                  }
              callTargets.add(CallTargetInfo(targetMethod, implementations, expression))
            }
          }

          override fun visitNewExpression(expression: PsiNewExpression) {
            ProgressManager.checkCanceled()
            super.visitNewExpression(expression)

            val resolvedConstructor = expression.resolveConstructor()
            if (resolvedConstructor != null) {
              callTargets.add(CallTargetInfo(resolvedConstructor, callExpression = expression))
            }
          }

          override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
            ProgressManager.checkCanceled()
            super.visitMethodReferenceExpression(expression)

            val resolvedMethod = expression.resolve() as? PsiMethod ?: return
            val isSuperReference = expression.qualifierExpression is PsiSuperExpression

            val implementations =
                if (isSuperReference) {
                  null
                } else {
                  resolveOverrideImplementationsIfNeeded(resolvedMethod, null, context)
                }
            callTargets.add(CallTargetInfo(resolvedMethod, implementations, expression))
          }
        },
    )

    return callTargets
  }

  private fun findInjectionPoint(
      expression: PsiMethodCallExpression,
      context: VisitorContext,
  ): PsiElement? {
    val qualifier = expression.methodExpression.qualifierExpression ?: return null
    val resolved = resolveQualifierSource(qualifier) ?: return null
    return when (resolved) {
      is PsiField -> resolveInjectedField(resolved, context)
      is PsiParameter -> resolveInjectedParameter(resolved, context)
      else -> null
    }
  }

  private fun resolveQualifierSource(expression: PsiExpression?): PsiElement? =
      when (expression) {
        is PsiReferenceExpression -> {
          expression.resolve()
        }

        is PsiMethodCallExpression -> {
          resolveQualifierSource(expression.methodExpression.qualifierExpression)
        }

        is PsiParenthesizedExpression -> {
          resolveQualifierSource(expression.expression)
        }

        is PsiTypeCastExpression -> {
          resolveQualifierSource(expression.operand)
        }

        else -> {
          null
        }
      }

  private fun resolveInjectedField(
      field: PsiField,
      context: VisitorContext,
  ): PsiElement? {
    if (context.springAnalyzer.hasInjectionAnnotation(field)) {
      return field
    }
    val containingClass = field.containingClass ?: return null
    val setterParameter = findSetterInjectionParameter(containingClass, field, context)
    if (setterParameter != null) {
      return setterParameter
    }
    return findConstructorInjectionParameter(containingClass, field, context)
  }

  private fun resolveInjectedParameter(
      parameter: PsiParameter,
      context: VisitorContext,
  ): PsiElement? {
    if (context.springAnalyzer.hasInjectionAnnotation(parameter)) {
      return parameter
    }
    val owner = parameter.declarationScope
    if (owner is PsiMethod) {
      if (context.springAnalyzer.hasInjectionAnnotation(owner)) {
        return parameter
      }
      if (owner.isConstructor && isSingleConstructorWithParams(owner.containingClass)) {
        return parameter
      }
    }
    return null
  }

  private fun findSetterInjectionParameter(
      containingClass: PsiClass,
      field: PsiField,
      context: VisitorContext,
  ): PsiParameter? {
    val expectedSetterName = "set" + field.name.replaceFirstChar { it.uppercase() }
    val fieldType = field.type

    for (method in containingClass.methods) {
      if (!method.name.equals(expectedSetterName, ignoreCase = true)) continue
      val parameters = method.parameterList.parameters
      if (parameters.size != 1) continue
      val parameter = parameters[0]
      if (!typesMatch(fieldType, parameter.type)) continue
      if (!context.springAnalyzer.hasInjectionAnnotation(method) &&
          !context.springAnalyzer.hasInjectionAnnotation(parameter)) {
        continue
      }
      if (method.body == null || setterAssignsField(method, field)) {
        return parameter
      }
    }
    return null
  }

  private fun findConstructorInjectionParameter(
      containingClass: PsiClass,
      field: PsiField,
      context: VisitorContext,
  ): PsiParameter? {
    val constructors = containingClass.constructors
    if (constructors.isEmpty()) return null

    val eligibleConstructors =
        constructors.filter { ctor ->
          context.springAnalyzer.hasInjectionAnnotation(ctor) ||
              (constructors.size == 1 && ctor.parameterList.parametersCount > 0)
        }
    if (eligibleConstructors.isEmpty()) return null

    val fieldType = field.type
    val namedMatch =
        eligibleConstructors
            .flatMap { it.parameterList.parameters.asList() }
            .firstOrNull { parameter ->
              parameter.name == field.name && typesMatch(fieldType, parameter.type)
            }
    if (namedMatch != null) return namedMatch

    val typeMatches =
        eligibleConstructors
            .flatMap { it.parameterList.parameters.asList() }
            .filter { parameter -> typesMatch(fieldType, parameter.type) }
    return if (typeMatches.size == 1) typeMatches.first() else typeMatches.firstOrNull()
  }

  private fun isSingleConstructorWithParams(containingClass: PsiClass?): Boolean {
    if (containingClass == null) return false
    val constructors = containingClass.constructors
    return constructors.size == 1 && constructors.first().parameterList.parametersCount > 0
  }

  private fun typesMatch(
      left: PsiType,
      right: PsiType,
  ): Boolean {
    if (left.canonicalText == right.canonicalText) return true
    val leftClass = (left as? PsiClassType)?.resolve()
    val rightClass = (right as? PsiClassType)?.resolve()
    if (leftClass != null && rightClass != null) {
      return leftClass.isInheritor(rightClass, true) || rightClass.isInheritor(leftClass, true)
    }
    return false
  }

  private fun setterAssignsField(
      method: PsiMethod,
      field: PsiField,
  ): Boolean {
    val body = method.body ?: return false
    val assignments = PsiTreeUtil.collectElementsOfType(body, PsiAssignmentExpression::class.java)
    return assignments.any { assignment ->
      val left = assignment.lExpression as? PsiReferenceExpression ?: return@any false
      val resolved = left.resolve()
      resolved == field
    }
  }

  private fun resolveOverrideImplementationsIfNeeded(
      method: PsiMethod,
      injectionPoint: PsiElement?,
      context: VisitorContext,
  ): List<ImplementationInfo>? {
    if (!context.settings.resolveInterfaceImplementations) return null
    if (context.excludePatternMatcher.matchesMethod(method)) return null
    val implementations =
        context.interfaceResolver.resolveMethodImplementationsAdvanced(method, injectionPoint)
    val filteredImplementations =
        implementations.filterNot { impl ->
          val implMethod = impl.implementationMethod as? PsiMethod ?: return@filterNot false
          context.excludePatternMatcher.matchesMethod(implMethod)
        }
    return filteredImplementations.ifEmpty { null }
  }
}
