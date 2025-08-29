package com.github.rccccat.ideacallgraph.analysis

import com.github.rccccat.ideacallgraph.model.CallGraph
import com.github.rccccat.ideacallgraph.model.CallGraphEdge
import com.github.rccccat.ideacallgraph.model.CallGraphNode
import com.github.rccccat.ideacallgraph.settings.CallGraphSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Main analyzer for building call graphs from Java and Kotlin code
 */
class CallGraphAnalyzer(private val project: Project) {

    private val nodeFactory = CallGraphNodeFactory(project)
    private val springAnalyzer = SpringCallGraphAnalyzer()
    private val mybatisAnalyzer = MyBatisCallGraphAnalyzer(project)
    private val interfaceResolver = InterfaceImplementationResolver(project)
    private val kotlinAdvancedAnalyzer = KotlinAdvancedAnalyzer(project)
    private val settings = CallGraphSettings.getInstance()

    /**
     * Builds a call graph starting from the given method or function
     */
    fun buildCallGraph(startElement: PsiElement): CallGraph? {
        val rootNode = nodeFactory.createNode(startElement) ?: return null
        
        val nodes = mutableSetOf(rootNode)
        val edges = mutableSetOf<CallGraphEdge>()
        val visited = mutableSetOf<String>()
        
        buildCallGraphRecursive(rootNode, nodes, edges, visited, settings.projectMaxDepth, true)
        
        return CallGraph(rootNode, nodes, edges)
    }

    private fun buildCallGraphRecursive(
        currentNode: CallGraphNode,
        nodes: MutableSet<CallGraphNode>,
        edges: MutableSet<CallGraphEdge>,
        visited: MutableSet<String>,
        remainingDepth: Int,
        isProjectCode: Boolean
    ) {
        if (remainingDepth <= 0 || currentNode.id in visited) return
        
        visited.add(currentNode.id)
        val element = currentNode.elementPointer.element ?: return
        
        val callees = findCallees(element)
        for (callee in callees) {
            // Skip certain obvious system methods to reduce noise
            if (callee.target != null && shouldSkipMethod(callee.target!!)) continue
            
            val calleeNode = if (callee.target != null) {
                nodeFactory.createNode(callee.target!!)
            } else {
                // Handle synthetic nodes for coroutines, etc.
                null
            }
            
            if (calleeNode != null) {
                nodes.add(calleeNode)
                edges.add(CallGraphEdge(currentNode, calleeNode, callee.callType))
                
                // Handle interface implementations if enabled
                if (settings.resolveInterfaceImplementations && callee.callType == CallGraphEdge.CallType.INTERFACE_CALL) {
                    handleInterfaceImplementations(callee.target!!, calleeNode, nodes, edges, visited, remainingDepth, isProjectCode)
                }
                
                // Check if this is a MyBatis mapper method and create SQL node
                if (callee.target is PsiMethod && calleeNode.nodeType == CallGraphNode.NodeType.MYBATIS_MAPPER_METHOD) {
                    val mybatisInfo = mybatisAnalyzer.analyzeMapperMethod(callee.target as PsiMethod)
                    if (mybatisInfo.isMapperMethod && mybatisInfo.sqlType != null) {
                        val sqlNode = nodeFactory.createSqlNode(callee.target as PsiMethod, mybatisInfo)
                        if (sqlNode != null) {
                            nodes.add(sqlNode)
                            edges.add(CallGraphEdge(calleeNode, sqlNode, CallGraphEdge.CallType.MYBATIS_SQL_CALL))
                        }
                    }
                }
                
                // Determine if the called method is in project code or third-party library
                val calleeIsProjectCode = isProjectCode(callee.target!!)
                val nextDepth = if (calleeIsProjectCode) {
                    if (isProjectCode) remainingDepth - 1 else settings.projectMaxDepth - 1
                } else {
                    settings.thirdPartyMaxDepth
                }
                
                if (nextDepth > 0) {
                    buildCallGraphRecursive(calleeNode, nodes, edges, visited, nextDepth, calleeIsProjectCode)
                }
            }
        }
    }

    private fun handleInterfaceImplementations(
        interfaceMethod: PsiElement,
        interfaceNode: CallGraphNode,
        nodes: MutableSet<CallGraphNode>,
        edges: MutableSet<CallGraphEdge>,
        visited: MutableSet<String>,
        remainingDepth: Int,
        isProjectCode: Boolean
    ) {
        when (interfaceMethod) {
            is PsiMethod -> {
                val implementations = interfaceResolver.resolveInterfaceImplementations(interfaceMethod)
                
                // Filter implementations based on user settings
                val relevantImplementations = if (settings.traverseAllImplementations) {
                    implementations
                } else {
                    // Prioritize Spring components and project code
                    implementations.filter { it.isSpringComponent || it.isProjectCode }
                        .ifEmpty { implementations.take(1) } // Take at least one if no Spring components found
                }
                
                for (impl in relevantImplementations) {
                    val implNode = nodeFactory.createNode(impl.implementationMethod) ?: continue
                    nodes.add(implNode)
                    edges.add(CallGraphEdge(interfaceNode, implNode, CallGraphEdge.CallType.SPRING_INJECTION))
                    
                    // Continue building the graph from the implementation
                    val implIsProjectCode = isProjectCode(impl.implementationMethod)
                    val nextDepth = if (implIsProjectCode) {
                        if (isProjectCode) remainingDepth - 1 else settings.projectMaxDepth - 1
                    } else {
                        settings.thirdPartyMaxDepth
                    }
                    
                    if (nextDepth > 0) {
                        buildCallGraphRecursive(implNode, nodes, edges, visited, nextDepth, implIsProjectCode)
                    }
                }
            }
        }
    }

    private fun findCallees(element: PsiElement): List<CalleeInfo> {
        return ReadAction.compute<List<CalleeInfo>, Exception> {
            val callees = mutableListOf<CalleeInfo>()
            
            when (element) {
                is PsiMethod -> findJavaCallees(element, callees)
                is KtNamedFunction -> findKotlinCallees(element, callees)
            }
            
            callees
        }
    }

    private fun findJavaCallees(method: PsiMethod, callees: MutableList<CalleeInfo>) {
        method.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val resolvedMethod = expression.resolveMethod()
                if (resolvedMethod != null) {
                    val callType = determineJavaCallType(expression, resolvedMethod)
                    
                    // Enhanced interface resolution with Spring context
                    if (callType == CallGraphEdge.CallType.INTERFACE_CALL) {
                        val injectionPoint = findInjectionPoint(expression)
                        val advancedResult = interfaceResolver.resolveInterfaceImplementationsAdvanced(
                            resolvedMethod, injectionPoint
                        )
                        
                        // Add resolved implementations with their context
                        advancedResult.implementations.forEach { impl ->
                            callees.add(CalleeInfo(
                                impl.implementationMethod, 
                                CallGraphEdge.CallType.SPRING_INJECTION,
                                resolutionContext = impl.resolutionReason
                            ))
                        }
                        
                        // Also add the original interface call for completeness
                        callees.add(CalleeInfo(resolvedMethod, callType))
                    } else {
                        callees.add(CalleeInfo(resolvedMethod, callType))
                    }
                }
            }
            
            override fun visitNewExpression(expression: PsiNewExpression) {
                super.visitNewExpression(expression)
                
                val resolvedConstructor = expression.resolveConstructor()
                if (resolvedConstructor != null) {
                    callees.add(CalleeInfo(resolvedConstructor, CallGraphEdge.CallType.DIRECT_CALL))
                }
            }
        })
    }

    private fun findKotlinCallees(function: KtNamedFunction, callees: MutableList<CalleeInfo>) {
        // Analyze standard function calls
        function.accept(object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                
                @Suppress("DEPRECATION")
                val reference = expression.calleeExpression?.reference
                val resolved = reference?.resolve()
                
                when (resolved) {
                    is KtNamedFunction -> {
                        val callType = determineKotlinCallType(expression, resolved)
                        callees.add(CalleeInfo(resolved, callType))
                    }
                    is PsiMethod -> {
                        val callType = determineKotlinCallType(expression, resolved)
                        callees.add(CalleeInfo(resolved, callType))
                    }
                }
            }
            
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)
                
                // Handle extension function calls
                val selectorExpression = expression.selectorExpression
                if (selectorExpression is KtCallExpression) {
                    val receiverType = expression.receiverExpression.text
                    val functionName = selectorExpression.calleeExpression?.text
                    
                    if (functionName != null) {
                        // Try to find extension function
                        val extensionFunction = findExtensionFunction(receiverType, functionName)
                        if (extensionFunction != null) {
                            callees.add(CalleeInfo(
                                extensionFunction, 
                                CallGraphEdge.CallType.DIRECT_CALL,
                                resolutionContext = "Extension function on $receiverType"
                            ))
                        }
                    }
                }
            }
        })
        
        // Analyze extension function calls
        val extensionCalls = kotlinAdvancedAnalyzer.findExtensionFunctionCalls(function)
        extensionCalls.forEach { extCall ->
            if (extCall.extensionFunction != null) {
                callees.add(CalleeInfo(
                    extCall.extensionFunction,
                    CallGraphEdge.CallType.DIRECT_CALL,
                    resolutionContext = "Extension function: ${extCall.functionName} on ${extCall.receiverType}"
                ))
            }
        }
        
        // Analyze coroutine calls
        val coroutineCalls = kotlinAdvancedAnalyzer.analyzeCoroutines(function)
        coroutineCalls.forEach { coroutineCall ->
            callees.add(CalleeInfo(
                // For now, we create a synthetic element for coroutine calls
                // In a full implementation, you'd resolve the actual coroutine function
                null,
                CallGraphEdge.CallType.DIRECT_CALL,
                resolutionContext = "Coroutine: ${coroutineCall.functionName} (${coroutineCall.coroutineType})"
            ))
        }
    }
    
    private fun findExtensionFunction(receiverType: String, functionName: String): KtNamedFunction? {
        // This is a simplified search - in practice you'd use proper type resolution
        return ReadAction.compute<KtNamedFunction?, Exception> {
            // Search project for extension functions
            // This would need more sophisticated implementation
            null
        }
    }

    private fun findInjectionPoint(expression: PsiMethodCallExpression): PsiElement? {
        // Try to find the field or parameter that represents the injection point
        val qualifier = expression.methodExpression.qualifierExpression
        if (qualifier is PsiReferenceExpression) {
            val resolved = qualifier.resolve()
            if (resolved is PsiField || resolved is PsiParameter) {
                return resolved
            }
        }
        return null
    }

    private fun determineJavaCallType(expression: PsiMethodCallExpression, method: PsiMethod): CallGraphEdge.CallType {
        return ReadAction.compute<CallGraphEdge.CallType, Exception> {
            // Check for Spring-specific patterns
            springAnalyzer.analyzeCallType(expression, method)?.let { return@compute it }
            
            // Check for reflection calls
            if (isReflectionCall(expression)) {
                return@compute CallGraphEdge.CallType.REFLECTION_CALL
            }
            
            // Check for interface calls
            val containingClass = method.containingClass
            if (containingClass?.isInterface == true) {
                return@compute CallGraphEdge.CallType.INTERFACE_CALL
            }
            
            CallGraphEdge.CallType.DIRECT_CALL
        }
    }

    private fun determineKotlinCallType(expression: KtCallExpression, target: PsiElement): CallGraphEdge.CallType {
        // Similar logic for Kotlin
        return CallGraphEdge.CallType.DIRECT_CALL
    }

    private fun shouldSkipMethod(element: PsiElement): Boolean {
        return ReadAction.compute<Boolean, Exception> {
            when (element) {
                is PsiMethod -> {
                    val className = element.containingClass?.qualifiedName
                    val methodName = element.name
                    
                    // Check against exclude package patterns
                    if (className != null) {
                        for (pattern in settings.excludePackagePatterns) {
                            if (Regex(pattern).matches(className)) {
                                return@compute true
                            }
                        }
                    }
                    
                    // Check method filtering options
                    if (!settings.includeGettersSetters) {
                        if (methodName.startsWith("get") || methodName.startsWith("set") || methodName.startsWith("is")) {
                            return@compute true
                        }
                    }
                    
                    if (!settings.includeToString && methodName == "toString") {
                        return@compute true
                    }
                    
                    if (!settings.includeHashCodeEquals && (methodName == "equals" || methodName == "hashCode")) {
                        return@compute true
                    }
                    
                    false
                }
                is KtNamedFunction -> {
                    val functionName = element.name
                    val packageName = element.containingKtFile.packageFqName.asString()
                    
                    // Check against exclude package patterns
                    for (pattern in settings.excludePackagePatterns) {
                        if (Regex(pattern).matches(packageName)) {
                            return@compute true
                        }
                    }
                    
                    // Check method filtering options for Kotlin
                    if (!settings.includeToString && functionName == "toString") {
                        return@compute true
                    }
                    
                    if (!settings.includeHashCodeEquals && (functionName == "equals" || functionName == "hashCode")) {
                        return@compute true
                    }
                    
                    // Skip common Kotlin standard functions
                    functionName in listOf("copy", "component1", "component2", "component3", "component4", "component5")
                }
                else -> false
            }
        }
    }

    private fun isProjectCode(element: PsiElement): Boolean {
        return ReadAction.compute<Boolean, Exception> {
            val containingFile = element.containingFile
            if (containingFile == null) return@compute false
            
            val virtualFile = containingFile.virtualFile
            if (virtualFile == null) return@compute false
            
            // Check if it's in project source roots
            val fileIndex = ProjectFileIndex.getInstance(project)
            
            // It's project code if it's in source content or test content
            fileIndex.isInSourceContent(virtualFile) || fileIndex.isInTestSourceContent(virtualFile)
        }
    }

    private fun isSystemMethod(method: PsiMethod): Boolean {
        return ReadAction.compute<Boolean, Exception> {
            val className = method.containingClass?.qualifiedName ?: return@compute false
            className.startsWith("java.") || 
                   className.startsWith("javax.") ||
                   className.startsWith("kotlin.") ||
                   className.startsWith("kotlinx.")
        }
    }

    private fun isSystemFunction(function: KtNamedFunction): Boolean {
        return ReadAction.compute<Boolean, Exception> {
            val containingClass = function.containingClass()
            val packageName = function.containingKtFile.packageFqName.asString()
            packageName.startsWith("kotlin.") || 
                   packageName.startsWith("kotlinx.")
        }
    }

    private fun isReflectionCall(expression: PsiMethodCallExpression): Boolean {
        val methodName = expression.methodExpression.referenceName
        return methodName in listOf("invoke", "newInstance", "getDeclaredMethod", "getMethod")
    }

    /**
     * Finds all callers of the given method or function
     */
    fun findCallers(element: PsiElement): List<PsiElement> {
        val callers = mutableListOf<PsiElement>()
        val searchScope = project.allScope()
        
        ReferencesSearch.search(element, searchScope).forEach { reference ->
            val callingElement = PsiTreeUtil.getParentOfType(
                reference.element,
                PsiMethod::class.java,
                KtNamedFunction::class.java
            )
            if (callingElement != null) {
                callers.add(callingElement)
            }
        }
        
        return callers
    }

    private data class CalleeInfo(
        val target: PsiElement?,
        val callType: CallGraphEdge.CallType,
        val resolutionContext: String? = null
    )
}