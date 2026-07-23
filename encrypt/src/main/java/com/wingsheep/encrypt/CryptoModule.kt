package com.wingsheep.encrypt

import android.content.Context
import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager

/**
 * Main facade for the encryption module.
 *
 * Provides a single entry point to all crypto components:
 *
 * - [deviceKeyManager] — Layer 1: device identity keypair (AndroidKeyStore)
 * - [eciesDecryptor]   — Layer 2: ECIES group-key decryption
 * - [mlsGroupManager]  — Layer 3: MLS group encryption
 *
 * Post encryption/signing is available via the [PostCrypto] object.
 * Key rotation is available via [com.wingsheep.encrypt.rotation.KeyRotationHandler].
 *
 * Usage:
 * ```
 * val crypto = CryptoModule(context)
 * crypto.deviceKeyManager.generateKeypair()
 * val pk = crypto.deviceKeyManager.publicKeyBytes()
 * ```
 */
class CryptoModule(context: Context) {

    /** Layer 1 — Device identity keypair (AndroidKeyStore-backed). */
    val deviceKeyManager: DeviceKeyManager = DeviceKeyManager

    /** Layer 2 — ECIES decryptor for group-key delivery. */
    val eciesDecryptor: EciesDecryptor = EciesDecryptor

    /** Layer 3 — MLS group encryption manager. */
    val mlsGroupManager: MlsGroupManager = MlsGroupManager(context)
}
