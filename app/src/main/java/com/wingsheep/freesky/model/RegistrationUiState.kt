package com.wingsheep.freesky.model

sealed class RegistrationUiState {
    data object Checking : RegistrationUiState()
    data object NeedsRegistration : RegistrationUiState()
    data class Registered(val name: String, val color: Int) : RegistrationUiState()
    data class Error(val message: String) : RegistrationUiState()
}