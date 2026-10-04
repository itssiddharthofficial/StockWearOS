package com.siddharth.stockticker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    // ---- Watchlist ------------------------------------------------------

    @Query("SELECT * FROM watchlist ORDER BY position ASC")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT symbol FROM watchlist ORDER BY position ASC")
    suspend fun watchlistSymbols(): List<String>

    @Query("SELECT COUNT(*) FROM watchlist")
    suspend fun watchlistCount(): Int

    @Query("SELECT COUNT(*) FROM watchlist WHERE symbol = :symbol")
    suspend fun isInWatchlist(symbol: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchlistEntry(entry: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun deleteWatchlistEntry(symbol: String)

    @Query("UPDATE watchlist SET position = :position WHERE symbol = :symbol")
    suspend fun setPosition(symbol: String, position: Int)

    /** Re-packs positions to 0..n-1 after a removal so the radial layout stays stable. */
    @Transaction
    suspend fun compactPositions() {
        watchlistSymbols().forEachIndexed { index, symbol -> setPosition(symbol, index) }
    }

    // ---- Quotes ---------------------------------------------------------

    /**
     * The whole quote cache. It is bounded by the 5-symbol watchlist cap plus
     * transient search hits, so observing all rows is cheaper than re-subscribing
     * a parameterised query every time the watchlist changes.
     */
    @Query("SELECT * FROM stocks")
    fun observeAllStocks(): Flow<List<StockEntity>>

    @Query("SELECT * FROM stocks WHERE symbol = :symbol")
    fun observeStock(symbol: String): Flow<StockEntity?>

    @Query("SELECT * FROM stocks WHERE symbol = :symbol")
    suspend fun getStock(symbol: String): StockEntity?

    @Upsert
    suspend fun upsertStock(stock: StockEntity)

    @Upsert
    suspend fun upsertStocks(stocks: List<StockEntity>)

    @Query("UPDATE stocks SET lastErrorAt = :at WHERE symbol = :symbol")
    suspend fun markError(symbol: String, at: Long)

    /** Drops cached quotes for symbols nobody is watching any more. */
    @Query("DELETE FROM stocks WHERE symbol NOT IN (SELECT symbol FROM watchlist)")
    suspend fun pruneOrphanQuotes()
}
