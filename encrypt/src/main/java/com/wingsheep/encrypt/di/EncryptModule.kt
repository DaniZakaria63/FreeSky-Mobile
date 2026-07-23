package com.wingsheep.encrypt.di

import android.content.Context
import com.wingsheep.encrypt.PostCrypto
import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.crypto.Hkdf
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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

    @Provides
    @Singleton
    fun providePostCrypto(): PostCrypto = PostCrypto

    @Provides
    @Singleton
    fun provideMlsGroupManager(@ApplicationContext context: Context): MlsGroupManager =
        MlsGroupManager(context)
}