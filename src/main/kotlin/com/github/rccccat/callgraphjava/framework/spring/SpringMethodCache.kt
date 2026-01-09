package com.github.rccccat.callgraphjava.framework.spring

import com.github.rccccat.callgraphjava.cache.CallGraphCacheManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

internal data class MethodMappingIndex(
    val classMappingSignatures: Set<String>,
    val interfaceMappingKeys: Set<String>,
)

@Service(Service.Level.PROJECT)
class SpringMethodCache(
    project: Project,
) {
  private val cacheManager = CallGraphCacheManager.getInstance(project)
  private val mappingIndexCache =
      cacheManager.createCachedValue { ConcurrentHashMap<String, MethodMappingIndex>() }

  internal fun mappingIndexCache(): ConcurrentHashMap<String, MethodMappingIndex> =
      mappingIndexCache.value

  companion object {
    fun getInstance(project: Project): SpringMethodCache =
        project.getService(SpringMethodCache::class.java)
  }
}
