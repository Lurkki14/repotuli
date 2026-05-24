package com.lurkki14.repotuli

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.TimeZone


typealias StationCode = String

object MeasurementProxy {

    private val DATA_URL =
        "https://cdn.fmi.fi/apps/magnetic-disturbance-observation-graphs/serve-data.php"
    private val CLASS_NAME = "MeasurementProxy"
    private val TIMESTAMP_IDX = 0
    private val VALUE_IDX = 1

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

            synchronized(allMeasurements) {
                allMeasurements.clear()
                allMeasurements.putAll(newMeasurements)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getMeasurementsForStation(code: String): List<StationMeasurement>? {
        // Since the timestamps are Unix timestamps, but converted into Finnish timezone (WTF?)
        // calculate time elapsed relative to Finnish timezone
        // Epoch s -> epoch ms
        val nowUTC = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() * 1000
        val fiOffset = TimeZone.getTimeZone("Europe/Helsinki").getOffset(nowUTC)

        val fiNow = nowUTC + fiOffset

        // If we are more than 5 minutes ahead of latest measurement, run the update
        val interval = 300000UL
        if (fiNow.toULong() > (latestMeasurementTS + interval)) {
            updateMeasurements()

            allMeasurements.forEach { _, measurements ->
                val latest = measurements.lastOrNull()
                latest?.unixTS?.let {
                    if (it > latestMeasurementTS)
                        latestMeasurementTS = it
                }
            }
        }

        return allMeasurements[code]
    }

    private val allMeasurements = mutableMapOf<StationCode, List<StationMeasurement>>()
    private var latestMeasurementTS = 0UL
}
