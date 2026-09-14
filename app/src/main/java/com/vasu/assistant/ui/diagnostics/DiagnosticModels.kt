package com.vasu.assistant.ui.diagnostics

data class DiagnosticItem(
    val name: String,
    val status: DiagnosticStatus,
    val details: String
)

enum class DiagnosticStatus {
    OK, WARNING, ERROR, UNKNOWN
}
