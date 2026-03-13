package com.drop_db.saferide.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object OsrmApi {

    private val BASE_URLS = listOf(
        "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
        "https://router.project-osrm.org/route/v1/driving/"
    )

    suspend fun getRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> = withContext(Dispatchers.IO) {
        for (base in BASE_URLS) {
            val result = fetchRoute(base, from, to)
            if (result.isNotEmpty()) return@withContext result
        }
        emptyList()
    }

    private fun fetchRoute(baseUrl: String, from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val url = baseUrl +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson"
        return try {
            val json = request(url)
            parseRoute(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.setRequestProperty("User-Agent", "SafeRideApp/1.0")
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        return connection.inputStream.bufferedReader().use { it.readText() }.also {
            connection.disconnect()
        }
    }

    private fun parseRoute(json: String): List<GeoPoint> {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return emptyList()
        val routes = root.optJSONArray("routes") ?: return emptyList()
        if (routes.length() == 0) return emptyList()
        val geometry = routes.getJSONObject(0).optJSONObject("geometry") ?: return emptyList()
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
        return parseCoordinates(coordinates)
    }

    private fun parseCoordinates(coordinates: JSONArray): List<GeoPoint> {
        val result = ArrayList<GeoPoint>(coordinates.length())
        for (i in 0 until coordinates.length()) {
            val point = coordinates.optJSONArray(i) ?: continue
            if (point.length() < 2) continue
            result.add(GeoPoint(point.getDouble(1), point.getDouble(0)))
        }
        return result
    }
}
