package com.pasajeya.app.core.map

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pasajeya.app.feature.fare.domain.RouteStop
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val COLOR_ROUTE = 0xFF1F6FB2.toInt()
private const val COLOR_STOP = 0xFF6E7A87.toInt()
private const val COLOR_BOARDING = 0xFF2E9E5B.toInt()
private const val COLOR_ALIGHTING = 0xFFD64545.toInt()
private const val COLOR_USER = 0xFFE0A100.toInt()

/** Posición real del usuario (GPS) para dibujarla sobre el mapa. */
data class MapUserPosition(val lat: Double, val lng: Double)

/**
 * Mapa (OpenStreetMap vía osmdroid) con la ruta, sus paraderos y, opcionalmente, el paradero de
 * origen/destino resaltados y la posición del usuario.
 */
@Composable
fun RouteMap(
    stops: List<RouteStop>,
    description: String,
    modifier: Modifier = Modifier,
    boardingIndex: Int? = null,
    alightingIndex: Int? = null,
    userPosition: MapUserPosition? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            controller.setZoom(13.0)
            // El mapa vive dentro de una columna con scroll: se queda con los gestos.
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.semantics { contentDescription = description },
        update = { view ->
            view.overlays.clear()
            view.overlays.add(CopyrightOverlay(view.context))

            val points = stops.map { GeoPoint(it.lat, it.lng) }
            if (points.size >= 2) {
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(points)
                        outlinePaint.color = COLOR_ROUTE
                        outlinePaint.strokeWidth = 9f
                    }
                )
            }
            stops.forEachIndexed { index, stop ->
                val color = when (index) {
                    boardingIndex -> COLOR_BOARDING
                    alightingIndex -> COLOR_ALIGHTING
                    else -> COLOR_STOP
                }
                val highlighted = index == boardingIndex || index == alightingIndex
                view.overlays.add(
                    stopMarker(view, stop.name, stop.lat, stop.lng, color, highlighted)
                )
            }
            userPosition?.let {
                view.overlays.add(
                    stopMarker(view, "", it.lat, it.lng, COLOR_USER, true, sizeDp = 18)
                )
            }
            view.invalidate()

            // Reencuadra solo cuando cambia la ruta, no con cada lectura de GPS.
            val key = stops.hashCode()
            if (view.tag != key && points.isNotEmpty()) {
                view.tag = key
                val allPoints = if (userPosition != null) {
                    points + GeoPoint(userPosition.lat, userPosition.lng)
                } else {
                    points
                }
                val fit = {
                    if (allPoints.size == 1) {
                        view.controller.setZoom(16.0)
                        view.controller.setCenter(allPoints.first())
                    } else {
                        view.zoomToBoundingBox(
                            BoundingBox.fromGeoPoints(allPoints),
                            false,
                            96
                        )
                    }
                }
                if (view.width > 0 && view.height > 0) {
                    fit()
                } else {
                    view.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
                }
            }
        }
    )
}

private fun stopMarker(
    view: MapView,
    title: String,
    lat: Double,
    lng: Double,
    color: Int,
    highlighted: Boolean,
    sizeDp: Int = if (highlighted) 16 else 10
): Marker {
    val density = view.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val dot = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((2 * density).toInt(), AndroidColor.WHITE)
        setSize(sizePx, sizePx)
    }
    return Marker(view).apply {
        position = GeoPoint(lat, lng)
        icon = dot
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        this.title = title
        if (title.isEmpty()) setInfoWindow(null)
    }
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = context.packageName
    val base = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}
