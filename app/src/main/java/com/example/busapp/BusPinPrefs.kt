package com.example.busapp

import android.content.Context

object BusPinPrefs {
    private const val PREF_NAME = "bus_pin_prefs"
    private const val KEY_PREFIX = "pinned_bus_"

    fun getPinnedBusNumbers(context: Context, busStopId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("$KEY_PREFIX$busStopId", emptySet()).orEmpty()
    }

    fun setPinnedBusNumbers(context: Context, busStopId: String, busNumbers: Set<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("$KEY_PREFIX$busStopId", busNumbers).apply()
    }

    fun filterArrivals(context: Context, busStopId: String, arrivals: List<BusArrival>): List<BusArrival> {
        val pinned = getPinnedBusNumbers(context, busStopId)
        if (pinned.isEmpty()) return arrivals
        return arrivals.filter { pinned.contains(it.busNumber) }
    }
}
