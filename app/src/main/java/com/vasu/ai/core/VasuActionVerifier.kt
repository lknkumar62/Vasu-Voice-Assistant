package com.vasu.ai.core

import android.view.accessibility.AccessibilityNodeInfo
import com.vasu.ai.accessibility.VasuAccessibilityService

/** Verifies observable results of executed VASU actions. */
class VasuActionVerifier(
    private val screenDetector: VasuScreenTransitionDetector = VasuScreenTransitionDetector()
) {
    enum class Status { VERIFIED, NOT_VERIFIED, UNKNOWN }

    data class VerificationResult(
        val status: Status,
        val reason: String
    )

    fun verify(
        action: VasuAction,
        before: VasuScreenTransitionDetector.ScreenSnapshot?,
        after: VasuScreenTransitionDetector.ScreenSnapshot?
    ): VerificationResult = when (action) {
        is VasuAction.OpenApp -> verifyOpenApp(action, after)
        is VasuAction.TypeText -> verifyTypedText(action)
        VasuAction.ClearText -> verifyClearText()
        is VasuAction.ClickText -> verifyClickText(action.text, before, after)
        is VasuAction.LongClickText -> verifyClickText(action.text, before, after)
        is VasuAction.ClickDescription -> verifyClickDescription(action.description, before, after)
        is VasuAction.ClickViewId -> verifyClickViewId(action.viewId, before, after)
        is VasuAction.Scroll, is VasuAction.Swipe, VasuAction.PressEnter,
        VasuAction.Back, VasuAction.Home, VasuAction.Recents, VasuAction.Notifications,
        VasuAction.LockScreen,
        VasuAction.OpenWifiSettings, VasuAction.OpenBluetoothSettings,
        VasuAction.OpenBrightnessSettings, VasuAction.OpenDndSettings,
        VasuAction.OpenAirplaneModeSettings, VasuAction.OpenBatterySaverSettings,
        VasuAction.OpenLocationSettings -> verifyTransition(action, before, after)
        is VasuAction.Wait -> VerificationResult(Status.VERIFIED, "wait_completed")
        VasuAction.TakeScreenshot -> VerificationResult(Status.UNKNOWN, "screenshot_result_not_observable_from_accessibility_tree")
        is VasuAction.Volume -> VerificationResult(Status.UNKNOWN, "volume_state_not_reliably_observable_from_accessibility_tree")
        is VasuAction.Flashlight -> VerificationResult(Status.UNKNOWN, "flashlight_state_not_reliably_observable_from_accessibility_tree")
        VasuAction.Mute -> VerificationResult(Status.UNKNOWN, "mute_state_not_reliably_observable_from_accessibility_tree")
        is VasuAction.CallContact -> VerificationResult(Status.UNKNOWN, "call_side_effect_requires_device_level_verification")
        is VasuAction.SendSms -> VerificationResult(Status.UNKNOWN, "sms_side_effect_requires_message_state_verification")
    }

    private fun verifyOpenApp(action: VasuAction.OpenApp, after: VasuScreenTransitionDetector.ScreenSnapshot?): VerificationResult {
        val packageName = after?.packageName
        return if (packageName == action.packageName) {
            VerificationResult(Status.VERIFIED, "foreground_package_matches_expected_package")
        } else {
            VerificationResult(Status.NOT_VERIFIED, "expected=${action.packageName}, actual=$packageName")
        }
    }

    private fun verifyTypedText(action: VasuAction.TypeText): VerificationResult {
        val service = VasuAccessibilityService.instance ?: return VerificationResult(Status.UNKNOWN, "accessibility_service_unavailable")
        val root = service.root() ?: return VerificationResult(Status.UNKNOWN, "accessibility_root_unavailable")
        val editable = service.focusedEditable() ?: findEditable(root)
            ?: return VerificationResult(Status.NOT_VERIFIED, "no_editable_field_found_after_typing")
        if (editable.isPassword) return VerificationResult(Status.UNKNOWN, "password_field_cannot_be_verified_safely")
        val actualText = editable.text?.toString().orEmpty()
        return if (actualText == action.text) {
            VerificationResult(Status.VERIFIED, "editable_text_matches_requested_text")
        } else {
            VerificationResult(Status.NOT_VERIFIED, "expected_text_does_not_match_actual_text")
        }
    }

    private fun verifyClearText(): VerificationResult {
        val service = VasuAccessibilityService.instance ?: return VerificationResult(Status.UNKNOWN, "accessibility_service_unavailable")
        val root = service.root() ?: return VerificationResult(Status.UNKNOWN, "accessibility_root_unavailable")
        val editable = service.focusedEditable() ?: findEditable(root)
            ?: return VerificationResult(Status.NOT_VERIFIED, "no_editable_field_found_after_clear")
        if (editable.isPassword) return VerificationResult(Status.UNKNOWN, "password_field_cannot_be_verified_safely")
        val actualText = editable.text?.toString().orEmpty()
        return if (actualText.isEmpty()) {
            VerificationResult(Status.VERIFIED, "editable_text_is_empty")
        } else {
            VerificationResult(Status.NOT_VERIFIED, "editable_text_still_contains_content")
        }
    }

    private fun verifyClickText(text: String, before: VasuScreenTransitionDetector.ScreenSnapshot?, after: VasuScreenTransitionDetector.ScreenSnapshot?): VerificationResult {
        if (screenDetector.hasChanged(before, after)) return VerificationResult(Status.VERIFIED, "screen_changed_after_text_click")
        val service = VasuAccessibilityService.instance ?: return VerificationResult(Status.UNKNOWN, "accessibility_service_unavailable")
        val root = service.root() ?: return VerificationResult(Status.UNKNOWN, "accessibility_root_unavailable")
        val stillPresent = findText(root, text) != null
        return if (!stillPresent) VerificationResult(Status.VERIFIED, "clicked_text_target_no_longer_present")
        else VerificationResult(Status.UNKNOWN, "target_still_present_and_no_screen_transition_observed")
    }

    private fun verifyClickDescription(description: String, before: VasuScreenTransitionDetector.ScreenSnapshot?, after: VasuScreenTransitionDetector.ScreenSnapshot?): VerificationResult {
        if (screenDetector.hasChanged(before, after)) return VerificationResult(Status.VERIFIED, "screen_changed_after_description_click")
        val service = VasuAccessibilityService.instance ?: return VerificationResult(Status.UNKNOWN, "accessibility_service_unavailable")
        val root = service.root() ?: return VerificationResult(Status.UNKNOWN, "accessibility_root_unavailable")
        val stillPresent = findContentDescription(root, description) != null || findText(root, description) != null
        return if (!stillPresent) VerificationResult(Status.VERIFIED, "clicked_description_target_no_longer_present")
        else VerificationResult(Status.UNKNOWN, "description_target_still_present_and_screen_unchanged")
    }

    private fun verifyClickViewId(viewId: String, before: VasuScreenTransitionDetector.ScreenSnapshot?, after: VasuScreenTransitionDetector.ScreenSnapshot?): VerificationResult {
        if (screenDetector.hasChanged(before, after)) return VerificationResult(Status.VERIFIED, "screen_changed_after_view_id_click")
        val service = VasuAccessibilityService.instance ?: return VerificationResult(Status.UNKNOWN, "accessibility_service_unavailable")
        val root = service.root() ?: return VerificationResult(Status.UNKNOWN, "accessibility_root_unavailable")
        val stillPresent = findViewId(root, viewId) != null
        return if (!stillPresent) VerificationResult(Status.VERIFIED, "clicked_view_id_target_no_longer_present")
        else VerificationResult(Status.UNKNOWN, "view_id_target_still_present_and_screen_unchanged")
    }

    private fun verifyTransition(action: VasuAction, before: VasuScreenTransitionDetector.ScreenSnapshot?, after: VasuScreenTransitionDetector.ScreenSnapshot?): VerificationResult =
        if (screenDetector.hasChanged(before, after)) VerificationResult(Status.VERIFIED, "observable_screen_transition_detected")
        else VerificationResult(Status.NOT_VERIFIED, "expected_screen_transition_not_observed")

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled && node.isVisibleToUser && !node.isPassword) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val result = findEditable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findText(root: AccessibilityNodeInfo, expectedText: String): AccessibilityNodeInfo? {
        val actual = root.text?.toString()
        if (actual != null && actual.equals(expectedText, ignoreCase = true)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val result = findText(child, expectedText)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findContentDescription(root: AccessibilityNodeInfo, expectedDescription: String): AccessibilityNodeInfo? {
        val actual = root.contentDescription?.toString()
        if (actual != null && actual.equals(expectedDescription, ignoreCase = true)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val result = findContentDescription(child, expectedDescription)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findViewId(root: AccessibilityNodeInfo, expectedViewId: String): AccessibilityNodeInfo? {
        val actual = root.viewIdResourceName
        if (actual != null && (actual == expectedViewId || actual.endsWith(":id/$expectedViewId") || actual.endsWith("/$expectedViewId"))) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val result = findViewId(child, expectedViewId)
            if (result != null) return result
            child.recycle()
        }
        return null
    }
}
