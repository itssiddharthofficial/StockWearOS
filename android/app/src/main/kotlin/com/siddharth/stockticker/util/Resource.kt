package com.siddharth.stockticker.util

/**
 * One result type for the whole data layer, so every screen handles failure the
 * same way. `isStale` lets the UI show cached data with an honest "last updated"
 * marker instead of a blocking error.
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T, val isStale: Boolean = false) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}
