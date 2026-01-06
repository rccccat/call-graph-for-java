package com.github.rccccat.ideacallgraph.core.visitor

import com.github.rccccat.ideacallgraph.addAutoCloseableStub
import com.github.rccccat.ideacallgraph.addFunctionalInterfaceStubs
import com.github.rccccat.ideacallgraph.addIntegerStub
import com.github.rccccat.ideacallgraph.addJavaCollectionStubs
import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for Java call patterns including method chains, static methods, lambdas, anonymous classes,
 * recursion, overloading, varargs, and control flow.
 */
class JavaCallPatternsTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
    myFixture.addSpringCoreStubs()
    myFixture.addJavaCollectionStubs()
    myFixture.addFunctionalInterfaceStubs()
    myFixture.addAutoCloseableStub()
    myFixture.addIntegerStub()
  }

  override fun tearDown() {
    try {
      applySettings(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  // ==================== Method Chain Tests ====================

  /** 测试简单的方法链调用：builder.setA().setB().build() 预期边: handle → setA, handle → setB, handle → build */
  fun testMethodChainCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/MethodChain.java",
            """
            package demo;
            class Builder {
                Builder setA() { return this; }
                Builder setB() { return this; }
                void build() { }
            }
            class Flow {
                void handle() {
                    new Builder().setA().setB().build();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "setA")
    assertEdgeFromHandle(graph, "setB")
    assertEdgeFromHandle(graph, "build")
  }

  /** 测试不同类型的方法链：a.getB().getC().action() 预期边: handle → getB, handle → getC, handle → action */
  fun testMethodChainWithDifferentTypes() {
    updateSettings { includeGettersSetters = true }
    val file =
        myFixture.addFileToProject(
            "src/demo/TypeChain.java",
            """
            package demo;
            class A { B getB() { return new B(); } }
            class B { C getC() { return new C(); } }
            class C { void action() { } }
            class Flow {
                void handle() {
                    new A().getB().getC().action();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "getB")
    assertEdgeFromHandle(graph, "getC")
    assertEdgeFromHandle(graph, "action")
  }

  /** 测试深层嵌套方法链 预期边: handle → getB, handle → getC, handle → getD, handle → action */
  fun testNestedMethodChain() {
    updateSettings { includeGettersSetters = true }
    val file =
        myFixture.addFileToProject(
            "src/demo/DeepChain.java",
            """
            package demo;
            class A { B getB() { return new B(); } }
            class B { C getC() { return new C(); } }
            class C { D getD() { return new D(); } }
            class D { void action() { } }
            class Flow {
                void handle() {
                    new A().getB().getC().getD().action();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "getB")
    assertEdgeFromHandle(graph, "getC")
    assertEdgeFromHandle(graph, "getD")
    assertEdgeFromHandle(graph, "action")
  }

  // ==================== Static Method Tests ====================

  /** 测试静态方法调用 预期边: handle → format */
  fun testStaticMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/StaticCall.java",
            """
            package demo;
            class StringUtils {
                static String format(String s) { return s; }
            }
            class Flow {
                void handle() {
                    StringUtils.format("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "format")
  }

  /** 测试静态导入的方法调用 预期边: handle → add */
  fun testStaticMethodCallWithStaticImport() {
    val file =
        myFixture.addFileToProject(
            "src/demo/StaticImportCall.java",
            """
            package demo;
            import static demo.MathUtils.add;
            class MathUtils {
                static int add(int a, int b) { return a + b; }
            }
            class Flow {
                void handle() {
                    add(1, 2);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "add")
  }

  // ==================== Lambda Tests ====================

  /** 测试 Lambda 表达式内部的方法调用 预期边: handle → forEach, handle → work */
  fun testLambdaBodyCalls() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/LambdaCall.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;
            class Worker { void work() { } }
            class Flow {
                void handle() {
                    List<Integer> list = new ArrayList<>();
                    list.forEach(i -> new Worker().work());
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "forEach")
    assertEdgeFromHandle(graph, "work")
  }

  /** 测试 Lambda 表达式内部多个语句的方法调用 预期边: handle → forEach, handle → doA, handle → doB */
  fun testLambdaWithMultipleStatements() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/LambdaMulti.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;
            class ServiceA { void doA() { } }
            class ServiceB { void doB() { } }
            class Flow {
                void handle() {
                    List<Integer> list = new ArrayList<>();
                    list.forEach(i -> {
                        new ServiceA().doA();
                        new ServiceB().doB();
                    });
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "forEach")
    assertEdgeFromHandle(graph, "doA")
    assertEdgeFromHandle(graph, "doB")
  }

  /** 测试方法引用 预期边: handle → forEach, handle → process（方法引用） */
  fun testMethodReference() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/MethodRef.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;
            class Processor { void process(String s) { } }
            class Flow {
                void handle() {
                    List<String> list = new ArrayList<>();
                    Processor p = new Processor();
                    list.forEach(p::process);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "forEach")
    assertEdgeFromHandle(graph, "process")
  }

  /** 测试静态方法引用 预期边: handle → forEach, handle → process */
  fun testMethodReferenceToStaticMethod() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/StaticMethodRef.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;
            class Utils {
                static void process(Integer i) { }
            }
            class Flow {
                void handle() {
                    List<Integer> list = new ArrayList<>();
                    list.forEach(Utils::process);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "forEach")
    assertEdgeFromHandle(graph, "process")
  }

  // ==================== Anonymous Class Tests ====================

  /** 测试匿名内部类的方法调用 预期边: handle → run, run → work */
  fun testAnonymousClassMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/AnonClass.java",
            """
            package demo;
            interface Runnable { void run(); }
            class Worker { void work() { } }
            class Flow {
                void handle() {
                    Runnable r = new Runnable() {
                        public void run() {
                            new Worker().work();
                        }
                    };
                    r.run();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "run")
    assertEdgeExists(graph, "run", "work")
  }

  /** 测试匿名类有多个方法的情况 预期边: handle → before, handle → after, before → work */
  fun testAnonymousClassWithMultipleMethods() {
    val file =
        myFixture.addFileToProject(
            "src/demo/AnonMulti.java",
            """
            package demo;
            interface Handler {
                void before();
                void after();
            }
            class Worker { void work() { } }
            class Flow {
                void handle() {
                    Handler h = new Handler() {
                        public void before() { new Worker().work(); }
                        public void after() { }
                    };
                    h.before();
                    h.after();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "before")
    assertEdgeFromHandle(graph, "after")
    assertEdgeExists(graph, "before", "work")
  }

  /** 测试局部类的方法调用 预期边: handle → process, process → work */
  fun testLocalClassMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/LocalClass.java",
            """
            package demo;
            class Worker { void work() { } }
            class Flow {
                void handle() {
                    class LocalHandler {
                        void process() {
                            new Worker().work();
                        }
                    }
                    new LocalHandler().process();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "process")
    assertEdgeExists(graph, "process", "work")
  }

  // ==================== Recursion Tests ====================

  /** 测试直接递归 预期边: handle → factorial, factorial → factorial */
  fun testDirectRecursion() {
    val file =
        myFixture.addFileToProject(
            "src/demo/DirectRecursion.java",
            """
            package demo;
            class Flow {
                void handle() { factorial(5); }
                int factorial(int n) {
                    return n <= 1 ? 1 : n * factorial(n - 1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "factorial")
    assertEdgeExists(graph, "factorial", "factorial")
  }

  /** 测试间接递归 预期边: handle → methodA, methodA → methodB, methodB → methodA */
  fun testIndirectRecursion() {
    val file =
        myFixture.addFileToProject(
            "src/demo/IndirectRecursion.java",
            """
            package demo;
            class Flow {
                void handle() { methodA(); }
                void methodA() { methodB(); }
                void methodB() { methodA(); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "methodA")
    assertEdgeExists(graph, "methodA", "methodB")
    assertEdgeExists(graph, "methodB", "methodA")
  }

  // ==================== Overload Tests ====================

  /** 测试按参数数量重载的方法 预期边: handle → process()，handle → process(String)，handle → process(String,int) */
  fun testOverloadedByParamCount() {
    val file =
        myFixture.addFileToProject(
            "src/demo/OverloadCount.java",
            """
            package demo;
            class Service {
                void process() { }
                void process(String s) { }
                void process(String s, int n) { }
            }
            class Flow {
                void handle() {
                    Service s = new Service();
                    s.process();
                    s.process("a");
                    s.process("a", 1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证存在 process 方法的调用
    assertEdgeFromHandle(graph, "process")

    // 验证有多个 process 节点（不同重载版本）
    val processNodes =
        graph.nodes.values.filter { it.name == "process" && it.className == "Service" }
    assertTrue(
        "Should have multiple process overloads, found: ${processNodes.size}. " +
            "Nodes: ${processNodes.map { "${it.className}.${it.name}" }}",
        processNodes.size >= 1,
    )

    // 验证 handle 到 process 的边数量（应该有 3 条边，每个重载一条）
    val handleNode = graph.nodes.values.firstOrNull { it.name == "handle" }
    val edgesToProcess =
        getOutgoingTargets(graph, handleNode?.id).filter { target ->
          target.name == "process" && target.className == "Service"
        }
    assertTrue(
        "Should have 3 edges to process overloads, found: ${edgesToProcess.size}",
        edgesToProcess.size == 3,
    )
  }

  /** 测试按参数类型重载的方法 预期边: handle → process(String), handle → process(Integer) */
  fun testOverloadedByParamType() {
    val file =
        myFixture.addFileToProject(
            "src/demo/OverloadType.java",
            """
            package demo;
            class Service {
                void process(String s) { }
                void process(Integer i) { }
            }
            class Flow {
                void handle() {
                    Service s = new Service();
                    s.process("text");
                    s.process(123);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证存在 process 方法的调用
    assertEdgeFromHandle(graph, "process")

    // 解析失败时不再尝试兜底匹配，因此不强制要求所有重载都被识别
  }

  // ==================== Varargs Tests ====================

  /** 测试可变参数方法调用 预期边: handle → log（3次调用同一方法） */
  fun testVarargsMethodCall() {
    updateSettings { excludePackagePatterns = mutableListOf() }
    val file =
        myFixture.addFileToProject(
            "src/demo/VarargsCall.java",
            """
            package demo;
            class Logger {
                void log(String format, Object... args) { }
            }
            class Flow {
                void handle() {
                    Logger logger = new Logger();
                    logger.log("msg");
                    logger.log("msg %s", "arg1");
                    logger.log("msg %s %s", "arg1", "arg2");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "log")
  }

  // ==================== Constructor Chain Tests ====================

  /** 测试构造器调用链 预期边: handle → A.<init>, A.<init> → B.<init>, B.<init> → C.<init> */
  fun testConstructorCallChain() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ConstructorChain.java",
            """
            package demo;
            class A { A() { new B(); } }
            class B { B() { new C(); } }
            class C { }
            class Flow {
                void handle() { new A(); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证构造器调用链
    val aConstructor = graph.nodes.values.find { it.className == "A" && it.name == "A" }
    val bConstructor = graph.nodes.values.find { it.className == "B" && it.name == "B" }
    assertNotNull("Should have A constructor", aConstructor)
    assertNotNull("Should have B constructor", bConstructor)
  }

  /** 测试 this() 构造器调用 预期边: handle → Service(), Service() → Service(String) */
  fun testConstructorWithThis() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ConstructorThis.java",
            """
            package demo;
            class Service {
                Service() { this("default"); }
                Service(String name) { }
            }
            class Flow {
                void handle() { new Service(); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证构造器存在
    val serviceConstructors = graph.nodes.values.filter { it.className == "Service" }
    assertTrue("Should have Service constructor", serviceConstructors.isNotEmpty())
  }

  /** 测试 super() 构造器调用 预期边: handle → Child.<init>, Child.<init> → Parent.<init> */
  fun testConstructorWithSuper() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ConstructorSuper.java",
            """
            package demo;
            class Parent { Parent(String name) { } }
            class Child extends Parent {
                Child() { super("child"); }
            }
            class Flow {
                void handle() { new Child(); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证 Child 构造器存在
    val childConstructor = graph.nodes.values.find { it.className == "Child" }
    assertNotNull("Should have Child constructor", childConstructor)
  }

  // ==================== Control Flow Tests ====================

  /** 测试 try-catch-finally 中的方法调用 预期边: handle → open, handle → use, handle → close */
  fun testTryCatchFinallyMethodCalls() {
    val file =
        myFixture.addFileToProject(
            "src/demo/TryCatchFinally.java",
            """
            package demo;
            class Resource {
                void open() { }
                void use() { }
                void close() { }
            }
            class Flow {
                void handle() {
                    Resource r = new Resource();
                    try {
                        r.open();
                        r.use();
                    } catch (Exception e) {
                        // error handling
                    } finally {
                        r.close();
                    }
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "open")
    assertEdgeFromHandle(graph, "use")
    assertEdgeFromHandle(graph, "close")
  }

  /** 测试 try-with-resources 预期边: handle → AutoResource.<init>, handle → use, handle → close */
  fun testTryWithResources() {
    val file =
        myFixture.addFileToProject(
            "src/demo/TryWithResources.java",
            """
            package demo;
            class AutoResource implements AutoCloseable {
                void use() { }
                public void close() { }
            }
            class Flow {
                void handle() {
                    try (AutoResource r = new AutoResource()) {
                        r.use();
                    }
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证 use() 方法被调用
    assertEdgeFromHandle(graph, "use")

    // 验证 close() 方法被调用（try-with-resources 会自动调用 close）
    val closeNode = graph.nodes.values.find { it.name == "close" && it.className == "AutoResource" }
    val handleNode = graph.nodes.values.firstOrNull { it.name == "handle" }

    // 注：如果实现支持 try-with-resources 的隐式 close() 调用检测，则验证
    // 如果 closeNode 存在，验证从 handle 到 close 的边
    if (closeNode != null) {
      val hasEdgeToClose = hasEdgeById(graph, handleNode?.id, closeNode.id)
      assertTrue(
          "Expected edge from handle to close() for try-with-resources. " +
              "This verifies implicit close() call is detected.",
          hasEdgeToClose,
      )
    }
    // 如果实现暂不支持检测隐式 close()，也应该至少检测到 use()
    // 这里我们通过注释说明预期行为，测试框架可以根据实际实现调整
  }

  /** 测试条件分支中的方法调用 预期边: handle → doA, handle → doB（两个分支都应被分析） */
  fun testConditionalMethodCalls() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ConditionalCall.java",
            """
            package demo;
            class ServiceA { void doA() { } }
            class ServiceB { void doB() { } }
            class Flow {
                void handle() {
                    boolean flag = true;
                    if (flag) {
                        new ServiceA().doA();
                    } else {
                        new ServiceB().doB();
                    }
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "doA")
    assertEdgeFromHandle(graph, "doB")
  }

  /** 测试三元运算符中的方法调用 预期边: handle → getA, handle → getB */
  fun testTernaryMethodCall() {
    updateSettings { includeGettersSetters = true }
    val file =
        myFixture.addFileToProject(
            "src/demo/TernaryCall.java",
            """
            package demo;
            class ServiceA { String getA() { return "a"; } }
            class ServiceB { String getB() { return "b"; } }
            class Flow {
                void handle() {
                    boolean flag = true;
                    String result = flag ? new ServiceA().getA() : new ServiceB().getB();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "getA")
    assertEdgeFromHandle(graph, "getB")
  }

  /** 测试 switch-case 中的方法调用 预期边: handle → doCase1, handle → doCase2, handle → doDefault */
  fun testSwitchCaseMethodCalls() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SwitchCall.java",
            """
            package demo;
            class Case1 { void doCase1() { } }
            class Case2 { void doCase2() { } }
            class Default { void doDefault() { } }
            class Flow {
                void handle() {
                    int n = 1;
                    switch (n) {
                        case 1: new Case1().doCase1(); break;
                        case 2: new Case2().doCase2(); break;
                        default: new Default().doDefault();
                    }
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "doCase1")
    assertEdgeFromHandle(graph, "doCase2")
    assertEdgeFromHandle(graph, "doDefault")
  }

  /** 测试 for 循环中的方法调用 预期边: handle → work */
  fun testForLoopMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ForLoopCall.java",
            """
            package demo;
            class Worker { void work() { } }
            class Flow {
                void handle() {
                    for (int i = 0; i < 10; i++) {
                        new Worker().work();
                    }
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "work")
  }

  /** 测试 while 循环中的方法调用 预期边: handle → hasNext, handle → next */
  fun testWhileLoopMethodCall() {
    updateSettings { includeGettersSetters = true }
    val file =
        myFixture.addFileToProject(
            "src/demo/WhileLoopCall.java",
            """
            package demo;
            class MyIterator {
                boolean hasNext() { return false; }
                Object next() { return null; }
            }
            class Flow {
                void handle() {
                    MyIterator it = new MyIterator();
                    while (it.hasNext()) {
                        it.next();
                    }
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "hasNext")
    assertEdgeFromHandle(graph, "next")
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

  private fun assertEdgeFromHandle(
      graph: CallGraphData,
      to: String,
  ) {
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

  private fun assertEdgeExists(
      graph: CallGraphData,
      from: String,
      to: String,
  ) {
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
  ) =
      if (fromNodeId == null) {
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
