package uz.daftar.app.ui.screen.rasxod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Davr tanlash: Kun / Oy / Yil
            TabRow(selectedTabIndex = state.period.ordinal) {
                Tab(selected = state.period == RasxodPeriod.DAY, onClick = { vm.setPeriod(RasxodPeriod.DAY) }, text = { Text("Kun") })
                Tab(selected = state.period == RasxodPeriod.MONTH, onClick = { vm.setPeriod(RasxodPeriod.MONTH) }, text = { Text("Oy") })
                Tab(selected = state.period == RasxodPeriod.YEAR, onClick = { vm.setPeriod(RasxodPeriod.YEAR) }, text = { Text("Yil") })
            }
            Spacer(Modifier.height(8.dp))

            // ⬅️ Davr ➡️
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { vm.prev() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Oldingi")
                }
                Text(state.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Keyingi")
                }
            }
            Spacer(Modifier.height(8.dp))

            // Total card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        when (state.period) {
                            RasxodPeriod.DAY -> "Kunlik jami xarajat"
                            RasxodPeriod.MONTH -> "Oylik jami xarajat"
                            RasxodPeriod.YEAR -> "Yillik jami xarajat"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "${state.total.formatMoney()} so'm",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text("${state.items.size} ta yozuv", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Add form
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
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Izoh (ixtiyoriy)") },
                        placeholder = { Text("yoqilg'i, ovqat, va h.k.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            vm.add(amount, note)
                            amount = ""; note = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = amount.isNotBlank()
                    ) { Text("Rasxod qo'shish") }
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(
                "Xarajatlar ro'yxati",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            if (state.items.isEmpty()) {
                Text("📭 Hali xarajat yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        RasxodRow(item, onDelete = { vm.delete(item.id) })
                    }
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

private fun formatDate(iso: String): String {
    return try {
        val clean = iso.replace("T", " ").let { if (it.length > 19) it.substring(0, 19) else it }
        val dt = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        dt.format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm"))
    } catch (_: Exception) {
        iso.take(16)
    }
}
