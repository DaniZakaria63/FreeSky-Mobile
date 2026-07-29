package com.wingsheep.freesky.ui.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.freesky.model.RegistrationUiState
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
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
    private val registrationStore: RegistrationStore,
    private val mlsGroupManager: MlsGroupManager
) : ViewModel() {

    private val _state = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Checking)
    val state: StateFlow<RegistrationUiState> = _state.asStateFlow()

    private val _consentGiven = MutableStateFlow(false)
    val consentGiven: StateFlow<Boolean> = _consentGiven.asStateFlow()

    init {
        viewModelScope.launch {
            registrationStore.registrationState.collect { storeState ->
                if (storeState is RegistrationUiState.Registered && !DeviceKeyManager.keyExists()) {
                    Timber.w("DataStore says registered but device key missing — forcing re-register")
                    registrationStore.clear()
                    return@collect
                }
                if (storeState is RegistrationUiState.Registered) {
                    Timber.i("Already registered: name=\"${storeState.name}\" color=${storeState.color}")
                    // Restore MLS group state from persisted group key
                    val restoreResult = mlsGroupManager.restoreFromStorage()
                    if (restoreResult.isSuccess) {
                        Timber.i("MLS group restored from storage")
                    } else {
                        // Fallback: load key from DataStore and init
                        val groupKey = registrationStore.loadGroupKey()
                        if (groupKey != null && groupKey.size == 32) {
                            val pkSec1 = registrationHandler.deviceKeyManager.publicKeySec1()
                            val initResult = mlsGroupManager.initFromKeyMaterial(groupKey, pkSec1)
                            Timber.i("MLS group init from DataStore: ${if (initResult.isSuccess) "ok" else "failed"}")
                        }
                    }
                }
                _state.value = storeState
            }
        }
    }

    fun toggleConsent() {
        _consentGiven.value = !_consentGiven.value
    }

    fun register() {
        Timber.i("Register button pressed — starting registration flow")
        viewModelScope.launch {
            _state.value = RegistrationUiState.Checking
            try {
                val result = registrationHandler.register()
                // Persist crypto material FIRST — before identity triggers navigation
                registrationStore.saveGroupKey(result.groupKey)
                result.serverNoisePk?.let { registrationStore.saveServerNoisePk(it) }
                // Persist identity LAST — so CommunityViewModel sees complete DataStore state
                registrationStore.save(name = result.name, color = result.color)
                // Initialize MLS group with the decrypted group key
                val pkSec1 = registrationHandler.deviceKeyManager.publicKeySec1()
                val initResult = mlsGroupManager.initFromKeyMaterial(
                    groupStateBytes = result.groupKey,
                    identityKeyBytes = pkSec1
                )
                Timber.i("MLS group init: ${if (initResult.isSuccess) "ok" else "failed"}")
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
