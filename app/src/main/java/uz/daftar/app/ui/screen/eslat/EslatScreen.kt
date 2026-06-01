package uz.daftar.app.ui.screen.eslat

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
import uz.daftar.app.data.db.entity.EslatmaEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EslatScreen(
    onBack: () -> Unit,
    vm: EslatViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var text by remember { mutableStateOf("") }
    var dayIdx by remember { mutableStateOf(1) } // 0=Bugun, 1=Ertaga...
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("00") }

    val dayOptions = listOf("Bugun" to 0, "Ertaga" to 1, "2 kun" to 2, "3 kun" to 3, "1 hafta" to 7)

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("⏰ Eslatmalar") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Eslatma (masalan: Ali pul beradi)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Qachon:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayOptions.forEach { (label, idx) ->
                            FilterChip(
                                selected = dayIdx == idx,
                                onClick = { dayIdx = idx },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Soat: ")
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { if (it.length <= 2) hour = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(70.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Text("  :  ")
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { if (it.length <= 2) minute = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(70.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.add(text, dayIdx, hour.toIntOrNull() ?: 9, minute.toIntOrNull() ?: 0)
                            text = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("➕ Eslatma qo'yish") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Eslatmalar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text("Hozircha eslatma yo'q.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { e ->
                        EslatmaRow(e, onDelete = { vm.delete(e) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EslatmaRow(e: EslatmaEntity, onDelete: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm") }
    val whenStr = remember(e.triggerAt) {
        Instant.ofEpochMilli(e.triggerAt).atZone(ZoneId.systemDefault()).toLocalDateTime().format(fmt)
    }
    val past = e.triggerAt <= System.currentTimeMillis()
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(e.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    (if (past) "✅ " else "⏰ ") + whenStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (past) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "O'chirish")
            }
        }
    }
}
