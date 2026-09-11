package com.vasu.assistant.core.tts

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SpeechQueue - Manages sequential TTS playback with de-duplication.
 *
 * Queues multiple speech items and plays them in order.
 * Supports interruption and priority messages.
 * De-duplicates identical consecutive items to prevent repeated TTS.
 */
@Singleton
class SpeechQueue @Inject constructor() {

    companion object {
        private const val TAG = "SpeechQueue"
    }

    private val queue = ConcurrentLinkedQueue<SpeechItem>()

    private val _currentItem = MutableStateFlow<SpeechItem?>(null)
    val currentItem: StateFlow<SpeechItem?> = _currentItem.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    /** Tracks the last enqueued text to prevent duplicate consecutive enqueue. */
    private var lastEnqueuedText: String? = null

    /**
     * Add text to speech queue. De-duplicates if same text is enqueued consecutively.
     */
    fun enqueue(text: String, priority: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // De-duplication: skip if same text was just enqueued
        if (!priority && trimmed == lastEnqueuedText) {
            Log.d(TAG, "De-duplicated TTS request: \"$trimmed\"")
            return
        }

        val item = SpeechItem(
            text = trimmed,
            priority = priority
        )

        lastEnqueuedText = trimmed

        if (priority) {
            // Priority items go to front
            val tempList = queue.toList()
            queue.clear()
            queue.add(item)
            tempList.forEach { queue.add(it) }
        } else {
            queue.add(item)
        }

        _queueSize.value = queue.size
    }

    /**
     * Get next item and remove from queue
     */
    fun dequeue(): SpeechItem? {
        val item = queue.poll()
        _currentItem.value = item
        _queueSize.value = queue.size
        return item
    }

    /**
     * Peek at next item without removing
     */
    fun peek(): SpeechItem? = queue.peek()

    /**
     * Check if queue has items
     */
    fun hasNext(): Boolean = queue.isNotEmpty()

    /**
     * Clear entire queue
     */
    fun clear() {
        queue.clear()
        _currentItem.value = null
        _queueSize.value = 0
        lastEnqueuedText = null
    }

    /**
     * Skip current item
     */
    fun skipCurrent() {
        _currentItem.value = null
    }

    /**
     * Set processing state
     */
    fun setProcessing(processing: Boolean) {
        _isProcessing.value = processing
    }
}

/**
 * Speech queue item
 */
data class SpeechItem(
    val text: String,
    val priority: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
