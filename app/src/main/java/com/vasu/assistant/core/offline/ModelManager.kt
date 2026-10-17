package com.vasu.assistant.core.offline

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * FIX-8 Pass B: downloads / unpacks / deletes Vosk speech models under
 * `files/vosk/<spec.id>/`. Pure helpers live in [VoskCatalog]; this class owns
 * the IO, per-model progress state and zip-slip safety on unpack.
 *
 * Layout: the zip's single top-level folder ([VoskModelSpec.zipRoot]) is
 * stripped so `files/vosk/<id>/am` etc. feed `org.vosk.Model` directly.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed interface DownloadState {
        data object NotDownloaded : DownloadState
        data class Downloading(val percent: Int) : DownloadState
        data object Ready : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val lock = Any()

    init {
        _states.value = VoskCatalog.MODELS.associate { it.id to stateOf(it) }
    }

    fun modelDir(spec: VoskModelSpec): File = File(context.filesDir, "vosk/${spec.id}")

    /** A model is ready when the unpacked folder contains the acoustic model. */
    fun isReady(spec: VoskModelSpec): Boolean = File(modelDir(spec), "am").isDirectory

    fun download(modelId: String) {
        val spec = VoskCatalog.MODELS.firstOrNull { it.id == modelId } ?: return
        synchronized(lock) {
            if (jobs[modelId]?.isActive == true) return
            setState(modelId, DownloadState.Downloading(0))
            jobs[modelId] = scope.launch { runDownload(spec) }
        }
    }

    fun delete(modelId: String) {
        val spec = VoskCatalog.MODELS.firstOrNull { it.id == modelId } ?: return
        synchronized(lock) { jobs.remove(modelId)?.cancel() }
        scope.launch {
            modelDir(spec).deleteRecursively()
            File(context.cacheDir, "vosk-$modelId.zip").delete()
            setState(modelId, DownloadState.NotDownloaded)
        }
    }

    // ------------------------------------------------------------------ worker

    private suspend fun runDownload(spec: VoskModelSpec) {
        val zipFile = File(context.cacheDir, "vosk-${spec.id}.zip")
        val tmpDir = File(context.filesDir, "vosk/${spec.id}.tmp")
        try {
            downloadTo(spec, zipFile)
            unpack(zipFile, tmpDir, spec)
            coroutineContext.ensureActive()
            val target = modelDir(spec)
            target.deleteRecursively()
            if (!tmpDir.renameTo(target)) {
                tmpDir.copyRecursively(target, overwrite = true)
                tmpDir.deleteRecursively()
            }
            zipFile.delete()
            setState(spec.id, DownloadState.Ready)
            Log.i(TAG, "Model ${spec.id} ready at ${target.absolutePath}")
        } catch (c: CancellationException) {
            Log.i(TAG, "Model ${spec.id} download cancelled")
            tmpDir.deleteRecursively()
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Model ${spec.id} download failed: ${t.message}", t)
            tmpDir.deleteRecursively()
            zipFile.delete()
            setState(spec.id, DownloadState.Failed(t.message ?: "Download failed"))
        } finally {
            val self = coroutineContext[Job]
            synchronized(lock) {
                if (jobs[spec.id] === self) jobs.remove(spec.id)
            }
        }
    }

    private suspend fun downloadTo(spec: VoskModelSpec, zipFile: File) {
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Server returned HTTP $code")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                zipFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, n)
                        copied += n
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                setState(spec.id, DownloadState.Downloading(percent))
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun unpack(zipFile: File, tmpDir: File, spec: VoskModelSpec) {
        tmpDir.deleteRecursively()
        if (!tmpDir.mkdirs()) throw IllegalStateException("Cannot create ${tmpDir.name}")
        val root = tmpDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val relative = VoskCatalog.relativeEntryPath(entry.name, spec.zipRoot)
                if (relative != null) {
                    val target = File(tmpDir, relative)
                    if (!target.canonicalPath.startsWith(root + File.separator)) {
                        throw IllegalStateException("Blocked unsafe zip entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> zip.copyTo(output) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (!File(tmpDir, "am").isDirectory) {
            throw IllegalStateException("Model layout invalid (no acoustic model)")
        }
    }

    private fun setState(id: String, state: DownloadState) {
        _states.update { it + (id to state) }
    }

    private fun stateOf(spec: VoskModelSpec): DownloadState =
        if (isReady(spec)) DownloadState.Ready else DownloadState.NotDownloaded

    companion object {
        private const val TAG = "ModelManager"
    }
}
