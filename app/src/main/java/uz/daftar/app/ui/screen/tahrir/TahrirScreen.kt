package uz.daftar.app.ui.screen.tahrir

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
private fun colorFor(t: TxType) = when (t) {
    TxType.A -> cA; TxType.B -> cB; TxType.C -> cC
    TxType.D, TxType.K -> cDK; TxType.P -> cP; TxType.Q -> cQ
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TahrirScreen(
    onBack: () -> Unit,
    onEditTx: (Long) -> Unit,
    vm: TahrirViewModel = hiltViewModel()
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val nameFilter by vm.nameFilter.collectAsStateWithLifecycle()
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
                }
            }

            // ── Ro'yxat ──
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu sanada yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(rows, key = { _, r -> r.tx.id }) { idx, r ->
                        TahrirRow(
                            index = idx + 1,
                            tx = r.tx,
                            costPrice = r.costPrice,
                            onEdit = { onEditTx(r.tx.id) },
                            onDelete = { confirmId = r.tx.id },
                            onSetTier = { tier -> vm.setTier(r.tx, tier) }
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
private fun TahrirRow(
    index: Int,
    tx: Transaction,
    costPrice: Double?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetTier: (String) -> Unit
) {
    val isCargo = tx.type != TxType.P && tx.type != TxType.Q
    val entry = when (tx.type) {
        TxType.P -> "P:${tx.amount.formatMoney()}"
        TxType.Q -> "Q:${tx.amount.formatMoney()}"
        else -> "${tx.type.code.uppercase()}:${tx.amount.formatQty()}"
    }
    val name = tx.clientName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF7F7F9),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)) {
            // 1-qator: ism + entry + tahrir/o'chirish
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colorFor(tx.type), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("$index.", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF555555))
                Spacer(Modifier.width(6.dp))
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = Color(0xFF111111), modifier = Modifier.weight(1f))
                Text(entry, color = colorFor(tx.type), fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Tahrirlash", tint = cC)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "O'chirish", tint = cP)
                }
            }
            // 2-qator (faqat yuk uchun): T narx + T/T1 tarif tugmasi
            if (isCargo) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 6.dp)
                ) {
                    val manual = tx.tOverride != null
                    Text(
                        "T narx: " + (costPrice?.let { "${it.formatQty()} so'm" } ?: "—") +
                            (if (manual) "  (qo'lda)" else ""),
                        fontSize = 13.sp, color = Color(0xFF444444),
                        fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)
                    )
                    TierToggle(tx.costTier, onSetTier)
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
