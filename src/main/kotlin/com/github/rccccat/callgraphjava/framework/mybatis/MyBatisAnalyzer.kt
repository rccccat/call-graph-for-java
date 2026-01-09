package com.github.rccccat.callgraphjava.framework.mybatis

import com.github.rccccat.callgraphjava.api.model.CallGraphNodeData
import com.github.rccccat.callgraphjava.api.model.NodeType
import com.github.rccccat.callgraphjava.api.model.SqlType
import com.github.rccccat.callgraphjava.cache.CallGraphCacheManager
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraphNode
import com.github.rccccat.callgraphjava.util.buildMethodKeyWithParams
import com.github.rccccat.callgraphjava.util.extractStringValues
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import java.util.concurrent.ConcurrentHashMap

/** Analyzer for MyBatis mapper methods and SQL mappings. */
class MyBatisAnalyzer(
    private val project: Project,
    cacheManager: CallGraphCacheManager,
) {
  private val log = Logger.getInstance(MyBatisAnalyzer::class.java)
  private val mapperMethodCache =
      cacheManager.createCachedValue { ConcurrentHashMap<String, MyBatisMethodInfo>() }
  private val xmlIndexState =
      cacheManager.createCachedValue { XmlIndexState(ConcurrentHashMap(), Any()) }

  private val primitiveWrapperMap =
      mapOf(
          "boolean" to "java.lang.Boolean",
          "byte" to "java.lang.Byte",
          "short" to "java.lang.Short",
          "int" to "java.lang.Integer",
          "long" to "java.lang.Long",
          "char" to "java.lang.Character",
          "float" to "java.lang.Float",
          "double" to "java.lang.Double",
      )

  private data class XmlIndexState(
      val index: ConcurrentHashMap<String, MutableList<XmlSqlEntry>>,
      val lock: Any,
  ) {
    @Volatile var built: Boolean = false
  }

  private data class XmlSqlEntry(
      val sqlType: SqlType,
      val sql: String,
      val xmlFile: VirtualFile,
      val tagName: String,
      val parameterType: String?,
  )

  /** Analyzes a method to determine whether it is a MyBatis mapper method. */
  fun analyzeMapperMethod(method: PsiMethod): MyBatisMethodInfo {
    return ReadAction.compute<MyBatisMethodInfo, Exception> {
      val cacheKey = buildMethodKeyWithParams(method)
      val cache = mapperMethodCache.value
      cache[cacheKey]?.let {
        return@compute it
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
        cache[cacheKey] = result
        return@compute result
      }

      val xmlSql = findXmlSqlFromIndex(method)
      if (xmlSql != null) {
        val result =
            MyBatisMethodInfo(
                isMapperMethod = true,
                sqlType = xmlSql.sqlType,
                sqlStatement = xmlSql.sql,
                isAnnotationBased = false,
            )
        cache[cacheKey] = result
        return@compute result
      }

      val result = MyBatisMethodInfo(isMapperMethod = false)
      cache[cacheKey] = result
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

  private fun findXmlSqlFromIndex(method: PsiMethod): XmlSqlEntry? =
      findXmlEntryForMethod(method, logOnFailure = true)

  private fun buildXmlSqlIndex() {
    val state = xmlIndexState.value
    if (state.built) return
    synchronized(state.lock) {
      if (state.built) return

      val scope = GlobalSearchScope.projectScope(project)
      val xmlFiles =
          ReadAction.compute<Collection<VirtualFile>, Exception> {
            FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)
          }

      for (file in xmlFiles) {
        try {
          indexMapperXml(file, state.index)
        } catch (e: Exception) {
          log.warn("Failed to index XML file: ${file.path}", e)
        }
      }

      state.built = true
    }
  }

  private fun findXmlEntryForMethod(
      method: PsiMethod,
      logOnFailure: Boolean,
  ): XmlSqlEntry? {
    val containingClass = method.containingClass ?: return null
    val namespace = containingClass.qualifiedName ?: return null

    buildXmlSqlIndex()

    val key = "$namespace#${method.name}"
    val entries = xmlIndexState.value.index[key] ?: return null
    return matchXmlEntriesForMethod(method, entries, logOnFailure)
  }

  private fun matchXmlEntriesForMethod(
      method: PsiMethod,
      entries: List<XmlSqlEntry>,
      logOnFailure: Boolean,
  ): XmlSqlEntry? {
    if (entries.isEmpty()) return null
    if (entries.size == 1) return entries.first()

    val paramTypes = method.parameterList.parameters.map { it.type.canonicalText }
    if (paramTypes.size != 1) {
      if (logOnFailure) {
        log.warn(
            "Ambiguous MyBatis XML mapping for ${method.containingClass?.qualifiedName}#${method.name}(${paramTypes.joinToString(
                        ",",
                    )}): ${entries.size} XML entries",
        )
      }
      return null
    }

    val methodParamType = paramTypes.single()
    val matched =
        entries.filter { entry ->
          val entryParamType = entry.parameterType ?: return@filter false
          isParameterTypeMatch(entryParamType, methodParamType)
        }

    if (matched.size == 1) {
      return matched.first()
    }

    if (logOnFailure) {
      val entryParamTypes = entries.joinToString(",") { it.parameterType ?: "<none>" }
      log.warn(
          "Ambiguous MyBatis XML mapping for ${method.containingClass?.qualifiedName}#${method.name}($methodParamType): ${matched.size} matched entries, all entries [$entryParamTypes]",
      )
    }

    return null
  }

  private fun isParameterTypeMatch(
      entryParamType: String,
      methodParamType: String,
  ): Boolean {
    val entryType = normalizeTypeName(entryParamType)
    val methodType = normalizeTypeName(methodParamType)
    if (entryType == methodType) return true

    val entrySimple = entryType.substringAfterLast('.')
    val methodSimple = methodType.substringAfterLast('.')
    if (entrySimple == methodSimple) return true

    val boxedFromPrimitive = primitiveWrapperMap[entryType]
    if (boxedFromPrimitive != null && boxedFromPrimitive == methodType) return true

    val primitiveFromWrapper =
        primitiveWrapperMap.entries.firstOrNull { it.value == entryType }?.key
    return primitiveFromWrapper != null && primitiveFromWrapper == methodType
  }

  private fun normalizeTypeName(typeName: String): String {
    val trimmed = typeName.trim()
    return trimmed.substringBefore("<")
  }

  private fun indexMapperXml(
      file: VirtualFile,
      index: ConcurrentHashMap<String, MutableList<XmlSqlEntry>>,
  ): Boolean {
    return ReadAction.compute<Boolean, Exception> {
      val psiFile =
          PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute false
      val rootTag = psiFile.rootTag ?: return@compute false

      if (rootTag.name != "mapper") return@compute false

      val namespace = rootTag.getAttributeValue("namespace")?.trim()
      if (namespace.isNullOrEmpty()) return@compute false

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
        val parameterType = tag.getAttributeValue("parameterType")?.trim()?.ifEmpty { null }

        val key = "$namespace#$id"
        val entries = index.computeIfAbsent(key) { mutableListOf() }
        entries.add(XmlSqlEntry(sqlType, sql, file, tag.name, parameterType))
      }
      true
    }
  }

  private fun findXmlElementForMethod(method: PsiMethod): PsiElement? {
    val entry = findXmlEntryForMethod(method, logOnFailure = false) ?: return null

    return ReadAction.compute<PsiElement?, Exception> {
      val psiFile =
          PsiManager.getInstance(project).findFile(entry.xmlFile) as? XmlFile ?: return@compute null
      val rootTag = psiFile.rootTag ?: return@compute null

      for (tag in rootTag.subTags) {
        if (tag.name != entry.tagName) continue
        if (tag.getAttributeValue("id") != method.name) continue

        val tagParamType = tag.getAttributeValue("parameterType")?.trim()?.ifEmpty { null }
        if (entry.parameterType == null) {
          if (tagParamType != null) continue
        } else {
          if (tagParamType == null) continue
          if (!isParameterTypeMatch(tagParamType, entry.parameterType)) continue
        }

        return@compute tag
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
