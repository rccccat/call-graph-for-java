package com.github.rccccat.ideacallgraph.core.visitor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/** Visitor for finding call targets in Kotlin code. */
class KotlinCallVisitor : CallVisitor {
  override fun canVisit(element: PsiElement): Boolean = element is KtNamedFunction

  override fun findCallTargets(
      element: PsiElement,
      context: VisitorContext,
  ): List<CallTargetInfo> {
    val function = element as? KtNamedFunction ?: return emptyList()
    val callTargets = mutableListOf<CallTargetInfo>()

    function.accept(
        object : KtTreeVisitorVoid() {
          override fun visitCallExpression(expression: KtCallExpression) {
            ProgressManager.checkCanceled()
            super.visitCallExpression(expression)

            val calleeExpression = expression.calleeExpression ?: return
            val resolved = calleeExpression.references.firstOrNull()?.resolve()
            val injectionPoint = findKotlinInjectionPoint(expression)

            when (resolved) {
              is KtNamedFunction -> {
                callTargets.add(CallTargetInfo(resolved))
              }

              is PsiMethod -> {
                val implementations =
                    resolveInterfaceImplementationsIfNeeded(resolved, injectionPoint, context)
                callTargets.add(CallTargetInfo(resolved, implementations))
              }

              null -> {
                val fallbackTargets =
                    resolveKotlinCallTargets(function, expression, calleeExpression, context)
                fallbackTargets.forEach { target ->
                  val implementations =
                      (target as? PsiMethod)?.let {
                        resolveInterfaceImplementationsIfNeeded(it, injectionPoint, context)
                      }
                  callTargets.add(CallTargetInfo(target, implementations))
                }
              }
            }
          }
        },
    )

    return callTargets
  }

  private fun resolveKotlinCallTargets(
      function: KtNamedFunction,
      expression: KtCallExpression,
      calleeExpression: KtExpression,
      context: VisitorContext,
  ): List<PsiElement> {
    val calleeName = calleeExpression.text
    if (calleeName.isNullOrBlank()) return emptyList()

    val targets = mutableListOf<PsiElement>()
    val receiverClass = context.typeResolver.resolveKotlinReceiverClass(function, expression)

    if (receiverClass != null) {
      receiverClass.findMethodsByName(calleeName, true).forEach { method -> targets.add(method) }
      if (targets.isNotEmpty()) {
        return targets
      }
    }

    return targets
  }

  private fun findKotlinInjectionPoint(
      expression: KtCallExpression,
  ): PsiElement? {
    val parent = expression.parent as? KtQualifiedExpression ?: return null
    val receiver = parent.receiverExpression
    val resolved = resolveKotlinReceiverSource(receiver) ?: return null

    return when (resolved) {
      is KtProperty -> {
        if (!isKotlinInjectedProperty(resolved)) {
          null
        } else {
          resolved.toLightElements().firstOrNull { it is PsiField }
        }
      }

      is KtParameter -> {
        if (!isKotlinInjectedParameter(resolved)) {
          null
        } else {
          resolved.toLightElements().firstOrNull { it is PsiParameter }
        }
      }

      else -> {
        null
      }
    }
  }

  private fun resolveKotlinReceiverSource(receiver: KtExpression?): PsiElement? =
      when (receiver) {
        is KtNameReferenceExpression -> receiver.references.firstOrNull()?.resolve()
        is KtDotQualifiedExpression -> resolveKotlinReceiverSource(receiver.receiverExpression)
        is KtSafeQualifiedExpression -> resolveKotlinReceiverSource(receiver.receiverExpression)
        is KtArrayAccessExpression -> resolveKotlinReceiverSource(receiver.arrayExpression)
        is KtParenthesizedExpression -> resolveKotlinReceiverSource(receiver.expression)
        else -> null
      }

  private fun isKotlinInjectedProperty(property: KtProperty): Boolean {
    if (property.annotationEntries.any { annotation ->
      val name = annotation.shortName?.asString()
      name in setOf("Autowired", "Inject", "Resource")
    }) {
      return true
    }
    return isKotlinConstructorInjectedProperty(property)
  }

  private fun isKotlinInjectedParameter(parameter: KtParameter): Boolean {
    if (parameter.annotationEntries.any { annotation ->
      val name = annotation.shortName?.asString()
      name in setOf("Autowired", "Inject", "Resource")
    }) {
      return true
    }
    val constructor = parameter.parent?.parent as? KtPrimaryConstructor ?: return false
    val ktClass = constructor.parent as? KtClass ?: return false
    return isKotlinSpringComponent(ktClass)
  }

  private fun isKotlinSpringComponent(ktClass: KtClass): Boolean =
      ktClass.annotationEntries.any { annotation ->
        val name = annotation.shortName?.asString()
        name in
            setOf(
                "Controller",
                "RestController",
                "Service",
                "Component",
                "Repository",
                "Configuration",
            )
      }

  private fun isKotlinConstructorInjectedProperty(property: KtProperty): Boolean {
    val containingClass = property.parent?.parent as? KtClass ?: return false
    if (!isKotlinSpringComponent(containingClass)) {
      return false
    }
    val primaryConstructor = containingClass.primaryConstructor ?: return false
    return primaryConstructor.valueParameters.any { it.name == property.name }
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
