package com.vasu.ai.core

class VasuReplanLoopGuard {
    companion object {
        const val MAX_SAME_STEP_REPLANS = 2
    }

    private var lastStep = -1
    private var lastAction: String? = null
    private var repeatCount = 0

    fun shouldStop(stepIndex: Int, actionType: String): Boolean {
        if (stepIndex == lastStep && actionType == lastAction) {
            repeatCount++
        } else {
            lastStep = stepIndex
            lastAction = actionType
            repeatCount = 0
        }
        return repeatCount >= MAX_SAME_STEP_REPLANS
    }

    fun reset() {
        lastStep = -1
        lastAction = null
        repeatCount = 0
    }
}
