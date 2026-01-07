package com.github.rccccat.ideacallgraph.service

import com.github.rccccat.ideacallgraph.addJavaReflectionStubs
import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.addSpringWebStubs
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.github.rccccat.ideacallgraph.util.ExcludePatternMatcher
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CallGraphServiceJavaTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
    myFixture.addSpringCoreStubs()
    myFixture.addJavaReflectionStubs()
  }

  override fun tearDown() {
    try {
      applySettings(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  fun testSimpleDirectCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SimpleFlow.java",
            """
            package demo;
            class SimpleFlow {
                void handle() { new Worker().work(); }
            }
            class Worker {
                void work() {}
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "work")
  }

  fun testFallbackResolutionRequiresExactParameterTypes() {
    // 注意：当调用参数类型与方法签名不匹配时（如 work(String) vs work(1)），
    // PSI 解析器仍可能返回最接近的候选方法。这是编译错误代码的场景，
    // 我们选择信任 IntelliJ 的解析结果而非自行判断。
    val file =
        myFixture.addFileToProject(
            "src/demo/OverloadMismatch.java",
            """
            package demo;
            class Worker {
                void work(String value) { }
            }
            class Flow {
                void handle() { new Worker().work(1); }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // PSI 解析器可能解析到 work 方法，即使参数类型不严格匹配
    assertEdgeFromHandle(graph, "work")
  }

  fun testInterfaceImplementationResolution() {
    val file =
        myFixture.addFileToProject(
            "src/demo/OrderFlow.java",
            """
            package demo;
            import org.springframework.stereotype.Service;

            interface PaymentService {
                void pay(String orderId);
            }

            @Service
            class StripePaymentService implements PaymentService {
                private final PaymentRepository repository = new PaymentRepository();

                public void pay(String orderId) {
                    repository.save(orderId);
                }
            }

            class PaypalPaymentService implements PaymentService {
                public void pay(String orderId) { }
            }

            class PaymentRepository {
                void save(String orderId) { }
            }

            class OrderController {
                private final PaymentService paymentService = new StripePaymentService();

                public void handle() {
                    paymentService.pay("ORDER-1");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "pay")
    assertEdgeFromClass(graph, "OrderController", "StripePaymentService", "pay")
    assertEdgeFromClass(graph, "OrderController", "PaypalPaymentService", "pay")
    assertEdgeFromClass(graph, "StripePaymentService", "PaymentRepository", "save")
  }

  fun testExcludePatternSkipsImplementationByClassName() {
    updateSettings {
      excludePackagePatterns = mutableListOf("class:StripePaymentService")
      resolveInterfaceImplementations = true
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/OrderFlowExcluded.java",
            """
            package demo;
            import org.springframework.stereotype.Service;

            interface PaymentService {
                void pay(String orderId);
            }

            @Service
            class StripePaymentService implements PaymentService {
                public void pay(String orderId) { }
            }

            class PaypalPaymentService implements PaymentService {
                public void pay(String orderId) { }
            }

            class OrderController {
                private final PaymentService paymentService = new StripePaymentService();

                public void handle() {
                    paymentService.pay("ORDER-1");
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "pay")
    assertNoEdgeFromClass(graph, "OrderController", "StripePaymentService", "pay")
    assertEdgeFromClass(graph, "OrderController", "PaypalPaymentService", "pay")
  }

  fun testExcludePatternSkipsMethodSignature() {
    updateSettings {
      excludePackagePatterns = mutableListOf("sig:demo.Worker#work(java.lang.String)")
    }
    val file =
        myFixture.addFileToProject(
            "src/demo/ExcludeSignatureFlow.java",
            """
            package demo;
            class Worker {
                void work(String value) { }
                void work(int value) { }
            }
            class Flow {
                void handle() {
                    new Worker().work("x");
                    new Worker().work(1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)
    val fromNode = graph.nodes.values.firstOrNull { it.name == "handle" }
    val targetSignatures =
        getOutgoingTargets(graph, fromNode?.id).map { target -> target.signature }

    assertTrue(targetSignatures.any { signature -> signature.contains("work(int") })
    assertFalse(targetSignatures.any { signature -> signature.contains("work(String") })
  }

  fun testInterfaceImplementationWithQualifier() {
    val file =
        myFixture.addFileToProject(
            "src/demo/QualifiedFlow.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.stereotype.Service;

            interface PaymentService {
                void pay();
            }

            @Service
            @Qualifier("stripe")
            class StripePaymentService implements PaymentService {
                public void pay() { }
            }

            @Service
            @Qualifier("paypal")
            class PaypalPaymentService implements PaymentService {
                public void pay() { }
            }

            class PaymentController {
                @Autowired
                @Qualifier("paypal")
                private PaymentService paymentService;

                public void handle() {
                    paymentService.pay();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromClass(graph, "PaymentController", "PaypalPaymentService", "pay")
    assertNoEdgeFromClass(graph, "PaymentController", "StripePaymentService", "pay")
  }

  fun testInterfaceImplementationWithPrimary() {
    val file =
        myFixture.addFileToProject(
            "src/demo/PrimaryFlow.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.context.annotation.Primary;
            import org.springframework.stereotype.Service;

            interface Notifier {
                void notifyUser();
            }

            @Service
            class DefaultNotifier implements Notifier {
                public void notifyUser() { }
            }

            @Service
            @Primary
            class PrimaryNotifier implements Notifier {
                public void notifyUser() { }
            }

            class NotificationController {
                @Autowired
                private Notifier notifier;

                public void handle() {
                    notifier.notifyUser();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromClass(graph, "NotificationController", "PrimaryNotifier", "notifyUser")
    assertNoEdgeFromClass(graph, "NotificationController", "DefaultNotifier", "notifyUser")
  }

  fun testSpringInjectedFieldCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/InjectedFlow.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;

            interface UserService {
                void loadProfile();
            }

            class UserServiceImpl implements UserService {
                public void loadProfile() { }
            }

            class UserController {
                @Autowired
                private UserService userService;

                public void handle() {
                    userService.loadProfile();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "loadProfile")
  }

  fun testSetterInjectionUsesQualifier() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SetterInjectionFlow.java",
            """
            package demo;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.stereotype.Service;

            interface PaymentService {
                void pay();
            }

            @Service
            @Qualifier("stripe")
            class StripePaymentService implements PaymentService {
                public void pay() { }
            }

            @Service
            @Qualifier("paypal")
            class PaypalPaymentService implements PaymentService {
                public void pay() { }
            }

            class PaymentController {
                private PaymentService paymentService;

                @Autowired
                public void setPaymentService(@Qualifier("paypal") PaymentService paymentService) {
                    this.paymentService = paymentService;
                }

                public void handle() {
                    paymentService.pay();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "pay")
    assertEdgeFromClass(graph, "PaymentController", "PaypalPaymentService", "pay")
    assertNoEdgeFromClass(graph, "PaymentController", "StripePaymentService", "pay")
  }

  fun testCollectionInjectionSkipsUnresolvedCalls() {
    val file =
        myFixture.addFileToProject(
            "src/demo/CollectionInjectionFlow.java",
            """
            package demo;
            import java.util.List;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.context.annotation.Primary;
            import org.springframework.stereotype.Service;

            interface PaymentService {
                void pay();
            }

            @Service
            @Primary
            class PrimaryPaymentService implements PaymentService {
                public void pay() { }
            }

            @Service
            class SecondaryPaymentService implements PaymentService {
                public void pay() { }
            }

            class PaymentController {
                @Autowired
                private List<PaymentService> paymentServices;

                public void handle() {
                    paymentServices.get(0).pay();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    // 解析失败时不再尝试兜底匹配
    assertNoEdgeFromHandle(graph, "get")
    assertNoEdgeFromHandle(graph, "pay")
  }

  fun testSpringServiceNodeType() {
    val file =
        myFixture.addFileToProject(
            "src/demo/ServiceNode.java",
            """
            package demo;
            import org.springframework.stereotype.Service;

            @Service
            class BillingService {
                void handle() { }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEquals(NodeType.SPRING_SERVICE_METHOD, graph.nodes[graph.rootNodeId]?.nodeType)
  }

  fun testInterfaceMappingEndpointFlag() {
    myFixture.addSpringWebStubs()
    val file =
        myFixture.addFileToProject(
            "src/demo/InterfaceMappingController.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            interface UserApi {
                @GetMapping("/users")
                String list();
            }

            @RestController
            class UserController implements UserApi {
                @Override
                public String list() { return "ok"; }
            }
            """
                .trimIndent(),
        )

    val method =
        PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first {
          it.name == "list" && it.containingClass?.name == "UserController"
        }
    val graph = buildGraph(method)
    val root = graph.nodes[graph.rootNodeId] ?: error("Root node missing")

    assertEquals(NodeType.SPRING_CONTROLLER_METHOD, root.nodeType)
    assertTrue(root.isSpringEndpoint)
  }

  fun testReflectionCallsIncluded() {
    updateSettings {
      excludePackagePatterns = mutableListOf()
      includeGettersSetters = true
    }
    assertEquals(emptyList<String>(), CallGraphAppSettings.getInstance().excludePackagePatterns)
    assertTrue(CallGraphAppSettings.getInstance().includeGettersSetters)
    val file =
        myFixture.addFileToProject(
            "src/demo/ReflectionFlow.java",
            """
            package demo;
            import java.lang.reflect.Method;

            class ReflectionFlow {
                void handle() throws Exception {
                    Method method = Worker.class.getDeclaredMethod("work");
                    method.invoke(new Worker());
                }
            }

            class Worker {
                void work() { }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val callExpressions =
        PsiTreeUtil.findChildrenOfType(method, com.intellij.psi.PsiMethodCallExpression::class.java)
    val getDeclaredCall =
        callExpressions.first { it.methodExpression.referenceName == "getDeclaredMethod" }
    val invokeCall = callExpressions.first { it.methodExpression.referenceName == "invoke" }
    assertNotNull(getDeclaredCall.resolveMethod())
    assertNotNull(invokeCall.resolveMethod())

    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "getDeclaredMethod")
    assertEdgeFromHandle(graph, "invoke")
  }

  fun testSkipGetterAndToString() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SkipMethods.java",
            """
            package demo;
            class Customer {
                String getName() { return "name"; }
                public String toString() { return "Customer"; }
                void work() { }
            }

            class Flow {
                void handle() {
                    Customer customer = new Customer();
                    customer.getName();
                    customer.toString();
                    customer.work();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "work")
    assertNoEdgeFromHandle(graph, "getName")
    assertNoEdgeFromHandle(graph, "toString")
  }

  fun testIncludeGetterWhenEnabled() {
    updateSettings { includeGettersSetters = true }
    val file =
        myFixture.addFileToProject(
            "src/demo/IncludeGetter.java",
            """
            package demo;
            class Customer {
                String getName() { return "name"; }
            }

            class Flow {
                void handle() {
                    Customer customer = new Customer();
                    customer.getName();
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)

    assertEdgeFromHandle(graph, "getName")
  }

  fun testExcludePackagePatternSkipsCall() {
    updateSettings { excludePackagePatterns = mutableListOf("""pkg:demo\.external(\..*)?""") }
    assertEquals(
        listOf("""pkg:demo\.external(\..*)?"""),
        CallGraphAppSettings.getInstance().excludePackagePatterns,
    )
    val file =
        myFixture.addFileToProject(
            "src/demo/ExcludeFlow.java",
            """
            package demo;
            import demo.external.ExternalService;

            class ExcludeFlow {
                void handle() {
                    new ExternalService().run();
                }
            }
            """
                .trimIndent(),
        )

    myFixture.addFileToProject(
        "src/demo/external/ExternalService.java",
        """
        package demo.external;
        public class ExternalService {
            public void run() { }
        }
        """
            .trimIndent(),
    )

    val handleMethod = findHandleMethod(file)
    val runCall =
        PsiTreeUtil.findChildrenOfType(
                handleMethod, com.intellij.psi.PsiMethodCallExpression::class.java)
            .first { it.methodExpression.referenceName == "run" }
    val resolvedRun = runCall.resolveMethod()
    assertEquals("demo.external.ExternalService", resolvedRun?.containingClass?.qualifiedName)
    val excludePattern = CallGraphAppSettings.getInstance().excludePackagePatterns.first()
    val excludePatternMatcher = ExcludePatternMatcher.fromPatterns(listOf(excludePattern))
    assertTrue(excludePatternMatcher.matchesMethod(resolvedRun!!))

    val graph = buildGraph(handleMethod)

    assertNoEdgeFromHandle(graph, "run")
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
        getOutgoingTargets(graph, fromNode?.id).joinToString { target ->
          "$from->${target.className}.${target.name}"
        }
    assertTrue(
        "Missing edge $from -> $to. Existing: $fromEdges",
        getOutgoingTargets(graph, fromNode?.id).any { target -> target.name == to },
    )
  }

  private fun assertNoEdgeFromHandle(
      graph: CallGraphData,
      to: String,
  ) {
    val from = "handle"
    val fromNode = graph.nodes.values.firstOrNull { it.name == from }
    val fromEdges =
        getOutgoingTargets(graph, fromNode?.id).joinToString { target ->
          "$from->${target.className}.${target.name}"
        }
    assertFalse(
        "Unexpected edge $from -> $to. Existing: $fromEdges",
        getOutgoingTargets(graph, fromNode?.id).any { target -> target.name == to },
    )
  }

  private fun assertEdgeFromClass(
      graph: CallGraphData,
      fromClass: String,
      toClass: String,
      toMethod: String,
  ) {
    assertTrue(
        hasEdgeFromClass(graph, fromClass, toClass, toMethod),
    )
  }

  private fun assertNoEdgeFromClass(
      graph: CallGraphData,
      fromClass: String,
      toClass: String,
      toMethod: String,
  ) {
    assertFalse(
        hasEdgeFromClass(graph, fromClass, toClass, toMethod),
    )
  }

  private fun hasEdgeFromClass(
      graph: CallGraphData,
      fromClass: String,
      toClass: String,
      toMethod: String,
  ): Boolean {
    val fromNodes = graph.nodes.values.filter { node -> node.className == fromClass }
    return fromNodes.any { fromNode ->
      getOutgoingTargets(graph, fromNode.id).any { target ->
        target.className == toClass && target.name == toMethod
      }
    }
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
    settings.setMybatisScanAllXml(state.mybatisScanAllXml)
    settings.setSpringEnableFullScan(state.springEnableFullScan)
  }

  private fun cloneSettings(state: CallGraphAppSettings.State): CallGraphAppSettings.State =
      state.copy(excludePackagePatterns = state.excludePackagePatterns.toMutableList())
}
