package uz.daftar.app.core.chat

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatDataStore by preferencesDataStore(name = "daftar_chat")

/** Chat oqimini telefonda saqlaydi — ilova yopilib ochilganda holat o'zgarmaydi. */
@Singleton
class ChatStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val chatKey = stringPreferencesKey("chat_json")

    suspend fun load(): String =
        context.chatDataStore.data.map { it[chatKey] ?: "" }.first()

    suspend fun save(json: String) {
        context.chatDataStore.edit { it[chatKey] = json }
    }
}
