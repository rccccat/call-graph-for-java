package com.github.rccccat.ideacallgraph.util

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType

private fun normalizeJavaLang(text: String): String = text.replace("java.lang.", "")

class ExcludePatternMatcher
private constructor(
    private val entries: List<PatternEntry>,
) {
  private enum class Scope {
    PACKAGE,
    CLASS,
    METHOD,
    SIGNATURE,
  }

  private data class PatternEntry(
      val scope: Scope,
      val regex: Regex,
      val rawText: String,
      val normalizedRawText: String,
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
      val parsed = parseScope(trimmed)
      if (parsed == null) {
        return ParseResult(
            null,
            "Pattern must start with one of: pkg:, class:, method:, sig:",
        )
      }
      val (scope, regexText) = parsed
      if (regexText.isBlank()) {
        return ParseResult(null, "Pattern is empty")
      }
      val regex =
          runCatching { Regex(regexText) }
              .getOrElse { ex ->
                return ParseResult(null, ex.message ?: "Invalid regex")
              }
      return ParseResult(
          PatternEntry(scope, regex, regexText, normalizeJavaLang(regexText)),
          null,
      )
    }

    private fun parseScope(raw: String): Pair<Scope, String>? =
        when {
          raw.startsWith("pkg:") -> Scope.PACKAGE to raw.removePrefix("pkg:")
          raw.startsWith("class:") -> Scope.CLASS to raw.removePrefix("class:")
          raw.startsWith("method:") -> Scope.METHOD to raw.removePrefix("method:")
          raw.startsWith("sig:") -> Scope.SIGNATURE to raw.removePrefix("sig:")
          else -> null
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
      val qualifiedMethodSignature: String,
      val qualifiedSignatureWithQualifiedParams: String?,
  ) {
    val classTargets: List<String> = listOfNotNull(classQualifiedName, className)

    val signatureTargets: List<String> =
        listOfNotNull(
                methodSignature,
                qualifiedMethodSignature,
                qualifiedSignature,
                qualifiedSignatureWithQualifiedParams,
            )
            .distinct()

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
        val qualifiedParamTypes =
            method.parameterList.parameters.joinToString(",") { param ->
              qualifiedTypeText(param.type)
            }
        val methodSignature = "${method.name}($paramTypes)"
        val qualifiedMethodSignature = "${method.name}($qualifiedParamTypes)"
        val qualifiedSignature =
            qualifiedClassName?.let { classNameValue -> "$classNameValue#$methodSignature" }
        val qualifiedSignatureWithQualifiedParams =
            qualifiedClassName?.let { classNameValue ->
              "$classNameValue#$qualifiedMethodSignature"
            }
        return MethodTargets(
            packageName = packageName,
            classQualifiedName = qualifiedClassName,
            className = className,
            methodName = method.name,
            methodSignature = methodSignature,
            qualifiedSignature = qualifiedSignature,
            qualifiedMethodSignature = qualifiedMethodSignature,
            qualifiedSignatureWithQualifiedParams = qualifiedSignatureWithQualifiedParams,
        )
      }

      private fun qualifiedTypeText(type: PsiType): String =
          when (type) {
            is PsiArrayType -> "${qualifiedTypeText(type.componentType)}[]"
            is PsiClassType -> {
              val resolved = type.resolve()
              val rawName = resolved?.qualifiedName ?: type.canonicalText
              val parameters = type.parameters
              if (parameters.isEmpty()) {
                rawName
              } else {
                val typeArgs = parameters.joinToString(",") { param -> qualifiedTypeText(param) }
                "$rawName<$typeArgs>"
              }
            }

            else -> type.canonicalText
          }
    }
  }

  private fun PatternEntry.matches(targets: MethodTargets): Boolean =
      when (scope) {
        Scope.PACKAGE ->
            targets.packageName?.let { target -> target == rawText || regex.matches(target) }
                ?: false
        Scope.CLASS ->
            targets.classTargets.any { target -> target == rawText || regex.matches(target) }
        Scope.METHOD -> targets.methodName == rawText || regex.matches(targets.methodName)
        Scope.SIGNATURE ->
            targets.signatureTargets.any { target ->
              target == rawText ||
                  target == normalizedRawText ||
                  normalizeJavaLang(target) == rawText ||
                  normalizeJavaLang(target) == normalizedRawText ||
                  regex.matches(target)
            }
      }
}
