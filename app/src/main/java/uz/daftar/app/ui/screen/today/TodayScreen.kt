package uz.daftar.app.ui.screen.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.model.Transaction
import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNewTx: () -> Unit = {},   // qoldirildi (boshqa joydan kelishi mumkin)
    onClients: () -> Unit,
    onReports: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit = onSettings,
    onYukNarx: () -> Unit = onSettings,
    onAlias: () -> Unit = onSettings,
    onRasxod: () -> Unit = onSettings,
    onKarzina: () -> Unit = onSettings,
    vm: TodayViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    // Yangi yozuv qo'shilsa, oxiriga scroll
    LaunchedEffect(state.transactions.size) {
        if (state.transactions.isNotEmpty()) {
            listState.animateScrollToItem(state.transactions.size - 1)
        }
    }
    LaunchedEffect(state.justSentSummary) {
        state.justSentSummary?.let {
            snackbar.showSnackbar(it)
            vm.clearSentSummary()
        }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    count = state.selected.size,
                    onCancel = vm::clearSelection,
                    onDelete = vm::deleteSelected
                )
            } else {
                ChatTopBar(
                    filter = state.filter,
                    onFilterChange = vm::setFilter,
                    onClients = onClients,
                    onReports = onReports,
                    onSearch = onSearch,
                    onYukNarx = onYukNarx,
                    onAlias = onAlias,
                    onRasxod = onRasxod,
                    onKarzina = onKarzina,
                    onSettings = onSettings
                )
            }
        },
        bottomBar = {
            InputBar(
                input = state.input,
                onChange = vm::onInputChange,
                suggestions = state.suggestions,
                onSuggestionClick = vm::applySuggestion,
                onSend = vm::send,
                canSend = state.canSend,
                isSending = state.isSending,
                errorMessage = state.errorMessage,
                parsedCount = state.parsed.size
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(snackbarData = it) } }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Yuqori summary
            if (state.transactions.isNotEmpty()) {
                SummaryBar(state.totalByType, state.clientCount, state.filter)
            }
            HorizontalDivider()

            if (state.transactions.isEmpty()) {
                EmptyView(state.filter)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Sana bo'yicha guruhlash
                    val grouped = state.transactions.groupBy { it.date.toLocalDate() }
                        .toSortedMap()
                    for ((date, txs) in grouped) {
                        item("date-$date") { DateSeparator(date) }
                        items(txs, key = { it.id }) { tx ->
                            ChatBubble(
                                tx = tx,
                                isSelected = tx.id in state.selected,
                                inSelectionMode = state.isSelectionMode,
                                onClick = { if (state.isSelectionMode) vm.toggleSelect(tx.id) },
                                onLongClick = { vm.toggleSelect(tx.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────── TOP BAR ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    filter: Filter,
    onFilterChange: (Filter) -> Unit,
    onClients: () -> Unit,
    onReports: () -> Unit,
    onSearch: () -> Unit,
    onYukNarx: () -> Unit,
    onAlias: () -> Unit,
    onRasxod: () -> Unit,
    onKarzina: () -> Unit,
    onSettings: () -> Unit
) {
    var filterOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = { Text("Daftar", fontWeight = FontWeight.SemiBold) },
        actions = {
            // Filter dropdown (Bugun/Kecha/Yuk/Hammasi)
            Box {
                AssistChip(
                    onClick = { filterOpen = true },
                    label = { Text(filter.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                    Filter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label) },
                            onClick = { onFilterChange(f); filterOpen = false }
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            // Asosiy menu — 3 nuqta
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Menyu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("👥 Mijozlar") },
                        leadingIcon = { Icon(Icons.Outlined.People, null) },
                        onClick = { menuOpen = false; onClients() }
                    )
                    DropdownMenuItem(
                        text = { Text("📊 Hisobotlar") },
                        leadingIcon = { Icon(Icons.Outlined.Assessment, null) },
                        onClick = { menuOpen = false; onReports() }
                    )
                    DropdownMenuItem(
                        text = { Text("🔍 Qidirish") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        onClick = { menuOpen = false; onSearch() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("🚛 Yuk narxi (T)") },
                        leadingIcon = { Icon(Icons.Outlined.LocalShipping, null) },
                        onClick = { menuOpen = false; onYukNarx() }
                    )
                    DropdownMenuItem(
                        text = { Text("💸 Rasxod") },
                        leadingIcon = { Icon(Icons.Outlined.MoneyOff, null) },
                        onClick = { menuOpen = false; onRasxod() }
                    )
                    DropdownMenuItem(
                        text = { Text("🔁 Alias / Rename") },
                        leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) },
                        onClick = { menuOpen = false; onAlias() }
                    )
                    DropdownMenuItem(
                        text = { Text("🗑 Karzina") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; onKarzina() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("⚙️ Sozlamalar") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                        onClick = { menuOpen = false; onSettings() }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("$count tanlangan") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = "Bekor")
            }
        },
        actions = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "O'chirish",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

// ───────────────────── SUMMARY ─────────────────────

@Composable
private fun SummaryBar(totals: Map<TxType, Double>, clientCount: Int, filter: Filter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "${filter.label}: $clientCount mijoz",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        // Har yuk turi uchun jami
        listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K, TxType.P).forEach { t ->
            val v = totals[t]
            if (v != null && v > 0) {
                Text(
                    "${t.label}:${v.formatMoney()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (t == TxType.P) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyView(filter: Filter) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📭", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "${filter.label.lowercase()} yozuv yo'q",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pastdan birinchi yozuvni kiriting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ───────────────────── CHAT BUBBLE ─────────────────────

@Composable
private fun DateSeparator(date: LocalDate) {
    val fmt = remember { DateTimeFormatter.ofPattern("dd MMMM, EEEE", Locale("uz")) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                date.format(fmt),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    tx: Transaction,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        tx.type == TxType.P -> MaterialTheme.colorScheme.tertiaryContainer
        tx.type == TxType.Q -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val timeStr = tx.date.format(DateTimeFormatter.ofPattern("HH:mm"))
    val capitalized = tx.clientName.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = bg,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(start = 48.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        capitalized,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (inSelectionMode && isSelected) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${tx.type.label}: ${tx.amount.formatMoney()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

// ───────────────────── INPUT BAR ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    input: String,
    onChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    parsedCount: Int
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Autocomplete chips
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    suggestions.forEach { name ->
                        AssistChip(
                            onClick = { onSuggestionClick(name) },
                            label = {
                                Text(
                                    name.replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                                        else it.toString()
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // Tahlil / xato indikator
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else if (parsedCount > 0) {
                Text(
                    "✓ $parsedCount ta yozuv tushunildi",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("ali a10 n a20") },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = "Yuborish",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
