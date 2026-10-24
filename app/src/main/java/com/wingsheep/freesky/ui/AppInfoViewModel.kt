package com.wingsheep.freesky.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingsheep.network.config.RemoteConfigManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Supplies the remote-configured links shown in the About dialog.
 *
 * The privacy-policy URL is only known once the asynchronous remote-config fetch
 * completes, so it is observed rather than read once: a one-shot read always returns
 * the built-in default and a remote override would never reach the UI.
 */
@HiltViewModel
class AppInfoViewModel @Inject constructor(
    remoteConfig: RemoteConfigManager
) : ViewModel() {

    val privacyPolicyUrl: StateFlow<String> = remoteConfig.settings
        .map { it.privacyPolicyUrl }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = remoteConfig.settings.value.privacyPolicyUrl
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
