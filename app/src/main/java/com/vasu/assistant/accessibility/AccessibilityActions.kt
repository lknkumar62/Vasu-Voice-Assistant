package com.vasu.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.assistant.core.automation.ActionResult

/**
 * AccessibilityActions - Executes actions on accessibility nodes.
 *
 * Provides methods to click, type, scroll, and interact with UI elements.
 */
class AccessibilityActions(
    private val service: AccessibilityService
) {
    private val nodeFinder = AccessibilityNodeFinder()

    /**
     * Click on a node
     */
    fun click(node: AccessibilityNodeInfo): ActionResult {
        return try {
            if (node.isClickable) {
                val performed = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (performed) {
                    ActionResult.success("click", "Clicked on element")
                } else {
                    ActionResult.error("click", "Click action rejected by system", "ACTION_FAILED")
                }
            } else {
                // Try to find parent that is clickable
                val parent = node.parent
                if (parent != null && parent.isClickable) {
                    val performed = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (performed) {
                        ActionResult.success("click", "Clicked on parent element")
                    } else {
                        ActionResult.error("click", "Parent click action rejected", "ACTION_FAILED")
                    }
                } else {
                    ActionResult.error("click", "Element is not clickable", "ACTION_FAILED")
                }
            }
        } catch (e: Exception) {
            ActionResult.error("click", "Click failed: ${e.message}", "ACTION_FAILED")
        }
    }

    /**
     * Click by text
     */
    fun clickByText(text: String): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.error("click_text", "Active window is unavailable. Accessibility service may be interrupted.", "SERVICE_DISABLED")

        val node = nodeFinder.findNodeContainingText(root, text)
            ?: return ActionResult.error("click_text", "Element containing \"$text\" not found on current screen", "NODE_NOT_FOUND")

        return click(node)
    }

    /**
     * Click by content description
     */
    fun clickByDescription(description: String): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.error("click_desc", "Active window is unavailable. Accessibility service may be interrupted.", "SERVICE_DISABLED")

        val node = nodeFinder.findByDescription(root, description)
            ?: return ActionResult.error("click_desc", "Element with description \"$description\" not found", "NODE_NOT_FOUND")

        return click(node)
    }

    /**
     * Long click on a node
     */
    fun longClick(node: AccessibilityNodeInfo): ActionResult {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ActionResult.success("long_click", "Long clicked on element")
        } catch (e: Exception) {
            ActionResult.error("long_click", "Long click failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Type text into an editable field
     */
    fun typeText(node: AccessibilityNodeInfo, text: String): ActionResult {
        return try {
            if (!node.isEditable) {
                return ActionResult.error("type", "Element not editable", "Node is not editable")
            }

            // Focus the node first
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            // Clear existing text
            val clearArgs = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    ""
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)

            // Type new text
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            ActionResult.success("type", "Typed: $text")
        } catch (e: Exception) {
            ActionResult.error("type", "Type failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Type text by field label
     */
    fun typeTextByLabel(label: String, text: String): ActionResult {
        // Find editable field near the label
        val labelNode = nodeFinder.findNodeContainingText(service.rootInActiveWindow, label)
            ?: return ActionResult.error("type_label", "Label not found: $label", "Label node not found")

        // Find parent or sibling editable field
        val editableFields = nodeFinder.findEditableFields(service.rootInActiveWindow)
        if (editableFields.isEmpty()) {
            return ActionResult.error("type_label", "No editable fields found", "No editable fields")
        }

        // Try to find the closest editable field to the label
        val labelBounds = android.graphics.Rect()
        labelNode.getBoundsInScreen(labelBounds)

        val closestField = editableFields.minByOrNull { field ->
            val fieldBounds = android.graphics.Rect()
            field.getBoundsInScreen(fieldBounds)
            kotlin.math.abs(fieldBounds.centerY() - labelBounds.centerY())
        } ?: editableFields.first()

        return typeText(closestField, text)
    }

    /**
     * Scroll down
     */
    fun scrollDown(node: AccessibilityNodeInfo? = null): ActionResult {
        val scrollNode = node ?: service.rootInActiveWindow
            ?: return ActionResult.error("scroll", "No scrollable node found", "Root not available")

        return try {
            scrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            ActionResult.success("scroll_down", "Scrolled down")
        } catch (e: Exception) {
            ActionResult.error("scroll_down", "Scroll failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Scroll up
     */
    fun scrollUp(node: AccessibilityNodeInfo? = null): ActionResult {
        val scrollNode = node ?: service.rootInActiveWindow
            ?: return ActionResult.error("scroll", "No scrollable node found", "Root not available")

        return try {
            scrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            ActionResult.success("scroll_up", "Scrolled up")
        } catch (e: Exception) {
            ActionResult.error("scroll_up", "Scroll failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Go back
     */
    fun pressBack(): ActionResult {
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionResult.success("back", "Pressed back")
        } catch (e: Exception) {
            ActionResult.error("back", "Back failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Go home
     */
    fun pressHome(): ActionResult {
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ActionResult.success("home", "Pressed home")
        } catch (e: Exception) {
            ActionResult.error("home", "Home failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Open recent apps
     */
    fun pressRecents(): ActionResult {
        return try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            ActionResult.success("recents", "Opened recent apps")
        } catch (e: Exception) {
            ActionResult.error("recents", "Recents failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Tap at coordinates
     */
    fun tapAt(x: Float, y: Float): ActionResult {
        return try {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            service.dispatchGesture(gesture, null, null)
            ActionResult.success("tap", "Tapped at ($x, $y)")
        } catch (e: Exception) {
            ActionResult.error("tap", "Tap failed", e.message ?: "Unknown error")
        }
    }

    /**
     * Read current screen
     */
    fun readScreen(): ActionResult {
        val rootNode = service.rootInActiveWindow
            ?: return ActionResult.error("read", "Cannot read screen", "Root not available")

        val nodes = mutableListOf<String>()
        readNodeText(rootNode, nodes, 0)

        val screenContent = nodes.joinToString("\n")
        return ActionResult.success("read", screenContent)
    }

    private fun readNodeText(node: AccessibilityNodeInfo, result: MutableList<String>, depth: Int) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (!text.isNullOrBlank() || !desc.isNullOrBlank()) {
            val prefix = "  ".repeat(depth)
            val nodeText = text ?: desc ?: ""
            result.add("$prefix- $nodeText")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            readNodeText(child, result, depth + 1)
        }
    }
}
