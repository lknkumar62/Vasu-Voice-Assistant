package com.vasu.assistant.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageAnalyzer: StorageAnalyzer
) {
    private val rootDir: File = Environment.getExternalStorageDirectory()

    fun browseDirectory(path: String = rootDir.absolutePath): ActionResult {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                return ActionResult.error("browse", "Directory not found", path)
            }
            val files = dir.listFiles()?.map { f ->
                mapOf(
                    "name" to f.name,
                    "isDirectory" to f.isDirectory,
                    "size" to f.length(),
                    "lastModified" to f.lastModified(),
                    "extension" to (if (f.isFile) f.extension else ""),
                    "absolutePath" to f.absolutePath
                )
            }?.sortedWith(compareByDescending<Map<String, Any>> { it["isDirectory"] as Boolean }.thenBy { it["name"] as String })
                ?: emptyList()
            ActionResult.success("browse", "Found ${files.size} items in ${dir.name}", mapOf("files" to files, "path" to path))
        } catch (e: SecurityException) {
            ActionResult.error("browse", "Access denied", "No permission to access: $path")
        } catch (e: Exception) {
            ActionResult.error("browse", "Failed to browse", e.message ?: "Unknown error")
        }
    }

    fun searchFiles(query: String, directory: String = rootDir.absolutePath): ActionResult {
        return try {
            val dir = File(directory)
            val results = mutableListOf<Map<String, Any>>()
            searchRecursive(dir, query.lowercase(), results, maxResults = 50)
            ActionResult.success("search", "Found ${results.size} results for '$query'", mapOf("results" to results))
        } catch (e: Exception) {
            ActionResult.error("search", "Search failed", e.message ?: "Unknown")
        }
    }

    fun readFileContent(path: String): ActionResult {
        return try {
            val file = File(path)
            if (!file.exists()) return ActionResult.error("read", "File not found", path)
            if (file.length() > 1_048_576) {
                return ActionResult.error("read", "File too large", "Max 1MB for text reading")
            }
            val content = file.readText()
            ActionResult.success("read", "Read ${content.length} chars from ${file.name}", mapOf("content" to content, "name" to file.name))
        } catch (e: Exception) {
            ActionResult.error("read", "Failed to read file", e.message ?: "Unknown")
        }
    }

    fun renameFile(path: String, newName: String): ActionResult {
        return try {
            val file = File(path)
            if (!file.exists()) return ActionResult.error("rename", "File not found", path)
            val newFile = File(file.parent, newName)
            if (newFile.exists()) return ActionResult.error("rename", "Name already exists", newName)
            val success = file.renameTo(newFile)
            if (success) ActionResult.success("rename", "Renamed to $newName")
            else ActionResult.error("rename", "Rename failed", "Could not rename file")
        } catch (e: Exception) {
            ActionResult.error("rename", "Failed to rename", e.message ?: "Unknown")
        }
    }

    fun copyFile(sourcePath: String, destDir: String): ActionResult {
        return try {
            val source = File(sourcePath)
            if (!source.exists()) return ActionResult.error("copy", "Source not found", sourcePath)
            val dest = File(destDir, source.name)
            if (dest.exists()) return ActionResult.error("copy", "Destination already exists", dest.absolutePath)
            source.copyTo(dest)
            ActionResult.success("copy", "Copied ${source.name} to $destDir")
        } catch (e: Exception) {
            ActionResult.error("copy", "Copy failed", e.message ?: "Unknown")
        }
    }

    fun moveFile(sourcePath: String, destDir: String): ActionResult {
        return try {
            val source = File(sourcePath)
            if (!source.exists()) return ActionResult.error("move", "Source not found", sourcePath)
            val dest = File(destDir, source.name)
            if (dest.exists()) return ActionResult.error("move", "Destination exists", dest.absolutePath)
            source.renameTo(dest)
            ActionResult.success("move", "Moved ${source.name} to $destDir")
        } catch (e: Exception) {
            ActionResult.error("move", "Move failed", e.message ?: "Unknown")
        }
    }

    fun deleteFile(path: String): ActionResult {
        return try {
            val file = File(path)
            if (!file.exists()) return ActionResult.error("delete", "File not found", path)
            val success = file.delete()
            if (success) ActionResult.success("delete", "Deleted ${file.name}")
            else ActionResult.error("delete", "Delete failed", "Could not delete file")
        } catch (e: Exception) {
            ActionResult.error("delete", "Delete failed", e.message ?: "Unknown")
        }
    }

    fun deleteDirectory(path: String): ActionResult {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return ActionResult.error("delete_dir", "Directory not found", path)
            val count = dir.walkTopDown().count()
            dir.deleteRecursively()
            ActionResult.success("delete_dir", "Deleted directory and $count items")
        } catch (e: Exception) {
            ActionResult.error("delete_dir", "Delete failed", e.message ?: "Unknown")
        }
    }

    fun shareFile(path: String): ActionResult {
        return try {
            val file = File(path)
            if (!file.exists()) return ActionResult.error("share", "File not found", path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(path)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ActionResult.success("share", "Sharing ${file.name}")
        } catch (e: Exception) {
            ActionResult.error("share", "Share failed", e.message ?: "Unknown")
        }
    }

    fun getStorageInfo(): ActionResult {
        val info = storageAnalyzer.getStorageInfo()
        return ActionResult.success("storage", "Storage analyzed", info)
    }

    fun listImages(directory: String = rootDir.absolutePath): ActionResult {
        return try {
            val images = mutableListOf<Map<String, Any>>()
            val dir = File(directory)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
                }?.forEach { f ->
                    images.add(mapOf("name" to f.name, "path" to f.absolutePath, "size" to f.length(), "modified" to f.lastModified()))
                }
            }
            ActionResult.success("list_images", "Found ${images.size} images", mapOf("images" to images))
        } catch (e: Exception) {
            ActionResult.error("list_images", "Failed to list images", e.message ?: "Unknown")
        }
    }

    private fun searchRecursive(dir: File, query: String, results: MutableList<Map<String, Any>>, maxResults: Int) {
        if (results.size >= maxResults) return
        try {
            dir.listFiles()?.forEach { file ->
                if (results.size >= maxResults) return
                if (file.isDirectory && !file.name.startsWith(".")) {
                    searchRecursive(file, query, results, maxResults)
                } else if (file.name.lowercase().contains(query)) {
                    results.add(mapOf("name" to file.name, "path" to file.absolutePath, "size" to file.length(), "isDirectory" to file.isDirectory))
                }
            }
        } catch (_: SecurityException) { }
    }

    private fun getMimeType(path: String): String {
        val ext = File(path).extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }
}
