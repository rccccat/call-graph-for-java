package com.github.rccccat.ideacallgraph.export

import com.github.rccccat.ideacallgraph.addSpringCoreStubs
import com.github.rccccat.ideacallgraph.addSpringWebStubs
import com.github.rccccat.ideacallgraph.cache.CallGraphCacheManager
import com.github.rccccat.ideacallgraph.settings.CallGraphProjectSettings
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpringApiScannerTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addSpringCoreStubs()
        myFixture.addSpringWebStubs()
    }

    fun testScanSingleRestControllerEndpoint() {
        myFixture.addFileToProject(
            "src/demo/DemoController.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            class DemoController {
                @GetMapping("/ping")
                public String ping() { return "ok"; }
            }
            """.trimIndent(),
        )

        val endpoints =
            SpringApiScanner(project, CallGraphCacheManager.getInstance(project))
                .scanAllEndpoints(EmptyProgressIndicator())

        assertEquals(1, endpoints.size)
        assertEquals("ping", endpoints.first().name)
    }

    fun testScanMultipleControllersAndMappings() {
        myFixture.addFileToProject(
            "src/demo/AdminController.java",
            """
            package demo;
            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RequestMethod;

            @Controller
            class AdminController {
                @RequestMapping(path = "/admin/health", method = RequestMethod.GET)
                public String health() { return "ok"; }

                public String ignoredHelper() { return "no"; }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/demo/OrderController.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            class OrderController {
                @PostMapping("/orders")
                public void create() { }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/demo/NoController.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.GetMapping;

            class NoController {
                @GetMapping("/noop")
                public void noop() { }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/demo/EmptyController.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            class EmptyController {
                public void helper() { }
            }
            """.trimIndent(),
        )

        val endpoints =
            SpringApiScanner(project, CallGraphCacheManager.getInstance(project))
                .scanAllEndpoints(EmptyProgressIndicator())
        val endpointNames = endpoints.map { it.name }.toSet()

        assertEquals(setOf("health", "create"), endpointNames)
    }

    fun testScanInterfaceMappingEndpoint() {
        myFixture.addFileToProject(
            "src/demo/UserApi.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.GetMapping;

            interface UserApi {
                @GetMapping("/users")
                String list();
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/demo/UserController.java",
            """
            package demo;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            class UserController implements UserApi {
                @Override
                public String list() { return "ok"; }
            }
            """.trimIndent(),
        )

        val endpoints =
            SpringApiScanner(project, CallGraphCacheManager.getInstance(project))
                .scanAllEndpoints(EmptyProgressIndicator())
        val endpointNames = endpoints.map { it.name }.toSet()

        assertTrue(endpointNames.contains("list"))
    }

    fun testScanMetaAnnotationsWithFullScan() {
        CallGraphProjectSettings.getInstance(project).setSpringEnableFullScan(true)

        myFixture.addFileToProject(
            "src/demo/ApiController.java",
            """
            package demo;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import org.springframework.web.bind.annotation.RestController;

            @Retention(RetentionPolicy.RUNTIME)
            @RestController
            @interface ApiController { }
            """.trimIndent(),
        )

        myFixture.addFileToProject(
            "src/demo/PingMapping.java",
            """
            package demo;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import org.springframework.web.bind.annotation.GetMapping;

            @Retention(RetentionPolicy.RUNTIME)
            @GetMapping("/ping")
            @interface PingMapping { }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/demo/MetaController.java",
            """
            package demo;

            @ApiController
            class MetaController {
                @PingMapping
                public String ping() { return "ok"; }
            }
            """.trimIndent(),
        )

        val endpoints =
            SpringApiScanner(project, CallGraphCacheManager.getInstance(project))
                .scanAllEndpoints(EmptyProgressIndicator())
        val endpointNames = endpoints.map { it.name }.toSet()

        assertTrue(endpointNames.contains("ping"))
    }
}
