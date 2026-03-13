package com.drop_db.saferide.util

import com.drop_db.saferide.model.NominatimResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object NominatimApi {

    /**
     * Search places via Nominatim.
     *
     * When lat/lon are provided (user's real location), results are *strictly*
     * bounded to a ±0.25° box (≈ 25 km) around that point — so you only see
     * places in the same city (e.g. Limassol).
     *
     * Falls back to a global search if nothing is found inside the box.
     */
    suspend fun search(
        query: String,
        lat: Double = 0.0,
        lon: Double = 0.0
    ): List<NominatimResult> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")

        if (lat != 0.0 && lon != 0.0) {
            // First attempt: strictly bounded to user's city (±0.25°)
            val box = buildViewbox(lat, lon, delta = 0.25)
            val local = fetch("$encoded&format=json&limit=7&addressdetails=1$box&bounded=1")
            if (local.isNotEmpty()) return@withContext local

            // Second attempt: same box but not bounded (picks up nearby results first)
            val biased = fetch("$encoded&format=json&limit=7&addressdetails=1$box&bounded=0")
            if (biased.isNotEmpty()) return@withContext biased
        }

        // Fallback: global search
        fetch("$encoded&format=json&limit=7&addressdetails=1")
    }

    suspend fun reverse(lat: Double, lon: Double): NominatimResult? = withContext(Dispatchers.IO) {
        val params = "lat=$lat&lon=$lon&format=json&addressdetails=1"
        fetchReverse(params)
    }

    private fun buildViewbox(lat: Double, lon: Double, delta: Double): String {
        val minLon = lon - delta
        val maxLon = lon + delta
        val maxLat = lat + delta
        val minLat = lat - delta
        return "&viewbox=$minLon,$maxLat,$maxLon,$minLat"
    }

    private fun fetch(params: String): List<NominatimResult> {
        val url = "https://nominatim.openstreetmap.org/search?q=$params"
        return try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.setRequestProperty("User-Agent", "SafeRideApp/1.0")
            connection.connectTimeout = 3500
            connection.readTimeout = 3500
            val json = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseResults(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchReverse(params: String): NominatimResult? {
        val url = "https://nominatim.openstreetmap.org/reverse?$params"
        return try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.setRequestProperty("User-Agent", "SafeRideApp/1.0")
            connection.connectTimeout = 3500
            connection.readTimeout = 3500
            val json = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseReverse(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResults(json: String): List<NominatimResult> {
        val arr = JSONArray(json)
        val results = mutableListOf<NominatimResult>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val displayName = obj.optString("display_name", "")
            val lat = obj.optDouble("lat", 0.0)
            val lon = obj.optDouble("lon", 0.0)
            val shortName = displayName.substringBefore(",").trim()
            results.add(
                NominatimResult(
                    displayName = displayName,
                    shortName = shortName,
                    geoPoint = GeoPoint(lat, lon)
                )
            )
        }
        return results
    }

    private fun parseReverse(json: String): NominatimResult? {
        val obj = org.json.JSONObject(json)
        val displayName = obj.optString("display_name", "")
        val lat = obj.optDouble("lat", 0.0)
        val lon = obj.optDouble("lon", 0.0)
        if (displayName.isBlank()) return null
        return NominatimResult(
            displayName = displayName,
            shortName = displayName.substringBefore(",").trim(),
            geoPoint = GeoPoint(lat, lon)
        )
    }
}
