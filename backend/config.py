"""Configuration, all of it overridable by environment variable.

No secrets live in this file. If a paid fallback provider is ever added, its key
belongs in the environment -- never in the APK, which is trivially decompiled.
"""
import os

from dotenv import load_dotenv

load_dotenv()


class Config:
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = int(os.getenv("PORT", "5000"))
    DEBUG = os.getenv("FLASK_DEBUG", "0") == "1"

    # Matches the watch app's own refresh cadence. Fetching fresher than the UI
    # shows only burns upstream quota.
    CACHE_TTL_SECONDS = int(os.getenv("CACHE_TTL_SECONDS", "300"))
    SEARCH_CACHE_TTL_SECONDS = int(os.getenv("SEARCH_CACHE_TTL_SECONDS", "3600"))

    # Public deployments get rate limited so the proxy cannot be used as an open
    # relay for scraping the upstream feed.
    RATE_LIMIT = os.getenv("RATE_LIMIT", "60 per minute")
    ENABLE_RATE_LIMIT = os.getenv("ENABLE_RATE_LIMIT", "1") == "1"

    MAX_SYMBOLS_PER_REQUEST = int(os.getenv("MAX_SYMBOLS_PER_REQUEST", "10"))
    UPSTREAM_TIMEOUT_SECONDS = int(os.getenv("UPSTREAM_TIMEOUT_SECONDS", "10"))

    BENCHMARK_SYMBOL = os.getenv("BENCHMARK_SYMBOL", "^NSEI")
