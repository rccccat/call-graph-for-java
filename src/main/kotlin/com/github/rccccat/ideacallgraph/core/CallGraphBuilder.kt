package com.github.rccccat.ideacallgraph.core

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.core.resolver.InterfaceResolver
import com.github.rccccat.ideacallgraph.core.resolver.TypeResolver
import com.github.rccccat.ideacallgraph.core.traversal.DepthFirstTraverser
import com.github.rccccat.ideacallgraph.core.traversal.TraversalTarget
import com.github.rccccat.ideacallgraph.core.visitor.ImplementationInfo
import com.github.rccccat.ideacallgraph.core.visitor.JavaCallVisitor
import com.github.rccccat.ideacallgraph.core.visitor.VisitorContext
import com.github.rccccat.ideacallgraph.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraph
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode
import com.github.rccccat.ideacallgraph.ide.psi.PsiNodeFactory
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.github.rccccat.ideacallgraph.util.ExcludePatternMatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PropertyUtilBase

/** Coordinator for building call graphs. Orchestrates visitors, resolvers, and traversers. */
class CallGraphBuilder(
    private val project: Project,
    private val springAnalyzer: SpringAnalyzer,
    private val visitor: JavaCallVisitor,
    private val typeResolver: TypeResolver,
    private val interfaceResolver: InterfaceResolver,
    private val myBatisAnalyzer: MyBatisAnalyzer,
    private val nodeFactory: PsiNodeFactory,
) {
  private val traverser = DepthFirstTraverser()

  /** Builds a call graph starting from the given element. */
  fun build(startElement: PsiElement): IdeCallGraph? {
    val settings = CallGraphProjectSettings.getInstance(project)
    val excludePatternMatcher = ExcludePatternMatcher.fromPatterns(settings.excludePackagePatterns)
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
            excludePatternMatcher = excludePatternMatcher,
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

      // Check if visitor can handle this element
      if (!visitor.canVisit(element)) return@compute emptyList()

      // Get call targets
      val callTargets = visitor.findCallTargets(element, context)

      for (callTarget in callTargets) {
        val implementationTargets =
            callTarget.resolvedImplementations
                ?.mapNotNull { impl ->
                  if (shouldSkipTarget(impl.implementationMethod, context)) {
                    null
                  } else {
                    convertImplementationToTarget(impl, elementToNode, nodePointers)
                  }
                }
                .orEmpty()

        val hasImplementations = implementationTargets.isNotEmpty()
        val keepTargetNode = shouldKeepTargetNode(callTarget.target, hasImplementations)

        if (keepTargetNode && !shouldSkipTarget(callTarget.target, context)) {
          val targetIdeNode = nodeFactory.createIdeNode(callTarget.target)
          if (targetIdeNode != null) {
            val targetNode = targetIdeNode.data
            val targetElement = targetIdeNode.elementPointer.element ?: callTarget.target

            // Store the mapping
            if (!elementToNode.containsKey(targetNode.id)) {
              elementToNode[targetNode.id] = targetElement to targetNode
              nodePointers[targetNode.id] = targetIdeNode.elementPointer
            }

            targets.add(
                TraversalTarget(
                    node = targetNode,
                ),
            )
          }
        }

        if (hasImplementations) {
          targets.addAll(implementationTargets)
        }
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

  private fun shouldKeepTargetNode(
      target: PsiElement,
      hasImplementations: Boolean,
  ): Boolean {
    val method = target as? PsiMethod ?: return true
    val containingClass = method.containingClass ?: return true
    if (!hasImplementations) return true
    // Keep interface default methods (they have a body)
    if (containingClass.isInterface) {
      return method.body != null
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return false
    return true
  }

  private fun shouldSkipTarget(
      element: PsiElement,
      context: VisitorContext,
  ): Boolean {
    return when (element) {
      is PsiMethod -> {
        if (context.excludePatternMatcher.matchesMethod(element)) {
          return true
        }
        val methodName = element.name

        // Check method filtering
        if (!context.settings.includeGettersSetters) {
          if (PropertyUtilBase.isSimplePropertyGetter(element) ||
              PropertyUtilBase.isSimplePropertySetter(element)) {
            return true
          }
        }

        if (!context.settings.includeToString && methodName == "toString") return true
        if (!context.settings.includeHashCodeEquals &&
            (methodName == "equals" || methodName == "hashCode")) {
          return true
        }

        false
      }

      else -> {
        false
      }
    }
  }

}
