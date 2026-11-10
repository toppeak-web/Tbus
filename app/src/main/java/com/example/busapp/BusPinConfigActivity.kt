package com.example.busapp

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class BusPinConfigActivity : AppCompatActivity() {
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var emptyView: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val api = KakaoBusApi()
    private var busStopId: String = ""
    private var busItems: List<BusArrival> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_pin_config)

        titleView = findViewById(R.id.tv_pin_title)
        subtitleView = findViewById(R.id.tv_pin_subtitle)
        emptyView = findViewById(R.id.tv_pin_empty)
        listView = findViewById(R.id.lv_pin_buses)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, mutableListOf())
        listView.adapter = adapter

        busStopId = intent.getStringExtra(EXTRA_BUS_STOP_ID).orEmpty()
        val label = intent.getStringExtra(EXTRA_STOP_LABEL).orEmpty()
        if (label.isNotBlank()) {
            titleView.text = getString(R.string.pin_bus_title_with_stop, label)
        }

        findViewById<Button>(R.id.btn_pin_save).setOnClickListener {
            savePinned()
        }

        loadBusLines()
    }

    private fun loadBusLines() {
        if (busStopId.isBlank()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.widget_not_set_desc)
            listView.visibility = View.GONE
            return
        }

        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.main_searching)
        listView.visibility = View.GONE
        subtitleView.text = getString(R.string.pin_bus_subtitle)

        thread {
            val result = runCatching { api.fetchStopInfo(busStopId) }
            runOnUiThread {
                result.onSuccess { info ->
                    busItems = info.arrivals
                        .distinctBy { it.busNumber }
                        .sortedBy { it.busNumber }

                    if (busItems.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = getString(R.string.widget_no_arrival)
                        listView.visibility = View.GONE
                        return@onSuccess
                    }

                    val pinned = BusPinPrefs.getPinnedBusNumbers(this, busStopId)
                    adapter.clear()
                    adapter.addAll(busItems.map { item ->
                        if (item.direction.isBlank()) item.busNumber else "${item.busNumber} (${item.direction})"
                    })
                    adapter.notifyDataSetChanged()

                    busItems.forEachIndexed { index, item ->
                        if (pinned.contains(item.busNumber)) {
                            listView.setItemChecked(index, true)
                        }
                    }

                    emptyView.visibility = View.GONE
                    listView.visibility = View.VISIBLE
                }.onFailure {
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = getString(R.string.main_search_empty)
                    listView.visibility = View.GONE
                }
            }
        }
    }

    private fun savePinned() {
        if (busStopId.isBlank()) {
            finish()
            return
        }

        val selectedNumbers = mutableSetOf<String>()
        busItems.forEachIndexed { index, item ->
            if (listView.isItemChecked(index)) {
                selectedNumbers += item.busNumber
            }
        }

        BusPinPrefs.setPinnedBusNumbers(this, busStopId, selectedNumbers)
        BusWidgetProvider.refreshAllWidgets(this)
        Toast.makeText(this, R.string.pin_bus_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_BUS_STOP_ID = "extra_bus_stop_id"
        const val EXTRA_STOP_LABEL = "extra_stop_label"
    }
}
