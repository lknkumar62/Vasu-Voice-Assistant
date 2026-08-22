package com.vasu.ai.core

import android.graphics.Rect
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService

/** Deterministically finds the most likely visible search/input field. */
class VasuSearchFieldDetector {

    data class SearchCandidate(
        val node: AccessibilityNodeInfo,
        val text: String?,
        val hint: String?,
        val contentDescription: String?,
        val viewId: String?,
        val className: String?,
        val inputType: Int,
        val editable: Boolean,
        val focused: Boolean,
        val visible: Boolean,
        val enabled: Boolean,
        val clickable: Boolean,
        val bounds: Rect,
        val score: Int
    )

    data class DetectionResult(
        val found: Boolean,
        val candidate: SearchCandidate?,
        val candidates: List<SearchCandidate>,
        val reason: String
    )

    fun detect(): DetectionResult {
        val service = VasuAccessibilityService.instance
            ?: return failure("accessibility_service_unavailable")
        val root = service.root()
            ?: return failure("accessibility_root_unavailable")

        val candidates = mutableListOf<SearchCandidate>()
        collectCandidates(root, candidates)

        if (candidates.isEmpty()) return failure("no_editable_input_candidates")

        val usable = candidates
            .filter { it.visible && it.enabled && it.editable }
            .sortedByDescending { it.score }

        if (usable.isEmpty()) {
            candidates.forEach { it.node.recycle() }
            return DetectionResult(false, null, emptyList(), "no_usable_search_field")
        }

        val best = usable.first()
        println(
            "VASU_SEARCH_FIELD_DETECTION " +
                "candidates=${candidates.size} usable=${usable.size} " +
                "selectedScore=${best.score} hint=${best.hint} " +
                "description=${best.contentDescription} viewId=${best.viewId} " +
                "focused=${best.focused} inputType=${best.inputType} bounds=${best.bounds}"
        )

        return DetectionResult(
            found = true,
            candidate = best,
            candidates = usable,
            reason = if (usable.size == 1) "single_usable_input" else "best_search_candidate_selected"
        )
    }

    /** Caller must recycle returned candidate nodes after use. */
    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        candidates: MutableList<SearchCandidate>
    ) {
        if (isEditableCandidate(node)) {
            candidates += buildCandidate(node)
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectCandidates(child, candidates)
            child.recycle()
        }
    }

    private fun isEditableCandidate(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return node.isEditable || className.contains("EditText", ignoreCase = true)
    }

    private fun buildCandidate(node: AccessibilityNodeInfo): SearchCandidate {
        val bounds = Rect().also(node::getBoundsInScreen)
        val text = node.text?.toString()
        val hint = readHint(node)
        val description = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName
        val inputType = node.inputType
        val visible = node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0
        val enabled = node.isEnabled
        val focused = node.isFocused
        val clickable = node.isClickable
        val score = calculateScore(node, hint, description, viewId, inputType, visible, enabled, focused, clickable, bounds)

        return SearchCandidate(
            node = AccessibilityNodeInfo.obtain(node),
            text = text,
            hint = hint,
            contentDescription = description,
            viewId = viewId,
            className = node.className?.toString(),
            inputType = inputType,
            editable = node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true,
            focused = focused,
            visible = visible,
            enabled = enabled,
            clickable = clickable,
            bounds = bounds,
            score = score
        )
    }

    private fun calculateScore(
        node: AccessibilityNodeInfo,
        hint: String?,
        description: String?,
        viewId: String?,
        inputType: Int,
        visible: Boolean,
        enabled: Boolean,
        focused: Boolean,
        clickable: Boolean,
        bounds: Rect
    ): Int {
        var score = 0
        if (visible) score += 40
        if (enabled) score += 30
        if (focused) score += 35
        if (clickable) score += 10
        if (bounds.width() > 0 && bounds.height() > 0) score += 10
        if (containsSearchKeyword(hint)) score += 60
        if (containsSearchKeyword(description)) score += 45
        if (containsSearchKeyword(viewId)) score += 50
        if (isTextInput(inputType)) score += 25
        if (isPasswordInput(inputType)) score -= 100
        if (isPhoneInput(inputType)) score -= 60
        if (isNumberInput(inputType)) score -= 50
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true) score += 15
        return score
    }

    private fun readHint(node: AccessibilityNodeInfo): String? {
        val keys = listOf(
            "android.view.accessibility.AccessibilityNodeInfo.hintText",
            "androidx.view.accessibility.AccessibilityNodeInfoCompat.HINT_TEXT"
        )
        for (key in keys) {
            val value = node.extras.getCharSequence(key)?.toString()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun containsSearchKeyword(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = value.lowercase().replace('_', ' ').replace('-', ' ')
        return listOf(
            "search", "find", "query", "lookup", "search box", "search field",
            "search here", "type to search", "enter search"
        ).any { normalized.contains(it) }
    }

    private fun isTextInput(inputType: Int): Boolean =
        (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT

    private fun isPasswordInput(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    private fun isPhoneInput(inputType: Int): Boolean =
        (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE

    private fun isNumberInput(inputType: Int): Boolean =
        (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER

    private fun failure(reason: String) =
        DetectionResult(false, null, emptyList(), reason)
}
