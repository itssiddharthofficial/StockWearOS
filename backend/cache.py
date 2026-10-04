"""In-process TTL cache with jittered backoff on upstream failure.

The upstream feed is unofficial and throttles by IP. Caching is therefore not an
optimisation here, it is what keeps the proxy working: one cache entry serves
every watch, and a failed fetch is remembered briefly so a retry storm cannot
turn a hiccup into a block.
"""
import random
import threading
import time


class TtlCache:
    def __init__(self, ttl_seconds, failure_ttl_seconds=30):
        self._ttl = ttl_seconds
        self._failure_ttl = failure_ttl_seconds
        self._entries = {}
        self._failures = {}
        self._lock = threading.RLock()

    def get(self, key):
        """Returns (value, age_seconds) or (None, None)."""
        with self._lock:
            entry = self._entries.get(key)
            if not entry:
                return None, None
            value, stored_at = entry
            age = time.time() - stored_at
            if age > self._ttl:
                return None, None
            return value, age

    def get_stale(self, key):
        """Last known value regardless of age -- staleness beats blankness."""
        with self._lock:
            entry = self._entries.get(key)
            if not entry:
                return None, None
            value, stored_at = entry
            return value, time.time() - stored_at

    def put(self, key, value):
        with self._lock:
            self._entries[key] = (value, time.time())
            self._failures.pop(key, None)

    def in_backoff(self, key):
        """True while a recent failure for this key is still cooling off."""
        with self._lock:
            until = self._failures.get(key)
            if until is None:
                return False
            if time.time() >= until:
                self._failures.pop(key, None)
                return False
            return True

    def mark_failure(self, key):
        with self._lock:
            # Jitter so many keys failing at once do not retry in lockstep.
            jitter = random.uniform(0.5, 1.5)
            self._failures[key] = time.time() + self._failure_ttl * jitter

    def clear(self):
        with self._lock:
            self._entries.clear()
            self._failures.clear()
