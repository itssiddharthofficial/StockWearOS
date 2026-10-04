"""Upstream market data via yfinance, reshaped into the watch's DTO.

Everything the watch sees is built here, so a change in the upstream schema is a
one-file fix on the server and needs no app update.

Note on honesty: fields the upstream feed does not provide are returned as null
and render as an em dash on the watch. Nothing is invented to fill a gap --
specifically, the FII/DII/promoter shareholding pattern is not available from
this source and is therefore absent rather than mocked.
"""
import datetime
import logging
import math

import yfinance as yf

from cache import TtlCache
from config import Config
from utils import ValidationError, clean_int, clean_number, market_cap_label

log = logging.getLogger(__name__)

_quote_cache = TtlCache(Config.CACHE_TTL_SECONDS)
_history_cache = TtlCache(max(Config.CACHE_TTL_SECONDS, 3600))

PERIOD_DAYS = {
    "1d": 1,
    "1w": 7,
    "1m": 30,
    "3m": 91,
    "6m": 182,
    "1y": 365,
}


class UpstreamError(RuntimeError):
    """Upstream was unreachable or returned nothing usable."""


def _utc_now_iso():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _quote_from_info(symbol, info, fast=None):
    """Folds yfinance's loosely-typed dicts into the stable response shape."""
    source = info or {}
    fast = fast or {}

    price = clean_number(
        source.get("currentPrice")
        or source.get("regularMarketPrice")
        or fast.get("last_price")
    )
    previous = clean_number(
        source.get("regularMarketPreviousClose")
        or source.get("previousClose")
        or fast.get("previous_close")
    )
    change = None
    change_percent = None
    if price is not None and previous:
        change = price - previous
        change_percent = change / previous * 100.0

    dividend_yield = clean_number(source.get("dividendYield"))
    if dividend_yield is not None and dividend_yield < 1:
        # Reported as a fraction on some rows and as a percent on others.
        dividend_yield *= 100.0

    market_cap = clean_int(source.get("marketCap") or fast.get("market_cap"))

    return {
        "symbol": symbol,
        "name": source.get("longName") or source.get("shortName") or symbol,
        "price": price,
        "previous_close": previous,
        "change": change,
        "change_percent": change_percent,
        "currency": source.get("currency") or fast.get("currency") or "INR",
        "exchange": source.get("fullExchangeName") or source.get("exchange"),
        "market_cap": market_cap,
        "market_cap_label": market_cap_label(market_cap),
        "pe_ratio": clean_number(source.get("trailingPE")),
        "dividend_yield": dividend_yield,
        "week52_high": clean_number(
            source.get("fiftyTwoWeekHigh") or fast.get("year_high")
        ),
        "week52_low": clean_number(
            source.get("fiftyTwoWeekLow") or fast.get("year_low")
        ),
        "day_high": clean_number(source.get("regularMarketDayHigh")),
        "day_low": clean_number(source.get("regularMarketDayLow")),
        "volume": clean_int(source.get("regularMarketVolume")),
        "updated_at": _utc_now_iso(),
    }


def fetch_quotes(symbols):
    """Batched quotes. Returns {symbol: dto}; missing symbols are simply absent.

    Cached entries are served first, so a five-symbol watchlist usually costs
    zero upstream calls.
    """
    out = {}
    pending = []
    for symbol in symbols:
        cached, _age = _quote_cache.get(symbol)
        if cached is not None:
            out[symbol] = cached
        elif not _quote_cache.in_backoff(symbol):
            pending.append(symbol)

    if pending:
        try:
            # One Tickers object is fewer round trips than one call per symbol.
            tickers = yf.Tickers(" ".join(pending))
            for symbol in pending:
                ticker = tickers.tickers.get(symbol)
                if ticker is None:
                    _quote_cache.mark_failure(symbol)
                    continue
                try:
                    info = ticker.info
                except Exception:  # noqa: BLE001 - upstream raises many types
                    info = {}
                try:
                    fast = dict(ticker.fast_info)
                except Exception:  # noqa: BLE001
                    fast = {}
                dto = _quote_from_info(symbol, info, fast)
                if dto["price"] is None:
                    _quote_cache.mark_failure(symbol)
                    continue
                _quote_cache.put(symbol, dto)
                out[symbol] = dto
        except Exception as exc:  # noqa: BLE001
            log.warning("batch quote failed for %s: %s", pending, exc)
            for symbol in pending:
                _quote_cache.mark_failure(symbol)

    # Anything still missing falls back to the last known value, flagged stale.
    for symbol in symbols:
        if symbol not in out:
            stale, age = _quote_cache.get_stale(symbol)
            if stale is not None:
                copy = dict(stale)
                copy["stale"] = True
                copy["stale_age_seconds"] = int(age)
                out[symbol] = copy

    return out


# Chart windows the watch can ask for, mapped to upstream range/interval.
CHART_PERIODS = {
    "1D": ("5d", "5m"),
    "1W": ("5d", "30m"),
    "1M": ("1mo", "90m"),
    "3M": ("3mo", "1d"),
    "6M": ("6mo", "1d"),
    "1Y": ("1y", "1d"),
}

# Enough points to keep a price line jagged rather than smooth, few enough that
# a 1.4" canvas is not drawing sub-pixel segments.
MAX_POINTS = 120


def _bars(symbol, period, interval, cache_key):
    """Full OHLC bars from upstream, cached. Returns a list of dicts or None.

    Carries all four legs even though the chart only plots closes: the same
    helper feeds the returns and risk calculations, and keeping one bar shape
    means one place to fix if the upstream schema shifts.
    """
    cached, _age = _history_cache.get(cache_key)
    if cached is not None:
        return cached
    if _history_cache.in_backoff(cache_key):
        return None
    try:
        frame = yf.Ticker(symbol).history(period=period, interval=interval)
    except Exception as exc:  # noqa: BLE001
        log.warning("history failed for %s: %s", symbol, exc)
        _history_cache.mark_failure(cache_key)
        return None
    if frame is None or frame.empty:
        _history_cache.mark_failure(cache_key)
        return None
    for column in ("Open", "High", "Low", "Close"):
        if column not in frame:
            _history_cache.mark_failure(cache_key)
            return None

    bars = []
    for index, row in frame.iterrows():
        values = [clean_number(row[c]) for c in ("Open", "High", "Low", "Close")]
        # A bar missing any leg is a gap in the feed, not a flat bar: drop it
        # rather than substituting a neighbouring value.
        if any(v is None or v <= 0 for v in values):
            continue
        bars.append(
            {
                "timestamp": int(index.timestamp()),
                "date": index.strftime("%Y-%m-%d %H:%M" if interval.endswith("m") else "%Y-%m-%d"),
                "open": values[0],
                "high": values[1],
                "low": values[2],
                "close": values[3],
            }
        )
    if len(bars) < 2:
        _history_cache.mark_failure(cache_key)
        return None
    _history_cache.put(cache_key, bars)
    return bars


def fetch_price_points(symbol, period):
    """Close-only price points for one chart period tab.

    The watch draws a line chart, so it needs a label and a close per point and
    nothing else. Returning the full OHLC bar would be three quarters wasted
    payload over a Bluetooth-tethered link.
    """
    key = (period or "").upper()
    if key not in CHART_PERIODS:
        raise ValidationError(
            "period must be one of: %s" % ", ".join(sorted(CHART_PERIODS))
        )
    range_, interval = CHART_PERIODS[key]
    bars = _bars(symbol, range_, interval, "%s|%s" % (symbol, key))
    if not bars:
        return []
    if key == "1D":
        bars = _last_session_only(bars)
    bars = _thin(bars, MAX_POINTS)
    return [{"label": b["date"], "price": b["close"]} for b in bars]


def _last_session_only(bars):
    """Only the bars from the most recent IST trading day.

    A one-day window is empty on a weekend, so the 1D tab asks upstream for
    several sessions and the extras are trimmed here.
    """
    if not bars:
        return bars
    ist = 5 * 3600 + 1800
    latest_day = (max(b["timestamp"] for b in bars) + ist) // 86400
    return [b for b in bars if (b["timestamp"] + ist) // 86400 == latest_day]


def _thin(bars, max_points):
    """Sample every n-th bar, never average.

    Averaging adjacent closes is exactly what would smooth away the chop that
    makes a price line look like real trading, so every point kept is a price
    that genuinely traded. The last bar is always kept.
    """
    if len(bars) <= max_points or max_points < 2:
        return bars
    stride = int(math.ceil(len(bars) / float(max_points)))
    out = bars[::stride]
    if out[-1]["timestamp"] != bars[-1]["timestamp"]:
        out.append(bars[-1])
    return out


def _history_closes(symbol):
    """One year of daily closes: every return window plus the risk inputs."""
    bars = _bars(symbol, "1y", "1d", "%s|RETURNS" % symbol)
    if not bars:
        return None
    return [(b["timestamp"], b["close"]) for b in bars]


def _period_return(series, days):
    """Percent change over the window, or None when coverage is too short."""
    if not series or len(series) < 2:
        return None
    latest_ts, latest_close = series[-1]
    target = latest_ts - days * 86400
    if series[0][0] > target + 14 * 86400:
        return None
    base_ts, base_close = min(series, key=lambda point: abs(point[0] - target))
    if not base_close:
        return None
    return (latest_close - base_close) / base_close * 100.0


def _log_returns(series, count):
    closes = [close for _ts, close in series][-(count + 1):]
    out = []
    for i in range(1, len(closes)):
        if closes[i - 1] > 0 and closes[i] > 0:
            out.append(math.log(closes[i] / closes[i - 1]))
    return out


def _sample_weekly(series, stride=5):
    """Every fifth daily point, keeping the last, giving a weekly series."""
    if not series or len(series) < stride * 2:
        return series
    sampled = series[::stride]
    if sampled[-1][0] != series[-1][0]:
        sampled.append(series[-1])
    return sampled


def _volatility(series):
    """Annualised volatility (%) from weekly log returns over one year."""
    series = _sample_weekly(series)
    returns = _log_returns(series, 52)
    if len(returns) < 12:
        return None
    mean = sum(returns) / len(returns)
    variance = sum((r - mean) ** 2 for r in returns) / (len(returns) - 1)
    if variance <= 0:
        return None
    return math.sqrt(variance) * math.sqrt(52) * 100.0


def _beta(series, benchmark):
    """cov(stock, index) / var(index) over the aligned weekly overlap."""
    if not benchmark:
        return None
    series = _sample_weekly(series)
    benchmark = _sample_weekly(benchmark)
    index_by_ts = {ts: close for ts, close in benchmark}
    aligned = []
    for ts, close in series:
        # Weekly bars can be a day or two apart between the two feeds.
        for offset in (0, 86400, -86400, 172800, -172800, 259200, -259200):
            match = index_by_ts.get(ts + offset)
            if match:
                aligned.append((close, match))
                break
    aligned = aligned[-53:]
    if len(aligned) < 13:
        return None

    stock_returns = []
    index_returns = []
    for i in range(1, len(aligned)):
        if aligned[i - 1][0] > 0 and aligned[i - 1][1] > 0:
            stock_returns.append(math.log(aligned[i][0] / aligned[i - 1][0]))
            index_returns.append(math.log(aligned[i][1] / aligned[i - 1][1]))
    if len(stock_returns) < 12:
        return None

    s_mean = sum(stock_returns) / len(stock_returns)
    i_mean = sum(index_returns) / len(index_returns)
    covariance = sum(
        (stock_returns[i] - s_mean) * (index_returns[i] - i_mean)
        for i in range(len(stock_returns))
    )
    index_variance = sum((r - i_mean) ** 2 for r in index_returns)
    if index_variance <= 0:
        return None
    return covariance / index_variance


def fetch_detail(symbol, period="1y"):
    """Quote plus derived returns and risk stats for one symbol."""
    quotes = fetch_quotes([symbol])
    dto = quotes.get(symbol)
    series = _history_closes(symbol)

    if dto is None and series is None:
        raise UpstreamError(symbol)

    if dto is None:
        latest_ts, latest_close = series[-1]
        dto = {
            "symbol": symbol,
            "name": symbol,
            "price": latest_close,
            "previous_close": None,
            "change": None,
            "change_percent": None,
            "currency": "INR",
            "exchange": None,
            "market_cap": None,
            "market_cap_label": None,
            "pe_ratio": None,
            "dividend_yield": None,
            "week52_high": None,
            "week52_low": None,
            "day_high": None,
            "day_low": None,
            "volume": None,
            "updated_at": _utc_now_iso(),
            "partial": True,
        }
    else:
        dto = dict(dto)

    returns = {}
    if series:
        for key, days in PERIOD_DAYS.items():
            returns[key] = _period_return(series, days)
        dto["volatility"] = _volatility(series)
        benchmark = (
            None
            if symbol == Config.BENCHMARK_SYMBOL
            else _history_closes(Config.BENCHMARK_SYMBOL)
        )
        dto["beta"] = _beta(series, benchmark)
        if dto.get("week52_high") is None:
            window = [close for _ts, close in series[-52:]]
            dto["week52_high"] = max(window) if window else None
            dto["week52_low"] = min(window) if window else None
    else:
        dto["volatility"] = None
        dto["beta"] = None

    dto["returns"] = returns
    dto["period"] = period
    dto["period_return_percent"] = returns.get(period)

    # The chart page asks for one period at a time; include the requested one
    # so a cold open needs a single round trip. Close-only: the watch draws a
    # line, not candles.
    chart_period = (period or "1M").upper()
    if chart_period in CHART_PERIODS:
        dto["points"] = fetch_price_points(symbol, chart_period)
    else:
        dto["points"] = []

    # Not available from this data source; absent rather than fabricated.
    dto["shareholding"] = None
    return dto


def search_symbols(query, limit=20):
    """Symbol lookup, restricted to Indian listings and indices."""
    try:
        results = yf.Search(query, max_results=limit).quotes or []
    except Exception as exc:  # noqa: BLE001
        log.warning("search failed for %r: %s", query, exc)
        return []

    out = []
    for item in results:
        symbol = (item.get("symbol") or "").upper()
        if not symbol:
            continue
        if not (
            symbol.endswith(".NS") or symbol.endswith(".BO") or symbol.startswith("^")
        ):
            continue
        quote_type = (item.get("quoteType") or "").upper()
        if quote_type not in ("EQUITY", "INDEX", ""):
            continue
        if symbol.startswith("^"):
            exchange = "Index"
        elif symbol.endswith(".BO"):
            exchange = "BSE"
        else:
            exchange = "NSE"
        out.append(
            {
                "symbol": symbol,
                "name": item.get("longname") or item.get("shortname") or symbol,
                "exchange": exchange,
            }
        )
    return out


def health():
    """Cheap upstream liveness probe for the app's startup check."""
    try:
        frame = yf.Ticker(Config.BENCHMARK_SYMBOL).history(period="1d")
        return bool(frame is not None and not frame.empty)
    except Exception:  # noqa: BLE001
        return False
