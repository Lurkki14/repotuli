package com.lurkki14.repotuli

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.mapsforge.core.graphics.*
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.view.MapView

class StationLayer(private val context: Context, parent: AppCompatActivity, mapView: MapView) :
    Layer() {
    init {
        // Follow activity's lifecycle
        parent.lifecycleScope.launch {
            parent.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MeasurementProxy.allMeasurementsFlow.collect { allMeasurements_ ->
                    allMeasurements = allMeasurements_
                    // Repaint in case map isn't moved
                    mapView.repaint()
                }
            }
        }
    }

    override fun draw(
        boundingBox: BoundingBox?,
        zoomLevel: Byte,
        canvas: Canvas?,
        topLeftPoint: Point?,
        rotation: Rotation?
    ) {
        val AGF = AndroidGraphicFactory.INSTANCE
        // Seems that drawing everything manually here is better to avoid spaghetti
        // (otherwise our instantiator would need to call us again)
        if (canvas == null || boundingBox == null || topLeftPoint == null) return

        // TODO: is it fine to hardcode these as pixel width?
        val tileSize = displayModel.tileSize
        // Marker outline
        val paintStroke: Paint = AGF.createPaint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            setStyle(Style.STROKE)
        }
        val paintText: Paint = AGF.createPaint().apply {
            color = Color.BLACK
            setTextSize(32f)
            setTextAlign(Align.CENTER)
            setTypeface(FontFamily.DEFAULT, FontStyle.BOLD)
        }
        val paintTextOutline = AGF.createPaint().apply {
            color = Color.WHITE
            setTextSize(32f)
            setTextAlign(Align.CENTER)
            setStyle(Style.STROKE)
            setTypeface(FontFamily.DEFAULT, FontStyle.BOLD)
            strokeWidth = 5f
        }
        val paintFill: Paint = AGF.createPaint().apply {
            color = Color.RED
            setStyle(Style.FILL)
        }

        Station.AllStations.forEach { station ->
            val latLong = LatLong(station.coordinates.latitude, station.coordinates.longitude)
            if (boundingBox.contains(latLong)) {
                val x = MercatorProjection.longitudeToPixelX(
                    latLong.longitude,
                    zoomLevel,
                    tileSize
                ) - topLeftPoint.x
                val y = MercatorProjection.latitudeToPixelY(
                    latLong.latitude,
                    zoomLevel,
                    tileSize
                ) - topLeftPoint.y

                canvas.drawCircle(x.toInt(), y.toInt(), 25, paintFill)
                canvas.drawCircle(x.toInt(), y.toInt(), 25, paintStroke)

                var measurementString = ""
                allMeasurements[station.code]?.lastOrNull()?.value?.let {
                    measurementString = "%.2f".format(it)
                }
                // Draw white outline first
                canvas.drawText(station.name, x.toInt(), y.toInt() - 35, paintTextOutline)
                canvas.drawText(measurementString, x.toInt(), y.toInt() + 60, paintTextOutline)

                canvas.drawText(station.name, x.toInt(), y.toInt() - 35, paintText)
                canvas.drawText(measurementString, x.toInt(), y.toInt() + 60, paintText)
            }
        }
    }

    override fun onTap(tapLatLong: LatLong?, layerXY: Point?, tapXY: Point?): Boolean {
        if (tapLatLong == null) return false

        // Degrees (around 55 km)
        val threshold = 0.5

        // Find the first station in the list that is within threshold
        // TODO: is more optimised search needed?
        val clickedStation = Station.AllStations.firstOrNull { station ->
            val stationLatLong = LatLong(
                station.coordinates.latitude,
                station.coordinates.longitude
            )
            LatLongUtils.distance(stationLatLong, tapLatLong) <= threshold
        }

        if (clickedStation != null) {
            val intent = Intent(context, StationActivity::class.java)
            intent.putExtra("station", clickedStation)
            context.startActivity(intent)
            return true // Consume event
        }
        return false // Propagate event
    }

    private var allMeasurements = mapOf<StationCode, List<StationMeasurement>>()
}
