package com.github.rccccat.ideacallgraph.analysis

import com.github.rccccat.ideacallgraph.model.CallGraphEdge
import com.github.rccccat.ideacallgraph.model.CallGraphNode
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import java.util.*

/**
 * Analyzer for MyBatis framework specific patterns and XML mappings
 */
class MyBatisCallGraphAnalyzer(private val project: Project) {

    companion object {
        private val MAPPER_ANNOTATIONS = setOf(
            "Mapper", "Repository",
            "org.apache.ibatis.annotations.Mapper",
            "org.springframework.stereotype.Repository",
            "org.mybatis.spring.annotation.MapperScan"
        )

        private val SQL_ANNOTATIONS = setOf(
            "Select", "Insert", "Update", "Delete",
            "org.apache.ibatis.annotations.Select",
            "org.apache.ibatis.annotations.Insert",
            "org.apache.ibatis.annotations.Update",
            "org.apache.ibatis.annotations.Delete"
        )
    }

    /**
     * Analyzes a method to determine if it's a MyBatis mapper method
     */
    fun analyzeMapperMethod(method: PsiMethod): MyBatisMethodInfo {
        return ReadAction.compute<MyBatisMethodInfo, Exception> {
            val containingClass = method.containingClass ?: return@compute MyBatisMethodInfo()
            
            val isMapperInterface = isMapperInterface(containingClass)
            if (!isMapperInterface) return@compute MyBatisMethodInfo()

            // Check for annotation-based SQL first
            val annotationSql = findAnnotationBasedSql(method)
            if (annotationSql != null) {
                return@compute MyBatisMethodInfo(
                    isMapperMethod = true,
                    sqlType = annotationSql.sqlType,
                    sqlStatement = annotationSql.sql,
                    isAnnotationBased = true
                )
            }

            // Look for XML-based mapping
            val xmlSql = findXmlBasedSql(containingClass, method)
            if (xmlSql != null) {
                return@compute MyBatisMethodInfo(
                    isMapperMethod = true,
                    sqlType = xmlSql.sqlType,
                    sqlStatement = xmlSql.sql,
                    xmlFilePath = xmlSql.xmlFile,
                    isAnnotationBased = false
                )
            }

            MyBatisMethodInfo(isMapperMethod = true)
        }
    }

    /**
     * Creates a CallGraphNode for a MyBatis SQL statement
     */
    fun createSqlNode(
        mapperMethod: PsiMethod,
        mybatisInfo: MyBatisMethodInfo
    ): CallGraphNode? {
        return ReadAction.compute<CallGraphNode?, Exception> {
            val containingClass = mapperMethod.containingClass ?: return@compute null
            val sqlType = mybatisInfo.sqlType ?: return@compute null
            
            val sqlId = "${containingClass.qualifiedName}#${mapperMethod.name}_SQL"
            val sqlName = "${sqlType.name.lowercase()}_${mapperMethod.name}"
            val simplifiedSql = simplifySqlStatement(mybatisInfo.sqlStatement)
            
            // Try to create pointer to actual XML element, fallback to mapper method
            val pointer = if (!mybatisInfo.isAnnotationBased && mybatisInfo.xmlFilePath != null) {
                // For XML-based SQL, try to find the actual XML tag
                findXmlElementForMethod(containingClass, mapperMethod)?.let { xmlElement ->
                    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(xmlElement)
                } ?: SmartPointerManager.getInstance(project).createSmartPsiElementPointer(mapperMethod as PsiElement)
            } else {
                // For annotation-based SQL, use the method itself
                SmartPointerManager.getInstance(project).createSmartPsiElementPointer(mapperMethod as PsiElement)
            }
            
            CallGraphNode(
                id = sqlId,
                name = sqlName,
                className = containingClass.name + ".xml",
                signature = simplifiedSql ?: "SQL Statement",
                elementPointer = pointer,
                nodeType = CallGraphNode.NodeType.MYBATIS_SQL_STATEMENT,
                sqlType = sqlType,
                sqlStatement = mybatisInfo.sqlStatement,
                xmlFilePath = mybatisInfo.xmlFilePath,
                isProjectCode = true
            )
        }
    }

    /**
     * Finds the actual XML element for a mapper method
     */
    private fun findXmlElementForMethod(containingClass: PsiClass, method: PsiMethod): PsiElement? {
        val xmlFile = findMapperXmlFile(containingClass) ?: return null
        val xmlPsiFile = PsiManager.getInstance(project).findFile(xmlFile) as? XmlFile ?: return null
        val rootTag = xmlPsiFile.rootTag ?: return null
        
        // Look for the method in the XML file
        val methodTags = findChildTags(rootTag, listOf("select", "insert", "update", "delete"))
        
        for (tag in methodTags) {
            val id = tag.getAttributeValue("id")
            if (id == method.name) {
                return tag
            }
        }
        
        return null
    }

    private fun isMapperInterface(psiClass: PsiClass): Boolean {
        // Check for @Mapper annotation
        if (hasAnyAnnotation(psiClass, MAPPER_ANNOTATIONS)) {
            return true
        }

        // Check if it's an interface in a mapper package
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (qualifiedName.contains("mapper", ignoreCase = true)) {
            return psiClass.isInterface
        }

        // Check if there's a corresponding XML file
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
                        return SqlInfo(CallGraphNode.SqlType.SELECT, sql)
                    }
                }
                qualifiedName.endsWith("Insert") -> {
                    val sql = extractSqlFromAnnotation(annotation)
                    if (sql != null) {
                        return SqlInfo(CallGraphNode.SqlType.INSERT, sql)
                    }
                }
                qualifiedName.endsWith("Update") -> {
                    val sql = extractSqlFromAnnotation(annotation)
                    if (sql != null) {
                        return SqlInfo(CallGraphNode.SqlType.UPDATE, sql)
                    }
                }
                qualifiedName.endsWith("Delete") -> {
                    val sql = extractSqlFromAnnotation(annotation)
                    if (sql != null) {
                        return SqlInfo(CallGraphNode.SqlType.DELETE, sql)
                    }
                }
            }
        }
        
        return null
    }

    private fun findXmlBasedSql(containingClass: PsiClass, method: PsiMethod): XmlSqlInfo? {
        val xmlFile = findMapperXmlFile(containingClass) ?: return null
        val xmlPsiFile = PsiManager.getInstance(project).findFile(xmlFile) as? XmlFile ?: return null
        
        val rootTag = xmlPsiFile.rootTag ?: return null
        
        // Look for the method in the XML file
        val methodTags = findChildTags(rootTag, listOf("select", "insert", "update", "delete"))
        
        for (tag in methodTags) {
            val id = tag.getAttributeValue("id")
            if (id == method.name) {
                val sqlType = when (tag.name.lowercase()) {
                    "select" -> CallGraphNode.SqlType.SELECT
                    "insert" -> CallGraphNode.SqlType.INSERT
                    "update" -> CallGraphNode.SqlType.UPDATE
                    "delete" -> CallGraphNode.SqlType.DELETE
                    else -> continue
                }
                
                val sqlContent = tag.value.text.trim()
                if (sqlContent.isNotEmpty()) {
                    return XmlSqlInfo(
                        sqlType = sqlType,
                        sql = sqlContent,
                        xmlFile = xmlFile.path
                    )
                }
            }
        }
        
        return null
    }

    private fun findMapperXmlFile(psiClass: PsiClass): VirtualFile? {
        val qualifiedName = psiClass.qualifiedName ?: return null
        
        // Try different naming patterns for XML files
        val possibleNames = listOf(
            "${psiClass.name}.xml",
            "${psiClass.name}Mapper.xml"
        )
        
        val scope = GlobalSearchScope.projectScope(project)
        
        for (name in possibleNames) {
            val files = FilenameIndex.getFilesByName(project, name, scope)
            for (psiFile in files) {
                val virtualFile = psiFile.virtualFile ?: continue
                if (isMatchingMapperXml(virtualFile, qualifiedName)) {
                    return virtualFile
                }
            }
        }
        
        return null
    }

    private fun isMatchingMapperXml(virtualFile: VirtualFile, expectedNamespace: String): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile ?: return false
        val rootTag = psiFile.rootTag ?: return false
        
        if (rootTag.name != "mapper") return false
        
        val namespace = rootTag.getAttributeValue("namespace")
        return namespace == expectedNamespace
    }

    private fun findChildTags(parent: XmlTag, tagNames: List<String>): List<XmlTag> {
        val result = mutableListOf<XmlTag>()
        
        for (child in parent.subTags) {
            if (child.name.lowercase() in tagNames) {
                result.add(child)
            }
        }
        
        return result
    }

    private fun extractSqlFromAnnotation(annotation: PsiAnnotation): String? {
        // Try to get value from "value" attribute
        val valueAttr = annotation.findAttributeValue("value")
        if (valueAttr != null) {
            return extractStringValue(valueAttr)
        }
        
        return null
    }

    private fun extractStringValue(attributeValue: PsiAnnotationMemberValue): String? {
        return when (attributeValue) {
            is PsiLiteralExpression -> attributeValue.value as? String
            is PsiArrayInitializerMemberValue -> {
                // Join multiple strings if it's an array
                val values = mutableListOf<String>()
                for (initializer in attributeValue.initializers) {
                    if (initializer is PsiLiteralExpression) {
                        val value = initializer.value as? String
                        if (value != null) {
                            values.add(value)
                        }
                    }
                }
                if (values.isNotEmpty()) values.joinToString(" ") else null
            }
            else -> null
        }
    }

    private fun simplifySqlStatement(sql: String?): String? {
        if (sql == null) return null
        
        // Remove extra whitespace and normalize
        val normalized = sql.replace(Regex("\\s+"), " ").trim()
        
        // Truncate if too long for display
        return if (normalized.length > 100) {
            normalized.substring(0, 97) + "..."
        } else {
            normalized
        }
    }

    private fun hasAnyAnnotation(element: PsiModifierListOwner, annotations: Set<String>): Boolean {
        val modifierList = element.modifierList ?: return false
        return modifierList.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName != null && annotations.any { 
                qualifiedName.endsWith(it) || qualifiedName == it 
            }
        }
    }

    data class MyBatisMethodInfo(
        val isMapperMethod: Boolean = false,
        val sqlType: CallGraphNode.SqlType? = null,
        val sqlStatement: String? = null,
        val xmlFilePath: String? = null,
        val isAnnotationBased: Boolean = false
    )

    private data class SqlInfo(
        val sqlType: CallGraphNode.SqlType,
        val sql: String
    )

    private data class XmlSqlInfo(
        val sqlType: CallGraphNode.SqlType,
        val sql: String,
        val xmlFile: String
    )
}