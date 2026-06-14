package uz.daftar.app.ui.screen.clients

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.theme.DebtColor
import uz.daftar.app.core.theme.PaidColor
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.usecase.ClientSummary
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    onBack: () -> Unit,
    onClientClick: (String) -> Unit = {},
    debtorsOnly: Boolean = false,
    vm: ClientsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shown = if (debtorsOnly) state.filtered.filter { it.debt > 0 } else state.filtered
    val context = LocalContext.current
    var showPay by remember { mutableStateOf(false) }
    var payText by remember { mutableStateOf("") }
    LaunchedEffect(state.info) {
        state.info?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearInfo()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (debtorsOnly) "💳 Qarzdorlar (${shown.size})" else "👥 Mijozlar (${state.clients.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Yangilash") }; uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.filter,
                onValueChange = vm::onFilterChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Mijoz nomini qidiring…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true
            )

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("❌ ${state.error}", color = MaterialTheme.colorScheme.error)
                }

                state.clients.isEmpty() -> EmptyClients()

                shown.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (debtorsOnly) "Qarzdor yo'q ✅" else "Topilmadi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> {
                    if (debtorsOnly) {
                        val debtors = state.filtered.filter { it.debt > 0 }.sortedByDescending { it.debt }
                        val overpaid = state.filtered.filter { it.debt < 0 }.sortedBy { it.debt }
                        val totalDebt = debtors.sumOf { it.debt }
                        val totalOverpaid = -overpaid.sumOf { it.debt }  // ijobiy qiymat
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            item(key = "total-top") {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            "Jami qarz",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${totalDebt.formatMoney()} so'm",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = DebtColor
                                        )
                                    }
                                }
                            }
                            items(items = debtors, key = { "d-${it.name}" }) { c ->
                                ClientCard(c, onClick = { onClientClick(c.name) })
                            }
                            if (overpaid.isNotEmpty()) {
                                item(key = "overpaid-header") {
                                    Text(
                                        "💚 Ortiqcha to'lovlar",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }
                                items(items = overpaid, key = { "o-${it.name}" }) { c ->
                                    ClientCard(c, onClick = { onClientClick(c.name) })
                                }
                            }
                            item(key = "summary") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "🔢 JAMI",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Qarz: ${totalDebt.formatMoney()} so'm",
                                            color = uz.daftar.app.core.theme.DebtColor,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                        if (overpaid.isNotEmpty()) {
                                            Text(
                                                "Ortiqcha: ${totalOverpaid.formatMoney()} so'm",
                                                color = uz.daftar.app.core.theme.PaidColor
                                            )
                                            Text(
                                                "Sof: ${(totalDebt - totalOverpaid).formatMoney()} so'm",
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { showPay = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("\ud83d\udcb5 To'lov qabul qilish", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            items(items = shown, key = { it.name }) { c ->
                                ClientCard(c, onClick = { onClientClick(c.name) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPay) {
        PayDialog(
            text = payText,
            onText = { payText = it },
            onConfirm = {
                if (payText.isNotBlank()) vm.addPayment(payText)
                payText = ""
                showPay = false
            },
            onDismiss = { showPay = false; payText = "" }
        )
    }
}

@Composable
private fun PayDialog(
    text: String,
    onText: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\ud83d\udcb5 To'lov qabul qilish") },
        text = {
            Column {
                Text("Mijoz nomi va summa:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = { Text("ali 50000") },
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("\u2705 Saqlash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor") } }
    )
}

@Composable
private fun ClientCard(c: ClientSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(avatarColor(c.name), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    c.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = c.name.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                c.lastYukDate?.let {
                    Text(
                        "Oxirgi yuk: ${it.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${c.debt.formatMoney()} so'm",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (c.debt > 0) DebtColor else PaidColor
                )
                Text(
                    if (c.debt > 0) "qarz" else "to'liq",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyClients() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👥", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text("Mijozlar yo'q", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Yangi yozuv qiling — mijoz avtomatik qo'shiladi",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFF5C6BC0), Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFFEF5350),
    Color(0xFF42A5F5), Color(0xFFFFA726), Color(0xFF66BB6A), Color(0xFFEC407A)
)

private fun avatarColor(name: String): Color =
    AVATAR_COLORS[kotlin.math.abs(name.hashCode()) % AVATAR_COLORS.size]
