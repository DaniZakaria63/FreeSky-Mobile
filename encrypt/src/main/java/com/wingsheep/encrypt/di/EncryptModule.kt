package com.wingsheep.encrypt.di

import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.crypto.Hkdf
import com.wingsheep.encrypt.identity.DeviceKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for the stateless crypto helpers.
 *
 * Stateful collaborators ([com.wingsheep.encrypt.PostCrypto],
 * [com.wingsheep.encrypt.mls.MlsGroupManager]) are constructor-injected directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object EncryptModule {

    @Provides
    @Singleton
    fun provideDeviceKeyManager(): DeviceKeyManager = DeviceKeyManager

    @Provides
    @Singleton
    fun provideEciesDecryptor(): EciesDecryptor = EciesDecryptor

    @Provides
    @Singleton
    fun provideHkdf(): Hkdf = Hkdf
}
