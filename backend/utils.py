"""Validation and formatting helpers.

The symbol parameters are attacker-controllable even though they are typed on a
watch, so they are validated here before anything reaches the upstream library.
"""
import math
import re

# NSE/BSE tickers plus index symbols: letters, digits, dot, caret, hyphen and
# ampersand (M&M.NS is a real listing). Nothing else gets through.
SYMBOL_RE = re.compile(r"^[A-Z0-9][A-Z0-9.&^-]{0,19}$")

MAX_QUERY_LENGTH = 48


class ValidationError(ValueError):
    """Raised for input that should never reach the data source."""


def normalise_symbol(raw):
    """Upper-cases and validates one symbol, or raises ValidationError."""
    if raw is None:
        raise ValidationError("symbol is required")
    symbol = str(raw).strip().upper()
    if not symbol:
        raise ValidationError("symbol is required")
    if symbol.startswith("^"):
        candidate = symbol[1:]
        if not re.match(r"^[A-Z0-9]{1,18}$", candidate):
            raise ValidationError("invalid index symbol: %s" % raw)
        return symbol
    if not SYMBOL_RE.match(symbol):
        raise ValidationError("invalid symbol: %s" % raw)
    return symbol


def parse_symbols(raw, limit):
    """Splits and validates a comma-separated symbol list."""
    if not raw:
        raise ValidationError("symbols is required")
    parts = [p for p in (s.strip() for s in str(raw).split(",")) if p]
    if not parts:
        raise ValidationError("symbols is required")
    if len(parts) > limit:
        raise ValidationError("at most %d symbols per request" % limit)
    seen = []
    for part in parts:
        symbol = normalise_symbol(part)
        if symbol not in seen:
            seen.append(symbol)
    return seen


def sanitise_query(raw):
    """Trims a search query to something safe to forward upstream."""
    query = (raw or "").strip()
    if not query:
        raise ValidationError("q is required")
    if len(query) > MAX_QUERY_LENGTH:
        query = query[:MAX_QUERY_LENGTH]
    # Keep it to characters that can appear in a company name or ticker.
    return re.sub(r"[^A-Za-z0-9 .&^-]", "", query)


def clean_number(value):
    """Returns a JSON-safe float, or None for missing/NaN/infinite values."""
    if value is None:
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if math.isnan(number) or math.isinf(number):
        return None
    return number


def clean_int(value):
    number = clean_number(value)
    return None if number is None else int(number)


def market_cap_label(value):
    """Indian convention: crore and lakh crore, which is how NSE quotes it."""
    number = clean_number(value)
    if not number or number <= 0:
        return None
    crore = number / 1e7
    if crore >= 1e5:
        return "%.1fL Cr" % (crore / 1e5)
    if crore >= 1e3:
        return "{:,.0f} Cr".format(crore)
    if crore >= 1:
        return "%.1f Cr" % crore
    return "%.1f L" % (number / 1e5)
