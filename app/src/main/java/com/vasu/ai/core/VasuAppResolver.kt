package com.vasu.ai.core

import android.content.Context

class VasuAppResolver(context: Context) {
    private val packageManager = context.packageManager
    private val appDiscovery = VasuInstalledAppDiscovery(context)

    private val aliases = mapOf(
        "whatsapp" to listOf("com.whatsapp"),
        "whatsapp business" to listOf("com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube"),
        "yt" to listOf("com.google.android.youtube"),
        "instagram" to listOf("com.instagram.android"),
        "insta" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana"),
        "messenger" to listOf("com.facebook.orca"),
        "telegram" to listOf("org.telegram.messenger", "org.thunderdog.challegram"),
        "spotify" to listOf("com.spotify.music"),
        "x" to listOf("com.twitter.android"),
        "twitter" to listOf("com.twitter.android"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "camera" to listOf("com.android.camera2", "com.google.android.GoogleCamera"),
        "gallery" to listOf("com.google.android.apps.photos", "com.android.gallery3d"),
        "photos" to listOf("com.google.android.apps.photos"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer"),
        "contacts" to listOf("com.google.android.contacts", "com.android.contacts"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "files" to listOf("com.google.android.documentsui", "com.google.android.apps.nbu.files"),
        "file manager" to listOf("com.google.android.documentsui", "com.google.android.apps.nbu.files"),
        "play store" to listOf("com.android.vending"),
        "settings" to listOf("com.android.settings")
    )

    fun resolve(labelOrPackage: String): String? {
        val query = normalize(labelOrPackage)
        if (query.isBlank()) return null

        runCatching {
            packageManager.getApplicationInfo(query, 0)
            return query
        }

        aliases[query]
            ?.asSequence()
            ?.mapNotNull(::installedPackage)
            ?.firstOrNull()
            ?.let { return it }

        val candidates = appDiscovery.findCandidates(query)
        val best = candidates.firstOrNull()
        if (best != null) {
            println(
                "VASU_APP_RESOLUTION " +
                    "query=$labelOrPackage " +
                    "selected=${best.label} " +
                    "package=${best.packageName} " +
                    "candidates=${candidates.size}"
            )
            return best.packageName
        }

        println("VASU_APP_RESOLUTION query=$labelOrPackage result=NOT_FOUND")
        return null
    }

    private fun installedPackage(packageName: String): String? =
        runCatching {
            packageManager.getApplicationInfo(packageName, 0)
            packageName
        }.getOrNull()

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .removePrefix("app ")
        .removeSuffix(" app")
        .trim()
}
