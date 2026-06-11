package com.liquidcode7.hearthcraft.di

import android.content.Context
import androidx.room.Room
import com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HearthCraftDatabase =
        Room.databaseBuilder(context, HearthCraftDatabase::class.java, "hearthcraft.db")
            .build()
}
