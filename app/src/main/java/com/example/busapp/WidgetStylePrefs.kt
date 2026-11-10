package com.example.busapp

import android.content.Context

data class WidgetStyle(
    val theme: Int,
    val layoutMode: Int,
    val maxArrivalMinutes: Int
)

object WidgetStylePrefs {
    private const val PREF_NAME = "widget_style_prefs"
    private const val KEY_THEME_PREFIX = "theme_"
    private const val KEY_LAYOUT_PREFIX = "layout_"
    private const val KEY_MAX_MINUTES_PREFIX = "max_minutes_"

    const val THEME_SKY = 0
    const val THEME_MINT = 1
    const val THEME_MONO = 2

    const val LAYOUT_COMPACT = 0
    const val LAYOUT_DEFAULT = 1
    const val LAYOUT_EXPANDED = 2

    fun getStyle(context: Context, appWidgetId: Int): WidgetStyle {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return WidgetStyle(
            theme = prefs.getInt("$KEY_THEME_PREFIX$appWidgetId", THEME_SKY),
            layoutMode = prefs.getInt("$KEY_LAYOUT_PREFIX$appWidgetId", LAYOUT_DEFAULT),
            maxArrivalMinutes = prefs.getInt("$KEY_MAX_MINUTES_PREFIX$appWidgetId", 0)
        )
    }

    fun setStyle(context: Context, appWidgetId: Int, style: WidgetStyle) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("$KEY_THEME_PREFIX$appWidgetId", style.theme)
            .putInt("$KEY_LAYOUT_PREFIX$appWidgetId", style.layoutMode)
            .putInt("$KEY_MAX_MINUTES_PREFIX$appWidgetId", style.maxArrivalMinutes)
            .apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("$KEY_THEME_PREFIX$appWidgetId")
            .remove("$KEY_LAYOUT_PREFIX$appWidgetId")
            .remove("$KEY_MAX_MINUTES_PREFIX$appWidgetId")
            .apply()
    }
}
