package com.vasu.assistant.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityNodeFinder {

    companion object {
        private const val TAG = "AccessibilityNodeFinder"
    }

    fun findNodeContainingText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lowerText = text.lowercase()

        val exactNodes = root.findAccessibilityNodeInfosByText(text)
        if (!exactNodes.isNullOrEmpty()) return exactNodes[0]

        val descNodes = findByDescription(root, text)
        if (descNodes != null) return descNodes

        return findNodeRecursive(root, lowerText)
    }

    fun findByDescription(root: AccessibilityNodeInfo?, description: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lowerDesc = description.lowercase()

        if (root.contentDescription?.toString()?.lowercase()?.contains(lowerDesc) == true) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findByDescription(child, description)
            if (found != null) return found
        }
        return null
    }

    fun findEditableFields(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val editableFields = mutableListOf<AccessibilityNodeInfo>()
        findEditableFieldsRecursive(root, editableFields)
        return editableFields
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        if (nodeText.contains(text)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeRecursive(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findEditableFieldsRecursive(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isEditable) results.add(node)
        for (i in 0 until node.childCount) {
            findEditableFieldsRecursive(node.getChild(i), results)
        }
    }
}
