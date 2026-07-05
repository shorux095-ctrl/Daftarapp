package uz.daftar.app.ui.screen.tahrir

import uz.daftar.app.core.util.yukRangi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.model.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val cA = Color(0xFF2E7D32)
private val cB = Color(0xFFF57F17)
private val cC = Color(0xFF1565C0)
private val cDK = Color(0xFF7B1FA2)
private val cP = Color(0xFFD32F2F)
private val cQ = Color(0xFF616161)
private fun colorFor(t: TxType) = yukRangi(t)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahrirScreen(
    onBack: () -> Unit,
    onEditTx: (Long) -> Unit,
    onOpenClient: (String) -> Unit = {},
    vm: TahrirViewModel = hiltViewModel()
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val nameFilter by vm.nameFilter.collectAsStateWithLifecycle()
    val allNames by vm.allNames.collectAsStateWithLifecycle()
    val allClient by vm.allClient.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pinSet by vm.pinSet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var confirmAll by remember { mutableStateOf(false) }
    var confirmId by remember { mutableStateOf<Long?>(null) }
    var delPin by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    val dateStr = remember(date) { date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }
    val isToday = date == LocalDate.now()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("✏️ Tahrirlash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(snackbarData = it) } }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Sana tanlash ──
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                  if (allClient.isNotBlank()) {
                    // 📂 BUTUN TARIX rejimi — Tahrir uslubida (tahrir/o'chirish/T-T1 ishlaydi)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📂 " + allClient.replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Butun tarix · ${rows.size} yozuv", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                        OutlinedButton(
                            onClick = { vm.closeAllClient() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("✖ Yopish", fontSize = 13.sp) }
                    }
                  } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.prevDay() }) { Text("◀", fontSize = 20.sp) }
                        Text(
                            "🗓 $dateStr" + if (isToday) "  (bugun)" else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(vertical = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick = { vm.nextDay() }) { Text("▶", fontSize = 20.sp) }
                    }
                    // Bugun / Kecha + (o'ngda) qisqa "Hammasini o'chirish"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(selected = isToday, onClick = { vm.today() }, label = { Text("Bugun") })
                        FilterChip(
                            selected = date == LocalDate.now().minusDays(1),
                            onClick = { vm.setDate(LocalDate.now().minusDays(1)) },
                            label = { Text("Kecha") }
                        )
                        Spacer(Modifier.weight(1f))
                        if (rows.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { confirmAll = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = cP),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 34.dp)
                            ) { Text("🗑 Hammasi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameFilter,
                        onValueChange = { vm.setNameFilter(it) },
                        label = { Text("Ism bo'yicha qidirish") },
                        singleLine = true,
                        trailingIcon = {
                            if (nameFilter.isNotEmpty()) {
                                TextButton(onClick = { vm.setNameFilter("") }) { Text("✕") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // ✨ YORDAM: yozayotganda mavjud ismlardan tanlash
                    run {
                        val q = nameFilter.trim().lowercase()
                        val hints = if (q.isBlank()) emptyList()
                            else allNames.filter { it.contains(q) && it != q }.take(5)
                        if (hints.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF1F3F6),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(vertical = 2.dp)) {
                                    for (h in hints) {
                                        TextButton(
                                            onClick = { vm.setNameFilter(h) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "👤 " + h.replaceFirstChar { it.uppercase() },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color(0xFF374151)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Umumiy qidiruv: ism kiritilsa, butun tarixni ochish (barcha sanalar)
                    if (nameFilter.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            onClick = { vm.openAllClient(nameFilter.trim()) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE8F5EC),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📂 \"${nameFilter.trim()}\" — butun tarixini Tahrirlashda ochish",
                                    color = Color(0xFF1AA35A), fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp, modifier = Modifier.weight(1f)
                                )
                                Text("→", color = Color(0xFF1AA35A), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                  }
                }
            }

            // ── Ro'yxat (mijoz bo'yicha guruhlangan: yuk+pul birga) ──
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu sanada yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val grouped = remember(rows) { rows.groupBy { it.tx.clientName }.entries.toList() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(grouped, key = { _, e -> e.key }) { idx, entry ->
                        ClientGroupCard(
                            index = idx + 1,
                            clientName = entry.key,
                            rows = entry.value,
                            onEdit = { id -> onEditTx(id) },
                            onDelete = { id -> confirmId = id },
                            onSetTier = { tx, tier -> vm.setTier(tx, tier) }
                        )
                    }
                }
            }
        }
    }

    // ── Tasdiqlash: bitta o'chirish ──
    confirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("O'chirilsinmi?") },
            text = { Text("Bu yozuv o'chiriladi. Bu amalni ortga qaytarib bo'lmaydi.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(id); confirmId = null }) {
                    Text("O'chirish", color = cP)
                }
            },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("Bekor") } }
        )
    }

    // ── Tasdiqlash: hammasini o'chirish ──
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false; delPin = "" },
            title = { Text("Hammasini o'chirish?") },
            text = {
                Column {
                    Text("$dateStr sanasidagi ${rows.size} ta yozuv o'chiriladi. Bu amalni ortga qaytarib bo'lmaydi.")
                    if (pinSet) {
                        Spacer(Modifier.height(10.dp))
                        Text("Tasdiqlash uchun PIN kodni kiriting:", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = delPin,
                            onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) delPin = it },
                            label = { Text("PIN kod") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteAllShown(delPin) { confirmAll = false; delPin = "" } },
                    enabled = !pinSet || delPin.length >= 4
                ) {
                    Text("Hammasini o'chirish", color = cP)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false; delPin = "" }) { Text("Bekor") } }
        )
    }

    // ── Kalendar (istalgan sanani tanlash) ──
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        vm.setDate(d)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Bekor") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ClientGroupCard(
    index: Int,
    clientName: String,
    rows: List<TahrirRowData>,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSetTier: (Transaction, String) -> Unit
) {
    val name = clientName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF7F7F9),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            // Mijoz sarlavhasi
            Text("$index. $name", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF111111))
            Spacer(Modifier.height(4.dp))
            // Har bir yozuv (yuk va pul birga, bitta kartada)
            rows.forEachIndexed { i, r ->
                val tx = r.tx
                val isCargo = tx.type != TxType.P && tx.type != TxType.Q
                val entry = when (tx.type) {
                    TxType.P -> "P:${tx.amount.formatMoney()}"
                    TxType.Q -> "Q:${tx.amount.formatMoney()}"
                    else -> "${tx.type.code.uppercase()}:${tx.amount.formatQty()}"
                }
                if (i > 0) HorizontalDivider(color = Color(0x0D000000), modifier = Modifier.padding(vertical = 3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(colorFor(tx.type), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(entry, color = colorFor(tx.type), fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    // 🕐 Yozuv sanasi va vaqti (butun tarix rejimida sana muhim)
                    val tm = tx.date.let { d ->
                        if (d.length >= 16) d.substring(8, 10) + "." + d.substring(5, 7) + " " + d.substring(11, 16) else ""
                    }
                    if (tm.isNotBlank()) {
                        Text("🕐 $tm", fontSize = 11.sp, color = Color(0xFF9AA0A6))
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { onEdit(tx.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Tahrirlash", tint = cC)
                    }
                    IconButton(onClick = { onDelete(tx.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "O'chirish", tint = cP)
                    }
                }
                if (isCargo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 19.dp, end = 6.dp, top = 1.dp)
                    ) {
                        val manual = tx.tOverride != null
                        Text(
                            "T narx: " + (r.costPrice?.let { "${it.formatQty()} so'm" } ?: "—") +
                                (if (manual) "  (qo'lda)" else ""),
                            fontSize = 12.sp, color = Color(0xFF444444),
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)
                        )
                        TierToggle(tx.costTier) { onSetTier(tx, it) }
                    }
                }
            }
        }
    }
}

/** Kichik [T | T1] tarif tanlagich */
@Composable
private fun TierToggle(current: String?, onSet: (String) -> Unit) {
    val isT1 = current == "t1"
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8EBEF)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TierSeg("T", !isT1, cC) { onSet("t") }
        TierSeg("T1", isT1, cB) { onSet("t1") }
    }
}

@Composable
private fun TierSeg(text: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) activeColor else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) Color.White else Color(0xFF555555),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
