package uz.daftar.app.core.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore by preferencesDataStore(name = "daftar_ai")

/** AI kalitlari — telefonda saqlanadi, kodga yozilmaydi. Ko'p provayder (fallback). */
@Singleton
class AiSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun keyOf(provider: String) = stringPreferencesKey("key_$provider")

    suspend fun getKey(provider: String): String =
        context.aiDataStore.data.map { it[keyOf(provider)] ?: "" }.first()

    suspend fun setKey(provider: String, key: String) {
        context.aiDataStore.edit { it[keyOf(provider)] = key.trim() }
    }

    /** Qaysi provayderlarda kalit bor — saqlangan holat */
    suspend fun configured(): List<String> =
        AiProviders.ALL.filter { getKey(it.id).isNotBlank() }.map { it.id }
}
