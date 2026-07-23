package com.wingsheep.freesky.model

import kotlinx.coroutines.flow.Flow

interface RegistrationStore {
    val registrationState: Flow<RegistrationUiState>
    suspend fun save(name: String, color: Int)
}