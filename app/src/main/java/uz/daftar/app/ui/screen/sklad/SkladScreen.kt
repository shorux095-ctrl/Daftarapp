package uz.daftar.app.ui.screen.sklad

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            // Qo'shish formasi
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Tovar nomi") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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

            Spacer(Modifier.height(16.dp))

            if (showMoney) {
                // ── YUK PULI ko'rinishi ──
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
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sums, key = { it.name }) { s ->
                        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(s.name, fontWeight = FontWeight.Bold)
                                Text("Kirim puli: ${s.pulKirim.toLong().formatMoney()} • Chiqim puli: ${s.pulChiqim.toLong().formatMoney()} so'm",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                // ── YUK TURLARI bo'yicha qoldiq (mijozlarga sotilgan avtomatik ayriladi) ──
                if (typeStock.isNotEmpty()) {
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
                                        color = if (ts.qolgan > 0) MaterialTheme.colorScheme.primary
                                                else androidx.compose.ui.graphics.Color(0xFFD32F2F))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Kelgan = skladga A/B/C/D/K nomi bilan qo'shilgan kirim",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── YUK (qoldiq) ko'rinishi ──
                Text("Tovarlar (qoldiq)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (sums.isEmpty()) {
                    Text("Sklad bo'sh. Yuqoridan tovar qo'shing.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sums, key = { it.name }) { s ->
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
                                        color = if (s.qoldi > 0) MaterialTheme.colorScheme.primary
                                                else androidx.compose.ui.graphics.Color(0xFFD32F2F))
                                    if (s.oxirgiKelgan > 0L) {
                                        val d = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                            .format(java.util.Date(s.oxirgiKelgan))
                                        Text("🗓 Oxirgi kelgan: $d" +
                                            (if (s.oxirgiNarx > 0) "  •  ${s.oxirgiNarx.formatQty()} so'm" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                    if (s.pulChiqim > 0) {
                                        Text("💰 Sotuvdan: ${s.pulChiqim.toLong().formatMoney()} so'm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Yozuvlar tarixi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(items, key = { it.id }) { e ->
                    SkladRow(e, onDelete = { vm.delete(e) })
                }
            }
        }
    }
}

@Composable
private fun SkladRow(e: SkladEntity, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (e.isIn) "➕" else "➖", modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${e.name} — ${e.qty.formatQty()}", style = MaterialTheme.typography.bodyMedium)
                if (e.price > 0) Text("${(e.qty * e.price).toLong().formatMoney()} so'm",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "O'chirish") }
        }
    }
}
