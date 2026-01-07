package com.github.rccccat.callgraphjava.framework.mybatis

import com.github.rccccat.callgraphjava.addMyBatisStubs
import com.github.rccccat.callgraphjava.addSpringCoreStubs
import com.github.rccccat.callgraphjava.api.model.CallGraphData
import com.github.rccccat.callgraphjava.cache.CallGraphCacheManager
import com.github.rccccat.callgraphjava.service.CallGraphServiceImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * MyBatis integration tests.
 *
 * Note: SQL node coverage is intentionally light; add integration cases as needed.
 */
class MyBatisAnalyzerTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.addSpringCoreStubs()
    myFixture.addMyBatisStubs()
  }

  fun testMapperMethodIsRecognized() {
    myFixture.addFileToProject(
        "src/demo/mapper/UserMapper.java",
        """
        package demo.mapper;
        import org.apache.ibatis.annotations.Mapper;
        import org.apache.ibatis.annotations.Select;

        @Mapper
        public interface UserMapper {
            @Select("select * from users where id = #{id}")
            String findById(int id);
        }
        """
            .trimIndent(),
    )

    val file =
        myFixture.addFileToProject(
            "src/demo/UserService.java",
            """
            package demo;
            import demo.mapper.UserMapper;
            import org.springframework.beans.factory.annotation.Autowired;

            class UserService {
                @Autowired
                private UserMapper userMapper;

                String handle() {
                    return userMapper.findById(1);
                }
            }
            """
                .trimIndent(),
        )

    val method = findHandleMethod(file)
    val graph = buildGraph(method)
    val handleNode = graph.nodes.values.firstOrNull { it.name == "handle" }
    val hasEdgeToFindById =
        if (handleNode == null) {
          false
        } else {
          graph.getCallTargets(handleNode.id).any { target -> target.name == "findById" }
        }

    assertTrue(
        "Expected edge to findById",
        hasEdgeToFindById,
    )
  }

  // TODO: Add SQL node coverage for annotation/XML cases.
  // fun testAnnotationSqlCreatesSqlNode() { ... }
  // fun testXmlSqlCreatesSqlNode() { ... }

  fun testCacheResetsAfterPsiChange() {
    val mapperFile =
        myFixture.addFileToProject(
            "src/demo/mapper/UserMapper.java",
            """
            package demo.mapper;
            import org.apache.ibatis.annotations.Mapper;
            import org.apache.ibatis.annotations.Select;

            @Mapper
            public interface UserMapper {
                @Select("select * from users where id = #{id}")
                String findById(int id);
            }
            """
                .trimIndent(),
        )

    val analyzer = MyBatisAnalyzer(project, CallGraphCacheManager.getInstance(project))
    val methodBefore =
        PsiTreeUtil.findChildrenOfType(mapperFile, PsiMethod::class.java).first {
          it.name == "findById"
        }
    val infoBefore = analyzer.analyzeMapperMethod(methodBefore)
    assertEquals("select * from users where id = #{id}", infoBefore.sqlStatement)

    val updatedText =
        """
        package demo.mapper;
        import org.apache.ibatis.annotations.Mapper;
        import org.apache.ibatis.annotations.Select;

        @Mapper
        public interface UserMapper {
            @Select("select id from users where id = #{id}")
            String findById(int id);
        }
        """
            .trimIndent()

    WriteCommandAction.runWriteCommandAction(project) {
      val document =
          PsiDocumentManager.getInstance(project).getDocument(mapperFile)
              ?: error("Mapper document not found")
      document.setText(updatedText)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    val updatedFile =
        PsiManager.getInstance(project).findFile(mapperFile.virtualFile)
            ?: error("Updated mapper file not found")
    val methodAfter =
        PsiTreeUtil.findChildrenOfType(updatedFile, PsiMethod::class.java).first {
          it.name == "findById"
        }
    val infoAfter = analyzer.analyzeMapperMethod(methodAfter)
    assertEquals("select id from users where id = #{id}", infoAfter.sqlStatement)
  }

  private fun findHandleMethod(file: com.intellij.psi.PsiFile): PsiMethod =
      PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).first { it.name == "handle" }

  private fun buildGraph(method: PsiMethod): CallGraphData {
    val service = CallGraphServiceImpl.getInstance(project)
    service.resetCaches()
    val graph = service.buildCallGraph(method) ?: error("Call graph build failed")
    return graph.data
  }
}
