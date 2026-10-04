package com.siddharth.stockticker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached snapshot of one symbol. Every field except [symbol] is nullable: the
 * upstream data source is unofficial and routinely omits fundamentals, and a
 * missing field must render as an em dash, never crash or show a fake number.
 */
@Entity(tableName = "stocks")
data class StockEntity(
    @PrimaryKey val symbol: String,
    val name: String? = null,
    val price: Double? = null,
    val previousClose: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val currency: String? = "INR",
    val exchange: String? = null,

    val marketCap: Long? = null,
    val peRatio: Double? = null,
    val dividendYield: Double? = null,
    val week52High: Double? = null,
    val week52Low: Double? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val volume: Long? = null,

    /**
     * Period returns in percent, computed on-device from the price history.
     * 1D is not stored: it is the quote's own day change, so keeping a second
     * copy of it here would just be a way for the two to disagree.
     */
    val return1w: Double? = null,
    val return1m: Double? = null,
    val return3m: Double? = null,
    val return6m: Double? = null,
    val return1y: Double? = null,

    /** Annualised volatility (%) and beta vs Nifty 50, both computed locally. */
    val volatility: Double? = null,
    val beta: Double? = null,

    /**
     * Last ~20 daily closes, comma separated, for the home row sparkline.
     *
     * Stored as text rather than a related table: it is a fixed-size display
     * artefact, never queried or joined, and a second table would cost a join
     * on every home frame to save nothing.
     */
    val trend: String? = null,
    val trendUpdatedAt: Long = 0L,

    /** When the quote was last refreshed, and when the deep detail last was. */
    val quoteUpdatedAt: Long = 0L,
    val detailUpdatedAt: Long = 0L,

    /** Set when the last refresh for this symbol failed, so the UI can degrade it. */
    val lastErrorAt: Long = 0L
) {
    val hasQuote: Boolean get() = price != null && quoteUpdatedAt > 0L

    /** Parsed sparkline points; empty when nothing has been cached yet. */
    val trendPoints: List<Double>
        get() = trend?.split(',')
            ?.mapNotNull { it.toDoubleOrNull() }
            ?.filter { it > 0.0 }
            .orEmpty()
}
