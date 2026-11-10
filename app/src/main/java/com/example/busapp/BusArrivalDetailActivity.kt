package com.example.busapp

import android.content.Intent
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BusArrivalDetailActivity : AppCompatActivity() {
    private lateinit var stopView: TextView
    private lateinit var stopNumberView: TextView
    private lateinit var updatedView: TextView
    private lateinit var arrivalsContainer: LinearLayout
    private val api = KakaoBusApi()
    private var currentBusStopId: String? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREA)
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_arrival_detail)
        applyWindowInsets()

        stopView = findViewById(R.id.tv_detail_stop)
        stopNumberView = findViewById(R.id.tv_detail_stop_number)
        updatedView = findViewById(R.id.tv_detail_updated)
        arrivalsContainer = findViewById(R.id.layout_arrivals)

        findViewById<ImageButton>(R.id.btn_detail_refresh).setOnClickListener { loadArrivals() }
        findViewById<Button>(R.id.btn_detail_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_detail_open_app).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        loadArrivals()
    }

    override fun onResume() {
        super.onResume()
        if (currentBusStopId != null) {
            loadArrivals()
        }
    }

    private fun loadArrivals() {
        val widgetId = intent.getIntExtra(
            EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val busStopId = intent.getStringExtra(EXTRA_BUS_STOP_ID)
            ?: BusWidgetPrefs.getBusStopId(this, widgetId)

        currentBusStopId = busStopId

        if (busStopId.isNullOrBlank()) {
            stopView.text = getString(R.string.widget_not_set_title)
            stopNumberView.text = ""
            renderEmpty(getString(R.string.widget_not_set_desc))
            updatedView.text = getString(R.string.widget_updated_default)
            return
        }

        stopView.text = getString(R.string.detail_loading)
        stopNumberView.text = busStopId
        renderEmpty(getString(R.string.detail_loading))
        updatedView.text = "${getString(R.string.widget_update_prefix)} --:--:--"

        thread {
            val nowText = timeFormatter.format(Instant.now())
            val result = runCatching { api.fetchStopInfo(busStopId) }

            runOnUiThread {
                result.onSuccess { stopInfo ->
                    val pinned = BusPinPrefs.getPinnedBusNumbers(this, busStopId)
                    val arrivals = if (pinned.isEmpty()) {
                        stopInfo.arrivals
                    } else {
                        stopInfo.arrivals.filter { pinned.contains(it.busNumber) }
                    }

                    stopView.text = stopInfo.stopName
                    stopNumberView.text = stopInfo.stopNumber
                    if (arrivals.isEmpty() && pinned.isNotEmpty()) {
                        renderEmpty(getString(R.string.widget_no_pinned_arrival))
                    } else {
                        renderArrivals(arrivals, busStopId)
                    }
                }.onFailure {
                    renderEmpty(getString(R.string.widget_no_arrival))
                }
                updatedView.text = "${getString(R.string.widget_update_prefix)} $nowText"
            }
        }
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.detail_root)
        val header = findViewById<View>(R.id.detail_header)
        val bottomBar = findViewById<View>(R.id.detail_bottom_bar)
        val headerBaseTopPadding = header.paddingTop
        val bottomBarBaseBottomPadding = bottomBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(
                header.paddingLeft,
                headerBaseTopPadding + sysBars.top,
                header.paddingRight,
                header.paddingBottom
            )
            bottomBar.setPadding(
                bottomBar.paddingLeft,
                bottomBar.paddingTop,
                bottomBar.paddingRight,
                bottomBarBaseBottomPadding + sysBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderEmpty(message: String) {
        arrivalsContainer.removeAllViews()
        val text = TextView(this).apply {
            this.text = message
            textSize = 22f
            setPadding(16, 30, 16, 30)
        }
        arrivalsContainer.addView(text)
    }

    private fun renderArrivals(arrivals: List<BusArrival>, busStopId: String) {
        arrivalsContainer.removeAllViews()

        arrivals.forEach { arrival ->
            val itemView = layoutInflater.inflate(R.layout.item_arrival_popup, arrivalsContainer, false)
            val busNumberView = itemView.findViewById<TextView>(R.id.tv_popup_bus_number)
            val minuteView = itemView.findViewById<TextView>(R.id.tv_popup_arrival_minute)
            val directionView = itemView.findViewById<TextView>(R.id.tv_popup_direction)
            val currentView = itemView.findViewById<TextView>(R.id.tv_popup_current)
            val nextView = itemView.findViewById<TextView>(R.id.tv_popup_next)

            busNumberView.text = arrival.busNumber
            minuteView.text = formatMinute(arrival.arrivalTimeSeconds)
            directionView.text = if (arrival.direction.isBlank()) {
                getString(R.string.widget_no_arrival)
            } else {
                trimDirection(arrival.direction, 24)
            }

            val currentStopText = if (arrival.busStopCount >= 0) {
                getString(R.string.detail_current_stop_format, minuteView.text.toString(), arrival.busStopCount)
            } else {
                getString(R.string.detail_current_stop_unknown_format, minuteView.text.toString())
            }
            currentView.text = currentStopText

            val nextText = if (arrival.nextArrivalTimeSeconds != null) {
                val nextMinute = formatMinute(arrival.nextArrivalTimeSeconds)
                val nextStops = if ((arrival.nextBusStopCount ?: -1) >= 0) {
                    getString(R.string.detail_next_stop_with_minute_format, nextMinute, arrival.nextBusStopCount)
                } else {
                    getString(R.string.detail_next_stop_unknown_format, nextMinute)
                }
                nextStops
            } else {
                getString(R.string.detail_next_none_line)
            }
            nextView.text = nextText

            if (arrival.lineId.isNotBlank()) {
                itemView.setOnClickListener {
                    val intent = Intent(this, BusRouteStopsActivity::class.java).apply {
                        putExtra(BusRouteStopsActivity.EXTRA_LINE_ID, arrival.lineId)
                        putExtra(BusRouteStopsActivity.EXTRA_BUS_NUMBER, arrival.busNumber)
                        putExtra(BusRouteStopsActivity.EXTRA_BUS_STOP_ID, busStopId)
                    }
                    startActivity(intent)
                }
            }

            arrivalsContainer.addView(itemView)
        }
    }

    private fun formatMinute(seconds: Int): String {
        val minute = seconds / 60
        return if (minute <= 0) {
            getString(R.string.widget_arriving_soon)
        } else {
            getString(R.string.widget_minute_format, minute)
        }
    }

    private fun trimDirection(direction: String, maxLength: Int): String {
        val clean = direction.trim()
        if (clean.length <= maxLength) return clean
        return clean.take(maxLength) + "..."
    }

    companion object {
        const val EXTRA_APPWIDGET_ID = "extra_app_widget_id"
        const val EXTRA_BUS_STOP_ID = "extra_bus_stop_id"
    }
}
