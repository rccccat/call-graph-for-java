package com.github.rccccat.ideacallgraph.core.dataflow

import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for the parameter usage filtering feature using Slice Analysis. When enabled, calls to
 * methods where parameters are not "effectively used" are filtered from the call graph.
 */
class ParameterUsageFilterTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
  }

  override fun tearDown() {
    try {
      applySettings(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  // ========== Tests with filtering DISABLED (default behavior) ==========

  fun testFilteringDisabledByDefault() {
    assertFalse(CallGraphAppSettings.getInstance().filterByParameterUsage)
  }

  fun testUnusedParameterIncludedWhenFilteringDisabled() {
    val file =
        myFixture.addFileToProject(
            "src/demo/UnusedParamDisabled.java",
            """
            package demo;
            class Service {
                void unusedParam(String param) {
                    System.out.println("ignored");
                }
            }
            class Flow {
                void handle() {
                    new Service().unusedParam("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // With filtering disabled, call should be included
    assertEdgeFromHandle(graph, "unusedParam")
  }

  // ========== Tests with filtering ENABLED - Should INCLUDE ==========

  fun testDirectParameterUsage() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/DirectUsage.java",
            """
            package demo;
            class Service {
                void directUse(String param) {
                    System.out.println(param);
                }
            }
            class Flow {
                void handle() {
                    new Service().directUse("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "directUse")
  }

  fun testParameterUsedViaAssignment() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/AssignmentUsage.java",
            """
            package demo;
            class Service {
                void assignAndUse(String param) {
                    String local = param;
                    System.out.println(local);
                }
            }
            class Flow {
                void handle() {
                    new Service().assignAndUse("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "assignAndUse")
  }

  fun testParameterUsedViaTransform() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/TransformUsage.java",
            """
            package demo;
            class Service {
                void transformAndUse(String param) {
                    String transformed = transform(param);
                    System.out.println(transformed);
                }
                String transform(String s) { return s.toUpperCase(); }
            }
            class Flow {
                void handle() {
                    new Service().transformAndUse("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "transformAndUse")
  }

  fun testParameterReturned() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/ReturnUsage.java",
            """
            package demo;
            class Service {
                String returnParam(String param) {
                    return param;
                }
            }
            class Flow {
                void handle() {
                    new Service().returnParam("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "returnParam")
  }

  fun testParameterPassedToAnotherMethod() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/PassToMethod.java",
            """
            package demo;
            class Service {
                void passToMethod(String param) {
                    helper(param);
                }
                void helper(String s) { }
            }
            class Flow {
                void handle() {
                    new Service().passToMethod("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "passToMethod")
  }

  fun testParameterUsedAsMethodReceiver() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/ReceiverUsage.java",
            """
            package demo;
            class Service {
                void useAsReceiver(String param) {
                    param.length();
                }
            }
            class Flow {
                void handle() {
                    new Service().useAsReceiver("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "useAsReceiver")
  }

  fun testParameterAssignedToField() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/FieldAssignment.java",
            """
            package demo;
            class Service {
                private String field;
                void assignToField(String param) {
                    this.field = param;
                }
            }
            class Flow {
                void handle() {
                    new Service().assignToField("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "assignToField")
  }

  fun testNoParameterMethodAlwaysIncluded() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/NoParam.java",
            """
            package demo;
            class Service {
                void noParam() {
                    System.out.println("hello");
                }
            }
            class Flow {
                void handle() {
                    new Service().noParam();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "noParam")
  }

  fun testConstructorAlwaysIncluded() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/ConstructorCall.java",
            """
            package demo;
            class Service {
                Service(String unused) { }
            }
            class Flow {
                void handle() {
                    new Service("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // Constructor calls should always be included
    assertEdgeFromHandle(graph, "Service")
  }

  // ========== Tests with filtering ENABLED - Should FILTER ==========

  fun testUnusedParameterFiltered() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/UnusedParam.java",
            """
            package demo;
            class Service {
                void unusedParam(String param) {
                    System.out.println("ignored");
                }
            }
            class Flow {
                void handle() {
                    new Service().unusedParam("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertNoEdgeFromHandle(graph, "unusedParam")
  }

  /**
   * Known limitation: When a parameter is assigned to a local variable that's never used, the
   * current Slice Analysis approach still considers it as "used" because the data flows to the
   * local variable. Detecting that the local variable itself is never used would require deeper
   * recursive analysis.
   *
   * This test documents the current behavior - the call is NOT filtered even though the parameter
   * is effectively unused.
   */
  fun testAssignedButNeverUsed_KnownLimitation() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/AssignedNotUsed.java",
            """
            package demo;
            class Service {
                void assignedNotUsed(String param) {
                    String local = param;
                    System.out.println("ignored");
                }
            }
            class Flow {
                void handle() {
                    new Service().assignedNotUsed("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // Note: This is a known limitation - the call is NOT filtered because
    // the Slice Analysis tracks the flow to local variable `local`, even though
    // `local` is never actually used. The parameter has a reference, so it's
    // considered "used" by the current implementation.
    assertEdgeFromHandle(graph, "assignedNotUsed")
  }

  // ========== Edge cases ==========

  fun testAbstractMethodAlwaysIncluded() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/AbstractMethod.java",
            """
            package demo;
            abstract class AbstractService {
                abstract void process(String param);
            }
            class ServiceImpl extends AbstractService {
                void process(String param) {
                    System.out.println("ignored");
                }
            }
            class Flow {
                void handle() {
                    AbstractService service = new ServiceImpl();
                    service.process("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // Abstract methods should always be included (conservative handling)
    assertEdgeFromHandle(graph, "process")
  }

  fun testInterfaceMethodAlwaysIncluded() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/InterfaceMethod.java",
            """
            package demo;
            interface ServiceInterface {
                void process(String param);
            }
            class ServiceImpl implements ServiceInterface {
                public void process(String param) {
                    System.out.println("ignored");
                }
            }
            class Flow {
                void handle() {
                    ServiceInterface service = new ServiceImpl();
                    service.process("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // Interface methods have no body - conservatively included
    assertEdgeFromHandle(graph, "process")
  }

  fun testMultipleParametersAtLeastOneUsed() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/MultipleParams.java",
            """
            package demo;
            class Service {
                void multipleParams(String used, String unused) {
                    System.out.println(used);
                }
            }
            class Flow {
                void handle() {
                    new Service().multipleParams("a", "b");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // At least one parameter is used, so the call should be included
    assertEdgeFromHandle(graph, "multipleParams")
  }

  fun testMultipleParametersAllUnused() {
    enableParameterFiltering()
    val file =
        myFixture.addFileToProject(
            "src/demo/AllParamsUnused.java",
            """
            package demo;
            class Service {
                void allUnused(String a, String b, int c) {
                    System.out.println("none used");
                }
            }
            class Flow {
                void handle() {
                    new Service().allUnused("a", "b", 1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // All parameters unused, should be filtered
    assertNoEdgeFromHandle(graph, "allUnused")
  }

  // ========== Helper methods ==========

  private fun enableParameterFiltering() {
    updateSettings { filterByParameterUsage = true }
  }

  private fun findHandleMethod(file: com.intellij.psi.PsiFile): PsiMethod =
      PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "handle" }

  private fun buildGraph(method: PsiMethod): CallGraphData {
    val service = CallGraphServiceImpl.getInstance(project)
    service.resetCaches()
    val graph = service.buildCallGraph(method) ?: error("Call graph build failed")
    return graph.data
  }

  private fun assertEdgeFromHandle(
      graph: CallGraphData,
      to: String,
  ) {
    val from = "handle"
    val fromNode = graph.nodes.values.firstOrNull { it.name == from }
    val fromEdges =
        graph.edges
            .filter { edge -> edge.fromId == fromNode?.id }
            .joinToString { edge ->
              val toNode = graph.nodes[edge.toId]
              "$from->${toNode?.className}.${toNode?.name}"
            }
    assertTrue(
        "Missing edge $from -> $to. Existing: $fromEdges",
        graph.edges.any { edge ->
          edge.fromId == fromNode?.id && graph.nodes[edge.toId]?.name == to
        },
    )
  }

  private fun assertNoEdgeFromHandle(
      graph: CallGraphData,
      to: String,
  ) {
    val from = "handle"
    val fromNode = graph.nodes.values.firstOrNull { it.name == from }
    val fromEdges =
        graph.edges
            .filter { edge -> edge.fromId == fromNode?.id }
            .joinToString { edge ->
              val toNode = graph.nodes[edge.toId]
              "$from->${toNode?.className}.${toNode?.name}"
            }
    assertFalse(
        "Unexpected edge $from -> $to. Existing: $fromEdges",
        graph.edges.any { edge ->
          edge.fromId == fromNode?.id && graph.nodes[edge.toId]?.name == to
        },
    )
  }

  private fun updateSettings(block: CallGraphAppSettings.State.() -> Unit) {
    val state = cloneSettings(CallGraphAppSettings.getInstance().state).apply(block)
    applySettings(state)
  }

  private fun applySettings(state: CallGraphAppSettings.State) {
    val settings = CallGraphAppSettings.getInstance()
    settings.setProjectMaxDepth(state.projectMaxDepth)
    settings.setThirdPartyMaxDepth(state.thirdPartyMaxDepth)
    settings.setExcludePackagePatterns(state.excludePackagePatterns)
    settings.setIncludeGettersSetters(state.includeGettersSetters)
    settings.setIncludeToString(state.includeToString)
    settings.setIncludeHashCodeEquals(state.includeHashCodeEquals)
    settings.setResolveInterfaceImplementations(state.resolveInterfaceImplementations)
    settings.setFilterByParameterUsage(state.filterByParameterUsage)
  }

  private fun cloneSettings(state: CallGraphAppSettings.State): CallGraphAppSettings.State =
      state.copy(excludePackagePatterns = state.excludePackagePatterns.toMutableList())
}
