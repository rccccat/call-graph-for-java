package com.github.rccccat.ideacallgraph.framework.mybatis

import com.github.rccccat.ideacallgraph.api.model.CallGraphNodeData
import com.github.rccccat.ideacallgraph.api.model.NodeType
import com.github.rccccat.ideacallgraph.api.model.SqlType
import com.github.rccccat.ideacallgraph.ide.model.IdeCallGraphNode
import com.github.rccccat.ideacallgraph.util.extractStringValues
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import java.util.concurrent.ConcurrentHashMap

/** Analyzer for MyBatis mapper methods and SQL mappings. */
class MyBatisAnalyzer(
    private val project: Project,
) {
  private val log = Logger.getInstance(MyBatisAnalyzer::class.java)
  // Method analysis cache (cleared on any PSI change for annotation-based SQL)
  private val mapperMethodCache = ConcurrentHashMap<String, MyBatisMethodInfo>()
  @Volatile private var lastModificationCount = -1L

  // XML SQL index: namespace#methodName -> XmlSqlEntry (only rebuilt on XML changes)
  private val xmlSqlIndex = ConcurrentHashMap<String, XmlSqlEntry>()
  @Volatile private var xmlIndexBuilt = false

  private val cacheLock = Any()

  private data class XmlSqlEntry(
      val sqlType: SqlType,
      val sql: String,
      val xmlFile: VirtualFile,
      val tagName: String,
  )

  init {
    // Listen for XML file changes to invalidate index
    project.messageBus
        .connect()
        .subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
              override fun after(events: List<VFileEvent>) {
                for (event in events) {
                  val file = event.file ?: continue
                  if (file.extension == "xml") {
                    invalidateXmlIndex()
                    break
                  }
                }
              }
            },
        )
  }

  /** Analyzes a method to determine whether it is a MyBatis mapper method. */
  fun analyzeMapperMethod(method: PsiMethod): MyBatisMethodInfo {
    return ReadAction.compute<MyBatisMethodInfo, Exception> {
      clearMethodCacheIfNeeded()

      val cacheKey = buildMethodCacheKey(method)
      mapperMethodCache[cacheKey]?.let {
        return@compute it
      }

      // 1. Try annotation-based SQL (@Select, @Insert, etc.)
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

      // 2. Try XML-based SQL (from index)
      val xmlSql = findXmlSqlFromIndex(method)
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

      // 3. Not a mapper method
      val result = MyBatisMethodInfo(isMapperMethod = false)
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
      val containingClass = mapperMethod.containingClass ?: return@compute null
      val sqlType = mybatisInfo.sqlType ?: return@compute null

      val paramTypes =
          mapperMethod.parameterList.parameters.joinToString(",") { it.type.canonicalText }
      val sqlId = "${containingClass.qualifiedName}#${mapperMethod.name}($paramTypes):SQL"
      val sqlName = "${sqlType.name.lowercase()}_${mapperMethod.name}"
      val simplifiedSql = simplifySqlStatement(mybatisInfo.sqlStatement)

      val sqlElement =
          if (!mybatisInfo.isAnnotationBased) {
            findXmlElementForMethod(mapperMethod) ?: mapperMethod
          } else {
            mapperMethod
          }

      val pointer =
          SmartPointerManager.getInstance(project).createSmartPsiElementPointer(sqlElement)

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

  private fun findXmlSqlFromIndex(method: PsiMethod): XmlSqlEntry? {
    val containingClass = method.containingClass ?: return null
    val namespace = containingClass.qualifiedName ?: return null

    buildXmlSqlIndex()

    val key = "$namespace#${method.name}"
    return xmlSqlIndex[key]
  }

  private fun buildXmlSqlIndex() {
    if (xmlIndexBuilt) return
    synchronized(cacheLock) {
      if (xmlIndexBuilt) return

      val scope = GlobalSearchScope.allScope(project)
      val xmlFiles =
          ReadAction.compute<Collection<VirtualFile>, Exception> {
            FilenameIndex.getAllFilesByExt(project, "xml", scope)
          }

      for (file in xmlFiles) {
        try {
          indexMapperXml(file)
        } catch (e: Exception) {
          log.warn("Failed to index XML file: ${file.path}", e)
        }
      }

      xmlIndexBuilt = true
    }
  }

  private fun indexMapperXml(file: VirtualFile): Boolean {
    return ReadAction.compute<Boolean, Exception> {
      val psiFile =
          PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute false
      val rootTag = psiFile.rootTag ?: return@compute false

      // Quick check: only process <mapper> elements
      if (rootTag.name != "mapper") return@compute false

      val namespace = rootTag.getAttributeValue("namespace")?.trim()
      if (namespace.isNullOrEmpty()) return@compute false

      // Index all SQL tags
      for (tag in rootTag.subTags) {
        val sqlType =
            when (tag.name.lowercase()) {
              "select" -> SqlType.SELECT
              "insert" -> SqlType.INSERT
              "update" -> SqlType.UPDATE
              "delete" -> SqlType.DELETE
              else -> continue
            }
        val id = tag.getAttributeValue("id") ?: continue
        val sql = tag.value.text.trim()

        val key = "$namespace#$id"
        xmlSqlIndex[key] = XmlSqlEntry(sqlType, sql, file, tag.name)
      }
      true
    }
  }

  private fun invalidateXmlIndex() {
    synchronized(cacheLock) {
      xmlSqlIndex.clear()
      xmlIndexBuilt = false
      mapperMethodCache.clear()
    }
  }

  private fun clearMethodCacheIfNeeded() {
    val currentCount = PsiModificationTracker.getInstance(project).modificationCount
    if (currentCount == lastModificationCount) return

    synchronized(cacheLock) {
      if (currentCount == lastModificationCount) return
      mapperMethodCache.clear()
      lastModificationCount = currentCount
    }
  }

  private fun findXmlElementForMethod(method: PsiMethod): PsiElement? {
    val containingClass = method.containingClass ?: return null
    val namespace = containingClass.qualifiedName ?: return null

    buildXmlSqlIndex()

    val key = "$namespace#${method.name}"
    val entry = xmlSqlIndex[key] ?: return null

    return ReadAction.compute<PsiElement?, Exception> {
      val psiFile =
          PsiManager.getInstance(project).findFile(entry.xmlFile) as? XmlFile ?: return@compute null
      val rootTag = psiFile.rootTag ?: return@compute null

      for (tag in rootTag.subTags) {
        if (tag.getAttributeValue("id") == method.name) {
          return@compute tag
        }
      }
      null
    }
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

  private fun calculateLineNumber(
      element: PsiElement,
      offset: Int,
  ): Int {
    if (offset < 0) return -1
    val file = element.containingFile ?: return -1
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return -1
    return document.getLineNumber(offset) + 1
  }

  private fun buildMethodCacheKey(method: PsiMethod): String {
    val containingClass = method.containingClass?.qualifiedName ?: "Unknown"
    val paramTypes = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
    return "$containingClass#${method.name}($paramTypes)"
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
}
