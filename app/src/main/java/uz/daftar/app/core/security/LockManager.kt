package uz.daftar.app.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lockDataStore by preferencesDataStore(name = "daftar_lock")

/**
 * Biometric qulf sozlamasini saqlaydi (DataStore).
 * Qulf yoqilgan bo'lsa, ilova ochilganda barmoq izi so'raladi.
 */
@Singleton
class LockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lockEnabledKey = booleanPreferencesKey("lock_enabled")

    val lockEnabled: Flow<Boolean> = context.lockDataStore.data
        .map { prefs -> prefs[lockEnabledKey] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.lockDataStore.edit { prefs ->
            prefs[lockEnabledKey] = enabled
        }
    }
}
