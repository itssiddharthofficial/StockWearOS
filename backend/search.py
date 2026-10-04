"""Search: local NSE catalogue merged with upstream hits.

The local list answers instantly and keeps search working when upstream is down
or throttled; the upstream leg covers everything the list does not.
"""
from cache import TtlCache
from config import Config
from data_source import search_symbols

_search_cache = TtlCache(Config.SEARCH_CACHE_TTL_SECONDS)

# Mirrors the on-device catalogue in the app, so results are consistent whether
# the watch searches locally or through this proxy.
LOCAL_CATALOGUE = [
    ("^NSEI", "Nifty 50", "Index"),
    ("^BSESN", "BSE Sensex", "Index"),
    ("RELIANCE.NS", "Reliance Industries", "NSE"),
    ("TCS.NS", "Tata Consultancy Services", "NSE"),
    ("HDFCBANK.NS", "HDFC Bank", "NSE"),
    ("ICICIBANK.NS", "ICICI Bank", "NSE"),
    ("INFY.NS", "Infosys", "NSE"),
    ("SBIN.NS", "State Bank of India", "NSE"),
    ("BHARTIARTL.NS", "Bharti Airtel", "NSE"),
    ("ITC.NS", "ITC", "NSE"),
    ("LT.NS", "Larsen and Toubro", "NSE"),
    ("HINDUNILVR.NS", "Hindustan Unilever", "NSE"),
    ("AXISBANK.NS", "Axis Bank", "NSE"),
    ("KOTAKBANK.NS", "Kotak Mahindra Bank", "NSE"),
    ("BAJFINANCE.NS", "Bajaj Finance", "NSE"),
    ("MARUTI.NS", "Maruti Suzuki", "NSE"),
    ("M&M.NS", "Mahindra and Mahindra", "NSE"),
    ("TATAMOTORS.NS", "Tata Motors", "NSE"),
    ("TATASTEEL.NS", "Tata Steel", "NSE"),
    ("JSWSTEEL.NS", "JSW Steel", "NSE"),
    ("COALINDIA.NS", "Coal India", "NSE"),
    ("NTPC.NS", "NTPC", "NSE"),
    ("POWERGRID.NS", "Power Grid Corporation", "NSE"),
    ("ONGC.NS", "Oil and Natural Gas Corp", "NSE"),
    ("ADANIENT.NS", "Adani Enterprises", "NSE"),
    ("ADANIPORTS.NS", "Adani Ports and SEZ", "NSE"),
    ("ASIANPAINT.NS", "Asian Paints", "NSE"),
    ("NESTLEIND.NS", "Nestle India", "NSE"),
    ("TITAN.NS", "Titan Company", "NSE"),
    ("DMART.NS", "Avenue Supermarts", "NSE"),
    ("SUNPHARMA.NS", "Sun Pharmaceutical", "NSE"),
    ("DRREDDY.NS", "Dr Reddys Laboratories", "NSE"),
    ("CIPLA.NS", "Cipla", "NSE"),
    ("APOLLOHOSP.NS", "Apollo Hospitals", "NSE"),
    ("HCLTECH.NS", "HCL Technologies", "NSE"),
    ("WIPRO.NS", "Wipro", "NSE"),
    ("TECHM.NS", "Tech Mahindra", "NSE"),
    ("LTIM.NS", "LTIMindtree", "NSE"),
    ("INDUSINDBK.NS", "IndusInd Bank", "NSE"),
    ("BANKBARODA.NS", "Bank of Baroda", "NSE"),
    ("SBILIFE.NS", "SBI Life Insurance", "NSE"),
    ("HDFCLIFE.NS", "HDFC Life Insurance", "NSE"),
    ("BAJAJ-AUTO.NS", "Bajaj Auto", "NSE"),
    ("HEROMOTOCO.NS", "Hero MotoCorp", "NSE"),
    ("EICHERMOT.NS", "Eicher Motors", "NSE"),
    ("ULTRACEMCO.NS", "UltraTech Cement", "NSE"),
    ("GRASIM.NS", "Grasim Industries", "NSE"),
    ("VEDL.NS", "Vedanta", "NSE"),
    ("TATAPOWER.NS", "Tata Power", "NSE"),
    ("BEL.NS", "Bharat Electronics", "NSE"),
    ("HAL.NS", "Hindustan Aeronautics", "NSE"),
    ("ZOMATO.NS", "Eternal (Zomato)", "NSE"),
    ("DLF.NS", "DLF", "NSE"),
    ("JIOFIN.NS", "Jio Financial Services", "NSE"),
    ("LICI.NS", "Life Insurance Corp of India", "NSE"),
    ("IRCTC.NS", "IRCTC", "NSE"),
    ("SUZLON.NS", "Suzlon Energy", "NSE"),
]


def _score(symbol, name, query):
    base = symbol.split(".")[0].upper()
    upper_name = name.upper()
    if base == query:
        return 0
    if base.startswith(query):
        return 1
    if upper_name.startswith(query):
        return 2
    if any(word.startswith(query) for word in upper_name.split(" ")):
        return 3
    if query in base:
        return 4
    if query in upper_name:
        return 5
    return None


def local_matches(query, limit=24):
    upper = query.strip().upper()
    if not upper:
        return [
            {"symbol": s, "name": n, "exchange": e}
            for s, n, e in LOCAL_CATALOGUE[:limit]
        ]
    scored = []
    for symbol, name, exchange in LOCAL_CATALOGUE:
        score = _score(symbol, name, upper)
        if score is not None:
            scored.append((score, len(name), symbol, name, exchange))
    scored.sort()
    return [
        {"symbol": s, "name": n, "exchange": e}
        for _score_value, _length, s, n, e in scored[:limit]
    ]


def combined_search(query, limit=30):
    """Local hits first, upstream hits appended, de-duplicated by symbol."""
    cached, _age = _search_cache.get(query.upper())
    if cached is not None:
        return cached

    merged = {}
    for item in local_matches(query):
        merged[item["symbol"]] = item

    if len(query.strip()) >= 2:
        for item in search_symbols(query):
            merged.setdefault(item["symbol"], item)

    results = list(merged.values())[:limit]
    _search_cache.put(query.upper(), results)
    return results
