package com.github.rccccat.callgraphjava.core

import com.github.rccccat.callgraphjava.api.model.CallGraphNodeData
import com.github.rccccat.callgraphjava.api.model.NodeType
import com.github.rccccat.callgraphjava.core.resolver.InterfaceResolver
import com.github.rccccat.callgraphjava.core.resolver.TypeResolver
import com.github.rccccat.callgraphjava.core.traversal.DepthFirstTraverser
import com.github.rccccat.callgraphjava.core.traversal.TraversalTarget
import com.github.rccccat.callgraphjava.core.visitor.ImplementationInfo
import com.github.rccccat.callgraphjava.core.visitor.JavaCallVisitor
import com.github.rccccat.callgraphjava.core.visitor.VisitorContext
import com.github.rccccat.callgraphjava.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.callgraphjava.framework.spring.SpringAnalyzer
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraph
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.github.rccccat.callgraphjava.ide.psi.PsiNodeFactory
import com.github.rccccat.callgraphjava.settings.CallGraphProjectSettings
import com.github.rccccat.callgraphjava.util.ExcludePatternMatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PropertyUtilBase

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

  fun build(startElement: PsiElement): IdeCallGraph? {
    val settings = CallGraphProjectSettings.getInstance(project)
    val excludePatternMatcher = ExcludePatternMatcher.fromPatterns(settings.excludePackagePatterns)
    val nodePointers = mutableMapOf<String, SmartPsiElementPointer<PsiElement>>()
    val elementToNode = mutableMapOf<String, Pair<PsiElement, CallGraphNodeData>>()

    val rootNode =
        ReadAction.compute<IdeCallGraphNode?, Exception> {
          nodeFactory.createIdeNode(startElement)?.also { node ->
            nodePointers[node.id] = node.elementPointer
            val sourceElement = node.elementPointer.element ?: startElement
            elementToNode[node.id] = sourceElement to node.data
          }
        } ?: return null

    val context =
        VisitorContext(
            project = project,
            settings = settings,
            interfaceResolver = interfaceResolver,
            springAnalyzer = springAnalyzer,
            excludePatternMatcher = excludePatternMatcher,
        )

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

      if (!visitor.canVisit(element)) return@compute emptyList()

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
