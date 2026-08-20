package com.vasu.ai.core

import android.content.Context

class VasuAppResolver(context: Context) {
    private val packageManager = context.packageManager

    fun resolve(labelOrPackage: String): String? {
        val query = labelOrPackage.trim()
        if (query.isBlank()) return null

        runCatching {
            packageManager.getApplicationInfo(query, 0)
            return query
        }

        val apps = packageManager.getInstalledApplications(0)
        return apps.firstOrNull { info ->
            val label = packageManager.getApplicationLabel(info).toString()
            label.equals(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
        }?.packageName
    }
}
