package com.vasu.assistant.pcconnect

import android.content.Context
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PcTransferManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(3)
    private val transferHistory = mutableListOf<TransferRecord>()

    data class TransferRecord(
        val fileName: String, val direction: String, val size: Long,
        val success: Boolean, val timestamp: Long
    )

    fun startServer(port: Int = 8765): ActionResult {
        return try {
            serverSocket?.close()
            serverSocket = ServerSocket(port)
            executor.submit { acceptConnections() }
            ActionResult.success("server", "File server started on port $port")
        } catch (e: Exception) {
            ActionResult.error("server", "Failed to start server", e.message ?: "Unknown")
        }
    }

    fun stopServer(): ActionResult {
        return try {
            serverSocket?.close()
            serverSocket = null
            ActionResult.success("server", "File server stopped")
        } catch (e: Exception) {
            ActionResult.error("server", "Failed to stop server", e.message ?: "Unknown")
        }
    }

    fun sendFile(filePath: String, host: String, port: Int = 8765): ActionResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) return ActionResult.error("send", "File not found: $filePath")

            val socket = Socket(host, port)
            val outputStream = socket.getOutputStream()
            val dataOut = DataOutputStream(outputStream)

            val fileName = file.name
            dataOut.writeUTF(fileName)
            dataOut.writeLong(file.length())

            file.inputStream().use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()

            socket.close()

            transferHistory.add(TransferRecord(fileName, "SENT", file.length(), true, System.currentTimeMillis()))
            ActionResult.success("send", "Sent ${file.name} to $host", mapOf("size" to file.length()))
        } catch (e: Exception) {
            ActionResult.error("send", "Transfer failed", e.message ?: "Unknown")
        }
    }

    fun getTransferHistory(): ActionResult {
        val records = transferHistory.map { mapOf(
            "file" to it.fileName, "direction" to it.direction,
            "size" to it.size, "success" to it.success, "time" to it.timestamp
        ) }
        return ActionResult.success("history", "${records.size} transfers", mapOf("transfers" to records))
    }

    private fun acceptConnections() {
        while (serverSocket != null && !serverSocket?.isClosed!!) {
            try {
                val client = serverSocket?.accept() ?: break
                executor.submit { handleIncoming(client) }
            } catch (_: Exception) { break }
        }
    }

    private fun handleIncoming(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val dataIn = DataInputStream(input)

            val fileName = dataIn.readUTF()
            val fileSize = dataIn.readLong()

            val saveDir = File(context.filesDir, "received_files")
            saveDir.mkdirs()
            val outputFile = File(saveDir, fileName)

            FileOutputStream(outputFile).use { output ->
                input.copyTo(output, bufferSize = 8192)
            }

            transferHistory.add(TransferRecord(fileName, "RECEIVED", fileSize, true, System.currentTimeMillis()))
            socket.close()
        } catch (e: Exception) {
            transferHistory.add(TransferRecord("unknown", "RECEIVED", 0, false, System.currentTimeMillis()))
        }
    }
}
