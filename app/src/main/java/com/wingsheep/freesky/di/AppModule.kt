package com.wingsheep.freesky.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.wingsheep.freesky.pipeline.DataStoreRegistrationStore
import com.wingsheep.freesky.model.RegistrationStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.registrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "freesky_reg")

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindRegistrationStore(
        impl: DataStoreRegistrationStore
    ): RegistrationStore

    companion object {

        @Provides
        @Singleton
        fun provideRegistrationDataStore(
            @ApplicationContext context: Context
        ): DataStore<Preferences> = context.registrationDataStore
    }
}
