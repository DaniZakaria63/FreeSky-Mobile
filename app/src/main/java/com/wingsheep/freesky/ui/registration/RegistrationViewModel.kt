package com.wingsheep.freesky.ui.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.freesky.model.RegistrationUiState
import com.wingsheep.network.rotation.RegistrationHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val registrationHandler: RegistrationHandler,
    private val registrationStore: RegistrationStore
) : ViewModel() {

    private val _state = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Checking)
    val state: StateFlow<RegistrationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            registrationStore.registrationState.collect { storeState ->
                if (storeState is RegistrationUiState.Registered) {
                    Timber.i("Already registered: name=\"${storeState.name}\" color=${storeState.color}")
                }
                _state.value = storeState
            }
        }
    }

    fun register() {
        Timber.i("Register button pressed — starting registration flow")
        viewModelScope.launch {
            _state.value = RegistrationUiState.Checking
            try {
                val result = registrationHandler.register()
                registrationStore.save(name = result.name, color = result.color)
                Timber.i("Registered as: ${result.name} (color=${result.color})")
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                _state.value = RegistrationUiState.Error(
                    e.message ?: "Registration failed"
                )
            }
        }
    }
}