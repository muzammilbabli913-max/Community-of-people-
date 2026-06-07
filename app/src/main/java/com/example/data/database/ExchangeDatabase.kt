package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.UserDao
import com.example.data.dao.ListingDao
import com.example.data.dao.MessageDao
import com.example.data.model.User
import com.example.data.model.Listing
import com.example.data.model.Message

@Database(entities = [User::class, Listing::class, Message::class], version = 1, exportSchema = false)
abstract class ExchangeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun listingDao(): ListingDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: ExchangeDatabase? = null

        fun getDatabase(context: Context): ExchangeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExchangeDatabase::class.java,
                    "neighborhood_exchange_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
