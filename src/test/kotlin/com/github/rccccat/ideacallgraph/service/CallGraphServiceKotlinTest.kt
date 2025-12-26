package com.github.rccccat.ideacallgraph.service

import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.addSpringWebStubs
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.settings.CallGraphAppSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class CallGraphServiceKotlinTest : BasePlatformTestCase() {
  private lateinit var originalSettings: CallGraphAppSettings.State

  override fun setUp() {
    super.setUp()
    originalSettings = cloneSettings(CallGraphAppSettings.getInstance().state)
    myFixture.addSpringCoreStubs()
    myFixture.addSpringWebStubs()
  }

  override fun tearDown() {
    try {
      CallGraphAppSettings.getInstance().loadState(originalSettings)
    } finally {
      super.tearDown()
    }
  }

  fun testSimpleKotlinCallGraph() {
    val file =
        myFixture.addFileToProject(
            "src/demo/SimpleService.kt",
            """
            package demo
            class SimpleService {
                fun start() { step() }
                fun step() { }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "start")
    val graph = buildGraph(function)

    assertEdge(graph, "start", "step")
  }

  fun testKotlinServiceChain() {
    val file =
        myFixture.addFileToProject(
            "src/demo/OrderFlow.kt",
            """
            package demo

            import org.springframework.stereotype.Repository
            import org.springframework.stereotype.Service
            import org.springframework.web.bind.annotation.RestController

            @RestController
            class OrderController(private val orderService: OrderService) {
                fun handle() { orderService.placeOrder("ORDER-1") }
            }

            @Service
            class OrderService(private val orderRepository: OrderRepository) {
                fun placeOrder(orderId: String) { orderRepository.save(orderId) }
            }

            @Repository
            class OrderRepository {
                fun save(orderId: String) { }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "handle")
    val graph = buildGraph(function)

    assertEdge(graph, "handle", "placeOrder")
    assertEdge(graph, "placeOrder", "save")
  }

  fun testKotlinCallsJava() {
    myFixture.addFileToProject(
        "src/demo/JavaGateway.java",
        """
        package demo;
        public class JavaGateway {
            public void send() { }
        }
        """
            .trimIndent(),
    )

    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinCaller.kt",
            """
            package demo
            class KotlinCaller {
                fun call() { JavaGateway().send() }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "call")
    val graph = buildGraph(function)

    assertEdge(graph, "call", "send")
  }

  fun testKotlinInjectedPropertyCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinInjection.kt",
            """
            package demo

            import org.springframework.beans.factory.annotation.Autowired

            class UserService {
                fun load() { }
            }

            class UserController {
                @Autowired
                private var userService: UserService = UserService()

                fun handle() { userService.load() }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "handle")
    val graph = buildGraph(function)

    assertEdge(graph, "handle", "load")
  }

  fun testKotlinInjectedConstructorParamCall() {
    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinConstructorInjection.kt",
            """
            package demo

            import org.springframework.beans.factory.annotation.Autowired

            class UserService {
                fun load() { }
            }

            class UserController(@Autowired private val userService: UserService) {
                fun handle() { userService.load() }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "handle")
    val graph = buildGraph(function)

    assertEdge(graph, "handle", "load")
  }

  fun testKotlinConstructorInjectionWithoutAnnotation() {
    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinConstructorNoAnnotation.kt",
            """
            package demo

            import org.springframework.stereotype.Service

            class UserService {
                fun load() { }
            }

            @Service
            class UserController(private val userService: UserService) {
                fun handle() { userService.load() }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "handle")
    val graph = buildGraph(function)

    assertEdge(graph, "handle", "load")
  }

  fun testKotlinCollectionInjectionIncludesAllImplementations() {
    myFixture.addFileToProject(
        "src/demo/PaymentService.java",
        """
        package demo;
        public interface PaymentService {
            void pay();
        }
        """
            .trimIndent(),
    )

    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinCollectionInjection.kt",
            """
            package demo

            import org.springframework.beans.factory.annotation.Autowired
            import org.springframework.context.annotation.Primary
            import org.springframework.stereotype.Service

            @Service
            @Primary
            class PrimaryPaymentService : PaymentService {
                override fun pay() { }
            }

            @Service
            class SecondaryPaymentService : PaymentService {
                override fun pay() { }
            }

            class PaymentController {
                @Autowired
                private var paymentServices: List<PaymentService> = emptyList()

                fun handle() { paymentServices[0].pay() }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "handle")
    val graph = buildGraph(function)

    assertEdge(graph, "handle", "pay")
    assertPaymentServiceEdge(graph, "PrimaryPaymentService")
    assertPaymentServiceEdge(graph, "SecondaryPaymentService")
  }

  fun testKotlinControllerEndpointMetadata() {
    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinController.kt",
            """
            package demo

            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.RestController

            @RestController
            @RequestMapping("/api")
            class DemoController {
                @GetMapping("/ping")
                fun ping() { }
            }
            """
                .trimIndent(),
        ) as KtFile

    val function = findFunction(file, "ping")
    val graph = buildGraph(function)
    val root = graph.nodes[graph.rootNodeId] ?: error("Root node missing")

    assertEquals(NodeType.SPRING_CONTROLLER_METHOD, root.nodeType)
    assertTrue(root.isSpringEndpoint)
  }

  fun testKotlinInterfaceMappingEndpoint() {
    val file =
        myFixture.addFileToProject(
            "src/demo/KotlinInterfaceController.kt",
            """
            package demo

            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RestController

            interface UserApi {
                @GetMapping("/users")
                fun list(): String
            }

            @RestController
            class UserController : UserApi {
                override fun list(): String = "ok"
            }
            """
                .trimIndent(),
        ) as KtFile

    val controllerClass =
        file.findDescendantOfType<KtClass> { it.name == "UserController" }
            ?: error("UserController not found")
    val function =
        controllerClass.findDescendantOfType<KtNamedFunction> { it.name == "list" }
            ?: error("list not found")
    val graph = buildGraph(function)
    val root = graph.nodes[graph.rootNodeId] ?: error("Root node missing")

    assertEquals(NodeType.SPRING_CONTROLLER_METHOD, root.nodeType)
    assertTrue(root.isSpringEndpoint)
  }

  private fun findFunction(
      file: KtFile,
      name: String,
  ): KtNamedFunction =
      file.findDescendantOfType<KtNamedFunction> { it.name == name }
          ?: error("Function $name not found")

  private fun assertEdge(
      graph: CallGraphData,
      from: String,
      to: String,
  ) {
    assertTrue(
        graph.edges.any { edge ->
          graph.nodes[edge.fromId]?.name == from &&
              graph.nodes[edge.toId]?.name == to
        },
    )
  }

  private fun assertPaymentServiceEdge(
      graph: CallGraphData,
      toClass: String,
  ) {
    val fromClass = "PaymentService"
    val toMethod = "pay"
    val fromEdges =
        graph.edges
            .filter { edge -> graph.nodes[edge.fromId]?.className == fromClass }
            .joinToString { edge ->
              val fromNode = graph.nodes[edge.fromId]
              val toNode = graph.nodes[edge.toId]
              "${fromNode?.className}.${fromNode?.name}->${toNode?.className}.${toNode?.name}"
            }
    assertTrue(
        "Missing edge $fromClass -> $toClass.$toMethod. Existing: $fromEdges",
        graph.edges.any { edge ->
          graph.nodes[edge.fromId]?.className == fromClass &&
              graph.nodes[edge.toId]?.className == toClass &&
              graph.nodes[edge.toId]?.name == toMethod
        },
    )
  }

  private fun buildGraph(function: KtNamedFunction): CallGraphData {
    val service = CallGraphServiceImpl.getInstance(project)
    val graph = service.buildCallGraph(function) ?: error("Call graph build failed")
    return graph.data
  }

  private fun cloneSettings(state: CallGraphAppSettings.State): CallGraphAppSettings.State =
      state.copy(excludePackagePatterns = state.excludePackagePatterns.toMutableList())
}
