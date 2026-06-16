package uz.daftar.app.ui.screen.sklad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.data.db.entity.SkladEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkladScreen(
    onBack: () -> Unit,
    vm: SkladViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val typeStock by vm.typeStock.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(items) { vm.refreshTypeStock() }
    val snackbarHostState = remember { SnackbarHostState() }

    var showMoney by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var isIn by remember { mutableStateOf(true) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    val sums = remember(items) { SkladViewModel.summarize(items) }
    // Yuk turlari (A/B/C/D/K) "Tovarlar (qoldiq)" ro'yxatida TAKRORLANMAYDI —
    // ular yuqorida "Yuk turlari" kartasida ko'rsatiladi.
    val productSums = remember(sums) {
        sums.filter { it.name.trim().uppercase() !in SkladViewModel.CARGO_TYPES }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📦 Sklad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    TextButton(onClick = { showMoney = !showMoney }) {
                        Text(if (showMoney) "📦 Yuk" else "💰 Yuk puli")
                    }
                    uz.daftar.app.ui.common.HomeButton()
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } }
    ) { padding ->
        // ⬇️ Butun ekran BITTA LazyColumn — endi hamma joy scroll bo'ladi,
        //    "Tovarlar (qoldiq)" pastda kesilib qolmaydi.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ───── Qo'shish formasi ─────
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Tovar nomi") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Yuk turi (har birini alohida kirim qiling) — bosing, nom avtomatik to'ladi:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkladViewModel.CARGO_TYPES.forEach { t ->
                                FilterChip(
                                    selected = name.trim().uppercase() == t,
                                    onClick = { name = if (name.trim().uppercase() == t) "" else t },
                                    label = { Text(t) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = qty, onValueChange = { qty = it },
                                label = { Text("Miqdor") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = price, onValueChange = { price = it },
                                label = { Text("Narx (ixt.)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = isIn, onClick = { isIn = true }, label = { Text("➕ Kirim (keldi)") })
                            FilterChip(selected = !isIn, onClick = { isIn = false }, label = { Text("➖ Chiqim (ketdi)") })
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.add(name, qty, price, isIn); name = ""; qty = ""; price = "" },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Qo'shish") }
                    }
                }
            }

            if (showMoney) {
                // ───── YUK PULI ko'rinishi ─────
                item {
                    val kirim = sums.sumOf { it.pulKirim }
                    val chiqim = sums.sumOf { it.pulChiqim }
                    Card(
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("💰 Yuk puli", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("➕ Kirim: ${kirim.toLong().formatMoney()} so'm")
                            Text("➖ Chiqim: ${chiqim.toLong().formatMoney()} so'm")
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(
                                "Balans: ${(kirim - chiqim).toLong().formatMoney()} so'm",
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                items(sums, key = { "money_" + it.name }) { s ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text("Kirim puli: ${s.pulKirim.toLong().formatMoney()} • Chiqim puli: ${s.pulChiqim.toLong().formatMoney()} so'm",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                // ───── YUK TURLARI bo'yicha qoldiq ─────
                if (typeStock.isNotEmpty()) {
                    item {
                        val kamomad = typeStock.any { it.qolgan < 0 }
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("🚚 Yuk turlari (mijozlarga sotilgan ayrilgan)",
                                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                typeStock.forEach { ts ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(ts.type, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                        Text("kelgan ${ts.kelgan.formatQty()}  •  sotilgan ${ts.sotilgan.formatQty()}",
                                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("qoldi ${ts.qolgan.formatQty()}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (ts.qolgan >= 0) MaterialTheme.colorScheme.primary
                                                    else androidx.compose.ui.graphics.Color(0xFFD32F2F))
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Har bir turni alohida kirim qiling: nomga A/B/C/D/K yozing (yuqoridagi tugmalar). Qoldi = kelgan − sotilgan.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                if (kamomad) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("⚠️ Manfiy qoldi = sotilgani kiritilgan kirimdan ko'p. To'liq kirimni kiriting.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color(0xFFD32F2F))
                                }
                            }
                        }
                    }
                }

                // ───── TOVARLAR (qoldiq) — yuk turlari bu yerda ko'rinmaydi ─────
                item {
                    Text("Tovarlar (qoldiq)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (productSums.isEmpty()) {
                    item {
                        Text("Boshqa tovar yo'q. Yuqoridan nom yozib qo'shing (masalan: mayda xalta).",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(productSums, key = { "prod_" + it.name }) { s ->
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(s.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("📥 Kelgan: ${s.keldi.formatQty()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                                Text("📤 Sotilgan: ${s.chiqdi.formatQty()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F))
                                Text("📦 Qolgan: ${s.qoldi.formatQty()}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (s.qoldi >= 0) MaterialTheme.colorScheme.primary
                                            else androidx.compose.ui.graphics.Color(0xFFD32F2F))
                                if (s.oxirgiKelgan > 0L) {
                                    Text("🗓 Oxirgi kelgan: ${fmtDate(s.oxirgiKelgan)}" +
                                        (if (s.oxirgiNarx > 0) "  •  ${s.oxirgiNarx.formatQty()} so'm" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            // ───── Yozuvlar tarixi (sana + miqdor) ─────
            item {
                Spacer(Modifier.height(4.dp))
                Text("Yozuvlar tarixi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (items.isEmpty()) {
                item { Text("Hozircha yozuv yo'q.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(items, key = { it.id }) { e ->
                    SkladRow(e, onDelete = { vm.delete(e) })
                }
            }
        }
    }
}

/** Sanani dd.MM.yyyy ko'rinishida formatlash */
private fun fmtDate(ms: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
private fun SkladRow(e: SkladEntity, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.isIn) "➕" else "➖", modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${e.name} — ${e.qty.formatQty()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val line = buildString {
                    append("🗓 ${fmtDate(e.date)}")
                    if (e.price > 0) append("  •  ${(e.qty * e.price).toLong().formatMoney()} so'm")
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "O'chirish") }
        }
    }
}
