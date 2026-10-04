package com.siddharth.stockticker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StockEntity::class, WatchlistEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {

    abstract fun stockDao(): StockDao

    companion object {
        @Volatile private var instance: StockDatabase? = null

        fun get(context: Context): StockDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stock_ticker.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
