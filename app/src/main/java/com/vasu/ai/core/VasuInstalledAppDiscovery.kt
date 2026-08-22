package com.vasu.ai.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Read-only discovery of launchable applications available to VASU. */
class VasuInstalledAppDiscovery(private val context: Context) {
    data class InstalledApp(
        val packageName: String,
        val label: String,
        val normalizedLabel: String,
        val systemApp: Boolean,
        val launchable: Boolean
    )

    fun discover(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val result = linkedMapOf<String, InstalledApp>()

        for (resolveInfo in activities) {
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
            val packageName = appInfo.packageName ?: continue
            val label = appInfo.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isBlank()) continue

            result[packageName] = InstalledApp(
                packageName = packageName,
                label = label,
                normalizedLabel = normalize(label),
                systemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                launchable = true
            )
        }

        val apps = result.values.sortedWith(compareBy({ it.normalizedLabel }, { it.packageName }))
        println("VASU_APP_DISCOVERY count=${apps.size}")
        return apps
    }

    fun findCandidates(query: String): List<InstalledApp> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        return discover()
            .map { it to score(normalizedQuery, it) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<InstalledApp, Int>> { it.second }.thenBy { it.first.normalizedLabel })
            .map { it.first }
    }

    private fun score(query: String, app: InstalledApp): Int {
        var score = 0
        val label = app.normalizedLabel
        val packageName = app.packageName.lowercase()

        if (label == query) score += 1000
        if (label.startsWith(query)) score += 500
        if (label.contains(query)) score += 300
        if (packageName == query) score += 900
        if (packageName.contains(query)) score += 200

        for (token in query.split(" ").filter { it.isNotBlank() }) {
            if (label.contains(token)) score += 80
            if (packageName.contains(token)) score += 40
        }

        if (!app.systemApp) score += 5
        return score
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9\\u0900-\\u097F]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
