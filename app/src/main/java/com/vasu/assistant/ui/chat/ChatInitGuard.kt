package com.vasu.assistant.ui.chat

/**
 * Pure guard used by ChatViewModel init/collectors: runs [block], returning
 * the exception message on failure instead of propagating. Cancellation must
 * be rethrown by the caller (coroutines).
 */
object ChatInitGuard {
    fun runCatching(block: () -> Unit): String? {
        return try {
            block()
            null
        } catch (e: Exception) {
            e.message ?: e::class.java.simpleName
        }
    }
}
