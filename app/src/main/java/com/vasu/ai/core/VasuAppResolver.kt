package com.vasu.ai.core

import android.content.Context
import android.content.Intent

class VasuAppResolver(context: Context) {
    private val packageManager = context.packageManager

    fun resolve(labelOrPackage: String): String? {
        val query = labelOrPackage.trim()
        if (query.isBlank()) return null

        runCatching {
            packageManager.getApplicationInfo(query, 0)
            return query
        }

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
