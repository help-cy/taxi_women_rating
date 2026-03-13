package com.drop_db.saferide.model

data class Tariff(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val priceMultiplier: Float,
    val etaOffsetMin: Int,        // added to nearest driver ETA
    var price: Double = 0.0,      // filled after route is known
    var etaMin: Int = 0           // filled after route is known
)

val ALL_TARIFFS = listOf(
    Tariff("economy",  "Economy",    "🚗", "Best value",          1.00f,  3),
    Tariff("comfort",  "Comfort",    "🚙", "Premium sedans",      1.50f,  1),
    Tariff("premium",  "Premium",    "💎", "Luxury experience",   2.40f, -1),
    Tariff("safeplus", "SafeRide+",  "🛡", "Women drivers only",  1.35f,  4),
)
