package com.github.rccccat.ideacallgraph.service

import com.github.rccccat.ideacallgraph.framework.spring.SpringAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * Registry for stateless analyzers - application-level singleton. Avoids creating multiple
 * instances of analyzers.
 */
@Service(Service.Level.APP)
class AnalyzerRegistry {
  /** Spring analyzer - stateless, can be shared. */
  val springAnalyzer: SpringAnalyzer = SpringAnalyzer()

  companion object {
    fun getInstance(): AnalyzerRegistry =
        ApplicationManager.getApplication().getService(AnalyzerRegistry::class.java)
  }
}
