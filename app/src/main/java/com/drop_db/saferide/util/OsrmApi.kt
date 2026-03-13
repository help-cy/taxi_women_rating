package com.drop_db.saferide.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object OsrmApi {

    suspend fun getRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${from.longitude},${from.latitude};" +
                "${to.longitude},${to.latitude}" +
                "?overview=full&geometries=polyline"
        try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.setRequestProperty("User-Agent", "SafeRideApp/1.0")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseRoute(response)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRoute(json: String): List<GeoPoint> {
        val root = JSONObject(json)
        val routes = root.optJSONArray("routes") ?: return emptyList()
        if (routes.length() == 0) return emptyList()
        val geometry = routes.getJSONObject(0).optString("geometry", "")
        return PolylineDecoder.decode(geometry)
    }
}
