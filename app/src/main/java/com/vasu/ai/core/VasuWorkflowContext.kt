package com.vasu.ai.core

/** Lightweight non-persistent context shared by workflow steps. */
data class VasuWorkflowContext(
    val workflowId: String,
    val originalCommand: String,
    var currentPackage: String? = null,
    var currentScreenFingerprint: String? = null,
    var previousScreenFingerprint: String? = null,
    var lastActionType: String? = null,
    var lastActionVerified: Boolean = false,
    var lastFailureReason: String? = null,
    var stepIndex: Int = 0
)
