package com.vasu.assistant.core.awareness

/**
 * SuggestionEngine — proactive-help skeleton ported from Jarvis
 * (`src/awareness/suggestion-engine.ts`).
 *
 * Pure Kotlin, no `android.*` imports, so it is unit-testable on the JVM.
 * The coordinator (not this module) is responsible for producing [Signal]s
 * from Android APIs and for rendering/notifying on the returned value.
 * This object only decides *which* pending signal may fire now.
 *
 * Jarvis semantics preserved:
 * - Per-kind rate limits (ERROR fires fast, BREAK rarely).
 * - Priority order ERROR > STRUGGLE > STUCK > AUTOMATION > KNOWLEDGE >
 *   SCHEDULE > BREAK > GENERAL.
 * - 50-hash FIFO dedup so an identical suggestion is not re-emitted while
 *   its hash is retained (covers both delivered and dismissed).
 * - `evaluate` is pure + testable: time is the injected [nowMs] parameter,
 *   never read from the clock inside the decision.
 *
 * Jarvis cancel-window semantics (`src/daemon/commitment-executor.ts`:
 * passive = announce-only, moderate = 30s cancel window, aggressive = 5s)
 * are NOT implemented here. They belong to the future coordinator/executor
 * layer: a SCHEDULE signal that becomes a pending execution should reuse
 * those windows (default moderate 30s) before auto-firing.
 *
 * ## Mobile trigger mapping (KDoc only — coordinator wires later)
 *
 * | [Kind] | Jarvis trigger | Android mapping for the coordinator |
 * |---|---|---|
 * | ERROR | `error_detected` event | `ErrorLog` UNCAUGHT hook appends a Signal(ERROR) |
 * | STRUGGLE | `struggle_detected` (composite score) | Repeated-error / retry-loop counter in the assistant turn loop |
 * | STUCK | `stuck_detected` (same screen, minutes) | Same foreground screen for ~120s (UsageStatsManager / accessibility) |
 * | AUTOMATION | `assessOpportunities` proposal | On-device repeat-pattern detector (not yet built) |
 * | KNOWLEDGE | `context_changed` + vault entity hit | UsageStatsManager foreground-app delta → memory lookup |
 * | SCHEDULE | commitment due in 0–15 min / calendar 0–15 min | CalendarContract query: events due in 0–15 min |
 * | BREAK | activeMs ≥ 70 of last 90 min | Active-ms accumulator; fire at ≥ 70/90 min |
 * | GENERAL | cloud insight with actionable pattern | AIOrchestrator insight string containing suggest/try/consider |
 */
object SuggestionEngine {

    /** Suggestion kinds in priority order (ordinal = priority rank). */
    enum class Kind {
        ERROR,
        STRUGGLE,
        STUCK,
        AUTOMATION,
        KNOWLEDGE,
        SCHEDULE,
        BREAK,
        GENERAL
    }

    /**
     * Per-kind rate limits in ms, from Jarvis `TYPE_RATE_LIMITS`.
     * ERROR 15s, STRUGGLE 90s, STUCK 60s, AUTOMATION 120s, KNOWLEDGE 120s,
     * SCHEDULE 300s, BREAK 600s, GENERAL 60s.
     */
    val RATE_LIMITS_MS: Map<Kind, Long> = mapOf(
        Kind.ERROR to 15_000L,
        Kind.STRUGGLE to 90_000L,
        Kind.STUCK to 60_000L,
        Kind.AUTOMATION to 120_000L,
        Kind.KNOWLEDGE to 120_000L,
        Kind.SCHEDULE to 300_000L,
        Kind.BREAK to 600_000L,
        Kind.GENERAL to 60_000L
    )

    /** Maximum retained dedup hashes (FIFO), from Jarvis `MAX_DEDUP_HASHES`. */
    const val MAX_DEDUP_HASHES = 50

    /** One candidate suggestion produced by a trigger source. */
    data class Signal(
        val kind: Kind,
        val text: String,
        val atMs: Long = System.currentTimeMillis()
    )

    /**
     * Mutable emission state. Per-kind last-emitted timestamps plus a
     * 50-hash FIFO set covering both delivered and dismissed signals.
     * Not thread-safe; the coordinator must confine it to one thread/coroutine.
     */
    class State {
        /** Last emission time per kind (absent = never emitted). */
        val lastEmitted: MutableMap<Kind, Long> = mutableMapOf()

        private val hashQueue: ArrayDeque<String> = ArrayDeque()
        private val hashes: MutableSet<String> = mutableSetOf()

        /** Returns true if [hash] is retained in the dedup window. */
        fun contains(hash: String): Boolean = hashes.contains(hash)

        /** Records [hash], evicting the oldest once over [MAX_DEDUP_HASHES]. */
        fun addHash(hash: String) {
            if (!hashes.add(hash)) return
            hashQueue.addLast(hash)
            while (hashQueue.size > MAX_DEDUP_HASHES) {
                hashes.remove(hashQueue.removeFirst())
            }
        }

        /** Current dedup-window size (for tests/diagnostics). */
        fun hashCount(): Int = hashes.size
    }

    /**
     * Returns the highest-priority signal that may fire at [nowMs]: its
     * kind's rate limit has elapsed since the last emission AND its hash is
     * not in the delivered/dismissed set. Records the emission in [state]
     * (timestamp + hash) and returns the signal; returns null when nothing
     * is eligible.
     */
    fun evaluate(signals: List<Signal>, state: State, nowMs: Long): Signal? {
        val ordered = signals.sortedBy { it.kind.ordinal }
        for (signal in ordered) {
            val limit = RATE_LIMITS_MS[signal.kind] ?: 60_000L
            val last = state.lastEmitted[signal.kind]
            if (last != null && nowMs - last < limit) continue
            if (state.contains(hashOf(signal))) continue
            state.lastEmitted[signal.kind] = nowMs
            state.addHash(hashOf(signal))
            return signal
        }
        return null
    }

    /**
     * Records a user dismissal so the same suggestion is not re-emitted
     * while its hash is retained in the FIFO window.
     */
    fun markDismissed(state: State, signal: Signal) {
        state.addHash(hashOf(signal))
    }

    /**
     * Dedup hash, mirroring Jarvis `hashSuggestion` (type + title + 80-char
     * body snippet). Here: kind + first 80 chars of text.
     */
    fun hashOf(signal: Signal): String =
        "${signal.kind.name}:${signal.text.take(80)}"
}
