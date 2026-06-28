package com.lurkki14.repotuli

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

object MeasurementProxy : LifecycleEventObserver {
    private val DATA_URL =
        "https://cdn.fmi.fi/apps/magnetic-disturbance-observation-graphs/serve-data.php"
    private val CLASS_NAME = "MeasurementProxy"
    private val TIMESTAMP_IDX = 0
    private val VALUE_IDX = 1
    private val scope = CoroutineScope(Dispatchers.Main)

    private val updateJob: Job

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        // Restart update job
        if (event == Lifecycle.Event.ON_RESUME) {
            updateJob.cancel()
            scope.launch { updateLoop() }
        }
    }

    fun fiNow(): Long {
        val nowUTC = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() * 1000
        val fiOffset = TimeZone.getTimeZone("Europe/Helsinki").getOffset(nowUTC)
        return nowUTC + fiOffset
    }

    suspend fun updateLoop() {
        val delta = MeasurementCollector.nextExpectedTS.toLong() - fiNow()
        val delayMs = maxOf(delta, 0)
        delay(delayMs)
        updateMeasurements()
        updateLoop()
    }

    init {
        updateJob = scope.launch(start = CoroutineStart.LAZY) { updateLoop() }
        scope.launch {
            updateMeasurements()
            updateJob.start()
        }
    }

    fun fromJSONString(jsonString: String): Map<String, List<StationMeasurement>> {
        val jsonObject = JSONObject(jsonString)
        val measurements = mutableMapOf<String, List<StationMeasurement>>()

        for (key in jsonObject.keys()) {
            val stationData = jsonObject.getJSONObject(key)
            val dataSeries = stationData.getJSONArray("dataSeries")
            val stationMeasurements = mutableListOf<StationMeasurement>()

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
        // Check if something else is updating measurements
        if (fiNow().toULong() > MeasurementCollector.nextExpectedTS &&
                !MeasurementCollector.updateLock.isLocked)
            try {
                val jsonString = URL(DATA_URL).readText(Charsets.UTF_8)
                val newMeasurements = fromJSONString(jsonString)

                MeasurementCollector.pushNew(newMeasurements)
            } catch (e: Exception) {
                e.printStackTrace()
            }
    }

    /*private val allMeasurementsMut =
        MutableStateFlow<Map<String, List<StationMeasurement>>>(emptyMap())

    val allMeasurementsFlow = allMeasurementsMut.asStateFlow()
    private var latestMeasurementTS = 0UL*/
}
