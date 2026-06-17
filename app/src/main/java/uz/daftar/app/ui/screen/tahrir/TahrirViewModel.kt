package uz.daftar.app.ui.screen.tahrir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.daftar.app.data.repository.TransactionRepository
import uz.daftar.app.domain.model.Transaction
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TahrirViewModel @Inject constructor(
    private val repo: TransactionRepository
) : ViewModel() {

    private val userId = 1L

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _nameFilter = MutableStateFlow("")
    val nameFilter: StateFlow<String> = _nameFilter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Tanlangan sana + ism filtri bo'yicha o'sha kunning yozuvlari */
    val items: StateFlow<List<Transaction>> =
        combine(_date, _nameFilter) { d, nf -> Pair(d, nf) }
            .flatMapLatest { (d, nf) ->
                repo.observeBetween(userId, d, d.plusDays(1)).map { list ->
                    val f = nf.trim()
                    val filtered = if (f.isEmpty()) list
                        else list.filter { it.clientName.contains(f, ignoreCase = true) }
                    filtered.sortedBy { it.date }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDate(d: LocalDate) { _date.value = d }
    fun prevDay() { _date.value = _date.value.minusDays(1) }
    fun nextDay() { _date.value = _date.value.plusDays(1) }
    fun today() { _date.value = LocalDate.now() }
    fun setNameFilter(s: String) { _nameFilter.value = s }
    fun clearMessage() { _message.value = null }

    /** Bitta yozuvni o'chirish */
    fun delete(id: Long) {
        viewModelScope.launch {
            repo.deleteByIds(listOf(id))
            _message.value = "🗑 O'chirildi"
        }
    }

    /** Ekranda ko'rinayotgan barcha yozuvlarni o'chirish (adashib kiritilgan kunni tozalash) */
    fun deleteAllShown() {
        viewModelScope.launch {
            val ids = items.value.map { it.id }
            if (ids.isEmpty()) { _message.value = "O'chirish uchun yozuv yo'q"; return@launch }
            repo.deleteByIds(ids)
            _message.value = "🗑 ${ids.size} ta yozuv o'chirildi"
        }
    }
}
