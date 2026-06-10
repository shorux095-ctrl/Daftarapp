package uz.daftar.app.ui.screen.toliq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.domain.usecase.FullReport
import uz.daftar.app.domain.usecase.GetFullReportUseCase
import javax.inject.Inject

data class ToliqState(
    val isLoading: Boolean = true,
    val report: FullReport? = null,
    val error: String? = null
)

@HiltViewModel
class ToliqHisobotViewModel @Inject constructor(
    private val getFullReport: GetFullReportUseCase
) : ViewModel() {

    private val userId: Long = 1L

    private val _state = MutableStateFlow(ToliqState())
    val state: StateFlow<ToliqState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val r = getFullReport(userId)
                _state.update { it.copy(isLoading = false, report = r, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
