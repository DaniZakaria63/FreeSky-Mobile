package com.wingsheep.freesky.pipeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.freesky.model.RegistrationUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreRegistrationStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : RegistrationStore {

    private companion object {
        val KEY_NAME = stringPreferencesKey("user_name")
        val KEY_COLOR = intPreferencesKey("user_color")
    }

    override val registrationState: Flow<RegistrationUiState> = dataStore.data.map { prefs ->
        val name = prefs[KEY_NAME]
        if (name != null) {
            RegistrationUiState.Registered(name = name, color = prefs[KEY_COLOR] ?: 0)
        } else {
            RegistrationUiState.NeedsRegistration
        }
    }

    override suspend fun save(name: String, color: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NAME] = name
            prefs[KEY_COLOR] = color
        }
    }
}