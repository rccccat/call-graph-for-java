package com.github.rccccat.ideacallgraph.core.visitor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil

/** Visitor for finding call targets in Java code. */
class JavaCallVisitor : CallVisitor {
  override fun canVisit(element: PsiElement): Boolean = element is PsiMethod

  override fun findCallTargets(
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

            val injectionPoint = findInjectionPoint(expression, context)
            val arguments = expression.argumentList.expressions.toList()
            val resolvedMethod = expression.resolveMethod()
            val targetMethod =
                if (resolvedMethod != null &&
                    (isReflectionMethod(resolvedMethod) ||
                        matchesParameterTypes(resolvedMethod, arguments))) {
                  resolvedMethod
                } else {
                  resolveJavaCallTarget(expression, injectionPoint, context)
                }

            if (targetMethod != null) {
              val implementations =
                  resolveInterfaceImplementationsIfNeeded(targetMethod, injectionPoint, context)
              callTargets.add(CallTargetInfo(targetMethod, implementations))
            }
          }

          override fun visitNewExpression(expression: PsiNewExpression) {
            ProgressManager.checkCanceled()
            super.visitNewExpression(expression)

            val resolvedConstructor = expression.resolveConstructor()
            if (resolvedConstructor != null) {
              callTargets.add(CallTargetInfo(resolvedConstructor))
            }
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

  private fun resolveJavaCallTarget(
      expression: PsiMethodCallExpression,
      injectionPoint: PsiElement?,
      context: VisitorContext,
  ): PsiMethod? {
    val methodName = expression.methodExpression.referenceName ?: return null
    val qualifierExpression = expression.methodExpression.qualifierExpression
    val qualifierType = qualifierExpression?.type
    val baseElement = resolveQualifierSource(qualifierExpression)
    val baseType = (baseElement as? PsiVariable)?.type
    val contextElement = baseElement ?: injectionPoint

    val targetClass =
        context.typeResolver.resolveClassFromType(qualifierType, contextElement)
            ?: baseType?.let {
              context.typeResolver.resolveCollectionElementClass(it, contextElement)
                  ?: context.typeResolver.resolveClassFromType(it, contextElement)
            }
            ?: resolveInjectedTargetClass(injectionPoint, contextElement, context)

    val candidates = targetClass?.findMethodsByName(methodName, true).orEmpty()
    val arguments = expression.argumentList.expressions.toList()
    return candidates.firstOrNull { method -> matchesParameterTypes(method, arguments) }
  }

  private fun resolveInjectedTargetClass(
      injectionPoint: PsiElement?,
      contextElement: PsiElement?,
      context: VisitorContext,
  ): PsiClass? {
    val type =
        when (injectionPoint) {
          is PsiField -> injectionPoint.type
          is PsiParameter -> injectionPoint.type
          else -> return null
        }
    return context.typeResolver.resolveCollectionElementClass(type, contextElement)
        ?: context.typeResolver.resolveClassFromType(type, contextElement)
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

  private fun matchesParameterTypes(
      method: PsiMethod,
      arguments: List<PsiExpression>,
  ): Boolean {
    val parameters = method.parameterList.parameters
    if (!method.isVarArgs) {
      if (parameters.size != arguments.size) return false
      return parameters.indices.all { index ->
        val argumentType = arguments[index].type ?: return@all false
        TypeConversionUtil.isAssignable(parameters[index].type, argumentType)
      }
    }

    if (arguments.size < parameters.size - 1) return false
    for (index in 0 until parameters.size) {
      val parameter = parameters[index]
      if (index == parameters.size - 1) {
        val varArgType = (parameter.type as? PsiEllipsisType)?.componentType ?: parameter.type
        for (argIndex in index until arguments.size) {
          val argumentType = arguments[argIndex].type ?: return false
          if (!TypeConversionUtil.isAssignable(varArgType, argumentType)) return false
        }
      } else {
        val argumentType = arguments[index].type ?: return false
        if (!TypeConversionUtil.isAssignable(parameter.type, argumentType)) return false
      }
    }
    return true
  }

  private fun isReflectionMethod(method: PsiMethod): Boolean {
    val qualifiedName = method.containingClass?.qualifiedName ?: return false
    return qualifiedName == "java.lang.Class" || qualifiedName == "java.lang.reflect.Method"
  }

  private fun resolveInterfaceImplementationsIfNeeded(
      method: PsiMethod,
      injectionPoint: PsiElement?,
      context: VisitorContext,
  ): List<ImplementationInfo>? {
    if (!context.settings.resolveInterfaceImplementations) return null
    val containingClass = method.containingClass ?: return null
    if (!containingClass.isInterface) return null
    return context.interfaceResolver.resolveInterfaceImplementationsAdvanced(method, injectionPoint)
  }
}
