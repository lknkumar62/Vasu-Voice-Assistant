package com.vasu.assistant.core.offline

/**
 * FIX-8 Pass B: download catalogue for offline (Vosk) speech models, plus the
 * pure helpers the unpacker uses. Kotlin-only (no `android.*`) so the language
 * mapping and zip-path safety are unit-testable on the JVM.
 */
data class VoskModelSpec(
    /** Stable id; also the on-disk folder name under `files/vosk/`. */
    val id: String,
    val label: String,
    /** Settings language family this model serves ("en…" vs Hindi default). */
    val language: String,
    /** Approximate download size shown in Settings. */
    val sizeMb: Int,
    val url: String,
    /** Top-level folder inside the zip; stripped during unpack. */
    val zipRoot: String
)

object VoskCatalog {

    val MODELS: List<VoskModelSpec> = listOf(
        VoskModelSpec(
            id = "en-us-small",
            label = "English (US) — small",
            language = "en-US",
            sizeMb = 40,
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            zipRoot = "vosk-model-small-en-us-0.15"
        ),
        VoskModelSpec(
            id = "hi-small",
            label = "Hindi — small",
            language = "hi-IN",
            sizeMb = 42,
            url = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            zipRoot = "vosk-model-small-hi-0.22"
        )
    )

    /** English-tagged settings languages use the EN model; everything else the HI one. */
    fun modelForLanguage(languageTag: String): VoskModelSpec =
        if (languageTag.trim().lowercase().startsWith("en")) MODELS[0] else MODELS[1]

    /**
     * Maps a zip entry to its path relative to the model root folder, or null
     * when the entry lies outside [root] / is unsafe. Pure + total so the
     * unpacker can reject zip-slip shapes ("../evil", absolute paths, sibling
     * top-level folders, "." hops) before touching disk.
     */
    fun relativeEntryPath(entryName: String, root: String): String? {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        val prefix = "$root/"
        if (!normalized.startsWith(prefix)) return null
        val relative = normalized.removePrefix(prefix)
        if (relative.isEmpty()) return null
        if (relative.split('/').any { it == ".." || it == "." }) return null
        return relative
    }
}
