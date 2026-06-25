package com.lurkki14.repotuli

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
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
import kotlinx.coroutines.launch

class StationActivity : AppCompatActivity() {
    private val measurementProxy = MeasurementProxy

    fun fromFiUnixTS(ts: ULong, tz: ZoneId): ZonedDateTime {
        val fiOffset = TimeZone.getTimeZone("Europe/Helsinki").getOffset(ts.toLong())
        val utcTS = ts - fiOffset.toUInt()
        return Instant.ofEpochMilli(utcTS.toLong()).atZone(tz)
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
        findViewById<TextView>(R.id.stationName).text = station?.name

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
                    measurementProxy.allMeasurementsFlow.collect { allMeasurements ->
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
