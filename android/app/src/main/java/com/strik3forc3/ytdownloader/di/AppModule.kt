package com.strik3forc3.ytdownloader.di

import android.content.Context
import androidx.room.Room
import com.strik3forc3.ytdownloader.data.db.AppDatabase
import com.strik3forc3.ytdownloader.data.db.ProfileDao
import com.strik3forc3.ytdownloader.data.db.QueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytdownloader.db")
            // No fallbackToDestructiveMigration: losing a user's profiles on an upgrade
            // is exactly the failure mode the reference has, where one unparseable line
            // in settings.ini silently resets everything.
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides fun queueDao(database: AppDatabase): QueueDao = database.queueDao()

    @Provides fun profileDao(database: AppDatabase): ProfileDao = database.profileDao()
}
