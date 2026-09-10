package com.vasu.assistant.files

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getStorageInfo(): Map<String, Any> {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        val totalSize = totalBlocks * blockSize
        val availableSize = availableBlocks * blockSize
        val usedSize = totalSize - availableSize

        return mapOf(
            "totalBytes" to totalSize,
            "availableBytes" to availableSize,
            "usedBytes" to usedSize,
            "totalFormatted" to formatSize(totalSize),
            "availableFormatted" to formatSize(availableSize),
            "usedFormatted" to formatSize(usedSize),
            "usagePercent" to if (totalSize > 0) ((usedSize.toDouble() / totalSize) * 100).toInt() else 0
        )
    }

    fun getCategoryBreakdown(): ActionResult {
        val root = Environment.getExternalStorageDirectory()
        val categories = mutableMapOf<String, Long>()
        val dirs = listOf(
            "DCIM" to "Photos/Camera",
            "Pictures" to "Pictures",
            "Download" to "Downloads",
            "Documents" to "Documents",
            "Music" to "Music",
            "Movies" to "Videos",
            "WhatsApp" to "WhatsApp"
        )
        for ((dirName, label) in dirs) {
            val dir = File(root, dirName)
            if (dir.exists()) {
                categories[label] = getDirSize(dir)
            }
        }
        val formatted = categories.map { (k, v) -> k to formatSize(v) }
        return ActionResult.success("storage_breakdown", "Category breakdown", mapOf("categories" to formatted))
    }

    fun getLargestFiles(directory: String, limit: Int = 10): ActionResult {
        return try {
            val dir = File(directory)
            if (!dir.exists()) return ActionResult.error("largest", "Directory not found", directory)
            val files = dir.walkTopDown()
                .filter { it.isFile }
                .sortedByDescending { it.length() }
                .take(limit)
                .map { mapOf("name" to it.name, "path" to it.absolutePath, "size" to formatSize(it.length())) }
                .toList()
            ActionResult.success("largest", "Found ${files.size} largest files", mapOf("files" to files))
        } catch (e: Exception) {
            ActionResult.error("largest", "Scan failed", e.message ?: "Unknown")
        }
    }

    private fun getDirSize(dir: File): Long {
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) { 0L }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1_048_576 -> "${bytes / 1024} KB"
            bytes < 1_073_741_824 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
            else -> "${"%.2f".format(bytes / 1_073_741_824.0)} GB"
        }
    }
}
