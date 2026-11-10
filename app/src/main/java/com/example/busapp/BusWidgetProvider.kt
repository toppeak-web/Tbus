package com.example.busapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

open class BusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refreshWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            BusWidgetPrefs.removeBusStopId(context, it)
            WidgetStylePrefs.remove(context, it)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action != ACTION_REFRESH_WIDGET) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            refreshWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
            return
        }

        val allIds = appWidgetManager.getAppWidgetIds(ComponentName(context, BusWidget::class.java))
        refreshWidgets(context, appWidgetManager, allIds)
    }

    protected fun refreshWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return

        appWidgetIds.forEach { widgetId ->
            val style = WidgetStylePrefs.getStyle(context, widgetId)
            val busStopId = BusWidgetPrefs.getBusStopId(context, widgetId)
            val stopText = if (busStopId.isNullOrBlank()) {
                context.getString(R.string.widget_not_set_title)
            } else {
                "${context.getString(R.string.widget_stop_placeholder)} ($busStopId)"
            }
            val arrivalText = if (busStopId.isNullOrBlank()) {
                context.getString(R.string.widget_not_set_desc)
            } else {
                context.getString(R.string.widget_updating)
            }
            val loadingViews = buildRemoteViews(
                context = context,
                appWidgetId = widgetId,
                busStopId = busStopId,
                style = style,
                stopText = stopText,
                arrivalText = arrivalText,
                updatedText = "${context.getString(R.string.widget_update_prefix)} --:--:--"
            )
            appWidgetManager.updateAppWidget(widgetId, loadingViews)
        }

        thread {
            val nowText = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREA)
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())

            appWidgetIds.forEach { widgetId ->
                val style = WidgetStylePrefs.getStyle(context, widgetId)
                val busStopId = BusWidgetPrefs.getBusStopId(context, widgetId)
                if (busStopId.isNullOrBlank()) {
                    val notSetViews = buildRemoteViews(
                        context = context,
                        appWidgetId = widgetId,
                        busStopId = null,
                        style = style,
                        stopText = context.getString(R.string.widget_not_set_title),
                        arrivalText = context.getString(R.string.widget_not_set_desc),
                        updatedText = "${context.getString(R.string.widget_update_prefix)} $nowText"
                    )
                    appWidgetManager.updateAppWidget(widgetId, notSetViews)
                    return@forEach
                }
                val (stopText, arrivalText) = try {
                    val result = kakaoBusApi.fetchStopInfo(busStopId)
                    val pinnedFiltered = BusPinPrefs.filterArrivals(context, busStopId, result.arrivals)
                    val filteredArrivals = filterByMaxMinutes(pinnedFiltered, style.maxArrivalMinutes)
                    val hasPin = BusPinPrefs.getPinnedBusNumbers(context, busStopId).isNotEmpty()
                    val text = if (filteredArrivals.isEmpty()) {
                        when {
                            hasPin -> context.getString(R.string.widget_no_pinned_arrival)
                            style.maxArrivalMinutes > 0 -> context.getString(R.string.widget_no_filtered_arrival)
                            else -> context.getString(R.string.widget_no_arrival)
                        }
                    } else {
                        formatArrivalLines(filteredArrivals, context, getDisplayBusCount(style.layoutMode))
                    }
                    "${result.stopName} (${result.stopNumber})" to text
                } catch (_: Exception) {
                    "${context.getString(R.string.widget_stop_placeholder)} ($busStopId)" to
                        context.getString(R.string.widget_no_arrival)
                }

                val updatedText = "${context.getString(R.string.widget_update_prefix)} $nowText"
                val views = buildRemoteViews(
                    context = context,
                    appWidgetId = widgetId,
                    busStopId = busStopId,
                    style = style,
                    stopText = stopText,
                    arrivalText = arrivalText,
                    updatedText = updatedText
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        busStopId: String?,
        style: WidgetStyle,
        stopText: String,
        arrivalText: String,
        updatedText: String
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bus)
        views.setTextViewText(R.id.tv_stop, stopText)
        views.setTextViewText(R.id.tv_arrivals, arrivalText)
        views.setTextViewText(R.id.tv_updated, updatedText)
        applyStyle(context, views, style)

        val refreshIntent = Intent(context, BusWidget::class.java).apply {
            action = ACTION_REFRESH_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

        val pickStopIntent = Intent(context, WidgetConfigActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pickStopPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 10_000,
            pickStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_pick_stop, pickStopPendingIntent)

        val detailIntent = Intent(context, BusArrivalDetailActivity::class.java).apply {
            putExtra(BusArrivalDetailActivity.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(BusArrivalDetailActivity.EXTRA_BUS_STOP_ID, busStopId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val detailPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 20_000,
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, detailPendingIntent)
        views.setOnClickPendingIntent(R.id.tv_stop, detailPendingIntent)
        views.setOnClickPendingIntent(R.id.tv_arrivals, detailPendingIntent)
        views.setOnClickPendingIntent(R.id.tv_updated, detailPendingIntent)

        return views
    }

    private fun applyStyle(context: Context, views: RemoteViews, style: WidgetStyle) {
        when (style.theme) {
            WidgetStylePrefs.THEME_MINT -> {
                views.setInt(R.id.widget_root, "setBackgroundColor", 0xFFE0F2F1.toInt())
                views.setTextColor(R.id.tv_stop, 0xFF004D40.toInt())
                views.setTextColor(R.id.tv_arrivals, 0xFF1B5E20.toInt())
                views.setTextColor(R.id.tv_updated, 0xFF33691E.toInt())
                views.setTextColor(R.id.btn_refresh, 0xFF004D40.toInt())
                views.setTextColor(R.id.btn_pick_stop, 0xFF004D40.toInt())
            }
            WidgetStylePrefs.THEME_MONO -> {
                views.setInt(R.id.widget_root, "setBackgroundColor", 0xFFF5F5F5.toInt())
                views.setTextColor(R.id.tv_stop, 0xFF212121.toInt())
                views.setTextColor(R.id.tv_arrivals, 0xFF424242.toInt())
                views.setTextColor(R.id.tv_updated, 0xFF616161.toInt())
                views.setTextColor(R.id.btn_refresh, 0xFF212121.toInt())
                views.setTextColor(R.id.btn_pick_stop, 0xFF212121.toInt())
            }
            else -> {
                views.setInt(R.id.widget_root, "setBackgroundColor", 0xFFF2F6FB.toInt())
                views.setTextColor(R.id.tv_stop, 0xFF1A1A1A.toInt())
                views.setTextColor(R.id.tv_arrivals, 0xFF2B2B2B.toInt())
                views.setTextColor(R.id.tv_updated, 0xFF5B5B5B.toInt())
                views.setTextColor(R.id.btn_refresh, 0xFF01579B.toInt())
                views.setTextColor(R.id.btn_pick_stop, 0xFF01579B.toInt())
            }
        }

        when (style.layoutMode) {
            WidgetStylePrefs.LAYOUT_COMPACT -> {
                views.setViewVisibility(R.id.tv_updated, View.GONE)
                views.setViewVisibility(R.id.btn_pick_stop, View.GONE)
                views.setInt(R.id.tv_arrivals, "setMaxLines", 2)
                views.setTextViewTextSize(R.id.tv_stop, TypedValue.COMPLEX_UNIT_SP, 12f)
                views.setTextViewTextSize(R.id.tv_arrivals, TypedValue.COMPLEX_UNIT_SP, 11f)
            }
            WidgetStylePrefs.LAYOUT_EXPANDED -> {
                views.setViewVisibility(R.id.tv_updated, View.VISIBLE)
                views.setViewVisibility(R.id.btn_pick_stop, View.VISIBLE)
                views.setInt(R.id.tv_arrivals, "setMaxLines", 5)
                views.setTextViewTextSize(R.id.tv_stop, TypedValue.COMPLEX_UNIT_SP, 14f)
                views.setTextViewTextSize(R.id.tv_arrivals, TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            else -> {
                views.setViewVisibility(R.id.tv_updated, View.VISIBLE)
                views.setViewVisibility(R.id.btn_pick_stop, View.VISIBLE)
                views.setInt(R.id.tv_arrivals, "setMaxLines", 3)
                views.setTextViewTextSize(R.id.tv_stop, TypedValue.COMPLEX_UNIT_SP, 13f)
                views.setTextViewTextSize(R.id.tv_arrivals, TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        }
    }

    private fun filterByMaxMinutes(arrivals: List<BusArrival>, maxMinutes: Int): List<BusArrival> {
        if (maxMinutes <= 0) return arrivals
        val maxSeconds = maxMinutes * 60
        return arrivals.filter {
            it.arrivalTimeSeconds <= 0 || it.arrivalTimeSeconds <= maxSeconds
        }
    }

    private fun getDisplayBusCount(layoutMode: Int): Int {
        return when (layoutMode) {
            WidgetStylePrefs.LAYOUT_COMPACT -> 2
            WidgetStylePrefs.LAYOUT_EXPANDED -> 4
            else -> 3
        }
    }

    private fun formatArrivalLines(arrivals: List<BusArrival>, context: Context, maxItems: Int): String {
        if (arrivals.isEmpty()) return context.getString(R.string.widget_no_arrival)

        return arrivals.take(maxItems).joinToString(separator = "\n") { arrival ->
            val direction = trimDirection(arrival.direction)
            val minuteText = formatArrivalMinute(arrival.arrivalTimeSeconds, context)
            val stopCountText = if (arrival.busStopCount >= 0) {
                "${arrival.busStopCount}${context.getString(R.string.widget_stop_count_suffix)}"
            } else {
                context.getString(R.string.widget_stop_count_unknown)
            }
            val nextText = buildNextArrivalText(arrival, context)

            if (direction.isBlank()) {
                "${arrival.busNumber} $minuteText ($stopCountText)$nextText"
            } else {
                "${arrival.busNumber} $direction $minuteText ($stopCountText)$nextText"
            }
        }
    }

    private fun buildNextArrivalText(arrival: BusArrival, context: Context): String {
        val nextTime = arrival.nextArrivalTimeSeconds ?: return ""
        val nextCount = arrival.nextBusStopCount
        val nextMinuteText = formatArrivalMinute(nextTime, context)
        val nextCountText = if (nextCount != null && nextCount >= 0) {
            "${nextCount}${context.getString(R.string.widget_stop_count_suffix)}"
        } else {
            context.getString(R.string.widget_stop_count_unknown)
        }
        return " / ${context.getString(R.string.widget_next_prefix)} $nextMinuteText ($nextCountText)"
    }

    private fun formatArrivalMinute(arrivalTimeSeconds: Int, context: Context): String {
        val minutes = arrivalTimeSeconds / 60
        return if (minutes <= 0) {
            context.getString(R.string.widget_arriving_soon)
        } else {
            context.getString(R.string.widget_minute_format, minutes)
        }
    }

    private fun trimDirection(direction: String, maxLength: Int = 14): String {
        val clean = direction.trim()
        if (clean.length <= maxLength) return clean
        return clean.take(maxLength) + "..."
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.busapp.action.REFRESH_WIDGET"
        private val kakaoBusApi = KakaoBusApi()

        fun refreshFromConfig(context: Context, appWidgetId: Int) {
            val manager = AppWidgetManager.getInstance(context)
            BusWidgetProvider().refreshWidgets(context, manager, intArrayOf(appWidgetId))
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BusWidget::class.java))
            BusWidgetProvider().refreshWidgets(context, manager, ids)
        }
    }
}
