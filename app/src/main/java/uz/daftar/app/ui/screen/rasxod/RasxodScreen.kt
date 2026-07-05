package uz.daftar.app.ui.screen.rasxod

import androidx.compose.foundation.layout.*
import uz.daftar.app.core.voice.rememberVoiceInput
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.data.db.entity.RasxodEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RasxodScreen(
    onBack: () -> Unit,
    vm: RasxodViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📤 Rasxod") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } }
    ) { padding ->
        // Yuk rasxodi: input holati va ochiq/yopiq — ekran darajasida saqlanadi
        var yukExpanded by remember { mutableStateOf(false) }
        var yukDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
        var showYukDatePicker by remember { mutableStateOf(false) }
        val yukDmy = remember { java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy") }
        val yukInputs = remember(state.yukRateInputs) {
            androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(state.yukRateInputs) }
        }
        // Sotilgan miqdor (tur bo'yicha) — jonli hisob uchun
        val qtyMap = remember(state.yukBreakdown) { state.yukBreakdown.associate { it.type.lowercase() to it.qty } }
        val liveTotal = listOf("a", "b", "c", "d", "k").sumOf { t ->
            val rate = (yukInputs[t] ?: "").trim().replace(",", ".").toDoubleOrNull() ?: 0.0
            (qtyMap[t] ?: 0.0) * rate
        }

        if (showYukDatePicker) {
            val dps = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
            DatePickerDialog(
                onDismissRequest = { showYukDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dps.selectedDateMillis?.let {
                            yukDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        }
                        showYukDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showYukDatePicker = false }) { Text("Bekor") } }
            ) { DatePicker(state = dps) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // Davr tanlash + sana navigatsiya
            item {
                TabRow(selectedTabIndex = state.period.ordinal) {
                    Tab(selected = state.period == RasxodPeriod.DAY, onClick = { vm.setPeriod(RasxodPeriod.DAY) }, text = { Text("Kun") })
                    Tab(selected = state.period == RasxodPeriod.MONTH, onClick = { vm.setPeriod(RasxodPeriod.MONTH) }, text = { Text("Oy") })
                    Tab(selected = state.period == RasxodPeriod.YEAR, onClick = { vm.setPeriod(RasxodPeriod.YEAR) }, text = { Text("Yil") })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.prev() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Oldingi", modifier = Modifier.size(30.dp))
                    }
                    Text(state.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Keyingi", modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Jami xarajat kartasi (chiroyli)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💸", fontSize = 26.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when (state.period) {
                                    RasxodPeriod.DAY -> "Kunlik umumiy xarajat"
                                    RasxodPeriod.MONTH -> "Oylik umumiy xarajat"
                                    RasxodPeriod.YEAR -> "Yillik umumiy xarajat"
                                },
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(2.dp))
                            // UMUMIY = qo'lda xarajat + yuk rasxodi
                            Text(
                                "${(state.total + state.yukTotal).formatMoney()} so'm",
                                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "💵 Qo'lda: ${state.total.formatMoney()}  ·  🚚 Yuk: ${state.yukTotal.formatMoney()}",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Har oy xarajat (faqat Yil ko'rinishida)
            if (state.period == RasxodPeriod.YEAR && state.monthlyBreakdown.any { it.total > 0 }) {
                item {
                    val maxM = state.monthlyBreakdown.maxOf { it.total }.coerceAtLeast(1)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F9))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("📊 Har oy xarajat", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1A1A1A))
                            Spacer(Modifier.height(10.dp))
                            state.monthlyBreakdown.forEach { mr ->
                                val frac = (mr.total.toFloat() / maxM).coerceIn(0f, 1f)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Text(MONTHS_RZ[mr.month - 1], fontSize = 13.sp, color = Color(0xFF555555), modifier = Modifier.width(42.dp))
                                    Box(modifier = Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFECECEF))) {
                                        if (mr.total > 0) {
                                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)))
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (mr.total > 0) mr.total.formatMoney() else "—",
                                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (mr.total > 0) Color(0xFFC62828) else Color(0xFFAAAAAA),
                                        modifier = Modifier.width(78.dp), textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── YUK RASXODI (narx qo'yish + ko'rish) ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E9))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        TextButton(onClick = { yukExpanded = !yukExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Text("🚛 Yuk rasxodi", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                color = Color(0xFF8A4B1F), modifier = Modifier.weight(1f))
                            Text("${state.yukTotal.formatMoney()} so'm  ${if (yukExpanded) "▴" else "▾"}",
                                fontWeight = FontWeight.Bold, color = Color(0xFF8A4B1F))
                        }
                        if (yukExpanded) {
                            // Saqlangan hisob (tur: miqdor × narx = summa)
                            if (state.yukBreakdown.isEmpty()) {
                                Text("Bu davrda sotilgan yuk yo'q", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp))
                            } else {
                                state.yukBreakdown.forEach { ln ->
                                    Text(
                                        "${ln.type}: ${ln.qty.formatQty()} × ${ln.rate.formatQty()} = ${ln.cost.formatMoney()} so'm",
                                        fontSize = 14.sp, color = Color(0xFF333333),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text("Jami yuk: ${state.yukTotal.formatMoney()} so'm",
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF8A4B1F))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Narx qo'yish (tanlangan sanadan boshlab):",
                                style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A4B1F))
                            Spacer(Modifier.height(4.dp))
                            listOf("a", "b", "c", "d", "k").forEach { t ->
                                OutlinedTextField(
                                    value = yukInputs[t] ?: "",
                                    onValueChange = { yukInputs[t] = it },
                                    label = { Text("${t.uppercase()} narx (1 birlik uchun)") },
                                    placeholder = { Text("masalan 0.06") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(onClick = { showYukDatePicker = true }) {
                                Text("📅 Sana: ${yukDate?.format(yukDmy) ?: "bugun"}")
                            }
                            // Jonli hisob — yozayotgan narx bo'yicha (sotuv bo'lsa)
                            if (qtyMap.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFFFE2C7),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "📊 Yangi narx bo'yicha jami: ${liveTotal.formatMoney()} so'm",
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF8A4B1F),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    vm.saveYukRates(yukInputs.toMap(), yukDate)
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("💾 Narxlarni saqlash", fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Rasxod qo'shish formasi — faqat "Kun" bo'limida
            if (state.period == RasxodPeriod.DAY) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                label = { Text("Summa (so'm)") },
                                placeholder = { Text("5000") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            val voiceNote = rememberVoiceInput { note = it }
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Izoh (ixtiyoriy)") },
                                placeholder = { Text("yoqilg'i, ovqat, va h.k.") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { voiceNote("uz-UZ") }) {
                                        Icon(Icons.Outlined.Mic, contentDescription = "Ovoz")
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    vm.add(amount, note)
                                    amount = ""; note = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = amount.isNotBlank()
                            ) { Text("Rasxod qo'shish") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            item {
                Text(
                    "Xarajatlar ro'yxati",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.items.isEmpty()) {
                item {
                    Text("📭 Hali xarajat yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    RasxodRow(item, onDelete = { vm.delete(item.id) })
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RasxodRow(item: RasxodEntity, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${item.amount.formatMoney()} so'm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.note.isNotBlank()) {
                    Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatDate(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val MONTHS_RZ = listOf("Yan", "Fev", "Mar", "Apr", "May", "Iyn", "Iyl", "Avg", "Sen", "Okt", "Noy", "Dek")

private fun formatDate(iso: String): String {
    return try {
        val clean = iso.replace("T", " ").let { if (it.length > 19) it.substring(0, 19) else it }
        val dt = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        dt.format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm"))
    } catch (_: Exception) {
        iso.take(16)
    }
}
