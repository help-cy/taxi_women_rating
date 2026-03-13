package com.drop_db.saferide.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object OsrmApi {

    suspend fun getRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> = withContext(Dispatchers.IO) {
        val snappedFrom = getNearestRoadPoint(from) ?: from
        val snappedTo = getNearestRoadPoint(to) ?: to

        fetchRoute(snappedFrom, snappedTo).ifEmpty {
            fetchRoute(from, to)
        }
    }

    private fun getNearestRoadPoint(point: GeoPoint): GeoPoint? {
        val url = "https://router.project-osrm.org/nearest/v1/driving/" +
            "${point.longitude},${point.latitude}?number=1"
        return try {
            val json = request(url)
            parseNearest(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
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
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return connection.inputStream.bufferedReader().use { it.readText() }.also {
            connection.disconnect()
        }
    }

    private fun parseNearest(json: String): GeoPoint? {
        val root = JSONObject(json)
        val waypoints = root.optJSONArray("waypoints") ?: return null
        if (waypoints.length() == 0) return null
        val location = waypoints.getJSONObject(0).optJSONArray("location") ?: return null
        if (location.length() < 2) return null
        return GeoPoint(location.getDouble(1), location.getDouble(0))
    }

    private fun parseRoute(json: String): List<GeoPoint> {
        val root = JSONObject(json)
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
