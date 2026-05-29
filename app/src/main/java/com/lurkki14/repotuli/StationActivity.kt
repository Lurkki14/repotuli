package com.lurkki14.repotuli

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class StationActivity : AppCompatActivity() {
    private val measurementProxy = MeasurementProxy

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

        val latestReadingView = findViewById<TextView>(R.id.latestReading)

        if (station != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    measurementProxy.allMeasurementsFlow.collect { allMeasurements ->
                        val measurements = allMeasurements[station.code]
                        val latestValue = measurements?.lastOrNull()?.value
                        latestReadingView.text = if (latestValue != null) {
                            getString(R.string.latest_measurement, latestValue)
                        } else {
                            getString(R.string.latest_measurement_error)
                        }
                    }
                }
            }
        }
    }
}
