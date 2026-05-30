package com.lurkki14.repotuli

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var tileCache: TileCache
    private lateinit var tileLayer: TileDownloadLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidGraphicFactory.createInstance(application)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Mapsforge
        mapView = findViewById(R.id.mapView)
        mapView.isClickable = true
        mapView.mapScaleBar.isVisible = true
        mapView.setBuiltInZoomControls(true)
        mapView.mapZoomControls.setZoomLevelMin(1.toByte())
        mapView.mapZoomControls.setZoomLevelMax(20.toByte())

        tileCache = AndroidUtil.createTileCache(
            this, "repotuli-tile-cache",
            mapView.model.displayModel.tileSize, 1f,
            mapView.model.frameBufferModel.overdrawFactor
        )

        val tileSource = OpenStreetMapMapnik.INSTANCE
        // OSM requires a unique user agent to download tiles
        tileSource.setUserAgent("Repotuli/1.0 (https://github.com/lurkki14/repotuli)")

        tileLayer = TileDownloadLayer(
            tileCache, mapView.model.mapViewPosition, tileSource,
            AndroidGraphicFactory.INSTANCE
        )
        mapView.layerManager.layers.add(tileLayer)

        mapView.model.mapViewPosition.zoomLevel = 5.toByte()
        mapView.model.mapViewPosition.center = LatLong(60.1699, 24.9384) // Helsinki

        mapView.layerManager.layers.add(StationLayer(this, this, mapView))

        // Notify about ON_RESUME
        lifecycle.addObserver(MeasurementProxy)
    }

    // Map seems to seize downloading layers without these
    override fun onResume() {
        super.onResume()
        tileLayer.onResume()
    }

    override fun onPause() {
        tileLayer.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView.destroyAll()
        AndroidGraphicFactory.clearResourceMemoryCache()
        super.onDestroy()
    }
}
