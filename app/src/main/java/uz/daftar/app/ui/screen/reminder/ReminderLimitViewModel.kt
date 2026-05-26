package uz.daftar.app.ui.screen.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.entity.ClientLimitEntity
import uz.daftar.app.data.db.entity.ClientReminderEntity
import uz.daftar.app.domain.usecase.DeleteLimitUseCase
import uz.daftar.app.domain.usecase.DeleteReminderUseCase
import uz.daftar.app.domain.usecase.GetLimitsUseCase
import uz.daftar.app.domain.usecase.GetRemindersUseCase
import uz.daftar.app.domain.usecase.SetLimitUseCase
import uz.daftar.app.domain.usecase.SetReminderUseCase
import javax.inject.Inject

data class ReminderLimitState(
    val reminders: List<ClientReminderEntity> = emptyList(),
    val limits: List<ClientLimitEntity> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class ReminderLimitViewModel @Inject constructor(
    private val getReminders: GetRemindersUseCase,
    private val setReminder: SetReminderUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val getLimits: GetLimitsUseCase,
    private val setLimit: SetLimitUseCase,
    private val deleteLimit: DeleteLimitUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(ReminderLimitState())
    val state: StateFlow<ReminderLimitState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val rem = getReminders(userId)
            val lim = getLimits(userId)
            _state.update { it.copy(reminders = rem, limits = lim, isLoading = false) }
        }
    }

    fun addReminder(client: String, days: Int) {
        if (client.isBlank() || days <= 0) {
            _state.update { it.copy(message = "❌ Mijoz va kun to'g'ri kiritilsin") }
            return
        }
        viewModelScope.launch {
            setReminder(userId, client, days)
            _state.update { it.copy(message = "✅ Eslatma qo'shildi") }
            refresh()
        }
    }

    fun removeReminder(client: String) {
        viewModelScope.launch {
            deleteReminder(userId, client)
            _state.update { it.copy(message = "🗑 Eslatma o'chirildi") }
            refresh()
        }
    }

    fun addLimit(client: String, amount: Double) {
        if (client.isBlank() || amount <= 0) {
            _state.update { it.copy(message = "❌ Mijoz va summa to'g'ri kiritilsin") }
            return
        }
        viewModelScope.launch {
            setLimit(userId, client, amount)
            _state.update { it.copy(message = "✅ Limit qo'shildi") }
            refresh()
        }
    }

    fun removeLimit(client: String) {
        viewModelScope.launch {
            deleteLimit(userId, client)
            _state.update { it.copy(message = "🗑 Limit o'chirildi") }
            refresh()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
