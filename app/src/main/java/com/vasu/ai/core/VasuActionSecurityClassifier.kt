package com.vasu.ai.core

class VasuActionSecurityClassifier {

    fun classify(action: VasuAction): VasuSecurityActionType {
        val name = action.toString().lowercase()

        return when {
            name.contains("sms") ||
                name.contains("message") ->
                VasuSecurityActionType.SEND_SMS

            name.contains("call") ||
                name.contains("phone") ->
                VasuSecurityActionType.PHONE_CALL

            name.contains("contact") ->
                VasuSecurityActionType.CONTACT_CHANGE

            name.contains("setting") ->
                VasuSecurityActionType.SYSTEM_SETTING

            name.contains("external") ||
                name.contains("share") ->
                VasuSecurityActionType.EXTERNAL_ACTION

            else ->
                VasuSecurityActionType.NORMAL
        }
    }
}
