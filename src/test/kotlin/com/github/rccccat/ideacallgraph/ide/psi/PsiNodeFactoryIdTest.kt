package com.github.rccccat.ideacallgraph.ide.psi

import com.github.rccccat.ideacallgraph.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class PsiNodeFactoryIdTest : BasePlatformTestCase() {
  fun testKotlinNodeIdUniqueForTopLevelObjectAndLocal() {
    val file =
        myFixture.addFileToProject(
            "src/demo/IDEMyPluginTest.kt",
            """
            package demo

            fun ping(value: Int) { }

            object O {
                fun ping(value: Int) { }
            }

            fun caller() {
                fun ping(value: Int) { }
                ping(1)
                O.ping(2)
                demo.ping(3)
            }
            """
                .trimIndent(),
        ) as KtFile

    val topLevelPing =
        file.declarations.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == "ping" }
            ?: error("Top-level ping not found")
    val objectDecl =
        file.declarations.filterIsInstance<KtObjectDeclaration>().firstOrNull { it.name == "O" }
            ?: error("Object O not found")
    val objectPing =
        objectDecl.declarations.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == "ping" }
            ?: error("Object ping not found")
    val caller =
        file.declarations.filterIsInstance<KtNamedFunction>().firstOrNull { it.name == "caller" }
            ?: error("caller not found")
    val localPing =
        caller.findDescendantOfType<KtNamedFunction> { it.name == "ping" }
            ?: error("Local ping not found")

    val factory = PsiNodeFactory(project, SpringAnalyzer(), MyBatisAnalyzer(project))
    val ids =
        listOf(topLevelPing, objectPing, localPing).map { function ->
          factory.createNodeData(function)?.id ?: error("Node data missing")
        }

    assertEquals(3, ids.toSet().size)
  }

  fun testJavaNodeIdUniqueForLocalAndAnonymousClasses() {
    val file =
        myFixture.addFileToProject(
            "src/demo/LocalAndAnonymous.java",
            """
            package demo;

            interface Worker {
                void work();
            }

            class LocalAndAnonymous {
                void handle() {
                    class LocalWorker {
                        void work() { }
                    }
                    class LocalWorker2 {
                        void work() { }
                    }
                    new LocalWorker().work();
                    new LocalWorker2().work();
                    Worker w1 = new Worker() {
                        public void work() { }
                    };
                    Worker w2 = new Worker() {
                        public void work() { }
                    };
                    w1.work();
                    w2.work();
                }
            }
            """
                .trimIndent(),
        )

    val methods =
        PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)
            .filter { method ->
              method.name == "work" && method.containingClass?.qualifiedName == null
            }

    assertEquals(4, methods.size)

    val factory = PsiNodeFactory(project, SpringAnalyzer(), MyBatisAnalyzer(project))
    val ids = methods.map { method -> factory.createNodeData(method)?.id ?: error("Node data missing") }

    assertEquals(4, ids.toSet().size)
  }
}
