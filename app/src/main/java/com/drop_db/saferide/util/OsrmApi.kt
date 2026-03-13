package com.drop_db.saferide.util

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                val result = runCatching { fetch(base, from, to) }.getOrElse { emptyList() }

                if (result.size >= 2) {
                    winner.complete(result)
                }
                if (pending.decrementAndGet() == 0) {
                    winner.complete(emptyList())
                }
            }
        }

        return withTimeoutOrNull(10_000) { winner.await() } ?: emptyList()
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private fun fetch(baseUrl: String, from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val url = "$baseUrl${from.longitude},${from.latitude}" +
                  ";${to.longitude},${to.latitude}" +
                  "?overview=full&geometries=polyline"
        val json = request(url)
        return parse(json)
    }

    private fun request(url: String): String {
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.setRequestProperty("User-Agent", "SafeRideApp/1.0")
        conn.connectTimeout = 8000
        conn.readTimeout    = 8000
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
