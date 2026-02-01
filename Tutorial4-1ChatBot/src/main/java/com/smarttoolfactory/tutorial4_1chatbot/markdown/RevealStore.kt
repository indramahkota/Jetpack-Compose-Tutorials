package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import kotlin.collections.set

@Stable
class RevealStore {
    // paragraph nodeKey -> startIndex (monotonic)
    val startIndexByNodeKey = mutableStateMapOf<String, Int>()

    // paragraph nodeKey -> completed (used to disable animation forever)
    val completedByNodeKey = mutableStateMapOf<String, Boolean>()

    fun getStart(nodeKey: String): Int = startIndexByNodeKey[nodeKey] ?: 0

    fun setStartMonotonic(nodeKey: String, newStart: Int) {
        val old = startIndexByNodeKey[nodeKey] ?: 0
        startIndexByNodeKey[nodeKey] = maxOf(old, newStart)
    }

    fun markCompleted(nodeKey: String) {
        completedByNodeKey[nodeKey] = true
    }

    fun isCompleted(nodeKey: String): Boolean = completedByNodeKey[nodeKey] == true
}