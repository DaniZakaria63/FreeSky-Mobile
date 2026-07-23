package com.wingsheep.freesky.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.wingsheep.freesky.registration.DataStoreRegistrationStore
import com.wingsheep.freesky.registration.RegistrationStore
import com.wingsheep.freesky.tor.TorProxyManager
import com.wingsheep.network.NetworkClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
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
        fun provideOkHttpClient(
            torProxyManager: TorProxyManager
        ): OkHttpClient {
            return if (torProxyManager.isReady) {
                val proxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress("127.0.0.1", torProxyManager.socksPort)
                )
                NetworkClient.newClient().newBuilder()
                    .proxy(proxy)
                    .build()
            } else {
                NetworkClient.defaultClient
            }
        }

        @Provides
        @Singleton
        fun provideRegistrationDataStore(
            @ApplicationContext context: Context
        ): DataStore<Preferences> = context.registrationDataStore
    }
}