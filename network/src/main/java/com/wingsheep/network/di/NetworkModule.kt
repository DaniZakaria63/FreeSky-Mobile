package com.wingsheep.network.di

import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.network.api.ApiClient
import com.wingsheep.network.api.OkHttpApiClient
import com.wingsheep.network.rotation.KeyRotationHandler
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
    fun provideApiClient(client: OkHttpClient): ApiClient =
        OkHttpApiClient(baseUrl = "http://192.168.0.103:3000", client = client)

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
    fun provideKeyRotationHandler(
        apiClient: ApiClient,
        mlsManager: MlsGroupManager
    ): KeyRotationHandler = KeyRotationHandler(
        apiClient = apiClient,
        mlsManager = mlsManager
    )
}