package com.vasu.assistant.camera

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.vasu.assistant.core.automation.ActionResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Suspends until an ML Kit [Task] settles. kotlinx-coroutines-play-services is
 * not a dependency, so listeners bridge the Task instead of .await().
 */
internal suspend fun <T> awaitVisionTask(task: Task<T>): Result<T> =
    suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { continuation.resume(Result.success(it)) }
        task.addOnFailureListener { continuation.resume(Result.failure(it)) }
    }

internal fun loadVisionImage(context: Context, uri: Uri): Result<InputImage> = try {
    Result.success(InputImage.fromFilePath(context, uri))
} catch (e: Exception) {
    Result.failure(e)
}

internal fun visionImageError(action: String, error: Throwable?): ActionResult = when (error) {
    is SecurityException ->
        ActionResult.error(action, "Permission denied", "VASU has no access to this image")
    else ->
        ActionResult.error(action, "Image not readable", error?.message ?: "Could not open the image")
}
