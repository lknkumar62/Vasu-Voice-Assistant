package com.vasu.assistant.core.error

enum class AiErrorKind(val userMessage: String, val hinglishMessage: String) {
    NETWORK_ERROR("Network error. Please check your connection.", "Network mein dikkat hai"),
    API_RATE_LIMIT("Too many requests. Please try again later.", "Bahut saare requests ho gaye"),
    INVALID_API_KEY("Invalid API key. Please check Settings.", "API key sahi nahi hai"),
    MODEL_NOT_FOUND("Model not available. Please try again.", "Model nahi mila"),
    PARSING_ERROR("Error parsing response. Please try again.", "Response parse nahi ho saka"),
    TIMEOUT("Request timed out. Please try again.", "Bahut time lag gaya"),
    UNKNOWN_ERROR("An error occurred. Please try again.", "Kuch problem ho gaya"),
    NO_INTERNET("No internet connection. Device commands will work.", "Internet nahi hai"),
    INVALID_REQUEST("Invalid request. Please try again.", "Request galat hai"),
    INSUFFICIENT_QUOTA("API quota exceeded. Please try later.", "Quota khatum ho gaya"),
    SERVICE_UNAVAILABLE("Service unavailable. Please try again.", "Service nahi chal rahi"),
    AUTHENTICATION_FAILED("Authentication failed. Please check API key.", "Authentication fail ho gaya"),
    GEMINI_ERROR("Gemini service error. Please try again.", "Gemini mein problem hai");

    companion object {
        fun fromException(exception: Exception): AiErrorKind {
            return when {
                exception.message?.contains("network", ignoreCase = true) == true -> NETWORK_ERROR
                exception.message?.contains("timeout", ignoreCase = true) == true -> TIMEOUT
                exception.message?.contains("401", ignoreCase = true) == true -> INVALID_API_KEY
                exception.message?.contains("429", ignoreCase = true) == true -> API_RATE_LIMIT
                exception.message?.contains("404", ignoreCase = true) == true -> MODEL_NOT_FOUND
                exception.message?.contains("503", ignoreCase = true) == true -> SERVICE_UNAVAILABLE
                else -> UNKNOWN_ERROR
            }
        }
    }
}

enum class SttErrorKind(val userMessage: String, val hinglishMessage: String) {
    NO_MATCH("No speech detected. Please try again.", "Kuch sun nahi saka"),
    NETWORK_ERROR("Network error during speech recognition.", "Network mein problem"),
    AUDIO_ERROR("Microphone error. Please check permissions.", "Microphone mein problem"),
    SERVER_ERROR("Server error. Please try again.", "Server mein problem"),
    CLIENT_ERROR("Client error. Please try again.", "App mein problem"),
    SPEECH_TIMEOUT("No speech within timeout. Please try again.", "Kuch nahi bola"),
    INSUFFICIENT_PERMISSIONS("Microphone permission denied.", "Microphone permission nahi hai"),
    RECOGNIZER_BUSY("Speech recognizer is busy. Please try again.", "Recognizer busy hai"),
    LANGUAGE_NOT_SUPPORTED("Language not supported.", "Language support nahi hai"),
    UNKNOWN_ERROR("Speech recognition error. Please try again.", "STT mein problem");

    companion object {
        fun fromErrorCode(errorCode: Int): SttErrorKind {
            return when (errorCode) {
                1 -> NETWORK_ERROR
                2 -> NO_MATCH
                3 -> SPEECH_TIMEOUT
                4 -> NO_MATCH
                5 -> CLIENT_ERROR
                6 -> SERVER_ERROR
                7 -> NETWORK_ERROR
                8 -> AUDIO_ERROR
                9 -> SERVER_ERROR
                else -> UNKNOWN_ERROR
            }
        }
    }
}

sealed class ActionResult<T> {
    data class Success<T>(val data: T) : ActionResult<T>()
    data class Error<T>(val error: String, val kind: AiErrorKind? = null) : ActionResult<T>()
    class Loading<T> : ActionResult<T>()
}
