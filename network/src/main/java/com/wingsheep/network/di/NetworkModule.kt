package com.wingsheep.network.di

import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.network.NetworkClient
import com.wingsheep.network.api.ApiClient
import com.wingsheep.network.api.OkHttpApiClient
import com.wingsheep.network.rotation.KeyRotationHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetworkClient.defaultClient

    @Provides
    @Singleton
    fun provideApiClient(client: OkHttpClient): ApiClient =
        OkHttpApiClient(baseUrl = "https://api.freesky.app", client = client)

    @Provides
    @Singleton
    fun provideKeyRotationHandler(
        apiClient: ApiClient,
        mlsManager: MlsGroupManager
    ): KeyRotationHandler = KeyRotationHandler(
        apiClient = apiClient,
        mlsManager = mlsManager
    )
}