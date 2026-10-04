package com.siddharth.stockticker.util

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Period returns and risk stats, computed on-device from the price history we
 * already fetched. These are *derived* numbers, not scraped ones — which is why
 * the app can show them honestly even though the upstream feed doesn't offer
 * them. Anything that can't be computed from the data on hand returns null and
 * renders as an em dash.
 */
object SeriesStats {

    /** A dated close series, oldest first, with gaps already stripped. */
    data class Series(val timestamps: List<Long>, val closes: List<Double>) {
        val isUsable: Boolean get() = closes.size >= 2
    }

    fun clean(timestamps: List<Long?>?, closes: List<Double?>?): Series {
        if (timestamps == null || closes == null) return Series(emptyList(), emptyList())
        val ts = ArrayList<Long>(timestamps.size)
        val cs = ArrayList<Double>(closes.size)
        val n = minOf(timestamps.size, closes.size)
        for (i in 0 until n) {
            val t = timestamps[i]
            val c = closes[i]
            if (t != null && c != null && c > 0.0 && !c.isNaN()) {
                ts.add(t)
                cs.add(c)
            }
        }
        return Series(ts, cs)
    }

    /**
     * Percent change from the close nearest to [daysAgo] days before the latest
     * point, to the latest point. Null when the series doesn't reach back far
     * enough (so a 2-year-old listing shows "—" for 5Y rather than a wrong number).
     */
    /**
     * Takes every [stride]-th point of a daily series, giving a weekly one.
     *
     * The risk stats are documented on screen as weekly, and sampling one daily
     * fetch is a network call cheaper than fetching a weekly series separately.
     * The final point is always kept so the series ends at the most recent
     * close rather than wherever the stride happened to land.
     */
    fun sampleWeekly(daily: Series, stride: Int = 5): Series {
        if (!daily.isUsable || stride <= 1) return daily
        val ts = ArrayList<Long>(daily.closes.size / stride + 2)
        val cs = ArrayList<Double>(daily.closes.size / stride + 2)
        var index = 0
        while (index < daily.closes.size) {
            ts.add(daily.timestamps[index])
            cs.add(daily.closes[index])
            index += stride
        }
        val lastIndex = daily.closes.lastIndex
        if (ts.isEmpty() || ts.last() != daily.timestamps[lastIndex]) {
            ts.add(daily.timestamps[lastIndex])
            cs.add(daily.closes[lastIndex])
        }
        return Series(ts, cs)
    }

    fun periodReturn(series: Series, daysAgo: Int): Double? {
        if (!series.isUsable) return null
        val latestTs = series.timestamps.last()
        val latestClose = series.closes.last()
        val targetTs = latestTs - daysAgo * 86_400L

        // Require real coverage: the oldest point must predate the target window
        // with a little slack for weekends/holidays at the boundary.
        if (series.timestamps.first() > targetTs + 14 * 86_400L) return null

        var bestIndex = 0
        var bestDelta = Long.MAX_VALUE
        series.timestamps.forEachIndexed { index, ts ->
            val delta = kotlin.math.abs(ts - targetTs)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = index
            }
        }
        val base = series.closes[bestIndex]
        if (base <= 0.0) return null
        return (latestClose - base) / base * 100.0
    }

    /** Log returns between consecutive closes. */
    fun logReturns(series: Series): List<Double> {
        if (!series.isUsable) return emptyList()
        return series.closes.zipWithNext { a, b -> ln(b / a) }
    }

    /**
     * Annualised volatility in percent, from the most recent [periods] samples.
     * [periodsPerYear] is 52 for a weekly series, 252 for daily.
     */
    fun annualisedVolatility(
        series: Series,
        periods: Int = 52,
        periodsPerYear: Int = 52
    ): Double? {
        val returns = logReturns(series).takeLast(periods)
        if (returns.size < 12) return null
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        if (variance <= 0.0) return null
        return sqrt(variance) * sqrt(periodsPerYear.toDouble()) * 100.0
    }

    /**
     * Beta against a benchmark: cov(stock, index) / var(index), over the overlap
     * of the two series. Null unless there are enough aligned samples to mean
     * anything.
     */
    fun beta(stock: Series, benchmark: Series, periods: Int = 52): Double? {
        val aligned = align(stock, benchmark).takeLast(periods + 1)
        if (aligned.size < 13) return null
        val stockReturns = aligned.zipWithNext { a, b -> ln(b.first / a.first) }
        val indexReturns = aligned.zipWithNext { a, b -> ln(b.second / a.second) }
        val sMean = stockReturns.average()
        val iMean = indexReturns.average()
        var covariance = 0.0
        var indexVariance = 0.0
        for (i in stockReturns.indices) {
            covariance += (stockReturns[i] - sMean) * (indexReturns[i] - iMean)
            indexVariance += (indexReturns[i] - iMean) * (indexReturns[i] - iMean)
        }
        if (indexVariance <= 0.0) return null
        return covariance / indexVariance
    }

    /**
     * Pairs the two series on the nearest timestamp within three days, so a
     * holiday in one market doesn't shear the alignment for everything after it.
     */
    private fun align(a: Series, b: Series): List<Pair<Double, Double>> {
        if (!a.isUsable || !b.isUsable) return emptyList()
        val out = ArrayList<Pair<Double, Double>>(minOf(a.closes.size, b.closes.size))
        var j = 0
        val tolerance = 3 * 86_400L
        for (i in a.timestamps.indices) {
            val target = a.timestamps[i]
            while (j + 1 < b.timestamps.size &&
                kotlin.math.abs(b.timestamps[j + 1] - target) <= kotlin.math.abs(b.timestamps[j] - target)
            ) j++
            if (kotlin.math.abs(b.timestamps[j] - target) <= tolerance) {
                out.add(a.closes[i] to b.closes[j])
            }
        }
        return out
    }

    /** Where the price sits in its 52-week range, 0f..1f. Null if unknowable. */
    fun rangePosition(price: Double?, low: Double?, high: Double?): Float? {
        if (price == null || low == null || high == null) return null
        if (high <= low) return null
        return ((price - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    }
}
