package com.jarvis.display

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class CacheManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "jarvis_display_cache"
        private const val KEY_MESSAGES = "cached_messages"
        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_MUTED = "muted"
        private const val DEFAULT_SERVER = "192.168.1.18:8765"
    }

    fun saveMessages(messages: List<Message>) {
        val jsonArray = JSONArray()
        for (msg in messages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("text", JSONArray(msg.text.toList()))
                put("sender", msg.sender)
                put("created_at", msg.createdAt)
                put("expires_at", msg.expiresAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_MESSAGES, jsonArray.toString()).apply()
    }

    fun loadMessages(): List<Message> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val messages = mutableListOf<Message>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val textArray = obj.getJSONArray("text")
                val text = (0 until textArray.length()).map { textArray.getString(it) }
                messages.add(Message(
                    id = obj.getString("id"),
                    text = text,
                    sender = obj.optString("sender", ""),
                    createdAt = obj.optString("created_at", ""),
                    expiresAt = obj.optString("expires_at", "")
                ))
            }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getServerIp(): String = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER) ?: DEFAULT_SERVER

    fun setServerIp(ip: String) {
        prefs.edit().putString(KEY_SERVER_IP, ip).apply()
    }

    fun getLastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    fun setLastError(error: String?) {
        prefs.edit().putString(KEY_LAST_ERROR, error).apply()
    }

    fun isMuted(): Boolean = prefs.getBoolean(KEY_MUTED, false)

    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
    }
}
