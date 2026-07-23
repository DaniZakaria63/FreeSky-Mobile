package com.wingsheep.encrypt

import android.content.Context
import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager

class CryptoModule(context: Context) {

    val deviceKeyManager: DeviceKeyManager = DeviceKeyManager

    val eciesDecryptor: EciesDecryptor = EciesDecryptor

    val mlsGroupManager: MlsGroupManager = MlsGroupManager(context)
}