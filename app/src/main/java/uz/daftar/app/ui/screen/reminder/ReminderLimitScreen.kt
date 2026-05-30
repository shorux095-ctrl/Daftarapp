package uz.daftar.app.ui.screen.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderLimitScreen(
    onBack: () -> Unit,
    vm: ReminderLimitViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🔔 Eslatma va Limit") },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Eslatma") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Limit") })
            }
            when (tab) {
                0 -> ReminderTab(state, vm)
                1 -> LimitTab(state, vm)
            }
        }
    }
}

@Composable
private fun ReminderTab(state: ReminderLimitState, vm: ReminderLimitViewModel) {
    var client by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "💡 Eslatma — mijoz qarzini necha kundan keyin eslatish kerakligini belgilaydi",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = client,
            onValueChange = { client = it },
            label = { Text("Mijoz nomi") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = days,
            onValueChange = { days = it },
            label = { Text("Necha kun") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                vm.addReminder(client, days.toIntOrNull() ?: 0)
                client = ""; days = "7"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = client.isNotBlank()
        ) { Text("Eslatma qo'shish") }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Faol eslatmalar (${state.reminders.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (state.reminders.isEmpty()) {
            Text("Hali eslatma yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.reminders, key = { it.userId.toString() + ":" + it.clientName }) { r ->
                    InfoRow(
                        title = r.clientName.cap(),
                        subtitle = "${r.days} kunda eslatadi",
                        onDelete = { vm.removeReminder(r.clientName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitTab(state: ReminderLimitState, vm: ReminderLimitViewModel) {
    var client by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "💡 Limit — mijoz qarzi shu summadan oshganda ogohlantiradi",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = client,
            onValueChange = { client = it },
            label = { Text("Mijoz nomi") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Limit summasi") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                vm.addLimit(client, amount.replace(",", ".").toDoubleOrNull() ?: 0.0)
                client = ""; amount = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = client.isNotBlank() && amount.isNotBlank()
        ) { Text("Limit qo'shish") }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Faol limitlar (${state.limits.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (state.limits.isEmpty()) {
            Text("Hali limit yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.limits, key = { it.userId.toString() + ":" + it.clientName }) { l ->
                    InfoRow(
                        title = l.clientName.cap(),
                        subtitle = "Limit: ${l.limitAmt.formatMoney()} so'm",
                        onDelete = { vm.removeLimit(l.clientName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun String.cap(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
