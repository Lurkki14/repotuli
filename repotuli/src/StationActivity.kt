package com.lurkki14.repotuli

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.lurkki14.repotuli.NotificationHandler.notificationSettingsStore
import com.patrykandpatrick.vico.views.cartesian.*
import com.patrykandpatrick.vico.views.cartesian.Scroll
import com.patrykandpatrick.vico.views.cartesian.ScrollHandler
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis.Companion.bottom
import com.patrykandpatrick.vico.views.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.views.cartesian.data.columnModel
import com.patrykandpatrick.vico.views.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.views.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.views.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.views.common.Fill
import com.patrykandpatrick.vico.views.common.Insets
import com.patrykandpatrick.vico.views.common.component.LineComponent
import com.patrykandpatrick.vico.views.common.component.TextComponent
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

enum class AlertLevel {
    Medium, High, Custom
}

class StationActivity : AppCompatActivity() {
    private val measurementProxy = MeasurementProxy
    private var station: Station? = null
    private var selectedLevel = AlertLevel.High
    private lateinit var customThresholdInput: EditText
    private lateinit var stationNotifyToggle: MaterialSwitch

    fun fromFiUnixTS(ts: ULong, tz: ZoneId): ZonedDateTime {
        val fiOffset = TimeZone.getTimeZone("Europe/Helsinki").getOffset(ts.toLong())
        val utcTS = ts - fiOffset.toUInt()
        return Instant.ofEpochMilli(utcTS.toLong()).atZone(tz)
    }

    // For some reason this needs to be a top-level definition,
    // even though it's not part of AppCompatActivity
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
        }
    }

    private var settingsContinuation: Continuation<Boolean>? = null
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        settingsContinuation?.resume(true)
        settingsContinuation = null
    }

    fun hasNotifPerms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        // 32 and below don't need specific permission
            return true
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED)
    }

    fun requestNotifPerms() {
        if (!hasNotifPerms())
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private suspend fun showPermissionDialog(): Boolean =
        // Wait until the user chooses an action, so notification settings are shown if granted
        suspendCancellableCoroutine { continuation ->
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(R.string.permission_dialog_message)
                .setPositiveButton(R.string.permission_dialog_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    settingsContinuation = continuation
                    settingsLauncher.launch(intent)
                }
                .setNegativeButton(R.string.permission_dialog_cancel) { dialog, _ ->
                    dialog.dismiss()
                    continuation.resume(false)
                }
                .setOnCancelListener {
                    continuation.resume(false)
                }
                .show()
        }

    private suspend fun saveStationSetting(
        station: Station?,
        level: AlertLevel,
        input: EditText
    ) {
        station?.let {
            val threshold = when (level) {
                AlertLevel.Medium -> station.lowThreshold
                AlertLevel.High -> station.highThreshold
                AlertLevel.Custom -> input.text.toString()
                    .toDoubleOrNull() ?: station.highThreshold
            }

            val setting = StationSetting.newBuilder()
                .setCode(station.code)
                .setThreshold(threshold)
                .build()
            NotificationHandler.addStation(this, setting)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::stationNotifyToggle.isInitialized && stationNotifyToggle.isChecked) {
            lifecycleScope.launch {
                saveStationSetting(station, selectedLevel, customThresholdInput)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_station)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Notify about ON_RESUME
        lifecycle.addObserver(MeasurementProxy)

        // Required under API 33
        @Suppress("DEPRECATION")
        val station = intent.getSerializableExtra("station") as? Station
        this@StationActivity.station = station
        findViewById<TextView>(R.id.stationName).text = station?.name

        val alertSettingsContainer = findViewById<LinearLayout>(R.id.alertSettingsContainer)
        val alertLevelDropdown = findViewById<AutoCompleteTextView>(R.id.alertLevelDropdown)
        val customThresholdLayout = findViewById<View>(R.id.customThresholdLayout)
        customThresholdInput = findViewById(R.id.customThresholdInput)
        stationNotifyToggle = findViewById(R.id.stationNotifyToggle)
        if (station != null) {
            val options = AlertLevel.entries.map { level ->
                when (level) {
                    AlertLevel.Medium -> getString(
                        R.string.alert_level_medium,
                        station.lowThreshold
                    )

                    AlertLevel.High -> getString(R.string.alert_level_high, station.highThreshold)
                    AlertLevel.Custom -> getString(R.string.alert_level_custom)
                }
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options)
            alertLevelDropdown.setAdapter(adapter)

            lifecycleScope.launch {
                val settings = notificationSettingsStore.data.first()
                val setting = settings.stationsList.find { it.code == station.code }
                if (setting != null) {
                    val threshold = setting.threshold
                    selectedLevel = when (threshold) {
                        station.lowThreshold -> AlertLevel.Medium
                        station.highThreshold -> AlertLevel.High
                        else -> AlertLevel.Custom
                    }

                    stationNotifyToggle.isChecked = true
                    alertSettingsContainer.visibility = View.VISIBLE
                    alertLevelDropdown.setText(options[selectedLevel.ordinal], false)
                    if (selectedLevel == AlertLevel.Custom) {
                        customThresholdLayout.visibility = View.VISIBLE
                        customThresholdInput.setText(threshold.toString())
                    }
                } else {
                    alertLevelDropdown.setText(options[selectedLevel.ordinal], false)
                }
            }
        }

        alertLevelDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedLevel = AlertLevel.entries[position]
            customThresholdLayout.visibility =
                if (selectedLevel == AlertLevel.Custom) View.VISIBLE else View.GONE
        }

        stationNotifyToggle.setOnCheckedChangeListener { _, isChecked ->
            alertSettingsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE

            lifecycleScope.launch {
                if (isChecked) {
                    requestNotifPerms()
                    if (!hasNotifPerms()) {
                        showPermissionDialog()
                        if (!hasNotifPerms()) {
                            stationNotifyToggle.isChecked = false
                        }
                    }
                } else {
                    this@StationActivity.station?.let {
                        NotificationHandler.removeStation(
                            this@StationActivity,
                            it
                        )
                    }
                }
            }
        }

        val chartView = findViewById<CartesianChartView>(R.id.chartView)
        chartView.scrollHandler = ScrollHandler(
            initialScroll = Scroll.Absolute.End,
            autoScrollCondition = AutoScrollCondition.OnModelGrowth
        )
        val textColor = MaterialColors.getColor(chartView, android.R.attr.textColorPrimary)
        chartView.chart = CartesianChart(
            ColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    LineComponent(
                        fill = Fill(Color.RED),
                        thicknessDp = 8f
                    )
                ),
                columnCollectionSpacingDp = 0f
            ),
            marker = DefaultCartesianMarker(
                label = TextComponent(
                    color = textColor,
                    textSizeSp = 12f,
                ),
                valueFormatter = { _, targets ->
                    targets.joinToString { target ->
                        // TODO: not locale-aware
                        val time = fromFiUnixTS(target.x.toLong().toULong(), ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
                        val value = "%.2f".format(
                            (target as? ColumnCartesianLayerMarkerTarget)
                                ?.columns?.firstOrNull()?.entry?.y
                                ?: 0.0
                        )
                        "$time – $value"
                    }
                }
            ),
            startAxis = VerticalAxis.start(
                title = { _ -> getString(R.string.measurement_chart_y_axis_legend) },
                titleComponent = TextComponent(color = textColor, textSizeSp = 12f)
            ),
            bottomAxis = bottom(
                valueFormatter = { _, x, _ ->
                    fromFiUnixTS(x.toLong().toULong(), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
                },
                label = TextComponent(
                    color = textColor,
                    margins = Insets(horizontalDp = 16f),
                    textSizeSp = 10f,
                    truncateAt = null
                ),
            )
        )
        val modelProducer = CartesianChartModelProducer()
        chartView.modelProducer = modelProducer

        if (station != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    MeasurementCollector.allMeasurementsFlow.collect { allMeasurements ->
                        val measurements = allMeasurements[station.code] ?: emptyList()

                        // FMI API gives last 300 measurements
                        val lastN = measurements.takeLast(300)

                        if (!lastN.isEmpty())
                            modelProducer.runTransaction {
                                columnModel {
                                    series(
                                        x = lastN.map { it.unixTS.toLong() },
                                        y = lastN.map { it.value }
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
