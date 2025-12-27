package com.github.rccccat.ideacallgraph.framework.spring

import com.github.rccccat.ideacallgraph.addFunctionalInterfaceStubs
import com.github.rccccat.ideacallgraph.addJavaCollectionStubs
import com.github.rccccat.ideacallgraph.addObjectStub
import com.github.rccccat.ideacallgraph.addSpringAdvancedStubs
import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for advanced Spring scenarios including constructor injection, circular dependencies,
 *
 * @Lazy injection, @Bean methods, and collection injection.
 */
class SpringAdvancedTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
    myFixture.addSpringCoreStubs()
    myFixture.addSpringAdvancedStubs()
    myFixture.addJavaCollectionStubs()
    myFixture.addFunctionalInterfaceStubs()
    myFixture.addObjectStub()
  }

  override fun tearDown() {
    try {
      applySettings(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  // ==================== Constructor Injection Tests ====================

  /** 测试多参数构造器注入 预期边: handle → save(UserRepositoryImpl), handle → send(EmailServiceImpl) */
  fun testConstructorInjectionWithMultipleParams() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ConstructorInjection.java",
            """
            package demo;
            import org.springframework.stereotype.Service;

            interface UserRepository { void save(); }
            interface EmailService { void send(); }

            @Service
            class UserRepositoryImpl implements UserRepository {
                public void save() { }
            }

            @Service
            class EmailServiceImpl implements EmailService {
                public void send() { }
            }

            @Service
            class UserService {
                private final UserRepository userRepository;
                private final EmailService emailService;

                public UserService(UserRepository userRepository, EmailService emailService) {
                    this.userRepository = userRepository;
                    this.emailService = emailService;
                }

                void handle() {
                    userRepository.save();
                    emailService.send();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "save")
    assertEdgeFromHandle(graph, "send")
    assertEdgeFromClass(graph, "UserService", "UserRepositoryImpl", "save")
    assertEdgeFromClass(graph, "UserService", "EmailServiceImpl", "send")
  }

  /** 测试单构造器自动注入（无需 @Autowired） 预期边: handle → doWork */
  fun testSingleConstructorAutoInjection() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SingleConstructor.java",
            """
            package demo;
            import org.springframework.stereotype.Service;

            interface Worker { void doWork(); }

            @Service
            class WorkerImpl implements Worker {
                public void doWork() { }
            }

            @Service
            class TaskService {
                private final Worker worker;

                // 单构造器自动注入，无需 @Autowired
                public TaskService(Worker worker) {
                    this.worker = worker;
                }

                void handle() {
                    worker.doWork();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "doWork")
    assertEdgeFromClass(graph, "TaskService", "WorkerImpl", "doWork")
  }

  // ==================== Circular Dependency Tests ====================

  /** 测试循环依赖 预期: 图构建成功，检测到循环但不会无限循环 */
  fun testCircularDependency() {
    val file =
        myFixture.addFileToProject(
            "src/demo/CircularDependency.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;

            @Service
            class ServiceA {
                @Autowired ServiceB serviceB;
                void methodA() { serviceB.methodB(); }
            }

            @Service
            class ServiceB {
                @Autowired ServiceA serviceA;
                void methodB() { serviceA.methodA(); }
            }

            class Flow {
                @Autowired ServiceA serviceA;
                void handle() { serviceA.methodA(); }
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

  // ==================== @Lazy Injection Tests ====================

  /** 测试 @Lazy 注入（@Lazy 不应影响解析） 预期边: handle → pay(PaymentServiceImpl) */
  fun testLazyInjection() {
    val file =
        myFixture.addFileToProject(
            "src/demo/LazyInjection.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.context.annotation.Lazy;
            import org.springframework.stereotype.Service;

            interface PaymentService { void pay(); }

            @Service
            class PaymentServiceImpl implements PaymentService {
                public void pay() { }
            }

            class PaymentController {
                @Autowired
                @Lazy
                private PaymentService paymentService;

                void handle() {
                    paymentService.pay();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "pay")
    assertEdgeFromClass(graph, "PaymentController", "PaymentServiceImpl", "pay")
  }

  // ==================== Multiple Implementations Tests ====================

  /**
   * 测试无 @Qualifier 时多个实现都被解析 预期边: handle → process(StripeGateway), handle → process(PaypalGateway)
   */
  fun testMultipleImplementationsNoQualifier() {
    val file =
        myFixture.addFileToProject(
            "src/demo/MultiImpl.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;

            interface PaymentGateway { void process(); }

            @Service
            class StripeGateway implements PaymentGateway {
                public void process() { }
            }

            @Service
            class PaypalGateway implements PaymentGateway {
                public void process() { }
            }

            class PaymentService {
                @Autowired
                private PaymentGateway gateway;

                void handle() {
                    gateway.process();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 无 @Qualifier 时应该解析到所有实现
    assertEdgeFromHandle(graph, "process")
    assertEdgeFromClass(graph, "PaymentService", "StripeGateway", "process")
    assertEdgeFromClass(graph, "PaymentService", "PaypalGateway", "process")
  }

  // ==================== Collection Injection Tests ====================

  /** 测试 Map 注入 预期边: handle → get, handle → pay (所有实现) */
  fun testMapInjection() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/MapInjection.java",
            """
            package demo;
            import java.util.Map;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;

            interface PaymentService { void pay(); }

            @Service
            class StripePaymentService implements PaymentService {
                public void pay() { }
            }

            @Service
            class PaypalPaymentService implements PaymentService {
                public void pay() { }
            }

            class PaymentController {
                @Autowired
                private Map<String, PaymentService> paymentServices;

                void handle() {
                    paymentServices.get("stripe").pay();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "get")
    assertEdgeFromHandle(graph, "pay")
  }

  /** 测试 Set 注入 预期边: handle → forEach, handle → validate (所有实现) */
  fun testSetInjection() {
    updateSettings {
      includeGettersSetters = true
      excludePackagePatterns = mutableListOf()
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/SetInjection.java",
            """
            package demo;
            import java.util.Set;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;

            interface Validator { void validate(); }

            @Service
            class Validator1 implements Validator {
                public void validate() { }
            }

            @Service
            class Validator2 implements Validator {
                public void validate() { }
            }

            class ValidationService {
                @Autowired
                private Set<Validator> validators;

                void handle() {
                    validators.forEach(v -> v.validate());
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "forEach")
    assertEdgeFromHandle(graph, "validate")
  }

  // ==================== Nested Bean Call Tests ====================

  /** 测试嵌套 Bean 调用 预期边: handle → process, process → save, handle → notify */
  fun testNestedBeanCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/NestedBean.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Service;

            interface OrderRepository { void save(); }
            interface PaymentService { void process(); }
            interface NotificationService { void doNotify(); }

            @Service
            class OrderRepositoryImpl implements OrderRepository {
                public void save() { }
            }

            @Service
            class PaymentServiceImpl implements PaymentService {
                @Autowired
                private OrderRepository orderRepository;

                public void process() {
                    orderRepository.save();
                }
            }

            @Service
            class NotificationServiceImpl implements NotificationService {
                public void doNotify() { }
            }

            class OrderService {
                @Autowired
                private PaymentService paymentService;
                @Autowired
                private NotificationService notificationService;

                void handle() {
                    paymentService.process();
                    notificationService.doNotify();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "process")
    assertEdgeFromHandle(graph, "doNotify")
    assertEdgeExists(graph, "process", "save")
  }

  // ==================== Field Injection Without Annotation Tests ====================

  /** 测试无 @Autowired 注解的字段（非注入场景） 预期: 字段不会被当作注入点，因为没有 @Autowired */
  fun testFieldWithoutAutowiredAnnotation() {
    val file =
        myFixture.addFileToProject(
            "src/demo/NoAutowired.java",
            """
            package demo;
            import org.springframework.stereotype.Service;

            interface UserRepository { void save(); }

            @Service
            class UserRepositoryImpl implements UserRepository {
                public void save() { }
            }

            @Service
            class UserService {
                // 没有 @Autowired，不会被注入
                private UserRepository userRepository;

                void handle() {
                    userRepository.save();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 验证 save 被调用（即使没有 @Autowired，接口解析仍然生效）
    assertEdgeFromHandle(graph, "save")
  }

  // ==================== @Transactional Tests ====================

  /** 测试 @Transactional 方法 预期边: handle → createOrder, createOrder → saveOrder */
  fun testTransactionalMethod() {
    val file =
        myFixture.addFileToProject(
            "src/demo/TransactionalMethod.java",
            """
            package demo;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;

            @Service
            class OrderService {
                @Transactional
                public void createOrder() {
                    saveOrder();
                }

                void saveOrder() { }
            }

            class Flow {
                private final OrderService orderService = new OrderService();
                void handle() {
                    orderService.createOrder();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "createOrder")
    assertEdgeExists(graph, "createOrder", "saveOrder")
  }

  // ==================== Helper Methods ====================

  private fun findHandleMethod(file: com.intellij.psi.PsiFile): PsiMethod =
      PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "handle" }

  private fun buildGraph(method: PsiMethod): CallGraphData {
    val service = CallGraphServiceImpl.getInstance(project)
    val graph = service.buildCallGraph(method) ?: error("Call graph build failed")
    return graph.data
  }

  private fun assertEdgeFromHandle(graph: CallGraphData, to: String) {
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

  private fun assertEdgeExists(graph: CallGraphData, from: String, to: String) {
    val fromNode = graph.nodes.values.firstOrNull { it.name == from }
    assertTrue(
        "Missing edge $from -> $to",
        graph.edges.any { edge ->
          edge.fromId == fromNode?.id && graph.nodes[edge.toId]?.name == to
        },
    )
  }

  private fun assertEdgeFromClass(
      graph: CallGraphData,
      fromClass: String,
      toClass: String,
      toMethod: String,
  ) {
    assertTrue(
        "Missing edge from $fromClass to $toClass.$toMethod",
        graph.edges.any { edge ->
          graph.nodes[edge.fromId]?.className == fromClass &&
              graph.nodes[edge.toId]?.className == toClass &&
              graph.nodes[edge.toId]?.name == toMethod
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
    settings.setTraverseAllImplementations(state.traverseAllImplementations)
    settings.setFilterByParameterUsage(state.filterByParameterUsage)
  }

  private fun cloneSettings(state: CallGraphAppSettings.State): CallGraphAppSettings.State =
      state.copy(excludePackagePatterns = state.excludePackagePatterns.toMutableList())
}
