package com.wingsheep.network.di

import com.wingsheep.network.api.ApiClient
import com.wingsheep.network.api.OkHttpApiClient
import com.wingsheep.network.config.RemoteConfigManager
import com.wingsheep.network.noise.NoiseSessionFactory
import com.wingsheep.network.rotation.RegistrationHandler
import com.wingsheep.network.tor.TorProxyManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiClient(
        remoteConfig: RemoteConfigManager,
        torProxyManager: TorProxyManager
    ): ApiClient =
        OkHttpApiClient(
            // HTTPS tunnel hostname in production (`api_base_url` remote key);
            // direct http://ip:port fallback when unset.  Resolved per call so
            // the fetched remote config is always honoured.
            baseUrlProvider = { remoteConfig.apiBaseUrl },
            // Routed through the Tor SOCKS proxy whenever a circuit is up;
            // falls back to a direct connection otherwise.
            clientProvider = { torProxyManager.httpClient() }
        )

    @Provides
    @Singleton
    fun provideRegistrationHandler(
        apiClient: ApiClient,
        @ApplicationContext context: Context
    ): RegistrationHandler = RegistrationHandler(
        apiClient = apiClient,
        context = context
    )

    @Provides
    @Singleton
    fun provideNoiseSessionFactory(
        remoteConfig: RemoteConfigManager
    ): NoiseSessionFactory =
        // Noise is raw TCP — it cannot ride the Cloudflare HTTP proxy, so it
        // keeps dialing the server IP directly (server_ip + noise_port).
        // Host and port are read per session rather than copied here: the remote
        // config arrives asynchronously, so snapshotting it in a @Singleton would
        // freeze the defaults for the life of the process.
        NoiseSessionFactory(
            hostProvider = { remoteConfig.serverIp },
            portProvider = { remoteConfig.noisePort }
        )
}
