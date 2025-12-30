package com.github.rccccat.ideacallgraph.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

class ExcludePatternMatcher private constructor(
    private val entries: List<PatternEntry>,
) {
  private enum class Scope {
    ANY,
    PACKAGE,
    CLASS,
    METHOD,
    SIGNATURE,
  }

  private data class PatternEntry(
      val scope: Scope,
      val regex: Regex,
  )

  private data class ParseResult(
      val entry: PatternEntry?,
      val error: String?,
  )

  companion object {
    fun fromPatterns(patterns: List<String>): ExcludePatternMatcher {
      val entries = patterns.mapNotNull { compilePattern(it).entry }
      return ExcludePatternMatcher(entries)
    }

    fun validatePattern(pattern: String): String? = compilePattern(pattern).error

    private fun compilePattern(raw: String): ParseResult {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) {
        return ParseResult(null, "Pattern is empty")
      }
      val (scope, regexText) = parseScope(trimmed)
      if (regexText.isBlank()) {
        return ParseResult(null, "Pattern is empty")
      }
      val regex =
          runCatching { Regex(regexText) }
              .getOrElse { ex ->
                return ParseResult(null, ex.message ?: "Invalid regex")
              }
      return ParseResult(PatternEntry(scope, regex), null)
    }

    private fun parseScope(raw: String): Pair<Scope, String> =
        when {
          raw.startsWith("pkg:") -> Scope.PACKAGE to raw.removePrefix("pkg:")
          raw.startsWith("class:") -> Scope.CLASS to raw.removePrefix("class:")
          raw.startsWith("method:") -> Scope.METHOD to raw.removePrefix("method:")
          raw.startsWith("sig:") -> Scope.SIGNATURE to raw.removePrefix("sig:")
          else -> Scope.ANY to raw
        }
  }

  fun matchesElement(element: PsiElement): Boolean =
      (element as? PsiMethod)?.let { matchesMethod(it) } ?: false

  fun matchesMethod(method: PsiMethod): Boolean {
    if (entries.isEmpty()) return false
    val targets = MethodTargets.from(method)
    return entries.any { entry -> entry.matches(targets) }
  }

  private data class MethodTargets(
      val packageName: String?,
      val classQualifiedName: String?,
      val className: String?,
      val methodName: String,
      val methodSignature: String,
      val qualifiedSignature: String?,
  ) {
    val anyTargets: List<String> =
        listOfNotNull(
            packageName,
            classQualifiedName,
            className,
            methodName,
            methodSignature,
            qualifiedSignature,
        )

    val classTargets: List<String> = listOfNotNull(classQualifiedName, className)

    val signatureTargets: List<String> = listOfNotNull(methodSignature, qualifiedSignature)

    companion object {
      fun from(method: PsiMethod): MethodTargets {
        val containingClass = method.containingClass
        val qualifiedClassName = containingClass?.qualifiedName
        val packageName =
            qualifiedClassName?.let { name ->
              if (name.contains('.')) name.substringBeforeLast('.') else null
            }
        val className = containingClass?.name
        val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
        val methodSignature = "${method.name}($paramTypes)"
        val qualifiedSignature =
            qualifiedClassName?.let { classNameValue -> "$classNameValue#$methodSignature" }
        return MethodTargets(
            packageName = packageName,
            classQualifiedName = qualifiedClassName,
            className = className,
            methodName = method.name,
            methodSignature = methodSignature,
            qualifiedSignature = qualifiedSignature,
        )
      }
    }
  }

  private fun PatternEntry.matches(targets: MethodTargets): Boolean =
      when (scope) {
        Scope.ANY -> targets.anyTargets.any { regex.matches(it) }
        Scope.PACKAGE -> targets.packageName?.let { regex.matches(it) } ?: false
        Scope.CLASS -> targets.classTargets.any { regex.matches(it) }
        Scope.METHOD -> regex.matches(targets.methodName)
        Scope.SIGNATURE -> targets.signatureTargets.any { regex.matches(it) }
      }
}
