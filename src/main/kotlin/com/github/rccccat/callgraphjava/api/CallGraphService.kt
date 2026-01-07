package com.github.rccccat.callgraphjava.api

import com.github.rccccat.callgraphjava.api.model.CallGraphData
import com.github.rccccat.callgraphjava.ide.model.IdeCallGraph
import com.intellij.psi.PsiElement

/** 调用图服务接口 */
interface CallGraphService {
  fun buildCallGraph(startElement: PsiElement): IdeCallGraph?

  fun exportToJson(callGraph: IdeCallGraph): String

  fun exportToJsonCompact(callGraph: IdeCallGraph): String

  fun getData(callGraph: IdeCallGraph): CallGraphData
}
