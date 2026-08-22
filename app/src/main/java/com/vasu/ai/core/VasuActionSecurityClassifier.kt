package com.vasu.ai.core

class VasuActionSecurityClassifier {

    fun classify(action: VasuAction): VasuSecurityActionType {
        return when (action) {
            is VasuAction.CallContact ->
                VasuSecurityActionType.PHONE_CALL

            is VasuAction.SendSms ->
                VasuSecurityActionType.SEND_SMS

            else ->
                VasuSecurityActionType.NORMAL
        }
    }
}
