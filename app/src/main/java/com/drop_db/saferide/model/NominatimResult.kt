package com.drop_db.saferide.model

import org.osmdroid.util.GeoPoint

data class NominatimResult(
    val displayName: String,
    val shortName: String,
    val geoPoint: GeoPoint
)
