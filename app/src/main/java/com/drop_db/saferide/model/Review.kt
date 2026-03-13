package com.drop_db.saferide.model

import java.io.Serializable

data class Review(
    val reviewerName: String,
    val rating: Int,          // 1–5
    val comment: String,
    val date: String,
    val isFromWoman: Boolean
) : Serializable
