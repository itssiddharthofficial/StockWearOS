package com.siddharth.stockticker.util

import com.siddharth.stockticker.data.model.ChartPeriod
import java.util.Locale
import kotlin.math.abs

/**
 * All user-facing number formatting lives here so the same value never renders
 * two different ways on two different screens.
 */
object Formatters {

    const val DASH = "—"

    /** `3245.5` -> `"3,245.50"`. Null-safe: returns an em dash. */
    fun price(value: Double?): String {
        if (value == null || value.isNaN()) return DASH
        return String.format(Locale.US, "%,.2f", value)
    }

    /** Compact price for small bubbles: `3245.5` -> `"3,246"`, `24.35` -> `"24.35"`. */
    fun priceCompact(value: Double?): String {
        if (value == null || value.isNaN()) return DASH
        return if (abs(value) >= 1000) String.format(Locale.US, "%,.0f", value)
        else String.format(Locale.US, "%.2f", value)
    }

    /** `0.57` -> `"+0.57%"`, `-1.2` -> `"-1.20%"`. */
    fun percent(value: Double?, decimals: Int = 2): String {
        if (value == null || value.isNaN()) return DASH
        val sign = if (value > 0) "+" else ""
        return sign + String.format(Locale.US, "%.${decimals}f", value) + "%"
    }

    /**
     * Percent for a 48dp bubble. One decimal normally, none past ±100%, so the
     * widest string it can produce is `-12.3%` — which is what the bubble's text
     * size is chosen to hold without clipping.
     */
    fun percentTight(value: Double?): String {
        if (value == null || value.isNaN()) return DASH
        val sign = if (value > 0) "+" else ""
        val decimals = if (abs(value) >= 100) 0 else 1
        return sign + String.format(Locale.US, "%.${decimals}f", value) + "%"
    }

    /**
     * Market cap in Indian convention: crore / lakh crore, which is how NSE
     * figures are actually quoted. `15_200_000_000_000` -> `"15.2L Cr"`.
     */
    fun marketCap(value: Long?): String {
        if (value == null || value <= 0L) return DASH
        val crore = value / 1_00_00_000.0
        return when {
            crore >= 1_00_000 -> String.format(Locale.US, "%.1fL Cr", crore / 1_00_000)
            crore >= 1_000 -> String.format(Locale.US, "%,.0f Cr", crore)
            crore >= 1 -> String.format(Locale.US, "%.1f Cr", crore)
            else -> String.format(Locale.US, "%.1f L", value / 1_00_000.0)
        }
    }

    /** Ratios: two significant decimals, em dash when absent or nonsensical. */
    fun ratio(value: Double?): String {
        if (value == null || value.isNaN() || value <= 0.0) return DASH
        return String.format(Locale.US, "%.1f", value)
    }

    fun decimal(value: Double?, decimals: Int = 2): String {
        if (value == null || value.isNaN()) return DASH
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    /** `12_450_000` -> `"12.5M"`. Used for volume. */
    fun compactCount(value: Long?): String {
        if (value == null || value <= 0) return DASH
        return when {
            value >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
            value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }

    /** "2 min ago" / "Just now" / "3 h ago" — short enough for a watch caption. */
    fun relativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
        if (epochMillis == null || epochMillis <= 0L) return DASH
        val minutes = (now - epochMillis) / 60_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }

    /** Price with the rupee sign, for the chart tooltip. */
    fun rupees(value: Double?): String =
        if (value == null || value.isNaN()) DASH else "₹" + price(value)

    /**
     * Axis/tooltip label for one candle. Intraday windows get a clock time,
     * everything else a date, because "22 Sep" repeated sixteen times across a
     * one-day chart tells the reader nothing.
     */
    fun chartLabel(epochSeconds: Long, period: ChartPeriod): String {
        val date = java.util.Date(epochSeconds * 1000L)
        val pattern = when {
            period.labelAsTime -> "HH:mm"
            period == ChartPeriod.ONE_YEAR -> "MMM yy"
            else -> "d MMM"
        }
        val formatter = java.text.SimpleDateFormat(pattern, Locale.US)
        if (period.isIntraday) {
            // These are NSE session times. Showing them in the device timezone
            // would print 04:15 for the opening bell to a traveller.
            formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }
        return formatter.format(date)
    }

    /** `RELIANCE.NS` -> `RELIANCE`; `^NSEI` -> `NIFTY`. */
    fun displaySymbol(symbol: String): String = when (symbol) {
        "^NSEI" -> "NIFTY"
        "^BSESN" -> "SENSEX"
        else -> symbol.substringBefore('.')
    }

    /** Fits a symbol into a small bubble without a mid-word cut. */
    fun bubbleSymbol(symbol: String): String {
        val base = displaySymbol(symbol)
        return if (base.length <= 6) base else base.take(5) + "·"
    }
}
