package com.lurkki14.repotuli

import java.io.Serializable

data class Coordinates(
    val latitude: Double, val longitude: Double
): Serializable

// Structure from
// view-source:https://cdn.fmi.fi/apps/magnetic-disturbance-observation-graphs/index.php?embed=1&lang=fi&magDistObsStationParamName=station&station=MEK
data class Station(
    val code: String,
    val name: String,
    val lowThreshold: Int,
    val highThreshold: Int,
    val coordinates: Coordinates
) : Serializable {

    companion object {
        val AllStations = listOf(
            Station("KEV", "Kevo", 55, 165, Coordinates(69.76, 27.01)),
            Station("KIL", "Kilpisjärvi", 61, 210, Coordinates(69.02, 20.79)),
            Station("IVA", "Ivalo", 72, 275, Coordinates(68.56, 27.29)),
            Station("MUO", "Muonio", 75, 300, Coordinates(68.02, 23.53)),
            Station("SOD", "Sodankylä", 74, 290, Coordinates(67.37, 26.63)),
            Station("PEL", "Pello", 73, 285, Coordinates(66.90, 24.08)),
            Station("RAN", "Ranua", 70, 240, Coordinates(65.94, 26.51)),
            Station("OUJ", "Oulujärvi", 68, 200, Coordinates(64.52, 27.23)),
            Station("MEK", "Mekrijärvi", 64, 150, Coordinates(62.77, 30.97)),
            Station("HAN", "Hankasalmi", 63, 140, Coordinates(62.25, 26.60)),
            Station("NUR", "Nurmijärvi", 60, 120, Coordinates(60.51, 24.65)),
            Station("TAR", "Tartto", 55, 100, Coordinates(58.26, 26.46))
        )
    }
}
