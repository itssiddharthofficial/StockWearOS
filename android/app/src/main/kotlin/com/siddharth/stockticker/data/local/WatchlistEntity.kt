package com.siddharth.stockticker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Membership + ordering of the watchlist. Hard-capped at 5 by the repository. */
@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val symbol: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
