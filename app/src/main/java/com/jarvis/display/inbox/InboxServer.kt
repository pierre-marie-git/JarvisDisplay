package com.jarvis.display.inbox

import android.util.Log
import com.jarvis.display.Matrix
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Plain HTTP server using Java ServerSocket.
 * No third-party HTTP library dependencies.
 * Handles POST /api/matrices from the backend.
 */
class InboxServer(
    private val port: Int,
    private val onMatrices: (List<Matrix>) -> Unit
) : Runnable {
    private val tag = "InboxServer"
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        running = true
        Thread(this, "InboxServer").start()
        Log.i(tag, "InboxServer starting on port $port")
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        Log.i(tag, "InboxServer stopped")
    }

    override fun run() {
        try {
            serverSocket = ServerSocket(port, 10, java.net.InetAddress.getByName("0.0.0.0"))
            serverSocket?.soTimeout = 0  // no timeout
            Log.i(tag, "InboxServer listening on 0.0.0.0:$port")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start: ${e.message}")
            return
        }

        while (running) {
            try {
                val client = serverSocket!!.accept()
                // Handle each connection in a separate thread
                Thread({
                    try {
                        handleClient(client)
                    } catch (e: Exception) {
                        Log.e(tag, "Client error: ${e.message}")
                    } finally {
                        try { client.close() } catch (e: Exception) {}
                    }
                }, "InboxServer-client").start()
            } catch (e: Exception) {
                if (running) Log.e(tag, "Accept error: ${e.message}")
            }
        }
    }

    private fun handleClient(client: Socket) {
        val input = client.getInputStream()
        val output = client.getOutputStream()

        // Read request line
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ".toRegex(), 3)
        if (parts.size < 2) return
        val method = parts[0]
        val uri = parts[1]

        // Read headers
        val headers = mutableMapOf<String, String>()
        var contentLength = 0
        var line: String? = readLine(input)
        while (line != null && line.isNotEmpty()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val name = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim()
                headers[name.lowercase()] = value
                if (name.lowercase() == "content-length") {
                    contentLength = value.toIntOrNull() ?: 0
                }
            }
            line = readLine(input)
        }

        // Read body
        val body = if (contentLength > 0) {
            val bodyBytes = ByteArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val n = input.read(bodyBytes, totalRead, contentLength - totalRead)
                if (n <= 0) break
                totalRead += n
            }
            String(bodyBytes, Charsets.UTF_8)
        } else ""

        // Route request
        when {
            uri == "/api/matrices" && method == "POST" -> handleMatrices(output, body)
            uri == "/health" && method == "GET" -> sendResponse(output, 200, "OK")
            else -> sendResponse(output, 404, "Not found")
        }
    }

    private fun handleMatrices(output: OutputStream, body: String) {
        if (body.isBlank()) {
            sendResponse(output, 400, "Empty body")
            return
        }
        try {
            val matrices = parseMatrices(body)
            Log.i(tag, "Received ${matrices.size} matrix(ices)")
            onMatrices(matrices)
            sendJsonResponse(output, 200, JSONObject().put("received", matrices.size).toString())
        } catch (e: Exception) {
            Log.e(tag, "Parse error: ${e.message}")
            sendResponse(output, 400, "Bad JSON: ${e.message}")
        }
    }

    private fun parseMatrices(jsonStr: String): List<Matrix> {
        val json = JSONObject(jsonStr)
        val arr = json.optJSONArray("matrices") ?: JSONArray()
        val out = mutableListOf<Matrix>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val rowsArr = obj.optJSONArray("rows") ?: JSONArray()
            val rows = (0 until rowsArr.length()).map { rowsArr.optString(it, "") }
            out.add(Matrix(
                id = obj.optString("id", ""),
                rows = rows,
                durationSeconds = obj.optInt("duration", 8)
            ))
        }
        return out
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        var b: Int
        while (true) {
            b = input.read()
            if (b <= 0 || b == 10) break
            if (b != 13) sb.append(b.toChar())
        }
        return if (sb.isEmpty() && b <= 0) null else sb.toString()
    }

    private fun sendResponse(output: OutputStream, status: Int, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val statusLine = when (status) {
            200 -> "HTTP/1.1 200 OK"
            400 -> "HTTP/1.1 400 Bad Request"
            404 -> "HTTP/1.1 404 Not Found"
            405 -> "HTTP/1.1 405 Method Not Allowed"
            500 -> "HTTP/1.1 500 Internal Server Error"
            else -> "HTTP/1.1 $status"
        }
        val headers = "Content-Type: text/plain\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n"
        output.write("$statusLine\r\n$headers\r\n".toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, status: Int, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val statusLine = "HTTP/1.1 $status"
        val headers = "Content-Type: application/json\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n"
        output.write("$statusLine\r\n$headers\r\n".toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }
}
