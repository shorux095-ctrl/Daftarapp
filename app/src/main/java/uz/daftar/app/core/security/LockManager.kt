package uz.daftar.app.core.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lockDataStore by preferencesDataStore(name = "daftar_lock")

/**
 * Qulf sozlamalari (DataStore):
 *  - lock_enabled: biometric/barmoq izi qulfi
 *  - pin_code: ilova ichidagi maxsus PIN kod (ixtiyoriy)
 */
@Singleton
class LockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lockEnabledKey = booleanPreferencesKey("lock_enabled")
    private val pinKey = stringPreferencesKey("pin_code")

    val lockEnabled: Flow<Boolean> = context.lockDataStore.data
        .map { prefs -> prefs[lockEnabledKey] ?: false }

    suspend fun setLockEnabled(enabled: Boolean) {
        context.lockDataStore.edit { prefs ->
            prefs[lockEnabledKey] = enabled
        }
    }

    /** PIN kod (o'rnatilmagan bo'lsa null) */
    val pinCode: Flow<String?> = context.lockDataStore.data
        .map { prefs -> prefs[pinKey] }

    suspend fun setPinCode(pin: String?) {
        context.lockDataStore.edit { prefs ->
            if (pin.isNullOrBlank()) prefs.remove(pinKey)
            else prefs[pinKey] = pin
        }
    }
}
