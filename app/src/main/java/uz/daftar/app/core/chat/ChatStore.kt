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
    private val lastReportKey = stringPreferencesKey("last_report_date")
    private val pendingKey = stringPreferencesKey("pending_widget")

    suspend fun load(): String =
        context.chatDataStore.data.map { it[chatKey] ?: "" }.first()

    suspend fun save(json: String) {
        context.chatDataStore.edit { it[chatKey] = json }
    }

    /** Widjetdan saqlangan yozuvni navbatga qo'shish (keyin chatda ko'rsatiladi). */
    suspend fun addPending(line: String) {
        context.chatDataStore.edit { prefs ->
            val cur = prefs[pendingKey] ?: ""
            prefs[pendingKey] = if (cur.isBlank()) line else cur + "\n" + line
        }
    }

    /** Navbatdagi widjet yozuvlarini olib, navbatni tozalash. */
    suspend fun drainPending(): List<String> {
        val cur = context.chatDataStore.data.map { it[pendingKey] ?: "" }.first()
        if (cur.isNotBlank()) {
            context.chatDataStore.edit { it[pendingKey] = "" }
        }
        return cur.lines().map { it.trim() }.filter { it.isNotBlank() }
    }

    suspend fun getLastReportDate(): String =
        context.chatDataStore.data.map { it[lastReportKey] ?: "" }.first()

    suspend fun setLastReportDate(date: String) {
        context.chatDataStore.edit { it[lastReportKey] = date }
    }

    private val lastDebtRemKey = stringPreferencesKey("last_debt_rem_date")

    /** Qarz eslatmasi oxirgi ko'rsatilgan sana (kuniga 1 marta, 10:00 dan keyin). */
    suspend fun getLastDebtRemDate(): String =
        context.chatDataStore.data.map { it[lastDebtRemKey] ?: "" }.first()

    suspend fun setLastDebtRemDate(date: String) {
        context.chatDataStore.edit { it[lastDebtRemKey] = date }
    }
}
