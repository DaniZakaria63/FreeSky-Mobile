package com.wingsheep.network.di

import android.content.Context
import com.wingsheep.network.api.ApiClient
import com.wingsheep.network.api.OkHttpApiClient
import com.wingsheep.network.config.RemoteConfigManager
import com.wingsheep.network.rotation.RegistrationHandler
import com.wingsheep.network.tor.TorProxyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * HTTP bindings.
 *
 * Everything else in this module — [com.wingsheep.network.noise.NoiseSessionFactory],
 * [com.wingsheep.network.tor.TorConfigStore],
 * [com.wingsheep.network.config.RemoteConfigManager] — is constructor-injected.
 * [RegistrationHandler] is built here because its crypto collaborators are Kotlin
 * object singletons, not injectable constructor arguments.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Both the base URL and the client are resolved per call:
     *  - the remote config carrying the URL arrives asynchronously, so a snapshot
     *    taken here would freeze the built-in default for the whole process;
     *  - Tor may come and go, and [TorProxyManager.httpClient] picks the proxy or
     *    the direct fallback for the current circuit.
     */
    @Provides
    @Singleton
    fun provideApiClient(
        remoteConfig: RemoteConfigManager,
        torProxyManager: TorProxyManager
    ): ApiClient = OkHttpApiClient(
        baseUrlProvider = { remoteConfig.settings.value.apiBaseUrl },
        clientProvider = torProxyManager::httpClient
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
}
