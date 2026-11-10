package com.example.busapp

import android.content.Context

object BusStopRegistry {
    private const val PREF_NAME = "bus_stop_registry_prefs"
    private const val KEY_BUS_STOPS = "registered_bus_stops"
    private const val SEP = "\t"

    data class RegisteredBusStop(
        val busStopId: String,
        val displayName: String
    )

    fun getBusStops(context: Context): List<RegisteredBusStop> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BUS_STOPS, emptySet())
            .orEmpty()
            .mapNotNull { decode(it) }
            .sortedBy { it.displayName }
    }

    fun addBusStop(context: Context, busStopId: String, displayName: String) {
        val id = busStopId.trim()
        val name = displayName.trim()
        if (id.isEmpty()) return

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentStops = getBusStops(context)
        val deduped = currentStops.filterNot { it.busStopId == id }.toMutableList()
        deduped.add(RegisteredBusStop(id, if (name.isBlank()) id else name))

        prefs.edit()
            .putStringSet(KEY_BUS_STOPS, deduped.map { encode(it) }.toSet())
            .apply()
    }

    fun removeBusStopId(context: Context, busStopId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getBusStops(context).filterNot { it.busStopId == busStopId }
        prefs.edit().putStringSet(KEY_BUS_STOPS, current.map { encode(it) }.toSet()).apply()
    }

    private fun encode(stop: RegisteredBusStop): String {
        return "${stop.busStopId}$SEP${stop.displayName}"
    }

    private fun decode(raw: String): RegisteredBusStop? {
        if (raw.isBlank()) return null
        val parts = raw.split(SEP, limit = 2)
        val id = parts.firstOrNull()?.trim().orEmpty()
        if (id.isEmpty()) return null
        val name = parts.getOrNull(1)?.trim().orEmpty().ifBlank { id }
        return RegisteredBusStop(id, name)
    }
}
