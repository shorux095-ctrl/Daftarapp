package uz.daftar.app.core.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "daftar_theme")

/**
 * Mavzu (ko'rinish) sozlamasi:
 *  0 = Tizim (telefonga ergashadi), 1 = Yorug', 2 = Tungi
 */
@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modeKey = intPreferencesKey("theme_mode")

    val themeMode: Flow<Int> = context.themeDataStore.data.map { it[modeKey] ?: 0 }

    suspend fun setThemeMode(mode: Int) {
        context.themeDataStore.edit { it[modeKey] = mode }
    }
}
