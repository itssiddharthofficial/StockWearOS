# Wear OS Stock Ticker — Modification Spec v3

**Scope:** Supersedes the chart portion of `MODIFICATION_SPEC_v2.md` (§2b "Interactive price chart") only. Keeps everything else from v2 (home screen list redesign, fundamentals field list, data gaps) and everything from the original `WEAR_OS_STOCK_TICKER_SPEC.md`.

**Why:** the already-built detail screen's navigation (swipeable horizontal pages — Chart / Fundamentals / Returns / Risk — with a dot indicator, each page independently vertically scrollable) is approved as-is and should not be restructured. The only two changes wanted: (1) swap the line+area price chart for a candlestick chart, and (2) add the extra fundamentals/returns/risk data using that same existing horizontal-page + vertical-scroll pattern, not a new single long scrolling screen.

---

## 1. What stays exactly as built (no changes)

- The horizontal pager itself: swipe (or edge-tap arrows) between pages, dot indicator at the bottom showing position.
- Page order and content: **Chart → Fundamentals → Returns → Risk**.
- Each page's own independent vertical scroll (`ScalingLazyColumn` per page) for content that doesn't fit the circle in one view.
- The Fundamentals page layout (2-column cell grid: Mkt Cap, P/E, Div Yield, 52W High, Day Low, Day High).
- The Returns page layout (row per period — 1D/1W/1M/3M/6M/1Y — label, thin colored horizontal bar scaled to magnitude, % value colored green/red).
- The Risk page layout (2-column grid: Beta vs Nifty, Volatility, 52W Low, Volume) plus the on-device-computation disclosure line: *"Returns, beta and volatility are computed on device from weekly price history."*
- The small "Updated Xm ago" status line at the top of Fundamentals/Returns/Risk pages.

**Do not** flatten this into one long vertical screen (that was v2's §2a approach — drop it). The existing paged structure is the approved structure going forward.

---

## 2. Chart page — replace line+area with candlestick

### 2a. Visual spec

- Each candle: a thin high–low wick plus a wider open–close body.
- **Three colors, not two:**
  - **Green** (`#22C55E`) — close ≥ open (bullish candle).
  - **Red** (`#EF4444`) — close < open (bearish candle).
  - **Orange/amber** (`#F59E0B`) — the currently selected/scrubbed candle, overriding whatever its green/red state would be. This is a selection-state color, not a third price direction — it exists so the user's finger position is unambiguous against a dense row of small candles on a 1.4" screen.
- Period tabs above the chart: **1D / 1W / 1M / 3M / 6M / 1Y** — unchanged from v2, still drives which OHLC series is requested/rendered.
- Date range labels under the chart (start/end of the visible window) — unchanged from v2.

### 2b. Interaction

- Drag anywhere across the candle row: the nearest candle is highlighted orange, and a floating tooltip above the chart updates to show:
  - Line 1: `<date label> • ₹<close price>`
  - Line 2 (smaller, muted): `O <open>  H <high>  L <low>`
- On release: selection stays on the released candle (no snap-back) — unlike v2's line-chart marker, a candlestick's whole point is comparing a specific day's range, so leaving it in place is more useful here than snapping to "now."
- Switching period tabs regenerates the candle set and re-selects the most recent (rightmost) candle by default.

### 2c. Data requirement — OHLC, not just close

v2 only needed closing price per data point (line chart). Candlesticks need the full bar:

```json
{
  "symbol": "JSWSTEEL.NS",
  "period": "1M",
  "candles": [
    { "date": "2026-09-22", "open": 1245.00, "high": 1260.30, "low": 1238.50, "close": 1251.17 },
    { "date": "2026-09-23", "open": 1251.17, "high": 1255.00, "low": 1240.10, "close": 1246.80 }
  ]
}
```

- Backend: `yfinance`'s `ticker.history(period=..., interval=...)` already returns Open/High/Low/Close/Volume per bar — this is strictly less work than v2's approach, which had to discard everything except `Close`. Change `backend/data_source.py`'s history serializer to return all four fields instead of `close` only.
- Point count per period stays the same as v2's guidance (~12–16 bars is enough for a legible candlestick row on a 300px-wide chart area — more than ~20 candles and bodies become too thin to tap-select accurately).

---

## 3. Affected files (delta from v2)

| File | Change |
|---|---|
| `ui/components/PriceChart.kt` | Rename/repurpose to `CandlestickChart.kt`; replace `Path` line+area drawing with per-bar wick (`drawLine`) + body (`drawRect`) drawing in a `Canvas`; add the orange selected-state override described in §2b |
| `ui/detail/DetailScreen.kt` | **No structural change** — the existing `HorizontalPager` (or equivalent) stays; only the Chart page's inner content swaps from the old `PriceChart` composable to `CandlestickChart` |
| `data/remote/dto/StockDto.kt` | Add a `CandleDto(date, open, high, low, close)` list field in place of (or alongside, if the old line-chart path is kept elsewhere) the previous close-only series list |
| `backend/data_source.py` | History serializer returns OHLC per bar, not close-only (§2c) |
| `backend/app.py` | `/api/stocks/<symbol>?period=` response shape updates to the candle array shown in §2c |

Everything else listed in v2's §3 affected-files table (Fundamentals/Returns/Risk components, `StockListRow`, `Sparkline`, home screen) is unchanged by this revision.

---

## 4. Acceptance criteria

- Chart page renders a candlestick row (not a line) for every period tab, with visibly distinct green/red bodies.
- Dragging across the candles highlights exactly one candle orange at a time and updates both tooltip lines (close price + O/H/L) with no visible lag.
- Releasing the drag leaves the selection on the released candle (confirmed: no snap-back-to-latest behavior).
- Swiping between Chart / Fundamentals / Returns / Risk pages, and scrolling vertically within each, both continue to work exactly as in the current build — this change touches only the Chart page's internal content, nothing in the pager or the other three pages.
- Home screen (list redesign from v2) is unaffected by this revision.

---

*Hand this file together with `MODIFICATION_SPEC_v2.md` and `WEAR_OS_STOCK_TICKER_SPEC.md` — v3 only overrides v2 §2a/§2b; v2's home-screen section and fundamentals field list still apply.*
