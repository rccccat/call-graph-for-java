package com.github.rccccat.ideacallgraph.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MyToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(
      project: Project,
      toolWindow: ToolWindow,
  ) {
    val callGraphContent = CallGraphToolWindowContent(project)
    val contentFactory = ContentFactory.getInstance()
    val callGraphTab = contentFactory.createContent(callGraphContent, "Call Graph", false)
    toolWindow.contentManager.addContent(callGraphTab)

    val springTab =
        contentFactory.createContent(SpringApisToolWindowContent(project), "Spring APIs", false)
    toolWindow.contentManager.addContent(springTab)

    val mybatisTab =
        contentFactory.createContent(MyBatisMappingsToolWindowContent(project), "MyBatis", false)
    toolWindow.contentManager.addContent(mybatisTab)
  }

  override fun shouldBeAvailable(project: Project) = true
}
