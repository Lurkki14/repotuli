package com.lurkki14.repotuli

import java.io.Serializable

// Structure from
// view-source:https://cdn.fmi.fi/apps/magnetic-disturbance-observation-graphs/index.php?embed=1&lang=fi&magDistObsStationParamName=station&station=MEK
data class Station(
    val code: String,
    val name: String,
    val lowThreshold: Int,
    val highThreshold: Int) : Serializable
