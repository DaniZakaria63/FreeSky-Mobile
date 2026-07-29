package com.wingsheep.freesky.model

import kotlinx.coroutines.flow.Flow

interface RegistrationStore {
    val registrationState: Flow<RegistrationUiState>
    suspend fun save(name: String, color: Int)
    suspend fun saveGroupKey(groupKey: ByteArray)
    suspend fun saveServerNoisePk(pk: ByteArray)
    suspend fun loadGroupKey(): ByteArray?
    suspend fun loadServerNoisePk(): ByteArray?
    suspend fun clear()
}
