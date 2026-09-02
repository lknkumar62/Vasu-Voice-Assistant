package com.vasu.assistant.avatar

import javax.inject.Inject
import javax.inject.Singleton

data class Persona(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val voiceId: String = "default",
    val avatarEnabled: Boolean = false,
    val responseStyle: String = "friendly"
)

@Singleton
class AlternatePersonaAvatar @Inject constructor(
    private val avatarManager: AvatarManager
) {
    private var currentPersona: Persona = defaultPersona()
    private val customPersonas = mutableListOf<Persona>()

    fun getCurrentPersona(): Persona = currentPersona

    fun setPersona(personaId: String): Boolean {
        val found = customPersonas.find { it.id == personaId }
        if (found != null) {
            currentPersona = found
            return true
        }
        if (personaId == "default") {
            currentPersona = defaultPersona()
            return true
        }
        return false
    }

    fun createPersona(name: String, systemPrompt: String, voiceId: String = "default"): Persona {
        val persona = Persona(
            id = "persona_${System.currentTimeMillis()}",
            name = name,
            systemPrompt = systemPrompt,
            voiceId = voiceId
        )
        customPersonas.add(persona)
        return persona
    }

    fun deletePersona(personaId: String): Boolean {
        return customPersonas.removeAll { it.id == personaId }
    }

    fun getPersonas(): List<Persona> = listOf(defaultPersona()) + customPersonas

    private fun defaultPersona() = Persona(
        id = "default",
        name = "VASU",
        systemPrompt = "तुम VASU हो। सामान्य बातचीत में हमेशा स्वाभाविक, बोलचाल की हिंदी देवनागरी लिपि में उत्तर दो। उत्तर संक्षिप्त और स्पष्ट रखो।",
        responseStyle = "friendly"
    )
}
