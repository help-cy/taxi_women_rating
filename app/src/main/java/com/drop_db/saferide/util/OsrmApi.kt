package com.drop_db.saferide.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object OsrmApi {

    // All servers queried simultaneously — first valid response wins
    private val SERVERS = listOf(
        "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
        "https://router.project-osrm.org/route/v1/driving/",
        "https://osrm.openstreetmap.de/route/v1/driving/"
    )

    suspend fun getRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> =
        withTimeoutOrNull(9000) { race(from, to) } ?: emptyList()

    // Fire all servers at once, return first non-empty result
    private suspend fun race(from: GeoPoint, to: GeoPoint): List<GeoPoint> = coroutineScope {
        val jobs = SERVERS.map { base ->
            async(Dispatchers.IO) { fetchRoute(base, from, to) }
        }
        // Poll until one succeeds or all finish
        val pending = jobs.toMutableList()
        while (pending.isNotEmpty()) {
            val done = pending.filter { it.isCompleted }
            for (job in done) {
                val result = runCatching { job.await() }.getOrNull()
                if (!result.isNullOrEmpty()) {
                    jobs.forEach { it.cancel() }
                    return@coroutineScope result
                }
            }
            pending.removeAll(done)
            if (pending.isEmpty()) break
            kotlinx.coroutines.delay(50)
        }
        // All finished with no result — try remaining
        for (job in jobs) {
            val result = runCatching { job.await() }.getOrNull()
            if (!result.isNullOrEmpty()) return@coroutineScope result
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun request(url: String): String {
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.setRequestProperty("User-Agent", "SafeRideApp/1.0")
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun parseRoute(json: String): List<GeoPoint> {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return emptyList()
        val routes = root.optJSONArray("routes") ?: return emptyList()
        if (routes.length() == 0) return emptyList()
        val coords = routes.getJSONObject(0)
            .optJSONObject("geometry")
            ?.optJSONArray("coordinates") ?: return emptyList()
        return parseCoords(coords)
    }

    private fun parseCoords(coords: JSONArray): List<GeoPoint> {
        val result = ArrayList<GeoPoint>(coords.length())
        for (i in 0 until coords.length()) {
            val p = coords.optJSONArray(i) ?: continue
            if (p.length() < 2) continue
            result.add(GeoPoint(p.getDouble(1), p.getDouble(0)))
        }
        return result
    }
}
