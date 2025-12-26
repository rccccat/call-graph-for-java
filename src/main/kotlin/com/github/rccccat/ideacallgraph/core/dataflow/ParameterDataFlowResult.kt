package com.github.rccccat.ideacallgraph.core.dataflow

import com.intellij.psi.PsiParameter

/** Result of parameter data flow analysis. */
data class ParameterDataFlowResult(
    val parameter: PsiParameter,
    val isEffectivelyUsed: Boolean,
)
