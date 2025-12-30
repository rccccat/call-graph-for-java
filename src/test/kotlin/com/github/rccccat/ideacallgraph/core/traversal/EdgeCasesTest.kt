package com.github.rccccat.ideacallgraph.core.traversal

import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for edge cases including cycle detection, depth limits, abstract methods, interface default
 * methods, and private/final methods.
 */
class EdgeCasesTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
    myFixture.addSpringCoreStubs()
  }

  override fun tearDown() {
    try {
      applySettings(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  // ==================== Cycle Detection Tests ====================

  /** 测试循环检测不会导致无限循环 预期: 图构建成功，无超时，循环被检测 */
  fun testCycleDetectionDoesNotCauseInfiniteLoop() {
    val file =
        myFixture.addFileToProject(
            "src/demo/CycleDetection.java",
            """
            package demo;
            class A {
                void methodA(B b) { b.methodB(this); }
            }
            class B {
                void methodB(A a) { a.methodA(this); }
            }
            class Flow {
                void handle() {
                    new A().methodA(new B());
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证图构建成功，并且检测到循环
    assertEdgeFromHandle(graph, "methodA")
    assertEdgeExists(graph, "methodA", "methodB")
    assertEdgeExists(graph, "methodB", "methodA")
  }

  /** 测试多个循环在图中的情况 预期: 两个循环都被检测，图构建成功 */
  fun testMultipleCyclesInGraph() {
    val file =
        myFixture.addFileToProject(
            "src/demo/MultipleCycles.java",
            """
            package demo;
            class A {
                void a() { new B().b(); }
            }
            class B {
                void b() { new C().c(); }
            }
            class C {
                void c() { new A().a(); }
                void d() { new D().d(); }
            }
            class D {
                void d() { new C().d(); }
            }
            class Flow {
                void handle() {
                    new A().a();
                    new C().d();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证图构建成功
    assertEdgeFromHandle(graph, "a")
    assertEdgeFromHandle(graph, "d")
    assertEdgeExists(graph, "a", "b")
    assertEdgeExists(graph, "b", "c")
  }

  // ==================== Depth Limit Tests ====================

  /** 测试项目代码深度限制 预期: 只遍历到设置的深度，超出部分不包含 深度为 2: handle(0) -> m1(1) -> m2(2)，m3 不应该被遍历 */
  fun testProjectCodeDepthLimit() {
    updateSettings { projectMaxDepth = 2 }

    val file =
        myFixture.addFileToProject(
            "src/demo/DepthLimit.java",
            """
            package demo;
            class Level1 { void m1() { new Level2().m2(); } }
            class Level2 { void m2() { new Level3().m3(); } }
            class Level3 { void m3() { new Level4().m4(); } }
            class Level4 { void m4() { } }
            class Flow {
                void handle() { new Level1().m1(); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 深度为 2: handle(0) -> m1(1) -> m2(2)
    assertEdgeFromHandle(graph, "m1")
    assertEdgeExists(graph, "m1", "m2")

    // m3 和 m4 不应该出现在图中（超出深度限制）
    val m2Node = graph.nodes.values.find { it.name == "m2" }
    assertNotNull("m2 should exist", m2Node)

    // 验证 m2 没有到 m3 的边（因为深度限制）
    val m3Node = graph.nodes.values.find { it.name == "m3" }
    if (m3Node != null) {
      val hasEdgeFromM2ToM3 = hasEdgeById(graph, m2Node?.id, m3Node.id)
      assertFalse("m2 should not have edge to m3 due to depth limit", hasEdgeFromM2ToM3)
    }

    // 验证 m4 不应该存在
    val m4Node = graph.nodes.values.find { it.name == "m4" }
    assertNull("m4 should not exist due to depth limit", m4Node)
  }

  /**
   * 测试深层调用链（超出默认深度） 预期: 只遍历到设置的深度 深度为 5: handle(0) -> L1(1) -> L2(2) -> L3(3) -> L4(4) -> L5(5) L6+
   * 不应该被遍历
   */
  fun testDeepCallChain() {
    updateSettings { projectMaxDepth = 5 }

    val file =
        myFixture.addFileToProject(
            "src/demo/DeepChain.java",
            """
            package demo;
            class L1 { void m() { new L2().m(); } }
            class L2 { void m() { new L3().m(); } }
            class L3 { void m() { new L4().m(); } }
            class L4 { void m() { new L5().m(); } }
            class L5 { void m() { new L6().m(); } }
            class L6 { void m() { new L7().m(); } }
            class L7 { void m() { new L8().m(); } }
            class L8 { void m() { new L9().m(); } }
            class L9 { void m() { new L10().m(); } }
            class L10 { void m() { } }
            class Flow {
                void handle() { new L1().m(); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证图构建成功
    assertEdgeFromHandle(graph, "m")

    // L1 到 L5 应该存在
    val l1Node = graph.nodes.values.find { it.className == "L1" }
    val l5Node = graph.nodes.values.find { it.className == "L5" }
    assertNotNull("L1 should exist", l1Node)
    assertNotNull("L5 should exist", l5Node)

    // L6+ 不应该存在（超出深度限制）
    val l6Node = graph.nodes.values.find { it.className == "L6" }
    val l7Node = graph.nodes.values.find { it.className == "L7" }
    val l10Node = graph.nodes.values.find { it.className == "L10" }

    // 验证 L5 没有到 L6 的边（如果 L6 存在的话）
    if (l6Node != null) {
      val hasEdgeFromL5ToL6 = hasEdgeById(graph, l5Node?.id, l6Node.id)
      assertFalse("L5 should not have edge to L6 due to depth limit", hasEdgeFromL5ToL6)
    }

    // L7+ 绝对不应该存在
    assertNull("L7 should not exist due to depth limit", l7Node)
    assertNull("L10 should not exist due to depth limit", l10Node)
  }

  // ==================== Abstract Method Tests ====================

  /** 测试抽象方法调用 预期边: handle → template, template → process */
  fun testAbstractMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/AbstractMethod.java",
            """
            package demo;
            abstract class AbstractService {
                abstract void process();
                void template() {
                    process();
                }
            }
            class ConcreteService extends AbstractService {
                void process() { }
            }
            class Flow {
                void handle() {
                    AbstractService service = new ConcreteService();
                    service.template();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "template")
    assertEdgeExists(graph, "template", "process")
  }

  // ==================== Interface Default Method Tests ====================

  /** 测试接口默认方法 预期边: handle → process, process → doWork */
  fun testInterfaceDefaultMethod() {
    val file =
        myFixture.addFileToProject(
            "src/demo/DefaultMethod.java",
            """
            package demo;
            interface Service {
                default void process() {
                    doWork();
                }
                void doWork();
            }
            class ServiceImpl implements Service {
                public void doWork() { }
            }
            class Flow {
                void handle() {
                    Service service = new ServiceImpl();
                    service.process();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "process")
    assertEdgeExists(graph, "process", "doWork")
  }

  // ==================== Private Method Tests ====================

  /**
   * 测试私有方法调用链 预期边: handle → publicMethod, publicMethod → privateMethod, privateMethod →
   * anotherPrivateMethod
   */
  fun testPrivateMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/PrivateMethod.java",
            """
            package demo;
            class Service {
                public void publicMethod() {
                    privateMethod();
                }
                private void privateMethod() {
                    anotherPrivateMethod();
                }
                private void anotherPrivateMethod() { }
            }
            class Flow {
                void handle() {
                    new Service().publicMethod();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "publicMethod")
    assertEdgeExists(graph, "publicMethod", "privateMethod")
    assertEdgeExists(graph, "privateMethod", "anotherPrivateMethod")
  }

  // ==================== Final Method Tests ====================

  /** 测试 final 方法调用 预期边: handle → finalMethod, finalMethod → normalMethod（final方法不被override） */
  fun testFinalMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/FinalMethod.java",
            """
            package demo;
            class Parent {
                final void finalMethod() {
                    normalMethod();
                }
                void normalMethod() { }
            }
            class Child extends Parent {
                void normalMethod() { }
            }
            class Flow {
                void handle() {
                    Parent p = new Child();
                    p.finalMethod();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "finalMethod")
    assertEdgeExists(graph, "finalMethod", "normalMethod")
  }

  // ==================== This/Super Method Call Tests ====================

  /** 测试 this 方法调用 预期边: handle → methodA, methodA → methodB, methodB → methodC */
  fun testThisMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ThisCall.java",
            """
            package demo;
            class Service {
                void methodA() {
                    this.methodB();
                }
                void methodB() {
                    methodC();
                }
                void methodC() { }
            }
            class Flow {
                void handle() {
                    new Service().methodA();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "methodA")
    assertEdgeExists(graph, "methodA", "methodB")
    assertEdgeExists(graph, "methodB", "methodC")
  }

  /**
   * 测试 super 方法调用 预期边:
   * - handle → process (Child.process)
   * - Child.process → Parent.process (via super.process())
   * - Child.process → afterProcess
   * - Parent.process → doWork
   */
  fun testSuperMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SuperCall.java",
            """
            package demo;
            class Parent {
                void process() {
                    doWork();
                }
                void doWork() { }
            }
            class Child extends Parent {
                void process() {
                    super.process();
                    afterProcess();
                }
                void afterProcess() { }
            }
            class Flow {
                void handle() {
                    new Child().process();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // handle 调用 Child.process
    assertEdgeFromHandle(graph, "process")

    // 找到 Child.process 节点
    val childProcessNode =
        graph.nodes.values.find { it.name == "process" && it.className == "Child" }
    assertNotNull("Child.process should exist", childProcessNode)

    // 找到 Parent.process 节点
    val parentProcessNode =
        graph.nodes.values.find { it.name == "process" && it.className == "Parent" }

    // Child.process 调用 afterProcess
    val afterProcessNode = graph.nodes.values.find { it.name == "afterProcess" }
    assertNotNull("afterProcess should exist", afterProcessNode)
    val hasEdgeToAfterProcess =
        hasEdgeById(graph, childProcessNode?.id, afterProcessNode?.id)
    assertTrue("Child.process should call afterProcess", hasEdgeToAfterProcess)

    // Child.process 通过 super.process() 调用 Parent.process
    if (parentProcessNode != null) {
      val hasEdgeToParentProcess =
          hasEdgeById(graph, childProcessNode?.id, parentProcessNode.id)
      assertTrue("Child.process should call Parent.process via super", hasEdgeToParentProcess)

      // Parent.process 调用 doWork
      val doWorkNode = graph.nodes.values.find { it.name == "doWork" }
      if (doWorkNode != null) {
        val hasEdgeToDoWork =
            hasEdgeById(graph, parentProcessNode.id, doWorkNode.id)
        assertTrue("Parent.process should call doWork", hasEdgeToDoWork)
      }
    }
  }

  // ==================== Empty Method Body Tests ====================

  /** 测试空方法体 预期边: handle → emptyMethod（无出边） */
  fun testEmptyMethodBody() {
    val file =
        myFixture.addFileToProject(
            "src/demo/EmptyMethod.java",
            """
            package demo;
            class Service {
                void emptyMethod() { }
            }
            class Flow {
                void handle() {
                    new Service().emptyMethod();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "emptyMethod")
    // emptyMethod 应该没有出边
    val emptyMethodNode = graph.nodes.values.find { it.name == "emptyMethod" }
    val outgoingTargets = getOutgoingTargets(graph, emptyMethodNode?.id)
    assertTrue("emptyMethod should have no outgoing edges", outgoingTargets.isEmpty())
  }

  // ==================== Helper Methods ====================

  private fun findHandleMethod(file: com.intellij.psi.PsiFile): PsiMethod =
      PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "handle" }

  private fun buildGraph(method: PsiMethod): CallGraphData {
    val service = CallGraphServiceImpl.getInstance(project)
    service.resetCaches()
    val graph = service.buildCallGraph(method) ?: error("Call graph build failed")
    return graph.data
  }

  private fun assertEdgeFromHandle(graph: CallGraphData, to: String) {
    val from = "handle"
    val fromNode = graph.nodes.values.firstOrNull { it.name == from }
    val fromEdges =
        getOutgoingTargets(graph, fromNode?.id).joinToString { target ->
          "$from->${target.className}.${target.name}"
        }
    assertTrue(
        "Missing edge $from -> $to. Existing: $fromEdges",
        getOutgoingTargets(graph, fromNode?.id).any { target -> target.name == to },
    )
  }

  private fun assertEdgeExists(graph: CallGraphData, from: String, to: String) {
    val fromNode = graph.nodes.values.firstOrNull { it.name == from }
    assertTrue(
        "Missing edge $from -> $to",
        getOutgoingTargets(graph, fromNode?.id).any { target -> target.name == to },
    )
  }

  private fun hasEdgeById(
      graph: CallGraphData,
      fromId: String?,
      toId: String?,
  ): Boolean {
    if (fromId == null || toId == null) return false
    return graph.getCallTargetIds(fromId).contains(toId)
  }

  private fun getOutgoingTargets(
      graph: CallGraphData,
      fromNodeId: String?,
  ) = if (fromNodeId == null) {
    emptyList()
  } else {
    graph.getCallTargets(fromNodeId)
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
  }

  private fun cloneSettings(state: CallGraphAppSettings.State): CallGraphAppSettings.State =
      state.copy(excludePackagePatterns = state.excludePackagePatterns.toMutableList())
}
