package uz.daftar.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.daftar.app.core.security.LockManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val lockManager: LockManager
) : ViewModel() {

    val lockEnabled: StateFlow<Boolean> = lockManager.lockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            lockManager.setLockEnabled(enabled)
        }
    }
}
