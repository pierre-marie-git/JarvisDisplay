package com.jarvis.display

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class Message(
    val id: String,
    val text: List<String>,
    val sender: String,
    val createdAt: String,
    val expiresAt: String
)

data class StatusInfo(
    val status: String,
    val time: Long
)

class ApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var baseUrl: String = ""

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getBaseUrl(): String = baseUrl

    fun fetchMessages(callback: ApiCallback<List<Message>>) {
        val request = Request.Builder()
            .url("$baseUrl/api/messages")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        callback.onError("HTTP ${response.code}")
                        return
                    }
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val messagesArray = json.optJSONArray("messages") ?: JSONArray()
                    val messages = mutableListOf<Message>()
                    for (i in 0 until messagesArray.length()) {
                        val obj = messagesArray.getJSONObject(i)
                        val textArray = obj.optJSONArray("text") ?: JSONArray()
                        val text = (0 until minOf(textArray.length(), 5)).map {
                            textArray.optString(it, "")
                        }
                        // Pad to 5
                        val paddedText = text.toMutableList()
                        while (paddedText.size < 5) paddedText.add("")
                        messages.add(Message(
                            id = obj.optString("id", ""),
                            text = paddedText,
                            sender = obj.optString("sender", ""),
                            createdAt = obj.optString("created_at", ""),
                            expiresAt = obj.optString("expires_at", "")
                        ))
                    }
                    callback.onSuccess(messages)
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Parse error")
                }
            }
        })
    }

    fun fetchStatus(callback: ApiCallback<StatusInfo>) {
        val request = Request.Builder()
            .url("$baseUrl/api/status")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        callback.onError("HTTP ${response.code}")
                        return
                    }
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    callback.onSuccess(StatusInfo(
                        status = json.optString("status", ""),
                        time = json.optLong("time", 0L)
                    ))
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Parse error")
                }
            }
        })
    }

    fun fetchAudio(callback: AudioCallback) {
        val request = Request.Builder()
            .url("$baseUrl/api/audio")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        callback.onError("HTTP ${response.code}")
                        return
                    }
                    val stream = response.body?.byteStream()
                    if (stream != null) {
                        callback.onSuccess(stream)
                    } else {
                        callback.onError("Empty response")
                    }
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Audio fetch error")
                }
            }
        })
    }

    interface ApiCallback<T> {
        fun onSuccess(data: T)
        fun onError(error: String)
    }

    interface AudioCallback {
        fun onSuccess(stream: InputStream)
        fun onError(error: String)
    }
}
