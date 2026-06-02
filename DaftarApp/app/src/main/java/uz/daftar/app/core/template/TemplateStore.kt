package uz.daftar.app.core.template

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.templateDataStore by preferencesDataStore(name = "daftar_templates")

/**
 * Tezkor shablonlar (DataStore).
 * Masalan: "ali a10" yoki "super dokon a50 p1000" — bitta tugma bilan qayta yoziladi.
 */
@Singleton
class TemplateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringSetPreferencesKey("templates")

    val templates: Flow<List<String>> = context.templateDataStore.data
        .map { prefs -> (prefs[key] ?: emptySet()).toList().sorted() }

    suspend fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        context.templateDataStore.edit { prefs ->
            val cur = prefs[key] ?: emptySet()
            prefs[key] = cur + t
        }
    }

    suspend fun remove(text: String) {
        context.templateDataStore.edit { prefs ->
            val cur = prefs[key] ?: emptySet()
            prefs[key] = cur - text
        }
    }
}
