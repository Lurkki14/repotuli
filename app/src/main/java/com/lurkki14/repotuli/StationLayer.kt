package com.lurkki14.repotuli

import android.content.Context
import android.content.Intent
import org.mapsforge.core.graphics.Align
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layer

class StationLayer(private val context: Context) : Layer() {
    override fun draw(
        boundingBox: BoundingBox?,
        zoomLevel: Byte,
        canvas: Canvas?,
        topLeftPoint: Point?,
        rotation: Rotation?
    ) {
        // Seems that drawing everything manually here is better to avoid spaghetti
        // (otherwise our instantiator would need to call us again)
        if (canvas == null || boundingBox == null || topLeftPoint == null) return

        // TODO: is it fine to hardcode these as pixel width?
        val tileSize = displayModel.tileSize
        // Marker outline
        val paintStroke: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color = 0xFF000000.toInt() // Black
            strokeWidth = 2f
            setStyle(Style.STROKE)
        }
        val paintText: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color = 0xFF000000.toInt() // Black
            setTextSize(32f)
            setTextAlign(Align.CENTER)
        }
        val paintFill: Paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color = 0xFFFF0000.toInt() // Red
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

                canvas.drawText(station.name, x.toInt(), y.toInt() - 35, paintText)
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
}
