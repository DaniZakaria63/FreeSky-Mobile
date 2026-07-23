package com.wingsheep.freesky.di

import com.wingsheep.freesky.tor.TorProxyManager
import com.wingsheep.network.NetworkClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
}