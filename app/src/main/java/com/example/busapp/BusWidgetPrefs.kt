package com.example.busapp

import android.content.Context

object BusWidgetPrefs {
    private const val PREF_NAME = "bus_widget_prefs"
    private const val KEY_PREFIX_BUS_STOP_ID = "bus_stop_id_"

    fun getBusStopId(context: Context, appWidgetId: Int): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_PREFIX_BUS_STOP_ID$appWidgetId", null)
    }

    fun setBusStopId(context: Context, appWidgetId: Int, busStopId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_PREFIX_BUS_STOP_ID$appWidgetId", busStopId).apply()
    }

    fun removeBusStopId(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_PREFIX_BUS_STOP_ID$appWidgetId").apply()
    }
}
