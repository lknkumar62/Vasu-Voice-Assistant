package com.vasu.assistant.core.telemetry

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OpsUploader(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val events = getPendingEvents()
            if (events.isEmpty()) {
                Log.d(TAG, "No events to upload")
                return@withContext Result.success()
            }

            val payload = buildPayload(events)
            val responseCode = uploadToServer(payload)

            if (responseCode in 200..299) {
                clearUploadedEvents()
                Log.d(TAG, "Upload successful: $responseCode")
                Result.success()
            } else {
                Log.w(TAG, "Upload failed: $responseCode")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
            Result.retry()
        }
    }

    private fun getPendingEvents(): List<TelemetryManager.TelemetryEvent> {
        return try {
            val prefs = applicationContext.getSharedPreferences("vasu_telemetry", Context.MODE_PRIVATE)
            val json = prefs.getString("pending_events", "[]") ?: "[]"
            val array = JSONArray(json)
            val events = mutableListOf<TelemetryManager.TelemetryEvent>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                events.add(
                    TelemetryManager.TelemetryEvent(
                        type = obj.getString("type"),
                        name = obj.getString("name"),
                        timestamp = obj.getLong("timestamp"),
                        properties = parseProperties(obj.optJSONObject("properties"))
                    )
                )
            }
            events
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseProperties(obj: JSONObject?): Map<String, Any> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, Any>()
        obj.keys().forEach { key ->
            map[key] = obj.get(key)
        }
        return map
    }

    private fun buildPayload(events: List<TelemetryManager.TelemetryEvent>): JSONObject {
        val jsonArray = JSONArray()
        events.forEach { event ->
            val obj = JSONObject().apply {
                put("type", event.type)
                put("name", event.name)
                put("timestamp", event.timestamp)
                put("properties", JSONObject(event.properties))
            }
            jsonArray.put(obj)
        }

        return JSONObject().apply {
            put("events", jsonArray)
            put("session_id", TelemetryManager.init::class.java.simpleName)
            put("device", JSONObject(TelemetryManager.init.let {
                mapOf(
                    "sdk" to android.os.Build.VERSION.SDK_INT,
                    "model" to android.os.Build.MODEL
                )
            }))
        }
    }

    private fun uploadToServer(payload: JSONObject): Int {
        val url = URL(SERVER_URL)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Client", "VASU-Android")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            connection.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "HTTP error: ${e.message}")
            -1
        } finally {
            connection.disconnect()
        }
    }

    private fun clearUploadedEvents() {
        val prefs = applicationContext.getSharedPreferences("vasu_telemetry", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_events", "[]").apply()
    }

    companion object {
        private const val TAG = "OpsUploader"
        const val SERVER_URL = "https://telemetry.vasu.app/events"
        const val WORK_NAME = "vasu_telemetry_upload"
    }
}
