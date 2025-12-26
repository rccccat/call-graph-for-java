package com.github.rccccat.ideacallgraph.ide.psi

import com.github.rccccat.ideacallgraph.framework.mybatis.MyBatisAnalyzer
import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiNodeFactoryIdTest : BasePlatformTestCase() {
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
        PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).filter { method ->
          method.name == "work" && method.containingClass?.qualifiedName == null
        }

    assertEquals(4, methods.size)

    val factory = PsiNodeFactory(project, SpringAnalyzer(), MyBatisAnalyzer(project))
    val ids =
        methods.map { method -> factory.createNodeData(method)?.id ?: error("Node data missing") }

    assertEquals(4, ids.toSet().size)
  }
}
