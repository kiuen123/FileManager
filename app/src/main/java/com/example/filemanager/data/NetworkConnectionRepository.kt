package com.example.filemanager.data

import android.content.Context
import com.example.filemanager.model.NetworkConnection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NetworkConnectionRepository(context: Context) {

    private val prefs = context.getSharedPreferences("network_connections", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "connections_json"

    fun getAll(): List<NetworkConnection> {
        val json = prefs.getString(key, "[]") ?: "[]"
        val type = object : TypeToken<List<NetworkConnection>>() {}.type
        return try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
    }

    fun save(connection: NetworkConnection) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == connection.id }
        if (idx >= 0) list[idx] = connection else list.add(connection)
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }

    fun delete(id: String) {
        val list = getAll().filter { it.id != id }
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}

