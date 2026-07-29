package com.wingsheep.freesky.pipeline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.freesky.model.RegistrationUiState
import kotlinx.coroutines.flow.first
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
        val KEY_GROUP_KEY = byteArrayPreferencesKey("group_key")
        val KEY_SERVER_NOISE_PK = byteArrayPreferencesKey("server_noise_pk")
        val KEY_PRIVACY_URL = stringPreferencesKey("privacy_url")
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

    override suspend fun saveGroupKey(groupKey: ByteArray) {
        dataStore.edit { prefs ->
            prefs[KEY_GROUP_KEY] = groupKey
        }
    }

    override suspend fun saveServerNoisePk(pk: ByteArray) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_NOISE_PK] = pk
        }
    }

    override suspend fun loadGroupKey(): ByteArray? {
        return dataStore.data.first()[KEY_GROUP_KEY]
    }

    override suspend fun loadServerNoisePk(): ByteArray? {
        return dataStore.data.first()[KEY_SERVER_NOISE_PK]
    }

    override suspend fun savePrivacyUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PRIVACY_URL] = url
        }
    }

    override suspend fun loadPrivacyUrl(): String? {
        return dataStore.data.first()[KEY_PRIVACY_URL]
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
