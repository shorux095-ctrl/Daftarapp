package uz.daftar.app.ui.screen.eslat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.core.notify.EslatmaWorker
import uz.daftar.app.core.notify.cancelEslatmaAlarm
import uz.daftar.app.core.notify.scheduleEslatmaAlarm
import uz.daftar.app.data.db.dao.EslatmaDao
import uz.daftar.app.data.db.entity.EslatmaEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class EslatViewModel @Inject constructor(
    private val dao: EslatmaDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val userId = 1L
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val items: StateFlow<List<EslatmaEntity>> =
        dao.all(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** daysFromNow: 0=bugun, 1=ertaga ... ; hour/minute — vaqt */
    fun add(text: String, daysFromNow: Int, hour: Int, minute: Int) {
        val t = text.trim()
        if (t.isEmpty()) { _message.value = "❌ Eslatma matnini yozing"; return }
        val day = LocalDate.now().plusDays(daysFromNow.toLong())
        val triggerAt = day.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            _message.value = "❌ O'tib ketgan vaqt — keyinroq tanlang"
            return
        }
        viewModelScope.launch {
            val id = dao.insert(EslatmaEntity(userId = userId, text = t, triggerAt = triggerAt))
            scheduleNotification(id, t, triggerAt)
            _message.value = "✅ Eslatma qo'yildi"
        }
    }

    fun delete(item: EslatmaEntity) {
        viewModelScope.launch {
            WorkManager.getInstance(context).cancelAllWorkByTag("eslatma_${item.id}")
            cancelEslatmaAlarm(context, item.id)
            dao.delete(item.id)
            _message.value = "🗑 O'chirildi"
        }
    }

    private fun scheduleNotification(id: Long, text: String, triggerAt: Long) {
        // AlarmManager — ANIQ vaqtda ishlaydi (WorkManager Xiaomi'da kechikardi)
        scheduleEslatmaAlarm(context, id, text, triggerAt)
    }

    fun clearMessage() { _message.value = null }
}
