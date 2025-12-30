package com.github.rccccat.ideacallgraph.core.resolver

import com.github.rccccat.ideacallgraph.addObjectStub
import com.github.rccccat.ideacallgraph.api.model.CallGraphData
import com.github.rccccat.ideacallgraph.service.CallGraphServiceImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class InterfaceResolverScopeTest : BasePlatformTestCase() {
  private var externalLibrary: Library? = null
  private lateinit var externalLibraryRoot: Path

  override fun setUp() {
    super.setUp()
    myFixture.addObjectStub()
    addMapStub()
    addExternalMapLibrary()
  }

  override fun tearDown() {
    try {
      externalLibrary?.let { PsiTestUtil.removeLibrary(module, it) }
    } finally {
      super.tearDown()
    }
  }

  fun testInterfaceResolutionSkipsLibraryImplementations() {
    val file =
        myFixture.addFileToProject(
            "src/demo/LibraryScope.java",
            """
            package demo;
            import java.util.Map;

            class Worker {
                void work() { }
            }

            class Flow {
                private Map<String, Worker> map;

                void handle() {
                    map.get("k").work();
                }
            }
            """
                .trimIndent(),
        )

    val externalFile =
        LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(externalLibraryRoot.resolve("external/ExternalMap.java").toString())
    assertNotNull("ExternalMap source should exist in library root", externalFile)

    val graph = buildGraph(findHandleMethod(file))

    val classNames =
        graph.nodes.values.mapNotNull { node -> node.className }.sorted().joinToString()
    assertFalse(
        "ExternalMap should not appear in project-scope implementation resolution. Existing: $classNames",
        graph.nodes.values.any { node -> node.className == "ExternalMap" },
    )
  }

  private fun addMapStub() {
    myFixture.addFileToProject(
        "src/java/util/Map.java",
        """
        package java.util;
        public interface Map<K, V> {
            V get(Object key);
        }
        """
            .trimIndent(),
    )
  }

  private fun addExternalMapLibrary() {
    externalLibraryRoot = Files.createTempDirectory("external-map-lib")
    val packageDir = externalLibraryRoot.resolve("external")
    Files.createDirectories(packageDir)
    val source =
        """
        package external;
        import java.util.Map;
        public class ExternalMap<K, V> implements Map<K, V> {
            public V get(Object key) { return null; }
        }
        """
            .trimIndent()
    Files.writeString(
        packageDir.resolve("ExternalMap.java"),
        source,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
    val rootVirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(externalLibraryRoot.toString())
            ?: error("Failed to create external library root")
    externalLibrary =
        PsiTestUtil.newLibrary("external-map-lib").sourceRoot(rootVirtualFile).addTo(module)
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  private fun buildGraph(method: PsiMethod): CallGraphData {
    val service = CallGraphServiceImpl.getInstance(project)
    service.resetCaches()
    val graph = service.buildCallGraph(method) ?: error("Call graph build failed")
    return graph.data
  }

  private fun findHandleMethod(file: PsiFile): PsiMethod =
      PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "handle" }
}
