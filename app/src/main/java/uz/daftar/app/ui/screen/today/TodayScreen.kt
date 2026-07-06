package uz.daftar.app.ui.screen.today

import uz.daftar.app.core.util.yukRangi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.core.voice.rememberVoiceInput
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
    onToliq: () -> Unit = onSettings,
    onDaily: () -> Unit = onSettings,
    onBashorat: () -> Unit = onSettings,
    onGrafik: () -> Unit = onSettings,
    onCalc: () -> Unit = onSettings,
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
    onTahrir: () -> Unit = onSettings,
    onQoshimcha: () -> Unit = onSettings,
    vm: TodayViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Widjetdan qaytganda — yangi saqlangan yozuvlarni chatda ko'rsatish
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val listState = rememberLazyListState()
    // 📱 2-telefon (ko'ruvchi) rejimi tekshiruvi uchun kontekst
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    val inputFr = remember { FocusRequester() }
    val calScope = rememberCoroutineScope()
    val csvContext = LocalContext.current
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            calScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val tmp = java.io.File(csvContext.cacheDir, "import_tmp.bin")
                        csvContext.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { out -> input.copyTo(out) }
                        }
                        val header = ByteArray(16)
                        tmp.inputStream().use { it.read(header) }
                        if (String(header).startsWith("SQLite format 3")) {
                            tmp.absolutePath to true   // .db
                        } else {
                            tmp.readText() to false     // .csv
                        }
                    }.getOrNull()
                }?.let { (data, isDb) ->
                    if (isDb) vm.importDb(data) else vm.importCsv(data)
                }
            }
        }
    }
    var showCalendar by remember { mutableStateOf(false) }
    var deleteChatId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Pastki menyu ochiq/yopiq (Telegram'day toggle)
    var bottomMenuOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showDatePick by remember { mutableStateOf(false) }
    var showInput by remember { mutableStateOf(false) }
    // Yuk turi dialog (A/B/C/D/K bosilganda — bugun qancha, kimga)
    var yukTypeDialog by remember { mutableStateOf<String?>(null) }
    var crashText by remember { mutableStateOf<String?>(null) }

    // Yangi xabar qo'shilsa, chat oxiriga scroll (birinchi marta — DARHOL, sakramasdan)
    var firstScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.chat.size) {
        runCatching {
            if (state.chat.isNotEmpty()) {
                if (!firstScrollDone) {
                    listState.scrollToItem(state.chat.size - 1)
                    firstScrollDone = true
                } else {
                    listState.animateScrollToItem(state.chat.size - 1)
                }
            }
        }
    }
    LaunchedEffect(state.justSentSummary) {
        state.justSentSummary?.let {
            snackbar.showSnackbar(it)
            vm.clearSentSummary()
        }
    }

    val jumpToDate: (java.time.LocalDate) -> Unit = { target ->
        runCatching {
            val idx = state.chat.indexOfFirst {
                java.time.Instant.ofEpochMilli(it.ts)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate() == target
            }
            if (idx >= 0) calScope.launch { runCatching { listState.animateScrollToItem(idx.coerceAtLeast(0)) } }
            else calScope.launch { runCatching { snackbar.showSnackbar("Bu kunda yozuv yo'q") } }
        }
    }

    // Ilova ochilganda — oldingi crash bo'lgan bo'lsa, sababini chatda ko'rsatamiz
    LaunchedEffect(Unit) {
        runCatching {
            val f = java.io.File(csvContext.filesDir, "last_crash.txt")
            if (f.exists()) {
                val txt = f.readText()
                f.delete()
                if (txt.isNotBlank()) crashText = txt
            }
        }
    }

    state.voiceConfirm?.let { vtxt ->
        AlertDialog(
            onDismissRequest = { vm.voiceConfirmNo() },
            title = { Text("\ud83c\udfa4 Saqlansinmi?") },
            text = {
                Column {
                    Text("\"$vtxt\"", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    state.parsed.take(5).forEach { e ->
                        val items = e.items.entries.joinToString("  ") { (t, a) -> "${t.label}:${a.formatQty()}" }
                        Text("\u2022 ${e.clientName}  $items", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.voiceConfirmYes() }) { Text("\u2705 HA \u2014 saqlash") }
            },
            dismissButton = {
                TextButton(onClick = { vm.voiceConfirmNo() }) { Text("\u274c YO'Q") }
            }
        )
    }

    crashText?.let { ct ->
        AlertDialog(
            onDismissRequest = { crashText = null },
            title = { Text("⚠️ Oxirgi xato (skrinshot qiling)") },
            text = { Text(ct.take(1600), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { crashText = null }) { Text("Yopish") } }
        )
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

    state.confirmCloseDebt?.let { (name, debt) ->
        AlertDialog(
            onDismissRequest = { vm.cancelCloseDebt() },
            title = { Text("💰 Qarzni yopish") },
            text = {
                Text("${name.replaceFirstChar { it.uppercase() }} — ${debt} so'm qarz to'liq yopiladi (bugungi sana bilan to'lov yoziladi). Davom etilsinmi?")
            },
            confirmButton = { TextButton(onClick = { vm.confirmCloseDebt() }) { Text("Ha, yop") } },
            dismissButton = { TextButton(onClick = { vm.cancelCloseDebt() }) { Text("Yo'q") } }
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
        // imePadding() olib tashlandi — adjustResize klaviaturani o'zi boshqaradi (oq ekran sababi edi).
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
                    onRefresh = { vm.refresh() },
                    onOpenCalendar = { showCalendar = true },
                    onJumpToDate = jumpToDate,
                    onImport = { csvLauncher.launch("*/*") },
                    onClients = onClients,
                    onQarz = onQarz,
                    onReports = onReports,
                    onSearch = onSearch,
                    onYukNarx = onYukNarx,
                    onYukReport = onYukReport,
                    onToliq = onToliq,
                    onDaily = onDaily,
                    onBashorat = onBashorat,
                    onGrafik = onGrafik,
                    onCalc = onCalc,
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
                    onTahrir = onTahrir,
                    onQoshimcha = onQoshimcha,
                    onYukType = { yukTypeDialog = it },
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it }
                )
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                // ───── Doimiy pastki panel (5 tugma) ─────
                val panelVoice = rememberVoiceInput { spoken -> vm.onVoiceInput(spoken) }
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavBtn("🔮", "Bashorat") { onBashorat() }
                        BottomNavBtn("📦", "Yuk") { onYukReport() }
                        // ➕ Yozish tugmasi: bosilsa klaviatura, bosib turilsa ovoz
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(52.dp)
                                .combinedClickable(
                                    onClick = {
                                        bottomMenuOpen = false
                                        // 📱 Ko'ruvchi telefonda yozish YO'Q — asosiy telefon ma'lumoti buzilmasin
                                        val vwr = appCtx.getSharedPreferences("device_mode", android.content.Context.MODE_PRIVATE)
                                            .getBoolean("viewer", false)
                                        if (vwr) {
                                            android.widget.Toast.makeText(appCtx, "📱 2-telefon rejimi: faqat ko'rish. Yozish — asosiy telefonda", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            showInput = !showInput
                                            if (!showInput) { keyboardController?.hide(); focusManager.clearFocus() }
                                        }
                                    },
                                    onLongClick = {
                                        bottomMenuOpen = false
                                        panelVoice("uz-UZ")
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("➕", fontSize = 22.sp)
                            }
                        }
                        // 📊 Hisobot tugmasi: Bugun / Kecha
                        BottomNavBtn("📊", "Hisobot") { showDatePick = true }
                        BottomNavBtn("☰", "Menyu") { menuOpen = true }
                    }
                }
                // Bugun / Kecha tanlash oynasi (➕ yoki 📅 bosilganda)
                if (showDatePick) {
                    AlertDialog(
                        onDismissRequest = { showDatePick = false },
                        title = { Text("📊 Hisobot", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Qaysi kun hisoboti kerak?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        showDatePick = false
                                        keyboardController?.hide(); focusManager.clearFocus()
                                        vm.showDateReportButton(java.time.LocalDate.now())
                                    },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("📅 Bugun", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        showDatePick = false
                                        keyboardController?.hide(); focusManager.clearFocus()
                                        vm.showDateReportButton(java.time.LocalDate.now().minusDays(1))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("🗓 Kecha", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showDatePick = false }) { Text("Bekor") }
                        }
                    )
                }
                // (Bugun/Kecha/Hafta/Yuk paneli olib tashlandi — ➕ tugma va 📅 Sana orqali)
                // ───── Yozuv maydoni: faqat ➕ bosilganda ko'rinadi ─────
                if (showInput) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(80)
                        runCatching { inputFr.requestFocus() }
                        keyboardController?.show()
                    }
                    InputBar(
                        input = state.input,
                        onChange = vm::onInputChange,
                        suggestions = state.suggestions,
                        onSuggestionClick = vm::applySuggestion,
                        quickFills = state.quickFills,
                        onQuickFillClick = vm::applyQuickFill,
                        onVoice = vm::onVoiceInput,
                        onSend = {
                            bottomMenuOpen = false
                            showInput = false   // avval input yopiladi → klaviatura aniq yo'qoladi
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            vm.send()
                        },
                        canSend = state.canSend,
                        isSending = state.isSending,
                        errorMessage = state.errorMessage,
                        parsed = state.parsed,
                        menuOpen = bottomMenuOpen,
                        onToggleMenu = { bottomMenuOpen = !bottomMenuOpen },
                        inputFocusRequester = inputFr
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(snackbarData = it) } }
    ) { padding ->
        if (!state.restored) {
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
                                        onNext = { vm.shiftHistoryMonth(item.id, 1) },
                                        onCloseDebt = { vm.requestCloseDebt(item.preview.name, item.preview.debt) },
                                        onEditTx = onEditTx,
                                        onDeleteTx = { vm.deleteOne(it) }
                                    )
                                    is ChatItem.DebtRep -> DebtReminderCard(
                                        debtors = item.debtors,
                                        onClose = { vm.removeChat(item.id) }
                                    )
                                    is ChatItem.Saved -> SavedCard(item.info)
                                }
                                if (deleteChatId == item.id) {
                                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    val copyText = when (item) {
                                        is ChatItem.User -> item.text
                                        is ChatItem.Info -> item.text
                                        else -> null
                                    }
                                    Row(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (copyText != null) {
                                            Surface(
                                                onClick = {
                                                    clip.setText(androidx.compose.ui.text.AnnotatedString(copyText))
                                                    android.widget.Toast.makeText(ctx, "📋 Nusxa olindi", android.widget.Toast.LENGTH_SHORT).show()
                                                    deleteChatId = null
                                                },
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                color = MaterialTheme.colorScheme.secondary
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Nusxa", tint = androidx.compose.ui.graphics.Color.White)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Nusxa", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                        Surface(
                                            onClick = { vm.removeChat(item.id); deleteChatId = null },
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = MaterialTheme.colorScheme.error
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
    onRefresh: () -> Unit,
    onClients: () -> Unit,
    onQarz: () -> Unit,
    onReports: () -> Unit,
    onSearch: () -> Unit,
    onYukNarx: () -> Unit,
    onYukReport: () -> Unit,
    onToliq: () -> Unit,
    onDaily: () -> Unit,
    onBashorat: () -> Unit,
    onGrafik: () -> Unit,
    onCalc: () -> Unit,
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
    onTahrir: () -> Unit = {},
    onQoshimcha: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onJumpToDate: (java.time.LocalDate) -> Unit = {},
    onImport: () -> Unit = {},
    onYukType: (String) -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit
) {
    var filterOpen by remember { mutableStateOf(false) }
    var yuklarOpen by remember { mutableStateOf(false) }
    var hisobotOpen by remember { mutableStateOf(false) }
    val kbCtrl = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusMgr = androidx.compose.ui.platform.LocalFocusManager.current
    // Menyu yopilgach navigatsiyani bir lahza KEYIN bajaramiz (popup+nav poygasi => oq ekran).
    var pendingNav by remember { mutableStateOf<(() -> Unit)?>(null) }
    androidx.compose.runtime.LaunchedEffect(pendingNav) {
        val action = pendingNav ?: return@LaunchedEffect
        kotlinx.coroutines.delay(90)
        action()
        pendingNav = null
    }

    CenterAlignedTopAppBar(
        title = { Text("Daftar · v135", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            // Asosiy menu — chapda hamburger (☰)
            Box {
                IconButton(onClick = { runCatching { kbCtrl?.hide(); focusMgr.clearFocus() }; onMenuOpenChange(true) }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menyu")
                }
// ☰ menyu endi pastdan chiqadi (ModalBottomSheet — quyida)
            }
        },
        actions = {
            // ☁️ Drive sinxron nuqtachasi: YASHIL=ishlayapti, QIZIL=hali sinxron emas
            // (🔄 tugma olib tashlandi — hisobot har yozuvda o'zi avtomatik yangilanadi)
            run {
                val dctx = androidx.compose.ui.platform.LocalContext.current
                val lastSync = remember {
                    dctx.getSharedPreferences("drive_sync", android.content.Context.MODE_PRIVATE).getLong("last_sync", 0L)
                }
                val fresh = lastSync > 0L && (System.currentTimeMillis() - lastSync) < 2 * 60 * 60 * 1000L
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (fresh) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                )
                Spacer(Modifier.width(6.dp))
            }
            // 📅 Sana — istalgan kun yozuvlarini ko'rsatadi (Bugun o'rniga)
            TextButton(onClick = onOpenCalendar, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Sana")
            }
        }
    )

    // ── Ikonkali to'r menyu (pastdan chiqadi) ──
    if (menuOpen) {
        ModalBottomSheet(onDismissRequest = { onMenuOpenChange(false) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "Tez yuk (bugun)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A", "B", "C", "D", "K").forEach { t ->
                        AssistChip(
                            onClick = { onMenuOpenChange(false); pendingNav = { onYukType(t) } },
                            label = { Text(t) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                val tiles = listOf(
                    MenuItem("📋", "Qo'shimcha", "Qarz, statistika, qidiruv", Color(0xFFE3E0FF), onQoshimcha),
                    MenuItem("📦", "Yuk hisoboti", "Yuklar va holatlar", Color(0xFFD6F5E0), onYukReport),
                    MenuItem("✏️", "Tahrirlash", "Ma'lumotlarni tahrirlash", Color(0xFFEDE3FF), onTahrir),
                    MenuItem("📋", "To'liq", "To'liq ma'lumot", Color(0xFFFFE9D1), onToliq),
                    MenuItem("📅", "Kunlik", "Kun bo'yicha", Color(0xFFD9ECFF), onDaily),
                    MenuItem("👥", "Mijozlar", "Mijozlar ro'yxati", Color(0xFFD5F2EE), onClients),
                    MenuItem("🔮", "Bashorat", "Kim qachon oladi", Color(0xFFE3E0FF), onBashorat),
                    MenuItem("⏰", "Eslatma", "Eslatma + bildirishnoma", Color(0xFFFFE0E6), onEslat),
                    MenuItem("💳", "Qarzdorlar", "Qarzdorlar ro'yxati", Color(0xFFFFEEC2), onQarz),
                    MenuItem("🚚", "Narx", "Narxlar ro'yxati", Color(0xFFDCE8FF), onYukNarx),
                    MenuItem("💵", "Rasxod", "Rasxodlar hisobi", Color(0xFFD6F5E0), onRasxod),
                    MenuItem("🏬", "Sklad", "Ombor ma'lumoti", Color(0xFFEDE3FF), onSklad),
                    MenuItem("🧮", "Kalkulyator", "Hisob-kitob vositasi", Color(0xFFFFE9D1), onCalc),
                    MenuItem("❓", "Yordam", "Yordam va qo'llanma", Color(0xFFFFDCE6), onHelp),
                    MenuItem("⚙️", "Sozlama", "Sozlamalar", Color(0xFFE6E8EB), onSettings),
                )
                tiles.chunked(3).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowTiles.forEach { item ->
                            MenuTile(item.emoji, item.title, item.subtitle, item.bg, Modifier.weight(1f)) {
                                onMenuOpenChange(false); pendingNav = item.action
                            }
                        }
                        repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(2.dp))
                MenuSettingsRow { onMenuOpenChange(false); pendingNav = onSettings }
            }
        }
    }
}

private data class MenuItem(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val bg: Color,
    val action: () -> Unit
)

@Composable
private fun MenuTile(emoji: String, title: String, subtitle: String, bg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(122.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 6.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(50.dp).background(bg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 24.sp) }
            Spacer(Modifier.height(7.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), maxLines = 1)
            Text(
                subtitle, fontSize = 10.sp, color = Color(0xFF8A8A8A),
                textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun MenuSettingsRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).background(Color(0xFFEDEDED), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text("⚙️", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sozlamalar", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Text("Ilova sozlamalari", fontSize = 11.sp, color = Color(0xFF8A8A8A))
            }
            Text("›", fontSize = 22.sp, color = Color(0xFF8A8A8A))
        }
    }
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
                    val unitPrice = clientPrices?.get(tx.type)  // N narx (tannarx emas)
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
                                buildAnnotatedString {
                                    append("  ${tx.type.label}: ${tx.amount.formatQty()}  ")
                                    withStyle(
                                        SpanStyle(
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
    quickFills: List<String>,
    onQuickFillClick: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    parsed: List<ParsedEntry>,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    inputFocusRequester: FocusRequester? = null
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
            // Tez kiritish tugmalari — yozuvga kirilganda (fokusda) ko'rinadi
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
                                val needSpace = pos > 0 && cur[pos - 1] != ' '
                                val ins = (if (needSpace) " " else "") + code
                                val newText = cur.substring(0, pos) + ins + cur.substring(pos)
                                val newPos = pos + ins.length
                                tfv = TextFieldValue(newText, selection = TextRange(newPos))
                                onChange(newText)
                                if (code !in listOf("n", "t1")) numericMode = true
                            },
                            label = { Text(code.uppercase()) }
                        )
                    }
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

            // 💡 Tez to'ldirish — mijozning OXIRGI yuk / puli (bosilsa qatorga qo'shiladi)
            if (quickFills.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickFills.forEach { fill ->
                        AssistChip(
                            onClick = { onQuickFillClick(fill) },
                            label = { Text(fill.uppercase()) },
                            leadingIcon = { Text(if (fill.startsWith("p")) "💵" else "📦") }
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
                    modifier = Modifier.size(60.dp)
                ) {
                    Text(
                        if (menuOpen) "✕" else "☰",
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val voiceIn = rememberVoiceInput { spoken -> onVoice(spoken) }
                OutlinedTextField(
                    value = tfv,
                    onValueChange = { newV ->
                        tfv = newV
                        onChange(newV.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (inputFocusRequester != null) Modifier.focusRequester(inputFocusRequester) else Modifier)
                        .onFocusChanged { inputFocused = it.isFocused },
                    placeholder = { Text("Yozuv...") },
                    trailingIcon = {
                        IconButton(onClick = { voiceIn("uz-UZ") }) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Ovoz")
                        }
                    },
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
    onNext: () -> Unit,
    onCloseDebt: () -> Unit = {},
    onEditTx: (Long) -> Unit = {},
    onDeleteTx: (Long) -> Unit = {}
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var actionTx by remember { mutableStateOf<uz.daftar.app.data.db.entity.TransactionEntity?>(null) }
    val monthPrefix = "%04d-%02d".format(month.year, month.monthValue)
    val monthTxs = allTxs.filter { it.date.startsWith(monthPrefix) }.sortedBy { it.date }
    val monthLabel = MONTHS_UZ_TODAY[month.monthValue - 1]

    // Oylik qarz (oy oxiri)
    val monthDebt: Long = run {
        var r = 0.0
        for (tx in monthTxs) {
            when (tx.type.lowercase()) {
                "p" -> r -= tx.amount
                "q" -> r += tx.amount
                else -> { val p = priceByTx[tx.id]; if (p != null) r += tx.amount * p }
            }
        }
        Math.round(r)
    }

    // Har bir yozuvdan keyingi umumiy qoldiq (timeline o'ngi uchun)
    val balMap = remember(allTxs, priceByTx) {
        var b = 0.0
        val m = HashMap<Long, Double>()
        allTxs.sortedBy { it.date }.forEach { tx ->
            when (tx.type.lowercase()) {
                "p" -> b -= tx.amount
                "q" -> b += tx.amount
                else -> { val p = priceByTx[tx.id]; if (p != null) b += tx.amount * p }
            }
            m[tx.id] = b
        }
        m
    }

    val cGreen = androidx.compose.ui.graphics.Color(0xFF1AA35A)
    val cRed = androidx.compose.ui.graphics.Color(0xFFE53935)
    val cGray = androidx.compose.ui.graphics.Color(0xFF6B7280)
    fun typeColor(code: String) = yukRangi(code)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1) Yashil header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(cGreen)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
            ) {
                Text(
                    "${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }} \u2014 $monthLabel ${month.year}",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.20f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        "$monthLabel qarzi: ${monthDebt.toDouble().formatMoney()} so'm",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (monthTxs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("Bu oyda yozuv yo'q", color = cGray, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                        Text("\uD83D\uDCC5 KUNLIK YOZUVLAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cGray)
                    }
                    for (tx in monthTxs) {
                        val tc = typeColor(tx.type)
                        val isP = tx.type.equals("p", true)
                        val isQ = tx.type.equals("q", true)
                        val up = priceByTx[tx.id]
                        val desc = when {
                            isP -> "P(pul): ${tx.amount.formatMoney()}"
                            isQ -> "Q(qarz): ${tx.amount.formatMoney()}"
                            up != null -> "${tx.type.uppercase()}: ${tx.amount.formatQty()} \u00d7 ${up.formatQty()} = ${(tx.amount * up).formatMoney()} so'm"
                            else -> "${tx.type.uppercase()}: ${tx.amount.formatQty()}"
                        }
                        val dayNum = if (tx.date.length >= 10) tx.date.substring(8, 10) else "--"
                        val monIdx = (if (tx.date.length >= 7) tx.date.substring(5, 7).toIntOrNull() else null) ?: month.monthValue
                        val monAbbr = MONTHS_UZ_TODAY[(monIdx - 1).coerceIn(0, 11)].take(4).uppercase()
                        val time = if (tx.date.length >= 16) tx.date.substring(11, 16) else ""
                        val bal = Math.round(balMap[tx.id] ?: 0.0)
                        val balColor = if (bal > 0) cRed else cGreen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clickable { actionTx = tx }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Timeline rail + yashil nuqta
                            Box(modifier = Modifier.width(24.dp).fillMaxHeight()) {
                                Box(modifier = Modifier.width(2.dp).fillMaxHeight().align(Alignment.Center).background(cGreen.copy(alpha = 0.35f)))
                                Box(modifier = Modifier.size(13.dp).align(Alignment.Center).clip(CircleShape).background(cGreen))
                            }
                            Spacer(Modifier.width(8.dp))
                            // Rangli kun raqami + oy
                            Column(modifier = Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dayNum, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tc)
                                Text(monAbbr, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tc)
                            }
                            Spacer(Modifier.width(12.dp))
                            // Tavsif + vaqt
                            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(desc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tc)
                                if (time.isNotEmpty()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text("\uD83D\uDD50 $time", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF9AA0A6))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(bal.toDouble().formatMoney(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = balColor)
                                Text("so'm", fontSize = 11.sp, color = balColor)
                            }
                        }
                        HorizontalDivider(color = androidx.compose.ui.graphics.Color(0x14000000))
                    }
                }

                // JAMI HISOBOT
                run {
                    val cargoTypes = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
                    // Har yuk turi: SONI (amount yig'indisi) + summasi (amount × narx)
                    val qtyByType = LinkedHashMap<TxType, Double>()
                    val valByType = HashMap<TxType, Double>()
                    for (tx in monthTxs) {
                        val t = uz.daftar.app.domain.model.TxType.fromCode(tx.type) ?: continue
                        if (t in cargoTypes) {
                            qtyByType[t] = (qtyByType[t] ?: 0.0) + tx.amount
                            val p = priceByTx[tx.id]; if (p != null) valByType[t] = (valByType[t] ?: 0.0) + tx.amount * p
                        }
                    }
                    val pay = monthTxs.filter { it.type.equals("p", true) }.sumOf { it.amount }
                    val takenTypes = cargoTypes.filter { (qtyByType[it] ?: 0.0) > 0.0 }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color(0xFFF4F8F5)).padding(12.dp)
                    ) {
                        Text("JAMI HISOBOT", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(3.dp))
                        Text("Olingan yuklar (soni)", fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF6B7280),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        // Har olingan yuk turi — O'Z RANGIDA, soni bilan (Farq olib tashlandi)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (t in takenTypes) {
                                PrevTypeCol(
                                    letter = t.code.uppercase(),
                                    qty = qtyByType[t] ?: 0.0,
                                    value = valByType[t] ?: 0.0,
                                    color = typeColor(t.code),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Pul (pul olganim) — yuklar yonida, QIZIL
                            if (pay > 0.0) {
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(cRed.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) { Text("P", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cRed) }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Pul", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
                                    Spacer(Modifier.height(2.dp))
                                    Text("${Math.round(pay).toDouble().formatMoney()} so'm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cRed)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Jami yuk (barcha yuk puli) — to'q kulrang
                        val jamiYuk = valByType.values.sum()
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF374151).copy(alpha = 0.10f)).padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\uD83D\uDCE6 Jami yuk: ${Math.round(jamiYuk).toDouble().formatMoney()} so'm",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFF374151))
                        }
                        Spacer(Modifier.height(8.dp))
                        // Pul (pul olganim) — QIZIL
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(cRed.copy(alpha = 0.10f)).padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\uD83D\uDCB5 Pul: ${Math.round(pay).toDouble().formatMoney()} so'm",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cRed)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (debt > 0) cRed.copy(alpha = 0.10f) else cGreen.copy(alpha = 0.12f))
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (debt > 0) "\uD83D\uDCB3 Qarz: ${debt.toDouble().formatMoney()} so'm"
                                else if (debt == 0L) "\u2705 Qarz yo'q"
                                else "\uD83D\uDC9A Ortiq: ${(-debt).toDouble().formatMoney()} so'm",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = if (debt > 0) cRed else cGreen
                            )
                        }
                    }
                }
            }

            // Oldingi / Keyingi
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onPrev, modifier = Modifier.weight(1f)) { Text("\u2190 Oldingi") }
                FilledTonalButton(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Keyingi \u2192") }
            }
            if (debt > 0) {
                Button(
                    onClick = onCloseDebt,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cGreen)
                ) {
                    Text("\uD83D\uDCB0 Qarzni yopish (${debt.formatMoney()})")
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Yozuvga bosilganda — Nusxa / Tahrirlash / O'chirish
    actionTx?.let { tx ->
        val typeUp = tx.type.uppercase()
        val up = priceByTx[tx.id]
        val lineText = if (tx.type.lowercase() == "p")
            "P: ${tx.amount.formatMoney()}"
        else if (up != null)
            "$typeUp: ${tx.amount.formatQty()} × ${up.formatQty()} = ${(tx.amount * up).formatMoney()}"
        else "$typeUp: ${tx.amount.formatQty()}"
        AlertDialog(
            onDismissRequest = { actionTx = null },
            title = { Text("📝 $lineText") },
            text = { Text("${tx.date.take(10)} — nima qilamiz?") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(lineText))
                        actionTx = null
                    }) { Text("📋 Nusxa olish") }
                    TextButton(onClick = { val id = tx.id; actionTx = null; onEditTx(id) }) {
                        Text("✏️ Tahrirlash")
                    }
                    TextButton(onClick = { val id = tx.id; actionTx = null; onDeleteTx(id) }) {
                        Text("🗑 O'chirish", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { actionTx = null }) { Text("Bekor") } }
        )
    }
}


@Composable
private fun PrevTypeCol(letter: String, qty: Double, value: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text(letter, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color) }
        Spacer(Modifier.height(4.dp))
        Text("${qty.formatQty()} dona", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
        Spacer(Modifier.height(2.dp))
        Text("${value.formatMoney()} so'm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
@Composable
private fun PrevJamiCol(letter: String, label: String, value: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text(letter, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color) }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
        Spacer(Modifier.height(2.dp))
        Text("${value.toDouble().formatMoney()} so'm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
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
            val annotated = runCatching {
                buildAnnotatedString {
                    var i = 0
                    while (i < text.length) {
                        val mIdx = text.indexOf(marker, i)
                        val tm = timeRe.find(text, i)
                        val tIdx = tm?.range?.first ?: -1
                        when {
                            mIdx >= 0 && (tIdx < 0 || mIdx < tIdx) -> {
                                append(text.substring(i, mIdx))
                                withStyle(SpanStyle(color = errColor, fontWeight = FontWeight.Bold)) { append(marker) }
                                i = mIdx + marker.length
                            }
                            tIdx >= 0 && tm != null -> {
                                val tv = tm.value
                                append(text.substring(i, tIdx))
                                withStyle(SpanStyle(color = timeColor, fontWeight = FontWeight.Bold)) { append(tv) }
                                i = tIdx + tv.length
                            }
                            else -> { append(text.substring(i)); i = text.length }
                        }
                    }
                }
            }.getOrElse { androidx.compose.ui.text.AnnotatedString(text) }
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
private fun BottomNavBtn(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun JamiSummary(report: uz.daftar.app.domain.usecase.DateReport) {
    val cA = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    val cB = androidx.compose.ui.graphics.Color(0xFFF57F17)
    val cC = androidx.compose.ui.graphics.Color(0xFF1565C0)
    val cDK = androidx.compose.ui.graphics.Color(0xFF7B1FA2)
    val cP = androidx.compose.ui.graphics.Color(0xFFD32F2F)
    fun colorFor(t: TxType) = yukRangi(t)
    val cargoTypes = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
    val present = cargoTypes.filter { (report.totalsByType[it] ?: 0.0) != 0.0 }
    if (present.isEmpty() && report.totalPayments == 0.0) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color(0xFFF4F8F5)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "— JAMI —", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color(0xFF6B7280),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            if (present.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (t in present) {
                        val cnt = report.totalsByType[t] ?: 0.0
                        val money = report.revenueByType[t] ?: 0.0
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(36.dp).background(colorFor(t).copy(alpha = 0.16f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(t.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colorFor(t)) }
                            Spacer(Modifier.height(5.dp))
                            Text("${cnt.formatQty()} dona", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
                            Text(money.formatMoney(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorFor(t))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val pay = report.totalPayments
            val cGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32)
            // 📦 Jami yuk puli (yashil) + 💵 Pul olindi (qizil) — Farq YO'Q
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).background(cGreen.copy(alpha = 0.10f), RoundedCornerShape(10.dp)).padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦 Jami yuk", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
                        Text(report.totalRevenue.formatMoney(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cGreen)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f).background(cP.copy(alpha = 0.10f), RoundedCornerShape(10.dp)).padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💵 Pul olindi", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF6B7280))
                        Text(pay.formatMoney(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cP)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateReportCard(
    report: uz.daftar.app.domain.usecase.DateReport,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateStr = report.title.ifEmpty { report.date.format(DateTimeFormatter.ofPattern("dd.MM")) }
    // Ranglar: A=yashil, B=sariq, C=ko'k, D/K=binafsha, P=qizil, Q=kulrang
    val cA = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    val cB = androidx.compose.ui.graphics.Color(0xFFF57F17)
    val cC = androidx.compose.ui.graphics.Color(0xFF1565C0)
    val cDK = androidx.compose.ui.graphics.Color(0xFF7B1FA2)
    val cP = androidx.compose.ui.graphics.Color(0xFFD32F2F)
    val cQ = androidx.compose.ui.graphics.Color(0xFF616161)
    fun colorFor(t: TxType) = yukRangi(t)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "\uD83D\uDCC5 $dateStr",
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

            // Raqamlangan mijoz qatorlari — har biri alohida kartochka + rangli nuqta
            Column(modifier = Modifier.fillMaxWidth()) {
                for ((idx, line) in report.clientLines.withIndex()) {
                    val capitalized = line.clientName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                    // Nuqta rangi: birinchi yuk turi (P/Q bo'lmasa), aks holda birinchi yozuv
                    val dotType = line.entries.firstOrNull { it.type != TxType.P && it.type != TxType.Q }?.type
                        ?: line.entries.firstOrNull()?.type ?: TxType.C
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = androidx.compose.ui.graphics.Color(0xFFF7F7F9),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rangli nuqta
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .background(colorFor(dotType), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            // Raqam + ism
                            Text(
                                "${idx + 1}. $capitalized",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF111111),
                                modifier = Modifier.weight(1f)
                            )
                            // Yuk/pul qiymatlari (rangli) — o'ngda
                            val ann = buildAnnotatedString {
                                for ((j, e) in line.entries.withIndex()) {
                                    if (j > 0) append("  ")
                                    val piece = when (e.type) {
                                        TxType.P -> "P:${e.amount.formatMoney()}"
                                        TxType.Q -> "Q:${e.amount.formatMoney()}"
                                        else -> buildString {
                                            append("${e.type.code.uppercase()}:${e.amount.formatQty()}")
                                            e.price?.let {
                                                if (report.useNarx) append("(n:${it.formatQty()})")
                                                else append(" [${it.formatQty()}]")
                                            }
                                        }
                                    }
                                    withStyle(
                                        SpanStyle(color = colorFor(e.type), fontWeight = FontWeight.SemiBold)
                                    ) { append(piece) }
                                }
                            }
                            Text(
                                ann,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // JAMI xulosa endi PASTDA (mijoz qatorlaridan keyin)
            JamiSummary(report)
            // Eski "— JAMI —" bloki OLIB TASHLANDI
        }
    }
}


@Composable
private fun JamiBadge(
    letter: String,
    color: androidx.compose.ui.graphics.Color,
    value: String,
    sub: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color(0xFFF7F7F9),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(22.dp).background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        letter,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(sub, color = androidx.compose.ui.graphics.Color(0xFF888888), fontSize = 11.sp)
        }
    }
}


@Composable
private fun SavedCard(info: SavedInfo) {
    val green = androidx.compose.ui.graphics.Color(0xFF1AA35A)
    val greenTint = androidx.compose.ui.graphics.Color(0xFFE9F6EF)
    val red = androidx.compose.ui.graphics.Color(0xFFE53935)
    val gray = androidx.compose.ui.graphics.Color(0xFF6B7280)
    val ink = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    fun typeColor(code: String) = yukRangi(code)
    val firstType = info.lines.firstOrNull()?.type ?: "c"
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Chap: Saqlandi paneli
            Column(
                modifier = Modifier.width(108.dp).fillMaxHeight().background(greenTint).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Saqlandi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink)
                Spacer(Modifier.height(4.dp))
                Text("\uD83D\uDCC5 ${info.dateLabel}", fontSize = 12.sp, color = gray)
                Spacer(Modifier.height(10.dp))
                // 🎬 Lottie: yashil doira + ✓ chizilish animatsiyasi (yuklanmasa — eski statik belgi)
                val lottieComp by com.airbnb.lottie.compose.rememberLottieComposition(
                    com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(uz.daftar.app.R.raw.success_check)
                )
                if (lottieComp != null) {
                    com.airbnb.lottie.compose.LottieAnimation(
                        composition = lottieComp,
                        iterations = 1,
                        modifier = Modifier.size(58.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(green),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u2713", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            // O'ng: tafsilotlar
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(typeColor(firstType).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(firstType.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = typeColor(firstType))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(info.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ink)
                }
                Spacer(Modifier.height(8.dp))
                info.lines.forEach { line ->
                    val lc = typeColor(line.type)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(lc.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(line.type.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = lc)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(line.main, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = lc)
                            Text(line.sub, fontSize = 11.sp, color = if (line.sub == "Narx yo'q") androidx.compose.ui.graphics.Color(0xFFD32F2F) else gray)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (info.debt > 0) red.copy(alpha = 0.08f) else green.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (info.debt > 0) "\uD83D\uDCB3" else "\u2705", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                if (info.debt > 0) "Qarz: ${info.debt.formatMoney()} so'm" else "Qarz yo'q",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (info.debt > 0) red else green
                            )
                            Text(if (info.debt > 0) "Qarz mavjud" else "To'liq to'langan", fontSize = 11.sp, color = gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtReminderCard(
    debtors: List<uz.daftar.app.domain.usecase.OverdueDebtor>,
    onClose: () -> Unit
) {
    val total = debtors.sumOf { it.debt }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF8F0),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏰", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Qarz eslatmasi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A1A))
                    Text(
                        "Jami " + total.formatMoney() + " so'm \u00b7 " + debtors.size + " mijoz",
                        fontSize = 12.sp, color = Color(0xFF8A8A8A)
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Yopish", tint = Color(0xFFB0B0B0))
                }
            }
            Spacer(Modifier.height(6.dp))
            DebtBucket("🔵 10\u201314 kun", debtors.filter { it.daysOverdue in 10..14 }, Color(0xFF1565C0), Color(0xFFE8F0FE))
            DebtBucket("🟡 15\u201329 kun", debtors.filter { it.daysOverdue in 15..29 }, Color(0xFFF9A825), Color(0xFFFFF8E1))
            DebtBucket("🟠 30\u201359 kun", debtors.filter { it.daysOverdue in 30..59 }, Color(0xFFE65100), Color(0xFFFFF1E6))
            DebtBucket("🔴 60 kun va undan ortiq", debtors.filter { it.daysOverdue >= 60 }, Color(0xFFD32F2F), Color(0xFFFDECEA))
        }
    }
}

@Composable
private fun DebtBucket(
    title: String,
    items: List<uz.daftar.app.domain.usecase.OverdueDebtor>,
    accent: Color,
    badgeBg: Color
) {
    if (items.isEmpty()) return
    Text(
        title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = accent,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    items.sortedBy { it.daysOverdue }.forEach { d ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
        ) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                d.client.replaceFirstChar { it.uppercase() },
                fontSize = 14.sp, color = Color(0xFF222222),
                modifier = Modifier.weight(1f), maxLines = 1
            )
            Text(d.debt.formatMoney(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(d.daysOverdue.toString() + " kun", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium)
            }
        }
    }
}
