package com.github.rccccat.ideacallgraph.framework.mybatis

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.api.model.SqlType
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode
import com.github.rccccat.ideacallgraph.util.MyBatisAnnotations
import com.github.rccccat.ideacallgraph.util.extractStringValues
import com.github.rccccat.ideacallgraph.util.hasAnyAnnotation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import java.util.concurrent.ConcurrentHashMap

/** Analyzer for MyBatis mapper methods and SQL mappings. */
class MyBatisAnalyzer(
    private val project: Project,
) {
  private val mapperMethodCache = ConcurrentHashMap<String, MyBatisMethodInfo>()
  private val mapperXmlFileCache = ConcurrentHashMap<String, VirtualFile>()
  private val missingXmlFileCache = ConcurrentHashMap.newKeySet<String>()
  private val cacheLock = Any()
  @Volatile private var lastModificationCount = -1L

  private data class MapperXml(
      val file: VirtualFile,
      val rootTag: XmlTag,
  )

  /** Analyzes a method to determine whether it is a MyBatis mapper method. */
  fun analyzeMapperMethod(method: PsiMethod): MyBatisMethodInfo {
    return ReadAction.compute<MyBatisMethodInfo, Exception> {
      clearCachesIfNeeded()
      val cacheKey = buildMethodCacheKey(method)
      mapperMethodCache[cacheKey]?.let {
        return@compute it
      }

      val containingClass = method.containingClass ?: return@compute MyBatisMethodInfo()

      if (!isMapperInterface(containingClass)) {
        val result = MyBatisMethodInfo()
        mapperMethodCache[cacheKey] = result
        return@compute result
      }

      val annotationSql = findAnnotationBasedSql(method)
      if (annotationSql != null) {
        val result =
            MyBatisMethodInfo(
                isMapperMethod = true,
                sqlType = annotationSql.sqlType,
                sqlStatement = annotationSql.sql,
                isAnnotationBased = true,
            )
        mapperMethodCache[cacheKey] = result
        return@compute result
      }

      val xmlSql = findXmlBasedSql(containingClass, method)
      if (xmlSql != null) {
        val result =
            MyBatisMethodInfo(
                isMapperMethod = true,
                sqlType = xmlSql.sqlType,
                sqlStatement = xmlSql.sql,
                isAnnotationBased = false,
            )
        mapperMethodCache[cacheKey] = result
        return@compute result
      }

      val result = MyBatisMethodInfo(isMapperMethod = true)
      mapperMethodCache[cacheKey] = result
      result
    }
  }

  /** Creates an IDE call graph node for a MyBatis SQL statement. */
  fun createSqlNode(
      mapperMethod: PsiMethod,
      mybatisInfo: MyBatisMethodInfo,
  ): IdeCallGraphNode? {
    return ReadAction.compute<IdeCallGraphNode?, Exception> {
      clearCachesIfNeeded()
      val containingClass = mapperMethod.containingClass ?: return@compute null
      val sqlType = mybatisInfo.sqlType ?: return@compute null

      val paramTypes =
          mapperMethod.parameterList.parameters.joinToString(",") { it.type.canonicalText }
      val sqlId = "${containingClass.qualifiedName}#${mapperMethod.name}($paramTypes):SQL"
      val sqlName = "${sqlType.name.lowercase()}_${mapperMethod.name}"
      val simplifiedSql = simplifySqlStatement(mybatisInfo.sqlStatement)

      val sqlElement =
          if (!mybatisInfo.isAnnotationBased) {
            findXmlElementForMethod(containingClass, mapperMethod) ?: mapperMethod
          } else {
            mapperMethod
          }

      val pointer =
          SmartPointerManager.getInstance(project).createSmartPsiElementPointer(sqlElement)

      val elementFile = sqlElement.containingFile?.virtualFile
      val offset = sqlElement.textRange?.startOffset ?: -1
      val lineNumber = calculateLineNumber(sqlElement, offset)

      val nodeData =
          CallGraphNodeData(
              id = sqlId,
              name = sqlName,
              className = containingClass.name + ".xml",
              signature = simplifiedSql ?: "SQL Statement",
              nodeType = NodeType.MYBATIS_SQL_STATEMENT,
              isProjectCode = true,
              sqlType = sqlType,
              sqlStatement = mybatisInfo.sqlStatement,
              offset = offset,
              lineNumber = lineNumber,
          )

      IdeCallGraphNode(nodeData, pointer)
    }
  }

  private fun calculateLineNumber(
      element: PsiElement,
      offset: Int,
  ): Int {
    if (offset < 0) return -1
    val file = element.containingFile ?: return -1
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return -1
    return document.getLineNumber(offset) + 1
  }

  private fun clearCachesIfNeeded() {
    val currentCount = PsiModificationTracker.getInstance(project).modificationCount
    if (currentCount == lastModificationCount) {
      return
    }
    synchronized(cacheLock) {
      if (currentCount == lastModificationCount) {
        return
      }
      mapperMethodCache.clear()
      mapperXmlFileCache.clear()
      missingXmlFileCache.clear()
      lastModificationCount = currentCount
    }
  }

  private fun findXmlElementForMethod(
      containingClass: PsiClass,
      method: PsiMethod,
  ): PsiElement? {
    val mapperXml = findMapperXml(containingClass) ?: return null
    val methodTags =
        findChildTags(mapperXml.rootTag, listOf("select", "insert", "update", "delete"))
    return methodTags.firstOrNull { tag -> tag.getAttributeValue("id") == method.name }
  }

  private fun isMapperInterface(psiClass: PsiClass): Boolean {
    if (hasAnyAnnotation(psiClass, MyBatisAnnotations.mapperAnnotations)) {
      return true
    }

    val qualifiedName = psiClass.qualifiedName ?: return false
    if (qualifiedName.contains("mapper", ignoreCase = true)) {
      return psiClass.isInterface
    }

    return findMapperXmlFile(psiClass) != null
  }

  private fun findAnnotationBasedSql(method: PsiMethod): SqlInfo? {
    val annotations = method.annotations

    for (annotation in annotations) {
      val qualifiedName = annotation.qualifiedName ?: continue

      when {
        qualifiedName.endsWith("Select") -> {
          val sql = extractSqlFromAnnotation(annotation)
          if (sql != null) {
            return SqlInfo(SqlType.SELECT, sql)
          }
        }

        qualifiedName.endsWith("Insert") -> {
          val sql = extractSqlFromAnnotation(annotation)
          if (sql != null) {
            return SqlInfo(SqlType.INSERT, sql)
          }
        }

        qualifiedName.endsWith("Update") -> {
          val sql = extractSqlFromAnnotation(annotation)
          if (sql != null) {
            return SqlInfo(SqlType.UPDATE, sql)
          }
        }

        qualifiedName.endsWith("Delete") -> {
          val sql = extractSqlFromAnnotation(annotation)
          if (sql != null) {
            return SqlInfo(SqlType.DELETE, sql)
          }
        }
      }
    }

    return null
  }

  private fun findXmlBasedSql(
      containingClass: PsiClass,
      method: PsiMethod,
  ): XmlSqlInfo? {
    val mapperXml = findMapperXml(containingClass) ?: return null
    val methodTags =
        findChildTags(mapperXml.rootTag, listOf("select", "insert", "update", "delete"))

    for (tag in methodTags) {
      val id = tag.getAttributeValue("id")
      if (id == method.name) {
        val sqlType =
            when (tag.name.lowercase()) {
              "select" -> SqlType.SELECT
              "insert" -> SqlType.INSERT
              "update" -> SqlType.UPDATE
              "delete" -> SqlType.DELETE
              else -> continue
            }

        val sqlContent = tag.value.text.trim()
        if (sqlContent.isNotEmpty()) {
          return XmlSqlInfo(
              sqlType = sqlType,
              sql = sqlContent,
          )
        }
      }
    }

    return null
  }

  private fun findMapperXmlFile(psiClass: PsiClass): VirtualFile? {
    val qualifiedName = psiClass.qualifiedName ?: return null
    mapperXmlFileCache[qualifiedName]?.let {
      return it
    }
    if (missingXmlFileCache.contains(qualifiedName)) {
      return null
    }

    val possibleNames = listOf("${psiClass.name}.xml", "${psiClass.name}Mapper.xml")
    val scope = GlobalSearchScope.allScope(project)

    for (name in possibleNames) {
      val files = FilenameIndex.getFilesByName(project, name, scope)
      for (psiFile in files) {
        val virtualFile = psiFile.virtualFile ?: continue
        if (isMatchingMapperXml(virtualFile, qualifiedName)) {
          mapperXmlFileCache[qualifiedName] = virtualFile
          return virtualFile
        }
      }
    }

    missingXmlFileCache.add(qualifiedName)
    return null
  }

  private fun findMapperXml(containingClass: PsiClass): MapperXml? {
    val xmlFile = findMapperXmlFile(containingClass) ?: return null
    val xmlPsiFile = PsiManager.getInstance(project).findFile(xmlFile) as? XmlFile ?: return null
    val rootTag = xmlPsiFile.rootTag ?: return null
    return MapperXml(xmlFile, rootTag)
  }

  private fun buildMethodCacheKey(method: PsiMethod): String {
    val containingClass = method.containingClass?.qualifiedName ?: "Unknown"
    val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
    return "$containingClass#${method.name}($paramTypes)"
  }

  private fun isMatchingMapperXml(
      virtualFile: VirtualFile,
      expectedNamespace: String,
  ): Boolean {
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile ?: return false
    val rootTag = psiFile.rootTag ?: return false

    if (rootTag.name != "mapper") return false

    val namespace = rootTag.getAttributeValue("namespace")
    return namespace == expectedNamespace
  }

  private fun findChildTags(
      parent: XmlTag,
      tagNames: List<String>,
  ): List<XmlTag> {
    val result = mutableListOf<XmlTag>()

    for (child in parent.subTags) {
      if (child.name.lowercase() in tagNames) {
        result.add(child)
      }
    }

    return result
  }

  private fun extractSqlFromAnnotation(annotation: PsiAnnotation): String? {
    val valueAttr = annotation.findAttributeValue("value") ?: return null
    val values = extractStringValues(valueAttr)
    if (values.isEmpty()) return null
    return values.joinToString(" ")
  }

  private fun simplifySqlStatement(sql: String?): String? {
    if (sql == null) return null
    val normalized = sql.replace(Regex("\\s+"), " ").trim()
    return if (normalized.length > 100) {
      normalized.substring(0, 97) + "..."
    } else {
      normalized
    }
  }

  data class MyBatisMethodInfo(
      val isMapperMethod: Boolean = false,
      val sqlType: SqlType? = null,
      val sqlStatement: String? = null,
      val isAnnotationBased: Boolean = false,
  )

  private data class SqlInfo(
      val sqlType: SqlType,
      val sql: String,
  )

  private data class XmlSqlInfo(
      val sqlType: SqlType,
      val sql: String,
  )
}
