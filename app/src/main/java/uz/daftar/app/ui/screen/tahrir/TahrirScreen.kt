package uz.daftar.app.ui.screen.tahrir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
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
    val items by vm.items.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val nameFilter by vm.nameFilter.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var confirmAll by remember { mutableStateOf(false) }
    var confirmId by remember { mutableStateOf<Long?>(null) }

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
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick = { vm.nextDay() }) { Text("▶", fontSize = 20.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isToday, onClick = { vm.today() }, label = { Text("Bugun") })
                        FilterChip(
                            selected = date == LocalDate.now().minusDays(1),
                            onClick = { vm.setDate(LocalDate.now().minusDays(1)) },
                            label = { Text("Kecha") }
                        )
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

            // ── Hammasini o'chirish ──
            if (items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${items.size} ta yozuv", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { confirmAll = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cP)
                    ) { Text("🗑 Hammasini o'chirish") }
                }
            }

            // ── Ro'yxat ──
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu sanada yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(items, key = { _, tx -> tx.id }) { idx, tx ->
                        TahrirRow(
                            index = idx + 1,
                            tx = tx,
                            onEdit = { onEditTx(tx.id) },
                            onDelete = { confirmId = tx.id }
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
            onDismissRequest = { confirmAll = false },
            title = { Text("Hammasini o'chirish?") },
            text = { Text("$dateStr sanasidagi ${items.size} ta yozuv o'chiriladi. Bu amalni ortga qaytarib bo'lmaydi.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllShown(); confirmAll = false }) {
                    Text("Hammasini o'chirish", color = cP)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Bekor") } }
        )
    }
}

@Composable
private fun TahrirRow(index: Int, tx: Transaction, onEdit: () -> Unit, onDelete: () -> Unit) {
    val entry = when (tx.type) {
        TxType.P -> "P:${tx.amount.formatMoney()}"
        TxType.Q -> "Q:${tx.amount.formatMoney()}"
        else -> buildString {
            append("${tx.type.code.uppercase()}:${tx.amount.formatQty()}")
            tx.tOverride?.let { append(" [${it.formatQty()}]") }
        }
    }
    val name = tx.clientName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF7F7F9),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    }
}
