package com.github.rccccat.callgraphjava.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class CallGraphCacheManager(
    private val project: Project,
) {
  private val cachedValuesManager = CachedValuesManager.getManager(project)
  private val manualTracker = SimpleModificationTracker()

  fun <T> createCachedValue(
      valueProvider: () -> T,
  ): CachedValue<T> {
    return cachedValuesManager.createCachedValue(
        {
          CachedValueProvider.Result.create(
              valueProvider(),
              PsiModificationTracker.MODIFICATION_COUNT,
              ProjectRootModificationTracker.getInstance(project),
              manualTracker,
          )
        },
        false,
    )
  }

  fun invalidateAll() {
    manualTracker.incModificationCount()
  }

  companion object {
    fun getInstance(project: Project): CallGraphCacheManager =
        project.getService(CallGraphCacheManager::class.java)
  }
}
