package com.github.rccccat.ideacallgraph.core.resolver

import com.github.rccccat.ideacallgraph.addFunctionalInterfaceStubs
import com.github.rccccat.ideacallgraph.addIntegerStub
import com.github.rccccat.ideacallgraph.addJavaCollectionStubs
import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.addStreamStubs
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for generic type scenarios including generic methods, nested generics, wildcards, and
 * stream chains.
 */
class GenericTypeTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
    myFixture.addSpringCoreStubs()
    myFixture.addJavaCollectionStubs()
    myFixture.addFunctionalInterfaceStubs()
    myFixture.addStreamStubs()
    myFixture.addIntegerStub()
  }

  override fun tearDown() {
    try {
      applySettings(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  // ==================== Generic Method Tests ====================

  /** 测试泛型方法调用 预期边: handle → identity */
  fun testGenericMethodCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/GenericMethod.java",
            """
            package demo;
            class Utils {
                <T> T identity(T value) { return value; }
            }
            class Flow {
                void handle() {
                    Utils utils = new Utils();
                    String result = utils.identity("test");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "identity")
  }

  /** 测试带多个类型参数的泛型方法 预期边: handle → createMap, createMap → put */
  fun testGenericMethodWithMultipleTypeParams() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/MultiTypeParams.java",
            """
            package demo;
            import java.util.Map;
            import java.util.HashMap;

            class Utils {
                <K, V> Map<K, V> createMap(K key, V value) {
                    Map<K, V> map = new HashMap<>();
                    map.put(key, value);
                    return map;
                }
            }
            class Flow {
                void handle() {
                    Utils utils = new Utils();
                    Map<String, Integer> map = utils.createMap("count", 1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "createMap")
    assertEdgeExists(graph, "createMap", "put")
  }

  // ==================== Bounded Generic Tests ====================

  /** 测试有界泛型 预期边: handle → handle(GenericHandler), handle → eat */
  fun testBoundedGeneric() {
    val file =
        myFixture.addFileToProject(
            "src/demo/BoundedGeneric.java",
            """
            package demo;
            class Animal { void eat() { } }
            class Dog extends Animal { void bark() { } }
            class GenericHandler<T extends Animal> {
                void process(T animal) {
                    animal.eat();
                }
            }
            class Flow {
                void handle() {
                    GenericHandler<Dog> handler = new GenericHandler<>();
                    handler.process(new Dog());
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "process")
    assertEdgeExists(graph, "process", "eat")
  }

  // ==================== Wildcard Generic Tests ====================

  /** 测试上界通配符（生产者） 预期边: handle → processProducer, processProducer → get, processProducer → eat */
  fun testWildcardProducer() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/WildcardProducer.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;

            class Animal { void eat() { } }
            class Dog extends Animal { }

            class Flow {
                void processProducer(List<? extends Animal> animals) {
                    animals.get(0).eat();
                }
                void handle() {
                    List<Dog> dogs = new ArrayList<>();
                    processProducer(dogs);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "processProducer")
    assertEdgeExists(graph, "processProducer", "get")
    assertEdgeExists(graph, "processProducer", "eat")
  }

  /** 测试下界通配符（消费者） 预期边: handle → processConsumer, processConsumer → add */
  fun testWildcardConsumer() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/WildcardConsumer.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;

            class Animal { }
            class Dog extends Animal { }

            class Flow {
                void processConsumer(List<? super Dog> animals) {
                    animals.add(new Dog());
                }
                void handle() {
                    List<Animal> animals = new ArrayList<>();
                    processConsumer(animals);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "processConsumer")
    assertEdgeExists(graph, "processConsumer", "add")
  }

  // ==================== Nested Generic Tests ====================

  /** 测试嵌套泛型类型 预期边: handle → put, handle → get, handle → getName */
  fun testNestedGenericType() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/NestedGeneric.java",
            """
            package demo;
            import java.util.Map;
            import java.util.HashMap;
            import java.util.List;
            import java.util.ArrayList;

            class User { String getName() { return ""; } }
            class Flow {
                void handle() {
                    Map<String, List<User>> userGroups = new HashMap<>();
                    List<User> users = new ArrayList<>();
                    userGroups.put("admins", users);
                    users.get(0).getName();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "put")
    assertEdgeFromHandle(graph, "get")
    assertEdgeFromHandle(graph, "getName")
  }

  // ==================== Generic Return Type Tests ====================

  /** 测试泛型返回类型 预期边: handle → findById, handle → ifPresent, handle → getName */
  fun testGenericReturnType() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/GenericReturn.java",
            """
            package demo;
            import java.util.Optional;

            class User {
                String getName() { return ""; }
            }
            class UserRepository {
                Optional<User> findById(int id) { return Optional.empty(); }
            }
            class Flow {
                void handle() {
                    UserRepository repo = new UserRepository();
                    repo.findById(1).ifPresent(u -> u.getName());
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "findById")
    assertEdgeFromHandle(graph, "ifPresent")
    assertEdgeFromHandle(graph, "getName")
  }

  // ==================== Stream Chain Tests ====================

  /**
   * 测试 Stream 链式调用 预期边: handle → stream, handle → filter, handle → getAge, handle → map, handle →
   * getName, handle → collect
   */
  fun testStreamChain() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/StreamChain.java",
            """
            package demo;
            import java.util.List;
            import java.util.ArrayList;
            import java.util.stream.Collectors;

            class User {
                String getName() { return ""; }
                int getAge() { return 0; }
            }
            class Flow {
                void handle() {
                    List<User> users = new ArrayList<>();
                    users.stream()
                        .filter(u -> u.getAge() > 18)
                        .map(u -> u.getName())
                        .collect(Collectors.toList());
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "stream")
    assertEdgeFromHandle(graph, "filter")
    assertEdgeFromHandle(graph, "getAge")
    assertEdgeFromHandle(graph, "map")
    assertEdgeFromHandle(graph, "getName")
    assertEdgeFromHandle(graph, "collect")
  }

  // ==================== Generic Inheritance Tests ====================

  /** 测试泛型接口继承 预期边: handle → save, handle → findById */
  fun testGenericInheritance() {
    val file =
        myFixture.addFileToProject(
            "src/demo/GenericInheritance.java",
            """
            package demo;
            interface Repository<T> {
                void save(T entity);
                T findById(int id);
            }
            class User { }
            class UserRepository implements Repository<User> {
                public void save(User entity) { }
                public User findById(int id) { return null; }
            }
            class Flow {
                void handle() {
                    Repository<User> repo = new UserRepository();
                    repo.save(new User());
                    repo.findById(1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "save")
    assertEdgeFromHandle(graph, "findById")
  }

  /** 测试泛型类继承 预期边: handle → save, handle → find, handle → customMethod */
  fun testGenericClassInheritance() {
    val file =
        myFixture.addFileToProject(
            "src/demo/GenericClassInheritance.java",
            """
            package demo;
            class Repository<T> {
                void save(T entity) { }
                T find(int id) { return null; }
            }

            class User { }

            class UserRepository extends Repository<User> {
                void customMethod() { }
            }

            class Flow {
                void handle() {
                    UserRepository repo = new UserRepository();
                    repo.save(new User());
                    repo.find(1);
                    repo.customMethod();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "save")
    assertEdgeFromHandle(graph, "find")
    assertEdgeFromHandle(graph, "customMethod")
  }

  // ==================== Optional Chain Tests ====================

  /** 测试 Optional 链式调用 预期边: handle → of, handle → map, handle → getName, handle → orElse */
  fun testOptionalChain() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/OptionalChain.java",
            """
            package demo;
            import java.util.Optional;

            class User {
                String getName() { return ""; }
            }
            class Flow {
                void handle() {
                    Optional<User> optUser = Optional.of(new User());
                    String name = optUser.map(u -> u.getName()).orElse("default");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "of")
    assertEdgeFromHandle(graph, "map")
    assertEdgeFromHandle(graph, "orElse")
    // 验证 lambda 内部的 getName() 调用也被检测到
    assertEdgeFromHandle(graph, "getName")
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
