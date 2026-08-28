package com.lurkki14.repotuli

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Central object for different updaters to push measurements to,
// so UI can fetch from one place
object MeasurementCollector {
    suspend fun pushNew(measurements: Map<StationCode, List<StationMeasurement>>) {
        updateLock.withLock {
            val current = allMeasurementsMut.value.toMutableMap()
            allMeasurementsMut.value = current

            allMeasurementsMut.value = measurements
            allMeasurementsMut.value.forEach { (_, measurements) ->
                val latest = measurements.lastOrNull()
                latest?.unixTS?.let {
                    if (it > latestMeasurementTS)
                        latestMeasurementTS = it
                }
            }
            nextExpectedTS = latestMeasurementTS + (300_000UL + 10_000UL) // 5m 10s
        }
    }

    val updateLock = Mutex()
    private val allMeasurementsMut =
        MutableStateFlow<Map<StationCode, List<StationMeasurement>>>(emptyMap())

    val allMeasurementsFlow = allMeasurementsMut.asStateFlow()
    public var latestMeasurementTS = 0UL
    public var nextExpectedTS = 0UL
}
