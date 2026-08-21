package com.vasu.ai.core

import android.content.Context
import android.content.Intent

class VasuAppResolver(context: Context) {
    private val packageManager = context.packageManager

    private val aliases = mapOf(
        "whatsapp" to listOf("com.whatsapp"),
        "whatsapp business" to listOf("com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube"),
        "yt" to listOf("com.google.android.youtube"),
        "instagram" to listOf("com.instagram.android"),
        "insta" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana"),
        "messenger" to listOf("com.facebook.orca"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "camera" to listOf("com.android.camera2", "com.google.android.GoogleCamera"),
        "play store" to listOf("com.android.vending"),
        "settings" to listOf("com.android.settings")
    )

    fun resolve(labelOrPackage: String): String? {
        val query = labelOrPackage.trim()
        if (query.isBlank()) return null

        runCatching {
            packageManager.getApplicationInfo(query, 0)
            return query
        }

        val normalized = query.lowercase()
        aliases[normalized]?.firstNotNullOfOrNull { candidate ->
            runCatching {
                packageManager.getApplicationInfo(candidate, 0)
                candidate
            }.getOrNull()
        }?.let { return it }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .firstOrNull { info ->
                val label = packageManager.getApplicationLabel(info).toString()
                label.equals(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
            }
            ?.packageName
    }
}
