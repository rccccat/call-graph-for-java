package com.github.rccccat.ideacallgraph.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.psi.*

/**
 * Analyzer for Kotlin-specific language features: extension functions, delegation, coroutines
 */
class KotlinAdvancedAnalyzer(private val project: Project) {

    /**
     * Find all extension function calls in the given function
     */
    fun findExtensionFunctionCalls(function: KtNamedFunction): List<ExtensionCallInfo> {
        return ReadAction.compute<List<ExtensionCallInfo>, Exception> {
            val extensionCalls = mutableListOf<ExtensionCallInfo>()
            
            function.accept(object : KtVisitorVoid() {
                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    super.visitDotQualifiedExpression(expression)
                    
                    val selectorExpression = expression.selectorExpression
                    if (selectorExpression is KtCallExpression) {
                        val receiverType = getReceiverType(expression.receiverExpression)
                        val functionName = selectorExpression.calleeExpression?.text
                        
                        if (receiverType != null && functionName != null) {
                            val extensionFunction = findExtensionFunction(receiverType, functionName)
                            if (extensionFunction != null) {
                                extensionCalls.add(
                                    ExtensionCallInfo(
                                        callExpression = expression,
                                        receiverType = receiverType,
                                        extensionFunction = extensionFunction,
                                        functionName = functionName
                                    )
                                )
                            }
                        }
                    }
                }
            })
            
            extensionCalls
        }
    }

    /**
     * Analyze delegation patterns in Kotlin classes
     */
    fun analyzeDelegation(ktClass: KtClass): List<DelegationInfo> {
        return ReadAction.compute<List<DelegationInfo>, Exception> {
            val delegations = mutableListOf<DelegationInfo>()
            
            // Check class delegation
            ktClass.superTypeListEntries.forEach { entry ->
                if (entry is KtDelegatedSuperTypeEntry) {
                    val delegateExpression = entry.delegateExpression
                    val delegateType = entry.typeReference?.text
                    
                    if (delegateExpression != null && delegateType != null) {
                        delegations.add(
                            DelegationInfo(
                                delegationType = DelegationType.CLASS_DELEGATION,
                                delegateExpression = delegateExpression,
                                targetType = delegateType,
                                property = null
                            )
                        )
                    }
                }
            }
            
            // Check property delegation
            ktClass.getProperties().forEach { property ->
                val delegate = property.delegate
                if (delegate != null) {
                    val delegateExpression = delegate.expression
                    if (delegateExpression != null) {
                        delegations.add(
                            DelegationInfo(
                                delegationType = DelegationType.PROPERTY_DELEGATION,
                                delegateExpression = delegateExpression,
                                targetType = property.typeReference?.text ?: "Unknown",
                                property = property
                            )
                        )
                    }
                }
            }
            
            delegations
        }
    }

    /**
     * Analyze coroutine and async patterns
     */
    fun analyzeCoroutines(function: KtNamedFunction): List<CoroutineCallInfo> {
        return ReadAction.compute<List<CoroutineCallInfo>, Exception> {
            val coroutineCalls = mutableListOf<CoroutineCallInfo>()
            
            function.accept(object : KtVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    super.visitCallExpression(expression)
                    
                    val functionName = expression.calleeExpression?.text
                    val callType = when (functionName) {
                        "async" -> CoroutineType.ASYNC
                        "launch" -> CoroutineType.LAUNCH
                        "runBlocking" -> CoroutineType.RUN_BLOCKING
                        "withContext" -> CoroutineType.WITH_CONTEXT
                        "delay" -> CoroutineType.DELAY
                        else -> null
                    }
                    
                    if (callType != null) {
                        coroutineCalls.add(
                            CoroutineCallInfo(
                                callExpression = expression,
                                coroutineType = callType,
                                functionName = functionName ?: "unknown",
                                isAwaited = isAwaitedCall(expression)
                            )
                        )
                    }
                    
                    // Check for .await() calls
                    if (functionName == "await") {
                        coroutineCalls.add(
                            CoroutineCallInfo(
                                callExpression = expression,
                                coroutineType = CoroutineType.AWAIT,
                                functionName = "await",
                                isAwaited = true
                            )
                        )
                    }
                }
            })
            
            coroutineCalls
        }
    }

    private fun getReceiverType(expression: KtExpression): String? {
        // This is a simplified implementation
        // In a real scenario, you'd use Kotlin's type resolution
        return when (expression) {
            is KtNameReferenceExpression -> {
                // Try to infer type from context
                expression.text
            }
            is KtStringTemplateExpression -> "String"
            is KtConstantExpression -> {
                when (expression.node.elementType.toString()) {
                    "INTEGER_LITERAL" -> "Int"
                    "FLOAT_LITERAL" -> "Double"
                    "BOOLEAN_CONSTANT" -> "Boolean"
                    else -> "String"
                }
            }
            else -> expression.text
        }
    }

    private fun findExtensionFunction(receiverType: String, functionName: String): KtNamedFunction? {
        return ReadAction.compute<KtNamedFunction?, Exception> {
            // Simplified search - in a real implementation you'd use proper type resolution
            // This would require complex Kotlin compiler integration
            null  // Return null for now
        }
    }

    private fun isAwaitedCall(expression: KtCallExpression): Boolean {
        val parent = expression.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == expression) {
            val selector = parent.selectorExpression
            if (selector is KtCallExpression) {
                return selector.calleeExpression?.text == "await"
            }
        }
        return false
    }

    data class ExtensionCallInfo(
        val callExpression: KtDotQualifiedExpression,
        val receiverType: String,
        val extensionFunction: KtNamedFunction?,
        val functionName: String
    )

    data class DelegationInfo(
        val delegationType: DelegationType,
        val delegateExpression: KtExpression,
        val targetType: String,
        val property: KtProperty?
    )

    data class CoroutineCallInfo(
        val callExpression: KtCallExpression,
        val coroutineType: CoroutineType,
        val functionName: String,
        val isAwaited: Boolean
    )

    enum class DelegationType {
        CLASS_DELEGATION,
        PROPERTY_DELEGATION
    }

    enum class CoroutineType {
        ASYNC,
        LAUNCH,
        RUN_BLOCKING,
        WITH_CONTEXT,
        DELAY,
        AWAIT
    }
}