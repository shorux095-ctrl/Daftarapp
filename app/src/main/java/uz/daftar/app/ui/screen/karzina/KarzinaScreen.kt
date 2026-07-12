package uz.daftar.app.ui.screen.karzina

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.data.db.entity.DeletedTransactionEntity
import uz.daftar.app.domain.usecase.GetDeletedTransactionsUseCase
import uz.daftar.app.domain.usecase.PurgeKarzinaUseCase
import uz.daftar.app.domain.usecase.RestoreTransactionUseCase
import java.util.Locale
import javax.inject.Inject

data class KarzinaState(
    val items: List<DeletedTransactionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class KarzinaViewModel @Inject constructor(
    private val getDeleted: GetDeletedTransactionsUseCase,
    private val restore: RestoreTransactionUseCase,
    private val deletedDao: uz.daftar.app.data.db.dao.DeletedTransactionDao,   // v177
    private val purge: PurgeKarzinaUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val _state = MutableStateFlow(KarzinaState())
    val state: StateFlow<KarzinaState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            purge(userId)  // 7 kundan oldingilarni avtomatik tozalash
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val items = getDeleted(userId)
            _state.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun restoreTx(id: Long) {
        viewModelScope.launch {
            val ok = restore(id)
            _state.update { it.copy(message = if (ok) "✅ Tiklandi" else "❌ Tiklash xatosi") }
            load()
        }
    }

    /** v177: Karzinani butunlay tozalash */
    fun clearAll() {
        viewModelScope.launch {
            val n = runCatching { deletedDao.clearAllDeleted(1L) }.getOrDefault(0)
            _state.update { it.copy(message = "\uD83D\uDDD1 Karzina tozalandi ($n ta)") }
            load()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KarzinaScreen(
    onBack: () -> Unit,
    vm: KarzinaViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }   // v177

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("\uD83D\uDDD1 Karzinani tozalash") },
            text = { Text("Karzinadagi ${state.items.size} ta yozuv BUTUNLAY o'chiriladi. Ular endi tiklanmaydi. Davom etilsinmi?") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); confirmClear = false }) {
                    Text("Ha, tozalansin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Bekor") } }
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🗑 Karzina") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    // v177: Karzinani tozalash
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Text("\uD83E\uDDF9", fontSize = 18.sp)
                        }
                    }
                    uz.daftar.app.ui.common.HomeButton()
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "💡 O'chirilgan yozuvlar bu yerda 7 kun saqlanadi",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tugmani bosib tiklashingiz mumkin. 7 kun o'tgach avtomatik tozalanadi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Karzina bo'sh", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    Text(
                        "${state.items.size} ta o'chirilgan yozuv",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.items, key = { it.id }) { item ->
                            DeletedRow(item, onRestore = { vm.restoreTx(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedRow(item: DeletedTransactionEntity, onRestore: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.clientName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${item.type.uppercase()}: ${item.amount.formatMoney()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Sana: ${item.date.take(10)}  •  O'chirilgan: ${item.deletedAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Outlined.Restore,
                    contentDescription = "Tiklash",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
