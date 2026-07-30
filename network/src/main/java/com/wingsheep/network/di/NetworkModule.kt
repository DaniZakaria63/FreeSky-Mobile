package com.wingsheep.network.di

import com.wingsheep.network.api.ApiClient
import com.wingsheep.network.api.OkHttpApiClient
import com.wingsheep.network.config.RemoteConfigManager
import com.wingsheep.network.noise.NoiseSessionFactory
import com.wingsheep.network.rotation.RegistrationHandler
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiClient(
        client: OkHttpClient,
        remoteConfig: RemoteConfigManager
    ): ApiClient =
        OkHttpApiClient(
            baseUrl = "http://${remoteConfig.serverIp}:${remoteConfig.serverPort}",
            client = client
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
        NoiseSessionFactory(host = remoteConfig.serverIp, port = remoteConfig.noisePort)
}
