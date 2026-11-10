package com.example.busapp

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var customSummaryView: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private var busStops: List<BusStopRegistry.RegisteredBusStop> = emptyList()

    private var selectedThemeIndex: Int = WidgetStylePrefs.THEME_SKY
    private var selectedLayoutIndex: Int = WidgetStylePrefs.LAYOUT_DEFAULT
    private var selectedFilterMinutes: Int = 0

    private val themeOptions by lazy {
        listOf(
            getString(R.string.widget_theme_sky),
            getString(R.string.widget_theme_mint),
            getString(R.string.widget_theme_mono)
        )
    }
    private val layoutOptions by lazy {
        listOf(
            getString(R.string.widget_layout_compact),
            getString(R.string.widget_layout_default),
            getString(R.string.widget_layout_expanded)
        )
    }
    private val filterOptions by lazy {
        listOf(
            0 to getString(R.string.widget_filter_all),
            5 to getString(R.string.widget_filter_5),
            10 to getString(R.string.widget_filter_10),
            15 to getString(R.string.widget_filter_15)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        listView = findViewById(R.id.lv_widget_stops)
        emptyView = findViewById(R.id.tv_widget_config_empty)
        customSummaryView = findViewById(R.id.tv_widget_custom_summary)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf())
        listView.adapter = adapter

        findViewById<ImageButton>(R.id.btn_widget_custom).setOnClickListener {
            showCustomDialog()
        }

        findViewById<View>(R.id.btn_open_main).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<View>(R.id.btn_save).setOnClickListener {
            val selectedPosition = listView.checkedItemPosition
            if (selectedPosition == ListView.INVALID_POSITION) {
                Toast.makeText(this, R.string.widget_settings_select_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val busStopId = busStops.getOrNull(selectedPosition)?.busStopId ?: return@setOnClickListener

            BusWidgetPrefs.setBusStopId(this, appWidgetId, busStopId)
            WidgetStylePrefs.setStyle(
                this,
                appWidgetId,
                WidgetStyle(
                    theme = selectedThemeIndex.coerceIn(0, themeOptions.lastIndex),
                    layoutMode = selectedLayoutIndex.coerceIn(0, layoutOptions.lastIndex),
                    maxArrivalMinutes = selectedFilterMinutes
                )
            )
            BusWidgetProvider.refreshFromConfig(this, appWidgetId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }

        refreshRegisteredStops()
        bindStyleFromPrefs()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            refreshRegisteredStops()
        }
    }

    private fun refreshRegisteredStops() {
        busStops = BusStopRegistry.getBusStops(this)
        val labels = busStops.map { "${it.displayName} (${it.busStopId})" }
        adapter.clear()
        adapter.addAll(labels)
        adapter.notifyDataSetChanged()

        val hasItems = busStops.isNotEmpty()
        listView.visibility = if (hasItems) View.VISIBLE else View.GONE
        emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE

        val currentSelected = BusWidgetPrefs.getBusStopId(this, appWidgetId)
        if (currentSelected != null) {
            val selectedIndex = busStops.indexOfFirst { it.busStopId == currentSelected }
            if (selectedIndex >= 0) {
                listView.setItemChecked(selectedIndex, true)
            }
        }
    }

    private fun bindStyleFromPrefs() {
        val style = WidgetStylePrefs.getStyle(this, appWidgetId)
        selectedThemeIndex = style.theme.coerceIn(0, themeOptions.lastIndex)
        selectedLayoutIndex = style.layoutMode.coerceIn(0, layoutOptions.lastIndex)
        selectedFilterMinutes = style.maxArrivalMinutes
        updateCustomSummary()
    }

    private fun showCustomDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_widget_custom, null)
        val themeSpinner = dialogView.findViewById<Spinner>(R.id.sp_widget_theme_dialog)
        val layoutSpinner = dialogView.findViewById<Spinner>(R.id.sp_widget_layout_dialog)
        val filterSpinner = dialogView.findViewById<Spinner>(R.id.sp_widget_filter_dialog)
        val previewView = dialogView.findViewById<TextView>(R.id.tv_widget_preview_dialog)

        themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themeOptions)
        layoutSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, layoutOptions)
        filterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterOptions.map { it.second })

        themeSpinner.setSelection(selectedThemeIndex)
        layoutSpinner.setSelection(selectedLayoutIndex)
        val filterIndex = filterOptions.indexOfFirst { it.first == selectedFilterMinutes }.takeIf { it >= 0 } ?: 0
        filterSpinner.setSelection(filterIndex)

        val watcher = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val themeText = themeOptions[themeSpinner.selectedItemPosition.coerceIn(0, themeOptions.lastIndex)]
                val layoutText = layoutOptions[layoutSpinner.selectedItemPosition.coerceIn(0, layoutOptions.lastIndex)]
                val filterText = filterOptions[filterSpinner.selectedItemPosition.coerceIn(0, filterOptions.lastIndex)].second
                previewView.text = getString(R.string.widget_custom_preview_format, themeText, layoutText, filterText)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        themeSpinner.onItemSelectedListener = watcher
        layoutSpinner.onItemSelectedListener = watcher
        filterSpinner.onItemSelectedListener = watcher
        watcher.onItemSelected(null, null, 0, 0)

        AlertDialog.Builder(this)
            .setTitle(R.string.widget_custom_dialog_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.widget_custom_apply) { _, _ ->
                selectedThemeIndex = themeSpinner.selectedItemPosition.coerceIn(0, themeOptions.lastIndex)
                selectedLayoutIndex = layoutSpinner.selectedItemPosition.coerceIn(0, layoutOptions.lastIndex)
                selectedFilterMinutes = filterOptions[filterSpinner.selectedItemPosition.coerceIn(0, filterOptions.lastIndex)].first
                updateCustomSummary()
            }
            .show()
    }

    private fun updateCustomSummary() {
        val themeText = themeOptions[selectedThemeIndex.coerceIn(0, themeOptions.lastIndex)]
        val layoutText = layoutOptions[selectedLayoutIndex.coerceIn(0, layoutOptions.lastIndex)]
        val filterText = filterOptions.firstOrNull { it.first == selectedFilterMinutes }?.second
            ?: filterOptions.first().second
        customSummaryView.text = getString(R.string.widget_custom_preview_format, themeText, layoutText, filterText)
    }
}
