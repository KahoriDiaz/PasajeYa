package com.pasajeya.app.feature.trip.data

import android.content.Context
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.RoutePath
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Catálogo local (data/routes.json) usado por la demo cuando Firestore no tiene rutas. */
@Singleton
class RouteCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cache: Map<String, RoutePath>? = null

    suspend fun routes(): List<RoutePath> = withContext(Dispatchers.IO) {
        load().values.sortedBy { it.name }
    }

    suspend fun find(routeId: String): RoutePath? = withContext(Dispatchers.IO) {
        load()[routeId]
    }

    private fun load(): Map<String, RoutePath> {
        cache?.let { return it }
        val parsed = runCatching {
            val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val stopsArray = item.optJSONArray("stops") ?: JSONArray()
                    val stops = buildList {
                        for (stopIndex in 0 until stopsArray.length()) {
                            val stop = stopsArray.optJSONObject(stopIndex) ?: continue
                            val lat = stop.optDouble("lat", Double.NaN)
                            val lng = stop.optDouble("lng", Double.NaN)
                            if (lat.isNaN() || lng.isNaN()) continue
                            add(
                                RouteStop(
                                    name = stop.optString("name"),
                                    lat = lat,
                                    lng = lng
                                )
                            )
                        }
                    }
                    if (stops.isEmpty()) continue
                    put(
                        id,
                        RoutePath(
                            id = id,
                            name = item.optString("name", id),
                            stops = stops,
                            concessionaireName = item.optString("concessionaireId")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
        cache = parsed
        return parsed
    }

    private companion object {
        const val ASSET_NAME = "routes.json"
    }
}
