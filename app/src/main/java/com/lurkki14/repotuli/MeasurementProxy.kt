package com.lurkki14.repotuli

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.coroutines.EmptyCoroutineContext

typealias StationCode = String

object MeasurementProxy : LifecycleEventObserver {
    private val DATA_URL =
        "https://cdn.fmi.fi/apps/magnetic-disturbance-observation-graphs/serve-data.php"
    private val CLASS_NAME = "MeasurementProxy"
    private val TIMESTAMP_IDX = 0
    private val VALUE_IDX = 1
    private val scope = CoroutineScope(Dispatchers.Main)

    // Don't run on init
    private var updateJob =
        scope.launch(EmptyCoroutineContext, CoroutineStart.LAZY) { updateLoop() }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        // Restart update job
        if (event == Lifecycle.Event.ON_RESUME) {
            updateJob.cancel()
            updateJob = scope.launch { updateLoop() }
        }
    }

    suspend fun updateLoop() {
        // Since the timestamps are Unix timestamps, but converted into Finnish timezone (WTF?)
        // calculate time elapsed relative to Finnish timezone
        // Epoch s -> epoch ms
        val nowUTC = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() * 1000
        val fiOffset = TimeZone.getTimeZone("Europe/Helsinki").getOffset(nowUTC)
        val fiNow = nowUTC + fiOffset
        // Time until we think new measurements are available (5m 10s)
        val nextExpectedTS = latestMeasurementTS + (300_000UL + 10_000UL)
        val delta = nextExpectedTS.toLong() - fiNow
        // If past nextExpectedTS, update immediately
        val delayMs = maxOf(delta, 0)
        delay(delayMs)
        updateMeasurements()
        updateLoop()
    }

    init {
        // Unconditional update on init to keep updateLoop logic cleaner
        scope.launch {
            updateMeasurements()
            updateJob.start()
        }
    }

    // TODO: tests for this function
    fun fromJSONString(jsonString: String): Map<StationCode, List<StationMeasurement>> {
        val jsonObject = JSONObject(jsonString)
        val measurements = mutableMapOf<String, List<StationMeasurement>>()

        for (key in jsonObject.keys()) {
            val stationData = jsonObject.getJSONObject(key)
            // Actual array of measurements
            val dataSeries = stationData.getJSONArray("dataSeries")
            val stationMeasurements = mutableListOf<StationMeasurement>()

            // Don't spam errors in parsing loop
            var exception: JSONException? = null
            for (i in 0 until dataSeries.length()) {
                try {
                    val entry = dataSeries.getJSONArray(i)
                    val timestamp = entry.getLong(TIMESTAMP_IDX)
                    val value = entry.getDouble(VALUE_IDX)
                    stationMeasurements.add(StationMeasurement(timestamp.toULong(), value))
                } catch (e: JSONException) {
                    exception = e
                }
            }
            if (exception != null) {
                Log.i(CLASS_NAME, "Error parsing JSON for station $key", exception)
                exception = null
            }
            measurements[key] = stationMeasurements
        }
        return measurements
    }

    suspend fun updateMeasurements() = withContext(Dispatchers.IO) {
        try {
            val jsonString = URL(DATA_URL).readText(Charsets.UTF_8)
            val newMeasurements = fromJSONString(jsonString)

            allMeasurementsMut.value = newMeasurements
            allMeasurementsMut.value.forEach { (_, measurements) ->
                val latest = measurements.lastOrNull()
                latest?.unixTS?.let {
                    if (it > latestMeasurementTS)
                        latestMeasurementTS = it
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMeasurementsForStation(code: String): List<StationMeasurement>? {
        return allMeasurementsMut.value[code]
    }

    private val allMeasurementsMut =
        MutableStateFlow<Map<StationCode, List<StationMeasurement>>>(emptyMap())

    // There seems to be some magic happening wrt. this,
    // .collect not called when values haven't changed?
    val allMeasurementsFlow = allMeasurementsMut.asStateFlow()
    private var latestMeasurementTS = 0UL
}
