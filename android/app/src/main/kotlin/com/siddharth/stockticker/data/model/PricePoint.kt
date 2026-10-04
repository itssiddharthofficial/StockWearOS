package com.siddharth.stockticker.data.model

/** One close price at one instant. The line chart needs nothing more. */
data class PricePoint(
    val epochSeconds: Long,
    val price: Double
)

/**
 * The windows offered by the chart's period tabs.
 *
 * Intervals are deliberately finer than a candlestick chart would need. A line
 * drawn through sixteen points is a smooth curve; drawn through a hundred it
 * shows the small tick-by-tick chop that real price action has. The point count
 * is what makes the line read as market data rather than a graphic.
 */
enum class ChartPeriod(
    val label: String,
    val range: String,
    val interval: String,
    /** Sub-daily bars: affects cache lifetime and the label timezone. */
    val isIntraday: Boolean = false,
    /**
     * Axis labels read as clock times rather than dates.
     *
     * Only the single-session window wants times. 1W and 1M also fetch
     * sub-daily bars for the sake of a jagged line, but labelling a month of
     * them "09:15, 09:15, 15:15" tells the reader nothing about when.
     */
    val labelAsTime: Boolean = false
) {
    // Asks upstream for five days rather than one: on a weekend or a market
    // holiday a one-day window is simply empty, and a chart that disappears
    // outside trading hours is a bug, not an honest blank. The extra sessions
    // are trimmed off in the data source, leaving the most recent one.
    ONE_DAY("1D", "5d", "5m", isIntraday = true, labelAsTime = true),
    ONE_WEEK("1W", "5d", "30m", isIntraday = true),
    ONE_MONTH("1M", "1mo", "90m", isIntraday = true),
    THREE_MONTHS("3M", "3mo", "1d"),
    SIX_MONTHS("6M", "6mo", "1d"),
    ONE_YEAR("1Y", "1y", "1d");

    companion object {
        val labels: List<String> = entries.map { it.label }
        val default: ChartPeriod = ONE_MONTH
    }
}

/**
 * Upper bound on plotted points.
 *
 * Enough to keep the line jagged, few enough that a 1.4" canvas is not drawing
 * sub-pixel segments and that a finger can still land near a specific point.
 */
const val MAX_POINTS = 120

/** Indian market hours are IST, wherever the watch happens to be. */
const val IST_OFFSET_SECONDS = 5 * 3600 + 1800

/**
 * Keeps only the points belonging to the most recent trading session.
 *
 * Sessions are bucketed by IST calendar day, because that is the day the
 * exchange trades in; bucketing by the device timezone would split one NSE
 * session in two for a user outside India.
 */
fun lastSessionOnly(points: List<PricePoint>): List<PricePoint> {
    if (points.isEmpty()) return points
    fun sessionDay(epochSeconds: Long): Long =
        Math.floorDiv(epochSeconds + IST_OFFSET_SECONDS, 86_400L)

    val latestDay = sessionDay(points.maxOf { it.epochSeconds })
    return points.filter { sessionDay(it.epochSeconds) == latestDay }
}

/**
 * Thins [points] to at most [maxPoints] by **sampling**, never averaging.
 *
 * Averaging adjacent closes is exactly what would smooth away the chop that
 * makes the line look like real trading. Taking every n-th actual close keeps
 * every plotted value a price that genuinely traded, and keeps the jaggedness.
 * The last point is always kept so the line ends at the latest close.
 */
fun thinPoints(points: List<PricePoint>, maxPoints: Int = MAX_POINTS): List<PricePoint> {
    if (points.size <= maxPoints || maxPoints < 2) return points
    val stride = kotlin.math.ceil(points.size.toDouble() / maxPoints).toInt()
    val out = ArrayList<PricePoint>(maxPoints + 1)
    var index = 0
    while (index < points.size) {
        out += points[index]
        index += stride
    }
    if (out.last().epochSeconds != points.last().epochSeconds) out += points.last()
    return out
}
