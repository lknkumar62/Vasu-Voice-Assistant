package com.vasu.ai.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService

/**
 * Resolves the best matching accessibility node when multiple elements match the same target.
 * Resolution is deterministic and does not execute actions.
 */
class VasuElementResolver {
    enum class TargetType { TEXT, CONTENT_DESCRIPTION, VIEW_ID }

    data class Candidate(
        val node: AccessibilityNodeInfo,
        val text: String?,
        val contentDescription: String?,
        val viewId: String?,
        val className: String?,
        val clickable: Boolean,
        val enabled: Boolean,
        val visible: Boolean,
        val bounds: Rect,
        val score: Int,
        val depth: Int
    )

    data class ResolutionResult(
        val found: Boolean,
        val candidate: Candidate?,
        val candidates: List<Candidate>,
        val reason: String
    )

    fun resolveText(text: String): ResolutionResult {
        val service = VasuAccessibilityService.instance
            ?: return failure("accessibility_service_unavailable")
        val root = service.root()
            ?: return failure("accessibility_root_unavailable")
        val candidates = mutableListOf<Candidate>()
        collectTextCandidates(root, text, 0, candidates)
        return selectBest(candidates, text, TargetType.TEXT)
    }

    fun resolveContentDescription(description: String): ResolutionResult {
        val service = VasuAccessibilityService.instance
            ?: return failure("accessibility_service_unavailable")
        val root = service.root()
            ?: return failure("accessibility_root_unavailable")
        val candidates = mutableListOf<Candidate>()
        collectDescriptionCandidates(root, description, 0, candidates)
        return selectBest(candidates, description, TargetType.CONTENT_DESCRIPTION)
    }

    fun resolveViewId(viewId: String): ResolutionResult {
        val service = VasuAccessibilityService.instance
            ?: return failure("accessibility_service_unavailable")
        val root = service.root()
            ?: return failure("accessibility_root_unavailable")
        val candidates = mutableListOf<Candidate>()
        collectViewIdCandidates(root, viewId, 0, candidates)
        return selectBest(candidates, viewId, TargetType.VIEW_ID)
    }

    private fun collectTextCandidates(
        node: AccessibilityNodeInfo,
        expectedText: String,
        depth: Int,
        candidates: MutableList<Candidate>
    ) {
        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrBlank() && nodeText.equals(expectedText, ignoreCase = true)) {
            candidates += buildCandidate(node, depth)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectTextCandidates(child, expectedText, depth + 1, candidates)
            child.recycle()
        }
    }

    private fun collectDescriptionCandidates(
        node: AccessibilityNodeInfo,
        expectedDescription: String,
        depth: Int,
        candidates: MutableList<Candidate>
    ) {
        val description = node.contentDescription?.toString()
        if (!description.isNullOrBlank() && description.equals(expectedDescription, ignoreCase = true)) {
            candidates += buildCandidate(node, depth)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectDescriptionCandidates(child, expectedDescription, depth + 1, candidates)
            child.recycle()
        }
    }

    private fun collectViewIdCandidates(
        node: AccessibilityNodeInfo,
        expectedViewId: String,
        depth: Int,
        candidates: MutableList<Candidate>
    ) {
        val actualId = node.viewIdResourceName
        if (!actualId.isNullOrBlank() && (
            actualId == expectedViewId ||
                actualId.endsWith(":id/$expectedViewId") ||
                actualId.endsWith("/$expectedViewId")
            )) {
            candidates += buildCandidate(node, depth)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectViewIdCandidates(child, expectedViewId, depth + 1, candidates)
            child.recycle()
        }
    }

    private fun buildCandidate(node: AccessibilityNodeInfo, depth: Int): Candidate {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val visible = node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0
        val clickable = node.isClickable || hasClickableParent(node)
        val enabled = node.isEnabled
        val className = node.className?.toString()
        val score = calculateScore(node, visible, clickable, enabled, bounds, depth)
        return Candidate(
            node = AccessibilityNodeInfo.obtain(node),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            className = className,
            clickable = clickable,
            enabled = enabled,
            visible = visible,
            bounds = bounds,
            score = score,
            depth = depth
        )
    }

    private fun calculateScore(
        node: AccessibilityNodeInfo,
        visible: Boolean,
        clickable: Boolean,
        enabled: Boolean,
        bounds: Rect,
        depth: Int
    ): Int {
        var score = 0
        if (visible) score += 40
        if (enabled) score += 30
        if (clickable) score += 50
        if (bounds.width() > 0 && bounds.height() > 0) score += 10
        when (node.className?.toString()) {
            "android.widget.Button", "android.widget.ImageButton" -> score += 20
            "android.widget.EditText" -> score += 15
            "android.widget.TextView" -> score += 5
        }
        score += depth.coerceAtMost(10)
        return score
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable && parent.isEnabled && parent.isVisibleToUser) {
                parent.recycle()
                return true
            }
            val next = parent.parent
            parent.recycle()
            parent = next
            depth++
        }
        return false
    }

    private fun selectBest(
        candidates: List<Candidate>,
        query: String,
        targetType: TargetType
    ): ResolutionResult {
        if (candidates.isEmpty()) return failure("no_matching_${targetType.name.lowercase()}_candidate")
        val sorted = candidates.filter { it.enabled && it.visible }.sortedByDescending { it.score }
        if (sorted.isEmpty()) {
            candidates.forEach { it.node.recycle() }
            return ResolutionResult(false, null, candidates, "matching_candidates_not_interactable")
        }
        val best = sorted.first()
        println(
            "VASU_ELEMENT_RESOLUTION " +
                "type=$targetType query=$query candidates=${candidates.size} " +
                "selectedScore=${best.score} class=${best.className} " +
                "clickable=${best.clickable} enabled=${best.enabled} bounds=${best.bounds}"
        )
        return ResolutionResult(
            true,
            best,
            sorted,
            if (candidates.size == 1) "single_matching_candidate" else "best_candidate_selected_from_${candidates.size}"
        )
    }

    private fun failure(reason: String) = ResolutionResult(false, null, emptyList(), reason)
}
