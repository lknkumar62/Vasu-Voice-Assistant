package com.vasu.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityNodeFinder - Finds and filters accessibility nodes.
 *
 * Provides utility methods to find nodes by text, description,
 * view ID, or other properties.
 */
class AccessibilityNodeFinder {

    /**
     * Find node by text content
     */
    fun findByText(root: AccessibilityNodeInfo?, text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        if (root == null) return null

        val nodes = if (exact) {
            root.findAccessibilityNodeInfosByText(text)
        } else {
            root.findAccessibilityNodeInfosByText(text)
        }

        return nodes?.firstOrNull()
    }

    /**
     * Find all nodes matching text
     */
    fun findAllByText(root: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return root.findAccessibilityNodeInfosByText(text)?.toList() ?: emptyList()
    }

    /**
     * Find node by content description
     */
    fun findByDescription(root: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
        if (root == null) return null

        if (root.contentDescription?.toString()?.contains(description, ignoreCase = true) == true) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findByDescription(child, description)
            if (result != null) return result
        }

        return null
    }

    /**
     * Find node by view ID
     */
    fun findByViewId(root: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
    }

    /**
     * Find clickable nodes
     */
    fun findClickableNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()

        val result = mutableListOf<AccessibilityNodeInfo>()
        findClickableRecursive(root, result)
        return result
    }

    /**
     * Find editable text fields
     */
    fun findEditableFields(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()

        val result = mutableListOf<AccessibilityNodeInfo>()
        findEditableRecursive(root, result)
        return result
    }

    /**
     * Find scrollable nodes
     */
    fun findScrollableNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()

        val result = mutableListOf<AccessibilityNodeInfo>()
        findScrollableRecursive(root, result)
        return result
    }

    /**
     * Find node containing specific text (for buttons, links, etc.)
     */
    fun findNodeContainingText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val nodeText = root.text?.toString() ?: ""
        val contentDesc = root.contentDescription?.toString() ?: ""

        if (nodeText.contains(text, ignoreCase = true) ||
            contentDesc.contains(text, ignoreCase = true)
        ) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeContainingText(child, text)
            if (result != null) return result
        }

        return null
    }

    /**
     * Get node info summary
     */
    fun getNodeInfo(node: AccessibilityNodeInfo): Map<String, Any> {
        return mapOf(
            "className" to (node.className?.toString() ?: ""),
            "text" to (node.text?.toString() ?: ""),
            "contentDescription" to (node.contentDescription?.toString() ?: ""),
            "viewId" to (node.viewIdResourceName ?: ""),
            "isClickable" to node.isClickable,
            "isEditable" to node.isEditable,
            "isEnabled" to node.isEnabled,
            "isScrollable" to node.isScrollable,
            "isVisibleToUser" to node.isVisibleToUser,
            "bounds" to "${node.boundsInScreen}"
        )
    }

    private fun findClickableRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableRecursive(child, result)
        }
    }

    private fun findEditableRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableRecursive(child, result)
        }
    }

    private fun findScrollableRecursive(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (node.isScrollable) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollableRecursive(child, result)
        }
    }
}
