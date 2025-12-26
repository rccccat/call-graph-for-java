package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.addSpringWebStubs
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CallGraphJsonExporterTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.addSpringCoreStubs()
    myFixture.addSpringWebStubs()
  }

  fun testExportIncludesEndpointMetadataAndCallTargets() {
    val file =
        myFixture.addFileToProject(
            "src/demo/DemoController.java",
            """
            package demo;
            import org.springframework.stereotype.Service;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/api")
            class DemoController {
                private final DemoService demoService = new DemoService();

                @GetMapping("/ping")
                public String ping() { return demoService.work(); }
            }

            @Service
            class DemoService {
                public String work() { return "ok"; }
            }
            """
                .trimIndent(),
        )

    val method = findPingMethod(file)
    val service = CallGraphServiceImpl.getInstance(project)
    val graph = service.buildCallGraph(method) ?: error("Call graph build failed")
    val codeMap = CodeExtractor().extractCode(graph)
    val export = JsonExporter().convertToJsonExport(graph.data, codeMap)
    val rootNode = export.nodes[graph.data.rootNodeId] ?: error("Root node missing")
    val callTargetId =
        graph.data.getCallTargets(graph.data.rootNodeId).firstOrNull()?.id
            ?: error("Call target missing")

    assertEquals(graph.data.rootNodeId, export.rootId)
    assertEquals("DemoController.ping", rootNode.entryMethod)
    assertTrue(rootNode.isApiCenterMethod)
    assertTrue(rootNode.callTargets.contains(callTargetId))
    assertTrue(rootNode.selfCode.contains("ping()"))
  }

  private fun findPingMethod(file: com.intellij.psi.PsiFile): PsiMethod =
      PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "ping" }
}
