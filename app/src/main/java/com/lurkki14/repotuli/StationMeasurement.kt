package com.lurkki14.repotuli

// https://cdn.fmi.fi/apps/magnetic-disturbance-observation-graphs/serve-data.php
data class StationMeasurement(
    val unixTS: ULong,
    val value: Double)
