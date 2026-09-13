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

    /** Tracks the last enqueued response ID to prevent duplicate consecutive enqueue. */
    private var lastEnqueuedResponseId: String? = null

    /**
     * Add text to speech queue. De-duplicates if same responseId is enqueued consecutively.
     * Returns the queued item (carrying its unique ttsId), or null when skipped.
     */
    fun enqueue(text: String, responseId: String? = null, priority: Boolean = false): SpeechItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // De-duplication: skip if same responseId was just enqueued
        if (!priority && responseId != null && responseId == lastEnqueuedResponseId) {
            Log.d(TAG, "De-duplicated TTS request for responseId: $responseId")
            return null
        }

        val item = SpeechItem(
            text = trimmed,
            responseId = responseId,
            priority = priority
        )

        lastEnqueuedResponseId = responseId

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
        return item
    }

    /**
     * Get next item and remove from queue.
     * Clears the consecutive-duplicate guard so the same responseId may be
     * queued again for a LATER turn (genuine repeats), while an identical
     * responseId still queued behind it stays de-duplicated.
     */
    fun dequeue(): SpeechItem? {
        val item = queue.poll()
        _currentItem.value = item
        _queueSize.value = queue.size
        lastEnqueuedResponseId = null
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
        lastEnqueuedResponseId = null
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
 * Speech queue item. Each item carries a unique ttsId so TTS start/complete
 * events can be correlated across Chat, Voice, router and engine logs.
 */
data class SpeechItem(
    val text: String,
    val responseId: String? = null,
    val priority: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val ttsId: String = "tts_${System.nanoTime()}"
)
