package com.example.busapp

import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class BusRouteStopsActivity : AppCompatActivity() {
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var emptyView: TextView
    private lateinit var listView: ListView

    private val api = KakaoBusApi()
    private val stopItems = mutableListOf<StopRow>()
    private lateinit var adapter: StopRowAdapter

    private var lineId: String = ""
    private var busNumber: String = ""
    private var selectedStopId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_route_stops)

        titleView = findViewById(R.id.tv_route_title)
        subtitleView = findViewById(R.id.tv_route_subtitle)
        emptyView = findViewById(R.id.tv_route_empty)
        listView = findViewById(R.id.lv_route_stops)
        adapter = StopRowAdapter()
        listView.adapter = adapter

        lineId = intent.getStringExtra(EXTRA_LINE_ID).orEmpty()
        busNumber = intent.getStringExtra(EXTRA_BUS_NUMBER).orEmpty()
        selectedStopId = intent.getStringExtra(EXTRA_BUS_STOP_ID).orEmpty()

        findViewById<Button>(R.id.btn_route_refresh).setOnClickListener {
            loadRoute()
        }
        loadRoute()
    }

    private fun loadRoute() {
        if (lineId.isBlank()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.route_empty)
            listView.visibility = View.GONE
            return
        }

        titleView.text = if (busNumber.isBlank()) lineId else busNumber
        subtitleView.text = getString(R.string.route_loading)
        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.route_loading)
        listView.visibility = View.GONE

        thread {
            val result = runCatching { api.fetchBusLineInfo(lineId) }
            runOnUiThread {
                result.onSuccess { info ->
                    titleView.text = if (busNumber.isBlank()) info.lineName else busNumber
                    subtitleView.text = if (info.routeDetailName.isBlank()) {
                        info.lineName
                    } else {
                        info.routeDetailName
                    }

                    val markerMap = computeVehicleMarkerMap(info)
                    stopItems.clear()
                    info.busStops.forEach { stop ->
                        stopItems += StopRow(
                            stopName = stop.stopName,
                            order = stop.order,
                            stopId = stop.stopId,
                            vehicleCount = markerMap[stop.order] ?: 0,
                            isSelectedStop = selectedStopId.isNotBlank() && stop.stopId == selectedStopId
                        )
                    }
                    adapter.notifyDataSetChanged()

                    emptyView.visibility = if (stopItems.isEmpty()) View.VISIBLE else View.GONE
                    emptyView.text = getString(R.string.route_empty)
                    listView.visibility = if (stopItems.isEmpty()) View.GONE else View.VISIBLE
                }.onFailure {
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = getString(R.string.route_empty)
                    listView.visibility = View.GONE
                }
            }
        }
    }

    private fun computeVehicleMarkerMap(info: BusLineInfo): Map<Int, Int> {
        if (info.busStops.isEmpty()) return emptyMap()
        val maxOrder = info.busStops.maxOf { it.order }
        val map = mutableMapOf<Int, Int>()

        info.busLocations.forEach { location ->
            if (location.sectionOrder < 1) return@forEach
            val nextStop = if (location.sectionDist > 0 && location.sectionOffsetDist * 2 >= location.sectionDist) {
                location.sectionOrder + 1
            } else {
                location.sectionOrder
            }
            val order = nextStop.coerceIn(1, maxOrder)
            map[order] = (map[order] ?: 0) + 1
        }

        return map
    }

    private data class StopRow(
        val stopName: String,
        val order: Int,
        val stopId: String,
        val vehicleCount: Int,
        val isSelectedStop: Boolean
    )

    private inner class StopRowAdapter : BaseAdapter() {
        override fun getCount(): Int = stopItems.size
        override fun getItem(position: Int): Any = stopItems[position]
        override fun getItemId(position: Int): Long = stopItems[position].order.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_route_stop, parent, false)
            val item = stopItems[position]

            val icon = view.findViewById<ImageView>(R.id.iv_stop_bus)
            val name = view.findViewById<TextView>(R.id.tv_stop_name)
            val meta = view.findViewById<TextView>(R.id.tv_stop_meta)

            icon.visibility = if (item.vehicleCount > 0) View.VISIBLE else View.GONE
            name.text = "${item.order}. ${item.stopName}"
            val vehicleText = if (item.vehicleCount > 0) {
                getString(R.string.route_vehicle_count, item.vehicleCount)
            } else {
                getString(R.string.route_vehicle_none)
            }
            val selectedText = if (item.isSelectedStop) {
                getString(R.string.route_selected_stop)
            } else {
                ""
            }
            meta.text = if (selectedText.isBlank()) vehicleText else "$selectedText · $vehicleText"
            view.setBackgroundColor(
                if (item.isSelectedStop) 0x112196F3
                else Color.TRANSPARENT
            )
            return view
        }
    }

    companion object {
        const val EXTRA_LINE_ID = "extra_line_id"
        const val EXTRA_BUS_NUMBER = "extra_bus_number"
        const val EXTRA_BUS_STOP_ID = "extra_bus_stop_id"
    }
}
