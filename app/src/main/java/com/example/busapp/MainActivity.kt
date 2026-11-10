package com.example.busapp

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var inputKeyword: EditText
    private lateinit var listSearchResult: ListView
    private lateinit var listRegistered: ListView
    private lateinit var textSearchEmpty: TextView
    private lateinit var textRegisteredEmpty: TextView
    private lateinit var searchAdapter: ArrayAdapter<String>
    private lateinit var registeredAdapter: ArrayAdapter<String>

    private var searchedStops: List<BusStopSearchItem> = emptyList()
    private var registeredStops: List<BusStopRegistry.RegisteredBusStop> = emptyList()
    private val api = KakaoBusApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputKeyword = findViewById(R.id.et_main_keyword)
        listSearchResult = findViewById(R.id.lv_search_results)
        listRegistered = findViewById(R.id.lv_registered_stops)
        textSearchEmpty = findViewById(R.id.tv_main_search_empty)
        textRegisteredEmpty = findViewById(R.id.tv_main_registered_empty)
        val searchButton = findViewById<Button>(R.id.btn_search_bus_stop)

        searchAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        registeredAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listSearchResult.adapter = searchAdapter
        listRegistered.adapter = registeredAdapter

        searchButton.setOnClickListener {
            val keyword = inputKeyword.text.toString().trim()
            if (keyword.isBlank()) {
                Toast.makeText(this, R.string.main_invalid_search_keyword, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            searchBusStops(keyword)
        }

        listSearchResult.setOnItemClickListener { _, _, position, _ ->
            val stop = searchedStops.getOrNull(position) ?: return@setOnItemClickListener
            BusStopRegistry.addBusStop(this, stop.busStopId, stop.displayName)
            refreshRegisteredStops()
            BusWidgetProvider.refreshAllWidgets(this)
            Toast.makeText(this, getString(R.string.main_added_bus_stop, stop.displayName), Toast.LENGTH_SHORT).show()
        }

        listRegistered.setOnItemLongClickListener { _, _, position, _ ->
            val stop = registeredStops.getOrNull(position) ?: return@setOnItemLongClickListener true
            BusStopRegistry.removeBusStopId(this, stop.busStopId)
            refreshRegisteredStops()
            BusWidgetProvider.refreshAllWidgets(this)
            Toast.makeText(this, getString(R.string.main_removed_bus_stop, stop.displayName), Toast.LENGTH_SHORT).show()
            true
        }

        listRegistered.setOnItemClickListener { _, _, position, _ ->
            val stop = registeredStops.getOrNull(position) ?: return@setOnItemClickListener
            val intent = Intent(this, BusPinConfigActivity::class.java).apply {
                putExtra(BusPinConfigActivity.EXTRA_BUS_STOP_ID, stop.busStopId)
                putExtra(BusPinConfigActivity.EXTRA_STOP_LABEL, stop.displayName)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRegisteredStops()
    }

    private fun searchBusStops(keyword: String) {
        textSearchEmpty.visibility = View.VISIBLE
        textSearchEmpty.text = getString(R.string.main_searching)
        listSearchResult.visibility = View.GONE
        searchedStops = emptyList()
        searchAdapter.clear()
        searchAdapter.notifyDataSetChanged()

        thread {
            val result = try {
                api.searchBusStops(keyword)
            } catch (_: Exception) {
                emptyList()
            }

            runOnUiThread {
                searchedStops = result
                searchAdapter.clear()
                searchAdapter.addAll(result.map { it.displayName })
                searchAdapter.notifyDataSetChanged()

                if (result.isEmpty()) {
                    textSearchEmpty.visibility = View.VISIBLE
                    textSearchEmpty.text = getString(R.string.main_search_empty)
                    listSearchResult.visibility = View.GONE
                } else {
                    textSearchEmpty.visibility = View.GONE
                    listSearchResult.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun refreshRegisteredStops() {
        registeredStops = BusStopRegistry.getBusStops(this)
        registeredAdapter.clear()
        registeredAdapter.addAll(registeredStops.map { it.displayName })
        registeredAdapter.notifyDataSetChanged()

        val hasItems = registeredStops.isNotEmpty()
        textRegisteredEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        listRegistered.visibility = if (hasItems) View.VISIBLE else View.GONE
    }
}
