package com.vasu.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * ScreenReader - Reads and interprets screen content.
 *
 * Provides structured information about what's on screen
 * for AI processing and voice commands.
 */
class ScreenReader {
    private val nodeFinder = AccessibilityNodeFinder()

    /**
     * Read screen content as structured text
     */
    fun readScreen(root: AccessibilityNodeInfo?): ScreenContent {
        if (root == null) return ScreenContent.empty()

        val elements = mutableListOf<ScreenElement>()
        readElements(root, elements, 0)

        return ScreenContent(
            elements = elements,
            summary = generateSummary(elements)
        )
    }

    /**
     * Find interactive elements on screen
     */
    fun findInteractiveElements(root: AccessibilityNodeInfo?): List<ScreenElement> {
        if (root == null) return emptyList()

        val elements = mutableListOf<ScreenElement>()
        findInteractiveRecursive(root, elements)
        return elements
    }

    /**
     * Get element at specific coordinates
     */
    fun getElementAt(root: AccessibilityNodeInfo?, x: Float, y: Float): ScreenElement? {
        if (root == null) return null

        val bounds = android.graphics.Rect()
        root.getBoundsInScreen(bounds)

        if (bounds.contains(x.toInt(), y.toInt())) {
            return ScreenElement(
                text = root.text?.toString() ?: "",
                description = root.contentDescription?.toString() ?: "",
                className = root.className?.toString() ?: "",
                isClickable = root.isClickable,
                isEditable = root.isEditable,
                bounds = bounds,
                depth = 0
            )
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = getElementAt(child, x, y)
            if (result != null) return result
        }

        return null
    }

    /**
     * Generate voice description of screen
     */
    fun describeScreen(root: AccessibilityNodeInfo?): String {
        val content = readScreen(root)
        return content.summary
    }

    private fun readElements(node: AccessibilityNodeInfo, result: MutableList<ScreenElement>, depth: Int) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isEditable) {
            result.add(
                ScreenElement(
                    text = text,
                    description = desc,
                    className = node.className?.toString() ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = bounds,
                    depth = depth
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            readElements(child, result, depth + 1)
        }
    }

    private fun findInteractiveRecursive(node: AccessibilityNodeInfo, result: MutableList<ScreenElement>) {
        if (node.isClickable || node.isEditable) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)

            result.add(
                ScreenElement(
                    text = node.text?.toString() ?: "",
                    description = node.contentDescription?.toString() ?: "",
                    className = node.className?.toString() ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    bounds = bounds,
                    depth = 0
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findInteractiveRecursive(child, result)
        }
    }

    private fun generateSummary(elements: List<ScreenElement>): String {
        if (elements.isEmpty()) return "Screen is empty"

        val clickableCount = elements.count { it.isClickable }
        val editableCount = elements.count { it.isEditable }
        val textElements = elements.filter { it.text.isNotBlank() }

        val summary = StringBuilder()
        summary.append("Screen has ${elements.size} elements. ")

        if (clickableCount > 0) {
            summary.append("$clickableCount clickable. ")
        }
        if (editableCount > 0) {
            summary.append("$editableCount text fields. ")
        }

        if (textElements.isNotEmpty()) {
            summary.append("Text: ")
            summary.append(textElements.take(5).joinToString(", ") { "\"${it.text}\"" })
            if (textElements.size > 5) {
                summary.append(" and ${textElements.size - 5} more")
            }
        }

        return summary.toString()
    }
}

/**
 * Screen content data
 */
data class ScreenContent(
    val elements: List<ScreenElement>,
    val summary: String
) {
    companion object {
        fun empty() = ScreenContent(emptyList(), "Screen is empty")
    }
}

/**
 * Screen element data
 */
data class ScreenElement(
    val text: String,
    val description: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: android.graphics.Rect,
    val depth: Int
) {
    val displayText: String
        get() = text.ifBlank { description }.ifBlank { className.substringAfterLast(".") }
}
