package uz.daftar.app.ui.screen.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.core.parser.ParsedEntry
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
    onEditTx: (Long) -> Unit = {},
    onSearch: () -> Unit = onSettings,
    onYukNarx: () -> Unit = onSettings,
    onYukReport: () -> Unit = onSettings,
    onNReport: () -> Unit = {},
    onAlias: () -> Unit = onSettings,
    onRasxod: () -> Unit = onSettings,
    onKarzina: () -> Unit = onSettings,
    onQarz: () -> Unit = onClients,
    onManager: () -> Unit = onSettings,
    onDashboard: () -> Unit = onSettings,
    onHelp: () -> Unit = onSettings,
    onEslat: () -> Unit = onSettings,
    onSklad: () -> Unit = onSettings,
    vm: TodayViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val calScope = rememberCoroutineScope()
    var showCalendar by remember { mutableStateOf(false) }
    var deleteChatId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Pastki menyu ochiq/yopiq (Telegram'day toggle)
    var bottomMenuOpen by remember { mutableStateOf(false) }
    // Yuk turi dialog (A/B/C/D/K bosilganda — bugun qancha, kimga)
    var yukTypeDialog by remember { mutableStateOf<String?>(null) }

    // Yangi xabar qo'shilsa, chat oxiriga scroll
    LaunchedEffect(state.chat.size) {
        if (state.chat.isNotEmpty()) {
            listState.animateScrollToItem(state.chat.size - 1)
        }
    }
    LaunchedEffect(state.justSentSummary) {
        state.justSentSummary?.let {
            snackbar.showSnackbar(it)
            vm.clearSentSummary()
        }
    }

    val jumpToDate: (java.time.LocalDate) -> Unit = { target ->
        val idx = state.chat.indexOfFirst {
            java.time.Instant.ofEpochMilli(it.ts)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate() == target
        }
        if (idx >= 0) calScope.launch { listState.animateScrollToItem(idx) }
        else calScope.launch { snackbar.showSnackbar("Bu kunda yozuv yo'q") }
    }

    state.confirmDeleteDate?.let { d ->
        AlertDialog(
            onDismissRequest = { vm.cancelDeleteAll() },
            title = { Text("⚠️ O'chirishni tasdiqlang") },
            text = {
                Text("${d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))} dagi BARCHA yozuvlar o'chiriladi. Bu amalни orqaga qaytarib bo'lmaydi. Davom etilsinmi?")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmDeleteAll() }) {
                    Text("Ha, o'chir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelDeleteAll() }) { Text("Yo'q") } }
        )
    }

    state.confirmDeleteClient?.let { name ->
        AlertDialog(
            onDismissRequest = { vm.cancelDeleteAll() },
            title = { Text("⚠️ O'chirishni tasdiqlang") },
            text = {
                Text("${name.replaceFirstChar { it.uppercase() }} — BUTUN tarixi (barcha sanalar) o'chiriladi. Bu amalни orqaga qaytarib bo'lmaydi. Davom etilsinmi?")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmDeleteClient() }) {
                    Text("Ha, o'chir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.cancelDeleteAll() }) { Text("Yo'q") } }
        )
    }

    if (showCalendar) {
        val dpState = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = dpState.selectedDateMillis
                    showCalendar = false
                    if (ms != null) {
                        val target = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        vm.showDateReportButton(target)
                    }
                }) { Text("Ko'rish") }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Bekor") } }
        ) {
            androidx.compose.material3.DatePicker(state = dpState)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
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
                    onOpenCalendar = { showCalendar = true },
                    onJumpToDate = jumpToDate,
                    onClients = onClients,
                    onQarz = onQarz,
                    onReports = onReports,
                    onSearch = onSearch,
                    onYukNarx = onYukNarx,
                    onYukReport = onYukReport,
                    onNReport = { vm.showDateReportButton(java.time.LocalDate.now(), useNarx = true) },
                    onAlias = onAlias,
                    onRasxod = onRasxod,
                    onKarzina = onKarzina,
                    onSettings = onSettings,
                    onManager = onManager,
                    onDashboard = onDashboard,
                    onHelp = onHelp,
                    onEslat = onEslat,
                    onSklad = onSklad,
                    onYukType = { yukTypeDialog = it }
                )
            }
        },
        bottomBar = {
            Column {
                // Pastki menyu paneli (toggle bilan ochiladi/yashirinadi)
                if (bottomMenuOpen) {
                    Surface(tonalElevation = 2.dp) {
                        Column {
                            // Hisobotlar — bosganda oynaga to'liq ochiladi (T narx)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AssistChip(
                                    onClick = {
                                        bottomMenuOpen = false
                                        keyboardController?.hide(); focusManager.clearFocus()
                                        vm.showDateReportButton(java.time.LocalDate.now())
                                    },
                                    label = { Text("📅 Bugun") }
                                )
                                AssistChip(
                                    onClick = {
                                        bottomMenuOpen = false
                                        keyboardController?.hide(); focusManager.clearFocus()
                                        vm.showDateReportButton(java.time.LocalDate.now().minusDays(1))
                                    },
                                    label = { Text("📅 Kecha") }
                                )
                                AssistChip(
                                    onClick = {
                                        bottomMenuOpen = false
                                        keyboardController?.hide(); focusManager.clearFocus()
                                        vm.showWeekReport()
                                    },
                                    label = { Text("📆 Hafta") }
                                )
                                AssistChip(
                                    onClick = { bottomMenuOpen = false; onYukReport() },
                                    label = { Text("📦 Yuk") }
                                )
                            }
                            // Tezkor shablonlar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { vm.saveCurrentAsTemplate() },
                                    label = { Text("⭐ Saqlash") }
                                )
                                state.templates.forEach { t ->
                                    AssistChip(
                                        onClick = { vm.applyTemplate(t) },
                                        label = { Text(t) }
                                    )
                                }
                            }
                        }
                    }
                }
                // ───── Mijoz tarixi asosiy oynaga ko'chirildi ─────
                InputBar(
                    input = state.input,
                    onChange = vm::onInputChange,
                    suggestions = state.suggestions,
                    onSuggestionClick = vm::applySuggestion,
                    onSend = {
                        bottomMenuOpen = false
                        keyboardController?.hide(); focusManager.clearFocus()
                        vm.send()
                    },
                    canSend = state.canSend,
                    isSending = state.isSending,
                    errorMessage = state.errorMessage,
                    parsed = state.parsed,
                    menuOpen = bottomMenuOpen,
                    onToggleMenu = { bottomMenuOpen = !bottomMenuOpen }
                )
            }
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
            if (state.chat.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Yozuv yozing:\n\"Ali a10 n a20 p150\"\n\nHisobot: \"bugun\", \"kecha\"\nTarix: \"ali\"\nQarz eslatma: \"eslatma\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.chat, key = { _, it -> it.id }) { index, item ->
                        val prevTs = if (index == 0) null else state.chat[index - 1].ts
                        val showHeader = prevTs == null || !isSameDay(prevTs, item.ts)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (showHeader) ChatDateSeparator(item.ts)
                            Box(
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { if (deleteChatId == item.id) deleteChatId = null },
                                    onLongClick = { deleteChatId = item.id }
                                )
                            ) {
                                when (item) {
                                    is ChatItem.User -> ChatUserBubble(item.text)
                                    is ChatItem.Info -> ChatBotBubble(item.text)
                                    is ChatItem.DateRep -> DateReportCard(
                                        report = item.report,
                                        onClose = { vm.removeChat(item.id) }
                                    )
                                    is ChatItem.TextRep -> TextReportCard(
                                        report = item.report,
                                        onClose = { vm.removeChat(item.id) }
                                    )
                                    is ChatItem.History -> PreviewHistoryCard(
                                        name = item.preview.name,
                                        debt = item.preview.debt,
                                        allTxs = item.preview.transactions,
                                        priceByTx = item.preview.priceByTx,
                                        balanceAfter = item.preview.balanceAfter,
                                        month = item.preview.month,
                                        onPrev = { vm.shiftHistoryMonth(item.id, -1) },
                                        onNext = { vm.shiftHistoryMonth(item.id, 1) }
                                    )
                                }
                                if (deleteChatId == item.id) {
                                    Surface(
                                        onClick = { vm.removeChat(item.id); deleteChatId = null },
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "O'chirish", tint = androidx.compose.ui.graphics.Color.White)
                                            Spacer(Modifier.width(4.dp))
                                            Text("O'chirish", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Yuk turi dialog — A/B/C/D/K bosilganda: bugun qancha, kimga
    yukTypeDialog?.let { letter ->
        val matching = state.transactions.filter { it.type.code.equals(letter, ignoreCase = true) }
        val total = matching.sumOf { it.amount }
        val byClient = matching.groupBy { it.clientName }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
        AlertDialog(
            onDismissRequest = { yukTypeDialog = null },
            title = { Text("📦 $letter — bugun") },
            text = {
                Column {
                    Text(
                        "Jami: ${total.formatMoney()}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    if (byClient.isEmpty()) {
                        Text("Bugun $letter yo'q")
                    } else {
                        byClient.entries.sortedByDescending { it.value }.forEach { (name, amt) ->
                            val cap = name.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                            }
                            Text(
                                "• $cap: ${amt.formatMoney()}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { yukTypeDialog = null }) { Text("Yopish") }
            }
        )
    }
}

// ───────────────────── TOP BAR ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    filter: Filter,
    onFilterChange: (Filter) -> Unit,
    onClients: () -> Unit,
    onQarz: () -> Unit,
    onReports: () -> Unit,
    onSearch: () -> Unit,
    onYukNarx: () -> Unit,
    onYukReport: () -> Unit,
    onNReport: () -> Unit,
    onAlias: () -> Unit,
    onRasxod: () -> Unit,
    onKarzina: () -> Unit,
    onSettings: () -> Unit,
    onManager: () -> Unit,
    onDashboard: () -> Unit,
    onHelp: () -> Unit,
    onEslat: () -> Unit,
    onSklad: () -> Unit,
    onOpenCalendar: () -> Unit = {},
    onJumpToDate: (java.time.LocalDate) -> Unit = {},
    onYukType: (String) -> Unit
) {
    var filterOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var yuklarOpen by remember { mutableStateOf(false) }
    var hisobotOpen by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = { Text("Daftar", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            // Asosiy menu — chapda hamburger (☰)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menyu")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.heightIn(max = 460.dp)
                ) {
                    // ── YUKLAR (bosganda A B C D K) ──
                    DropdownMenuItem(
                        text = { Text("📦 Yuklar") },
                        leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                        trailingIcon = { Icon(if (yuklarOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                        onClick = { yuklarOpen = !yuklarOpen }
                    )
                    if (yuklarOpen) {
                        listOf("A", "B", "C", "D", "K").forEach { t ->
                            DropdownMenuItem(
                                text = { Text("     $t — bugun") },
                                onClick = { menuOpen = false; yuklarOpen = false; onYukType(t) }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text("📦 Yuk hisoboti") },
                        leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                        onClick = { menuOpen = false; onYukReport() }
                    )
                    DropdownMenuItem(
                        text = { Text("🔢 Hisobot (N narx)") },
                        leadingIcon = { Icon(Icons.Outlined.Receipt, null) },
                        onClick = { menuOpen = false; onNReport() }
                    )
                    DropdownMenuItem(
                        text = { Text("👥 Mijozlar") },
                        leadingIcon = { Icon(Icons.Outlined.People, null) },
                        onClick = { menuOpen = false; onClients() }
                    )
                    DropdownMenuItem(
                        text = { Text("💳 Qarzdorlar") },
                        onClick = { menuOpen = false; onQarz() }
                    )
                    DropdownMenuItem(
                        text = { Text("🔍 Qidirish (Al)") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        onClick = { menuOpen = false; onSearch() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("📊 Hisobotlar") },
                        leadingIcon = { Icon(Icons.Outlined.Assessment, null) },
                        trailingIcon = { Icon(if (hisobotOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) },
                        onClick = { hisobotOpen = !hisobotOpen }
                    )
                    if (hisobotOpen) {
                        DropdownMenuItem(text = { Text("     💰 T narx hisobot") }, onClick = { menuOpen = false; onReports() })
                        DropdownMenuItem(text = { Text("     🏷 N narx hisobot") }, onClick = { menuOpen = false; onReports() })
                        DropdownMenuItem(text = { Text("     📅 Oylik / Yillik") }, onClick = { menuOpen = false; onReports() })
                    }
                    DropdownMenuItem(
                        text = { Text("🚛 Narx (T)") },
                        leadingIcon = { Icon(Icons.Outlined.LocalShipping, null) },
                        onClick = { menuOpen = false; onYukNarx() }
                    )
                    DropdownMenuItem(
                        text = { Text("📈 Sof foyda") },
                        leadingIcon = { Icon(Icons.Outlined.TrendingUp, null) },
                        onClick = { menuOpen = false; onReports() }
                    )
                    DropdownMenuItem(
                        text = { Text("💸 Rasxod") },
                        leadingIcon = { Icon(Icons.Outlined.MoneyOff, null) },
                        onClick = { menuOpen = false; onRasxod() }
                    )
                    DropdownMenuItem(
                        text = { Text("📦 Sklad (ombor)") },
                        leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                        onClick = { menuOpen = false; onSklad() }
                    )
                    DropdownMenuItem(
                        text = { Text("⏰ Eslat (bildirishnoma)") },
                        leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                        onClick = { menuOpen = false; onEslat() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("🔁 Alias / Nomini o'zgartirish") },
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
                        text = { Text("📊 Dashboard") },
                        leadingIcon = { Icon(Icons.Outlined.BarChart, null) },
                        onClick = { menuOpen = false; onDashboard() }
                    )
                    DropdownMenuItem(
                        text = { Text("❓ Yordam") },
                        leadingIcon = { Icon(Icons.Outlined.HelpOutline, null) },
                        onClick = { menuOpen = false; onHelp() }
                    )
                    DropdownMenuItem(
                        text = { Text("🗂 Zaxira / Backup") },
                        leadingIcon = { Icon(Icons.Outlined.Backup, null) },
                        onClick = { menuOpen = false; onManager() }
                    )
                    HorizontalDivider()
                    // ── SOZLAMA (eng pastda) ──
                    DropdownMenuItem(
                        text = { Text("⚙️ Sozlamalar") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                        onClick = { menuOpen = false; onSettings() }
                    )
                }
            }
        },
        actions = {
            // Bugun + Kalendar (Kecha olib tashlandi)
            TextButton(onClick = { onJumpToDate(java.time.LocalDate.now()) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Bugun")
            }
            IconButton(onClick = onOpenCalendar) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Kalendar")
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
                    "${t.label}:${v.formatQty()}",
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
private fun ClientDayCard(
    date: LocalDate,
    clientName: String,
    txs: List<Transaction>,
    clientPrices: Map<TxType, Double>?,
    clientDebt: Long?,
    selected: Set<Long>,
    inSelectionMode: Boolean,
    onTxClick: (Long) -> Unit,
    onTxLongClick: (Long) -> Unit
) {
    val capitalized = clientName.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
    val dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM"))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(start = 32.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    "✅ Saqlandi",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "📅 $dateStr",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    capitalized,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                for (tx in txs) {
                    val isSelected = tx.id in selected
                    val unitPrice = tx.tOverride ?: clientPrices?.get(tx.type)
                    val noPrice = unitPrice == null &&
                        tx.type in setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
                    val lineText = when {
                        tx.type == TxType.P -> "  P: ${tx.amount.formatMoney()}"
                        unitPrice != null -> "  ${tx.type.label}: ${tx.amount.formatQty()} × ${unitPrice.formatQty()} = ${(tx.amount * unitPrice).formatMoney()}"
                        else -> "  ${tx.type.label}: ${tx.amount.formatMoney()}"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .combinedClickable(
                                onClick = { onTxClick(tx.id) },
                                onLongClick = { onTxLongClick(tx.id) }
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        if (noPrice) {
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    append("  ${tx.type.label}: ${tx.amount.formatQty()}  ")
                                    withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) { append("(narx yo'q)") }
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                lineText,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (clientDebt != null) {
                    Text(
                        when {
                            clientDebt > 0 -> "💳 Qarz: ${clientDebt.toDouble().formatMoney()} so'm"
                            clientDebt == 0L -> "✅ Qarz yo'q"
                            else -> "💚 Ortiq: ${(-clientDebt).toDouble().formatMoney()} so'm"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            clientDebt > 0 -> MaterialTheme.colorScheme.error
                            clientDebt == 0L -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
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
    parsed: List<ParsedEntry>,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit
) {
    // Kursorni boshqarish uchun TextFieldValue (tugma bosilganda kursor harfdan KEYIN turishi uchun)
    var tfv by remember { mutableStateOf(TextFieldValue(input)) }
    // Raqamli klaviatura rejimi — yuk tugmasi bosilganda yoqiladi (faqat son yozish uchun)
    var numericMode by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    LaunchedEffect(input) {
        if (input != tfv.text) {
            tfv = TextFieldValue(input, selection = TextRange(input.length))
        }
        if (input.isEmpty()) numericMode = false  // yangi yozuv → ism uchun harf klaviaturasi
    }
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tez kiritish tugmalari — faqat yozuvga kirilganda (fokusda) ko'rinadi
            if (inputFocused) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("a", "b", "c", "n", "p", "t1").forEach { code ->
                    AssistChip(
                        onClick = {
                            val cur = tfv.text
                            val pos = tfv.selection.end.coerceIn(0, cur.length)
                            // Oldingi belgi probel bo'lmasa — harf oldidan probel qo'shamiz
                            val needSpace = pos > 0 && cur[pos - 1] != ' '
                            val ins = (if (needSpace) " " else "") + code
                            val newText = cur.substring(0, pos) + ins + cur.substring(pos)
                            val newPos = pos + ins.length  // kursor harfdan KEYIN
                            tfv = TextFieldValue(newText, selection = TextRange(newPos))
                            onChange(newText)
                            // n/t1 (narx markerlari)dan keyin yuk turi harfi kerak — raqamga o'tmaymiz
                            if (code !in listOf("n", "t1")) numericMode = true
                        },
                        label = { Text(code.uppercase()) }
                    )
                }
                // Abc/123 — qo'lда harf klaviaturasiga qaytish (ism yozish uchun)
                AssistChip(
                    onClick = { numericMode = !numericMode },
                    label = { Text(if (numericMode) "Abc" else "123") }
                )
            }
            }

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
            } else if (parsed.isNotEmpty()) {
                // Jonli preview — bot'day "tushunildi" kartasi
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "📝 Tushunildi (${parsed.size} ta):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        parsed.take(6).forEach { e ->
                            val name = e.clientName.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                            }
                            val itemsStr = e.items.entries.joinToString("  ") { (t, a) ->
                                val n = e.clientPrices[t] ?: e.tPrices[t] ?: e.tOneTime[t]
                                if (n != null) "${t.label}:${a.formatQty()}[${n.formatQty()}]"
                                else "${t.label}:${a.formatQty()}"
                            }
                            Text(
                                "• $name  $itemsStr",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (parsed.size > 6) {
                            Text(
                                "… yana ${parsed.size - 6} ta",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Pastki menyu toggle (Telegram'day — bosganda ochiladi/yashirinadi)
                IconButton(
                    onClick = onToggleMenu,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text(
                        if (menuOpen) "✕" else "☰",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = tfv,
                    onValueChange = { newV ->
                        tfv = newV
                        onChange(newV.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { inputFocused = it.isFocused },
                    placeholder = { Text("Yozuv...") },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = if (numericMode) KeyboardType.Decimal else KeyboardType.Text
                    ),
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

// ───────────────────── Jonli tarix preview (input ostida) ─────────────────────

private val MONTHS_UZ_TODAY = listOf(
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
)

@Composable
private fun PreviewHistoryCard(
    name: String,
    debt: Long,
    allTxs: List<uz.daftar.app.data.db.entity.TransactionEntity>,
    priceByTx: Map<Long, Double?>,
    balanceAfter: Map<Long, Long>,
    month: java.time.YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val monthPrefix = "%04d-%02d".format(month.year, month.monthValue)
    val monthTxs = allTxs.filter { it.date.startsWith(monthPrefix) }
    val monthLabel = "${MONTHS_UZ_TODAY[month.monthValue - 1]} ${month.year}"

    // Oylik JAMI
    val byType = monthTxs.groupBy { it.type }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val payTotal = monthTxs.filter { it.type.lowercase() == "p" }.sumOf { it.amount }
    var revenue = 0.0
    for (tx in monthTxs) {
        val t = uz.daftar.app.domain.model.TxType.fromCode(tx.type) ?: continue
        if (t in setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
            val p = priceByTx[tx.id]
            if (p != null) revenue += tx.amount * p
        }
    }
    // Oylik qarz (oxirgi tx oxirigacha)
    val monthDebt: Long = run {
        var r = 0.0
        for (tx in monthTxs.sortedBy { it.date }) {
            when (tx.type.lowercase()) {
                "p" -> r -= tx.amount
                "q" -> r += tx.amount
                else -> { val p = priceByTx[tx.id]; if (p != null) r += tx.amount * p }
            }
        }
        r.toLong()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Sarlavha
            Text(
                "👤 ${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }} — $monthLabel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "💳 ${MONTHS_UZ_TODAY[month.monthValue - 1]} qarzi: ${monthDebt.toDouble().formatMoney()} so'm",
                style = MaterialTheme.typography.bodySmall,
                color = if (monthDebt > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            if (monthTxs.isEmpty()) {
                Text(
                    "Bu oyda yozuv yo'q",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Maksimal balandlik — uzun bo'lsa scroll
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState)
                ) {
                    val byDay = monthTxs.groupBy { it.date.take(10) }
                        .toSortedMap(compareBy { it })
                    for ((day, dayTxsRaw) in byDay) {
                        val dayTxs = dayTxsRaw.sortedBy { it.date }
                        Text(
                            "📅 ${day.substring(8, 10)}.${day.substring(5, 7)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                        for (tx in dayTxs) {
                            val type = uz.daftar.app.domain.model.TxType.fromCode(tx.type)
                            val isPayment = type == uz.daftar.app.domain.model.TxType.P
                            val up = priceByTx[tx.id]
                            val main = when {
                                isPayment -> "P(pul): ${tx.amount.formatMoney()}"
                                up != null -> "${tx.type.uppercase()}: ${tx.amount.formatQty()} × ${up.formatQty()} = ${(tx.amount * up).formatMoney()} so'm"
                                else -> "${tx.type.uppercase()}: ${tx.amount.formatQty()}"
                            }
                            val ba = balanceAfter[tx.id]
                            val bal = if (isPayment && ba != null) {
                                when {
                                    ba > 0 -> " → 💳 Qoldi: ${ba.formatMoney()}"
                                    ba == 0L -> " → ✅ 0"
                                    else -> " → 💚 Ortiq: ${(-ba).formatMoney()}"
                                }
                            } else ""
                            val time = if (tx.date.length >= 16) tx.date.substring(11, 16) else ""
                            Text(
                                "  $main  $time$bal",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (isPayment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // JAMI
                Text("📊 JAMI:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                for (t in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                    val amt = byType[t.code] ?: 0.0
                    if (amt > 0) Text(
                        "  ${t.label}: ${amt.formatQty()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (payTotal > 0) Text(
                    "  P(pul): ${payTotal.formatMoney()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "  J: ${revenue.formatMoney()} so'm",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (debt > 0) "💳 Qarz: ${debt.toDouble().formatMoney()} so'm"
                    else if (debt == 0L) "✅ Qarz yo'q"
                    else "💚 Ortiq: ${(-debt).toDouble().formatMoney()} so'm",
                    fontWeight = FontWeight.Bold,
                    color = when {
                        debt > 0 -> MaterialTheme.colorScheme.error
                        debt == 0L -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Oylik tugma
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onPrev, modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text("⬅️ Oldingi")
                }
                FilledTonalButton(onClick = onNext, modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Keyingi ➡️")
                }
            }
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val z = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(a).atZone(z).toLocalDate() ==
           java.time.Instant.ofEpochMilli(b).atZone(z).toLocalDate()
}

@Composable
private fun ChatDateSeparator(ts: Long) {
    val z = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(ts).atZone(z).toLocalDate()
    val today = java.time.LocalDate.now()
    val label = when (d) {
        today -> "Bugun"
        today.minusDays(1) -> "Kecha"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ChatUserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ChatBotBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = androidx.compose.ui.graphics.Color.White,
            shadowElevation = 1.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            val marker = "(narx yo'q)"
            val errColor = MaterialTheme.colorScheme.error
            val timeColor = MaterialTheme.colorScheme.primary
            val timeRe = Regex("🕒 \\d{1,2}:\\d{2}")
            val annotated = androidx.compose.ui.text.buildAnnotatedString {
                var i = 0
                while (i < text.length) {
                    val mIdx = text.indexOf(marker, i)
                    val tm = timeRe.find(text, i)
                    val tIdx = tm?.range?.first ?: -1
                    when {
                        mIdx >= 0 && (tIdx < 0 || mIdx < tIdx) -> {
                            append(text.substring(i, mIdx))
                            withStyle(androidx.compose.ui.text.SpanStyle(color = errColor, fontWeight = FontWeight.Bold)) { append(marker) }
                            i = mIdx + marker.length
                        }
                        tIdx >= 0 -> {
                            val tv = tm!!.value
                            append(text.substring(i, tIdx))
                            withStyle(androidx.compose.ui.text.SpanStyle(color = timeColor, fontWeight = FontWeight.Bold)) { append(tv) }
                            i = tIdx + tv.length
                        }
                        else -> { append(text.substring(i)); i = text.length }
                    }
                }
            }
            Text(
                annotated,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TextReportCard(
    report: uz.daftar.app.ui.screen.today.TextReport,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        color = androidx.compose.ui.graphics.Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Yopish")
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                Text(
                    report.body,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DateReportCard(
    report: uz.daftar.app.domain.usecase.DateReport,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateStr = report.title.ifEmpty { report.date.format(DateTimeFormatter.ofPattern("dd.MM")) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            // Sarlavha + yopish
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📅 $dateStr",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Yopish")
                }
            }
            Spacer(Modifier.height(8.dp))

            if (report.clientLines.isEmpty()) {
                Text(
                    "Bu sanada yozuv yo'q",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // Raqamlangan mijoz qatorlari (uzun bo'lsa scroll)
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(scroll)
            ) {
                for ((idx, line) in report.clientLines.withIndex()) {
                    val capitalized = line.clientName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                    val entriesStr = buildString {
                        for ((j, e) in line.entries.withIndex()) {
                            if (j > 0) append("  ")
                            when (e.type) {
                                TxType.P -> append("P:${e.amount.formatMoney()}")
                                TxType.Q -> append("Q:${e.amount.formatMoney()}")
                                else -> {
                                    append("${e.type.code.uppercase()}:${e.amount.formatQty()}")
                                    e.price?.let {
                                        if (report.useNarx) append("(n:${it.formatQty()})")
                                        else append("   [${it.formatQty()}]")
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "${idx + 1}. $capitalized  $entriesStr",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // JAMI bloki
            Text(
                "JAMI:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            for (type in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                val total = report.totalsByType[type] ?: 0.0
                if (total <= 0.0) continue
                val rev = report.revenueByType[type] ?: 0.0
                Text(
                    "${type.code.uppercase()} ${total.formatQty()}  = ${rev.formatMoney()} so'm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (report.useNarx) {
                // N rejimi: 🅿️ keyin 🔢 JAMI pul
                if (report.totalPayments > 0) {
                    Text(
                        "🅿️ ${report.totalPayments.formatMoney()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "🔢 JAMI pul: ${report.totalRevenue.formatMoney()} so'm",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                // T rejimi: J keyin 🅿️
                Text(
                    "J: ${report.totalRevenue.formatMoney()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (report.totalPayments > 0) {
                    Text(
                        "🅿️ ${report.totalPayments.formatMoney()} so'm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
