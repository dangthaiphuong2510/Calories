package com.example.calories.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AuthDataStore.DATA_STORE_NAME,
)

@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.authDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val isLoggedInFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_IS_LOGGED_IN] ?: false
    }

    init {
        scope.launch {
            isLoggedInFlow.collect { loggedIn ->
                _isLoggedIn.value = loggedIn
            }
        }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = loggedIn
        }
        _isLoggedIn.value = loggedIn
    }

    suspend fun clearLoginState() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_IS_LOGGED_IN)
        }
        _isLoggedIn.value = false
    }

    companion object {
        const val DATA_STORE_NAME = "auth_preferences"
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }
}
