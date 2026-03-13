package com.drop_db.saferide.util

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

object OsrmApi {

    // Independent scope — requests aren't tied to Activity lifecycle,
    // so cancelling an Activity won't abort an in-flight HTTP call mid-way.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val SERVERS = listOf(
        "https://router.project-osrm.org/route/v1/driving/",
        "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
    )

    /**
     * Fires all servers simultaneously. Returns the moment any one of them
     * delivers a valid route — without waiting for the others.
     */
    suspend fun getRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val winner  = CompletableDeferred<List<GeoPoint>>()
        val pending = AtomicInteger(SERVERS.size)

        SERVERS.forEach { base ->
            scope.launch {
                val result = runCatching { fetch(base, from, to) }.getOrElse { emptyList<GeoPoint>() }

                if (result.size >= 2) {
                    winner.complete(result)          // first valid result wins; subsequent calls are no-ops
                }
                if (pending.decrementAndGet() == 0) {
                    winner.complete(emptyList())     // all failed — unblock the caller
                }
            }
        }

        return withTimeoutOrNull(12_000) { winner.await() } ?: emptyList()
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private suspend fun fetch(baseUrl: String, from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val nearestBase = baseUrl.replace("/route/v1/driving/", "/nearest/v1/driving/")
        // Snap both coordinates to nearest road in parallel
        val (snappedFrom, snappedTo) = coroutineScope {
            val f = async(Dispatchers.IO) { snapToRoad(nearestBase, from) ?: from }
            val t = async(Dispatchers.IO) { snapToRoad(nearestBase, to)   ?: to   }
            f.await() to t.await()
        }
        val url = "$baseUrl${snappedFrom.longitude},${snappedFrom.latitude}" +
                  ";${snappedTo.longitude},${snappedTo.latitude}" +
                  "?overview=full&geometries=polyline"
        val json = request(url)
        Log.d("OSRM", "route response (first 200): ${json.take(200)}")
        return parse(json)
    }

    private fun snapToRoad(nearestBase: String, point: GeoPoint): GeoPoint? {
        return try {
            val url  = "$nearestBase${point.longitude},${point.latitude}"
            val json = JSONObject(request(url, timeoutMs = 2000))
            if (json.optString("code") != "Ok") return null
            val waypoints = json.optJSONArray("waypoints") ?: return null
            if (waypoints.length() == 0) return null
            val loc = waypoints.getJSONObject(0).optJSONArray("location") ?: return null
            GeoPoint(loc.getDouble(1), loc.getDouble(0))
        } catch (_: Exception) { null }
    }

    private fun request(url: String, timeoutMs: Int = 6000): String {
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.setRequestProperty("User-Agent", "SafeRideApp/1.0")
        conn.connectTimeout = timeoutMs
        conn.readTimeout    = timeoutMs
        return conn.inputStream.bufferedReader().use { it.readText() }
            .also { conn.disconnect() }
    }

    private fun parse(json: String): List<GeoPoint> {
        val root = JSONObject(json)
        if (root.optString("code") != "Ok") return emptyList()
        val routes = root.optJSONArray("routes") ?: return emptyList()
        if (routes.length() == 0) return emptyList()
        val encoded = routes.getJSONObject(0).optString("geometry", "")
        return if (encoded.isBlank()) emptyList() else PolylineDecoder.decode(encoded)
    }
}
