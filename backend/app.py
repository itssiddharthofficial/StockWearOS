"""Optional Flask proxy over the market data source.

The watch app does **not** need this to run -- it fetches directly and ships
standalone. Deploy this if you would rather centralise caching and rate limiting
(one cache entry serves every device, and the upstream schema can be patched
server-side without an app update). To switch the app over, set `BASE_URL` in
`data/remote/ApiClient.kt` to this service's HTTPS URL.

Endpoints:
    GET /api/health
    GET /api/stocks/watchlist?symbols=RELIANCE.NS,TCS.NS
    GET /api/stocks/<symbol>?period=1y      # quote + close-only price points
    GET /api/stocks/<symbol>/points?period=1M
    GET /api/search?q=reliance
"""
import logging

from flask import Flask, jsonify, request

from config import Config
from data_source import (
    CHART_PERIODS,
    PERIOD_DAYS,
    UpstreamError,
    fetch_price_points,
    fetch_detail,
    fetch_quotes,
    health,
)
from search import combined_search
from utils import ValidationError, parse_symbols, sanitise_query, normalise_symbol

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

app = Flask(__name__)
app.config.from_object(Config)

limiter = None
if Config.ENABLE_RATE_LIMIT:
    try:
        from flask_limiter import Limiter
        from flask_limiter.util import get_remote_address

        limiter = Limiter(
            get_remote_address,
            app=app,
            default_limits=[Config.RATE_LIMIT],
            storage_uri="memory://",
        )
    except ImportError:  # pragma: no cover - optional dependency
        log.warning("Flask-Limiter not installed; running without rate limiting")


@app.errorhandler(ValidationError)
def handle_validation_error(exc):
    return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.errorhandler(UpstreamError)
def handle_upstream_error(exc):
    return (
        jsonify(
            {
                "error": "upstream_unavailable",
                "message": "Could not reach data source",
                "symbol": str(exc),
            }
        ),
        504,
    )


@app.errorhandler(Exception)
def handle_unexpected_error(exc):
    # Never leak a stack trace to the device; log it and return a clean shape.
    log.exception("unhandled error: %s", exc)
    return (
        jsonify({"error": "internal_error", "message": "Something went wrong"}),
        500,
    )


@app.get("/api/health")
def get_health():
    upstream_ok = health()
    return (
        jsonify({"status": "ok" if upstream_ok else "degraded", "upstream": upstream_ok}),
        200,
    )


@app.get("/api/stocks/watchlist")
def get_watchlist():
    symbols = parse_symbols(
        request.args.get("symbols"), Config.MAX_SYMBOLS_PER_REQUEST
    )
    quotes = fetch_quotes(symbols)
    if not quotes:
        raise UpstreamError(",".join(symbols))
    # Partial success is normal and is reported as such: the app greys out the
    # symbols that failed instead of showing an app-wide error.
    return jsonify(
        {
            "stocks": [quotes[s] for s in symbols if s in quotes],
            "missing": [s for s in symbols if s not in quotes],
        }
    )


@app.get("/api/stocks/<symbol>")
def get_stock(symbol):
    normalised = normalise_symbol(symbol)
    period = (request.args.get("period") or "1y").lower()
    if period not in PERIOD_DAYS:
        raise ValidationError(
            "period must be one of: %s" % ", ".join(sorted(PERIOD_DAYS))
        )
    return jsonify(fetch_detail(normalised, period))


@app.get("/api/stocks/<symbol>/points")
def get_points(symbol):
    """Close-only price points for one chart period tab."""
    normalised = normalise_symbol(symbol)
    period = (request.args.get("period") or "1M").upper()
    if period not in CHART_PERIODS:
        raise ValidationError(
            "period must be one of: %s" % ", ".join(sorted(CHART_PERIODS))
        )
    points = fetch_price_points(normalised, period)
    if not points:
        raise UpstreamError(normalised)
    return jsonify({"symbol": normalised, "period": period, "points": points})


@app.get("/api/search")
def get_search():
    query = sanitise_query(request.args.get("q"))
    return jsonify({"results": combined_search(query)})


if __name__ == "__main__":
    # host=0.0.0.0 so a watch on the same Wi-Fi can reach it; bind to localhost
    # only if you are tunnelling in instead.
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
