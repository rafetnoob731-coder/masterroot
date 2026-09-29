package com.masterroot

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.masterroot.infrastructure.adb.*
import com.masterroot.infrastructure.storage.BackupRepository
import com.masterroot.infrastructure.storage.LogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindConnectionManager(impl: ConnectionManagerImpl): ConnectionManager
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
}
