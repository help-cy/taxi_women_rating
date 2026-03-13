package com.drop_db.saferide.model

import org.osmdroid.util.GeoPoint
import kotlin.random.Random

data class MockDriver(
    val id: Int,
    val name: String,
    val rating: Float,          // overall rating
    val womenRating: Float,     // rating specifically from women passengers
    val totalReviews: Int,
    val womenSafe: Boolean,
    val spawnPoint: GeoPoint,
    val carModel: String,
    val plateNumber: String,
    val eta: Int,               // minutes
    val reviewQuote: String     // featured review
)

private data class DriverTemplate(
    val name: String,
    val rating: Float,
    val womenRating: Float,
    val womenSafe: Boolean,
    val car: String,
    val plate: String,
    val totalReviews: Int,
    val reviewQuote: String
)

object MockData {

    private val templates = listOf(
        DriverTemplate(
            "Anna K.",   4.9f, 5.0f, true,  "Toyota Camry",   "ABA 441", 312,
            "\"Super friendly, felt completely safe. Will always request her!\""
        ),
        DriverTemplate(
            "Maria P.",  4.8f, 4.9f, true,  "Hyundai Sonata", "EKM 782", 187,
            "\"Very professional, spotless car. Highly recommended.\""
        ),
        DriverTemplate(
            "Alex V.",   4.7f, 4.6f, false, "Kia K5",         "HZT 319", 241,
            "\"Fast and polite, knows every shortcut in Limassol.\""
        ),
        DriverTemplate(
            "Sara T.",   5.0f, 5.0f, true,  "Mercedes E-Class","CYP 007",  98,
            "\"Luxury experience every time. Sara is simply the best!\""
        ),
        DriverTemplate(
            "David S.",  4.5f, 1.4f, false, "Skoda Octavia",  "LIM 553", 200,
            "\"Chill driver, good music, clean car. Solid 5 stars.\""
        ),
        DriverTemplate(
            "Olivia R.", 4.8f, 4.9f, true,  "VW Passat",      "NIC 290", 224,
            "\"Very calm driver, I feel safe every trip. My go-to!\""
        ),
        DriverTemplate(
            "John M.",   4.6f, 4.5f, false, "Ford Focus",     "PAF 117", 155,
            "\"Friendly and knows the city really well. Good chat too.\""
        ),
    )

    /** Generate mock drivers scattered within ~800 m of [center]. */
    fun driversAround(center: GeoPoint): List<MockDriver> {
        val rng = Random(center.latitude.toLong() xor center.longitude.toLong())
        return templates.mapIndexed { i, t ->
            val latOffset = (rng.nextDouble() - 0.5) * 0.014
            val lonOffset = (rng.nextDouble() - 0.5) * 0.018
            MockDriver(
                id          = i + 1,
                name        = t.name,
                rating      = t.rating,
                womenRating = t.womenRating,
                totalReviews= t.totalReviews,
                womenSafe   = t.womenSafe,
                spawnPoint  = GeoPoint(center.latitude + latOffset, center.longitude + lonOffset),
                carModel    = t.car,
                plateNumber = t.plate,
                eta         = rng.nextInt(2, 9),
                reviewQuote = t.reviewQuote
            )
        }
    }
}
