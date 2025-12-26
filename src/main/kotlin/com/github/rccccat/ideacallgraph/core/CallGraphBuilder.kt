package com.github.rccccat.ideacallgraph.core

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.core.resolver.InterfaceResolver
import com.github.rccccat.ideacallgraph.core.resolver.TypeResolver
import com.github.rccccat.ideacallgraph.core.traversal.DepthFirstTraverser
import com.github.rccccat.ideacallgraph.core.traversal.GraphTraverser
import com.github.rccccat.ideacallgraph.core.traversal.TraversalTarget
import com.github.rccccat.ideacallgraph.core.visitor.CallVisitor
import com.github.rccccat.ideacallgraph.core.visitor.ImplementationInfo
import com.github.rccccat.ideacallgraph.core.visitor.VisitorContext
import com.github.rccccat.ideacallgraph.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraph
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode
import com.github.rccccat.ideacallgraph.ide.psi.PsiNodeFactory
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Coordinator for building call graphs. Orchestrates visitors, resolvers, and traversers. */
class CallGraphBuilder(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
    private val visitors: List<CallVisitor>,
    private val traverser: GraphTraverser = DepthFirstTraverser(),
) {
  private val typeResolver = TypeResolver(project)
  private val interfaceResolver = InterfaceResolver(project, springAnalyzer)
  private val myBatisAnalyzer = MyBatisAnalyzer(project)
  private val nodeFactory = PsiNodeFactory(project, springAnalyzer, myBatisAnalyzer)

  /** Builds a call graph starting from the given element. */
  fun build(startElement: PsiElement): IdeCallGraph? {
    val settings = CallGraphProjectSettings.getInstance(project)
    val nodePointers = mutableMapOf<String, SmartPsiElementPointer<PsiElement>>()
    val elementToNode = mutableMapOf<String, Pair<PsiElement, CallGraphNodeData>>()

    // Create the root node
    val rootNode =
        ReadAction.compute<IdeCallGraphNode?, Exception> {
          nodeFactory.createIdeNode(startElement)?.also { node ->
            nodePointers[node.id] = node.elementPointer
            val sourceElement = node.elementPointer.element ?: startElement
            elementToNode[node.id] = sourceElement to node.data
          }
        } ?: return null

    // Create visitor context
    val context =
        VisitorContext(
            project = project,
            settings = settings,
            typeResolver = typeResolver,
            interfaceResolver = interfaceResolver,
            springAnalyzer = springAnalyzer,
        )

    // Traverse the graph
    val data =
        traverser.traverse(
            rootNode = rootNode.data,
            findCallTargets = { nodeId ->
              findCallTargetsForNode(nodeId, elementToNode, nodePointers, context)
            },
            settings = settings,
        )

    return IdeCallGraph(data, nodePointers.toMap())
  }

  private fun findCallTargetsForNode(
      nodeId: String,
      elementToNode: MutableMap<String, Pair<PsiElement, CallGraphNodeData>>,
      nodePointers: MutableMap<String, SmartPsiElementPointer<PsiElement>>,
      context: VisitorContext,
  ): List<TraversalTarget> {
    val (element, nodeData) = elementToNode[nodeId] ?: return emptyList()
    if (nodeData.nodeType == NodeType.MYBATIS_SQL_STATEMENT) {
      return emptyList()
    }

    return ReadAction.compute<List<TraversalTarget>, Exception> {
      val targets = mutableListOf<TraversalTarget>()

      // Find the appropriate visitor
      val visitor = visitors.find { it.canVisit(element) } ?: return@compute emptyList()

      // Get call targets
      val callTargets = visitor.findCallTargets(element, context)

      for (callTarget in callTargets) {
        if (shouldSkipTarget(callTarget.target, context.settings)) continue

        val targetIdeNode = nodeFactory.createIdeNode(callTarget.target) ?: continue
        val targetNode = targetIdeNode.data
        val targetElement = targetIdeNode.elementPointer.element ?: callTarget.target

        // Store the mapping
        if (!elementToNode.containsKey(targetNode.id)) {
          elementToNode[targetNode.id] = targetElement to targetNode
          nodePointers[targetNode.id] = targetIdeNode.elementPointer
        }

        // Convert implementations
        val implementations =
            callTarget.resolvedImplementations?.mapNotNull { impl ->
              convertImplementationToTarget(impl, elementToNode, nodePointers)
            }

        targets.add(
            TraversalTarget(
                node = targetNode,
                implementations = implementations,
            ),
        )
      }

      if (element is PsiMethod) {
        val myBatisInfo = myBatisAnalyzer.analyzeMapperMethod(element)
        if (myBatisInfo.isMapperMethod && myBatisInfo.sqlType != null) {
          val sqlNode = myBatisAnalyzer.createSqlNode(element, myBatisInfo)
          if (sqlNode != null) {
            if (!elementToNode.containsKey(sqlNode.id)) {
              val sqlElement = sqlNode.elementPointer.element ?: element
              elementToNode[sqlNode.id] = sqlElement to sqlNode.data
              nodePointers[sqlNode.id] = sqlNode.elementPointer
            }
            targets.add(
                TraversalTarget(
                    node = sqlNode.data,
                ),
            )
          }
        }
      }

      targets
    }
  }

  private fun convertImplementationToTarget(
      impl: ImplementationInfo,
      elementToNode: MutableMap<String, Pair<PsiElement, CallGraphNodeData>>,
      nodePointers: MutableMap<String, SmartPsiElementPointer<PsiElement>>,
  ): TraversalTarget? {
    val implIdeNode = nodeFactory.createIdeNode(impl.implementationMethod) ?: return null
    val implNode = implIdeNode.data

    if (!elementToNode.containsKey(implNode.id)) {
      val implElement = implIdeNode.elementPointer.element ?: impl.implementationMethod
      elementToNode[implNode.id] = implElement to implNode
      nodePointers[implNode.id] = implIdeNode.elementPointer
    }

    return TraversalTarget(
        node = implNode,
    )
  }

  private fun shouldSkipTarget(
      element: PsiElement,
      settings: CallGraphProjectSettings,
  ): Boolean {
    val excludeRegexes =
        settings.excludePackagePatterns.mapNotNull { pattern ->
          runCatching { Regex(pattern) }.getOrNull()
        }

    return when (element) {
      is PsiMethod -> {
        val className = element.containingClass?.qualifiedName
        val methodName = element.name

        // Check exclude patterns
        if (className != null && excludeRegexes.any { it.matches(className) }) {
          return true
        }

        // Check method filtering
        if (!settings.includeGettersSetters) {
          if (methodName.startsWith("get") ||
              methodName.startsWith("set") ||
              methodName.startsWith("is")) {
            return true
          }
        }

        if (!settings.includeToString && methodName == "toString") return true
        if (!settings.includeHashCodeEquals &&
            (methodName == "equals" || methodName == "hashCode")) {
          return true
        }

        false
      }

      is KtNamedFunction -> {
        val functionName = element.name ?: return false
        val packageName = element.containingKtFile.packageFqName.asString()

        // Check exclude patterns
        if (excludeRegexes.any { it.matches(packageName) }) {
          return true
        }

        // Check common methods
        if (!settings.includeToString && functionName == "toString") return true
        if (!settings.includeHashCodeEquals &&
            (functionName == "equals" || functionName == "hashCode")) {
          return true
        }

        // Skip common Kotlin functions
        functionName in
            listOf("copy", "component1", "component2", "component3", "component4", "component5")
      }

      else -> {
        false
      }
    }
  }

}
