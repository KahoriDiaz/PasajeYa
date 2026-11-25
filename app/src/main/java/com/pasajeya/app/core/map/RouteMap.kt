package com.pasajeya.app.core.map

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pasajeya.app.R
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

// Mismos colores que el resto de la app.
private const val COLOR_ROUTE = 0xFF006ADA.toInt()
private const val COLOR_ROUTE_CASING = 0xFFFFFFFF.toInt()
private const val COLOR_STOP = 0xFF9DB2CE.toInt()
private const val COLOR_BOARDING = 0xFF10B981.toInt()
private const val COLOR_ALIGHTING = 0xFFE11D48.toInt()
private const val COLOR_USER = 0xFFFFA500.toInt()

/** Posición real del usuario (GPS) para dibujarla sobre el mapa. */
data class MapUserPosition(val lat: Double, val lng: Double)

/**
 * Mapa (OpenStreetMap vía osmdroid) con la ruta, sus paraderos y, opcionalmente, el paradero de
 * origen/destino resaltados y la posición del usuario.
 *
 * @param targetPosition cuando cambia, el mapa se centra con [MapController.animateTo].
 * @param busPosition posición opcional del autobús (marcador azul) distinta de la del usuario.
 */
@Composable
fun RouteMap(
    stops: List<RouteStop>,
    description: String,
    modifier: Modifier = Modifier,
    boardingIndex: Int? = null,
    alightingIndex: Int? = null,
    userPosition: MapUserPosition? = null,
    targetPosition: MapUserPosition? = null,
    busPosition: MapUserPosition? = null,
    /** Al cambiar, el mapa vuelve a encuadrar la ruta completa. */
    fitRequest: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // El mapa de OpenStreetMap viene muy cargado de color y tapa la ruta. Se baja
            // la saturacion y se aclara un poco para que la linea y los paraderos resalten.
            overlayManager.tilesOverlay.setColorFilter(cleanMapFilter())
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

    // Volver a encuadrar la ruta cuando la persona toca el boton de centrar.
    LaunchedEffect(fitRequest, stops) {
        if (fitRequest > 0 && stops.size >= 2) {
            runCatching {
                mapView.zoomToBoundingBox(
                    BoundingBox.fromGeoPoints(stops.map { GeoPoint(it.lat, it.lng) }),
                    true,
                    96
                )
            }
        }
    }

    // Centrar el mapa en una posición objetivo cuando esta cambie.
    LaunchedEffect(targetPosition) {
        targetPosition?.let { pos ->
            mapView.controller.animateTo(GeoPoint(pos.lat, pos.lng))
            mapView.controller.setZoom(16.0)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.semantics { contentDescription = description },
        update = { view ->
            view.overlays.clear()
            view.overlays.add(CopyrightOverlay(view.context))

            val density = view.resources.displayMetrics.density
            val points = stops.map { GeoPoint(it.lat, it.lng) }
            if (points.size >= 2) {
                // Linea blanca debajo: hace que la ruta se despegue del mapa.
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(points)
                        outlinePaint.color = COLOR_ROUTE_CASING
                        outlinePaint.strokeWidth = 11f * density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.isAntiAlias = true
                    }
                )
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(points)
                        outlinePaint.color = COLOR_ROUTE
                        outlinePaint.strokeWidth = 6f * density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.isAntiAlias = true
                    }
                )
            }
            stops.forEachIndexed { index, stop ->
                // Los paraderos del viaje llevan pin; el resto, un punto discreto.
                when (index) {
                    boardingIndex -> view.overlays.add(
                        pinMarker(view, stop.name, stop.lat, stop.lng, COLOR_BOARDING)
                    )
                    alightingIndex -> view.overlays.add(
                        pinMarker(view, stop.name, stop.lat, stop.lng, COLOR_ALIGHTING)
                    )
                    else -> view.overlays.add(
                        stopMarker(view, stop.name, stop.lat, stop.lng, COLOR_STOP, false)
                    )
                }
            }
            userPosition?.let {
                view.overlays.add(
                    stopMarker(view, "", it.lat, it.lng, COLOR_USER, true, sizeDp = 18)
                )
            }
            busPosition?.let {
                view.overlays.add(busMarker(view, it.lat, it.lng))
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

/** Pin de paradero, para la subida y la bajada del viaje. */
private fun pinMarker(
    view: MapView,
    title: String,
    lat: Double,
    lng: Double,
    color: Int
): Marker {
    val density = view.resources.displayMetrics.density
    val width = (26 * density).toInt()
    val height = (34 * density).toInt()
    val base = ContextCompat.getDrawable(view.context, R.drawable.ic_map_pin_base)
    val pin = ContextCompat.getDrawable(view.context, R.drawable.ic_map_pin)?.mutate()
    pin?.setTint(color)
    val icon = if (base != null && pin != null) {
        LayerDrawable(arrayOf(base, pin))
    } else {
        pin ?: base
    }
    icon?.setBounds(0, 0, width, height)
    return Marker(view).apply {
        position = GeoPoint(lat, lng)
        this.icon = icon
        // La punta del pin marca el paradero.
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        this.title = title
        if (title.isEmpty()) setInfoWindow(null)
    }
}

/**
 * Filtro de color del mapa base: menos saturacion y un poco mas de luz, para que la ruta,
 * los paraderos y la unidad se lean encima.
 */
private fun cleanMapFilter(): ColorMatrixColorFilter {
    val desaturate = ColorMatrix().apply { setSaturation(0.68f) }
    val lighten = ColorMatrix(
        floatArrayOf(
            0.94f, 0f, 0f, 0f, 16f,
            0f, 0.94f, 0f, 0f, 16f,
            0f, 0f, 0.94f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    lighten.preConcat(desaturate)
    return ColorMatrixColorFilter(lighten)
}

/** Marcador del bus: el mismo dibujo del icono de la app sobre un circulo blanco. */
private fun busMarker(view: MapView, lat: Double, lng: Double): Marker {
    val density = view.resources.displayMetrics.density
    val size = (24 * density).toInt()
    val circle = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(AndroidColor.WHITE)
        setStroke((2 * density).toInt(), COLOR_ROUTE)
    }
    val bus = ContextCompat.getDrawable(view.context, R.drawable.ic_launcher_mono)
    val icon = if (bus == null) {
        circle
    } else {
        LayerDrawable(arrayOf(circle, bus)).apply {
            val inset = (4 * density).toInt()
            setLayerInset(1, inset, inset, inset, inset)
        }
    }
    icon.setBounds(0, 0, size, size)
    return Marker(view).apply {
        position = GeoPoint(lat, lng)
        this.icon = icon
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
    }
}

private fun stopMarker(
    view: MapView,
    title: String,
    lat: Double,
    lng: Double,
    color: Int,
    highlighted: Boolean,
    sizeDp: Int = if (highlighted) 18 else 9
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
