package uz.daftar.app.ui.screen.yuknarx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatPrice
import uz.daftar.app.data.db.entity.YukNarxEntity
import uz.daftar.app.domain.model.TxType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YukNarxScreen(
    onBack: () -> Unit,
    vm: YukNarxViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val inputs = remember { mutableStateMapOf<TxType, String>() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🚛 Yuk narxlari") },
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
                .verticalScroll(rememberScrollState())
        ) {
            // ── T / T1 tab ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.group == "t",
                    onClick = { vm.setGroup("t"); inputs.clear() },
                    label = { Text("T narx") }
                )
                FilterChip(
                    selected = state.group == "t1",
                    onClick = { vm.setGroup("t1"); inputs.clear() },
                    label = { Text("T1 narx") }
                )
            }
            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (state.group == "t") "💡 Global T narx (tannarx)" else "💡 T1 narx (2-tannarx)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.group == "t")
                            "Barcha yuklar uchun standart tannarx. Foyda hisobida ishlatiladi."
                        else
                            "Ba'zi yuklarning ikkinchi tannarxi (t1 belgili yuklar uchun). Foydaga ta'sir qiladi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(
                if (state.group == "t") "Hozirgi T narxlar" else "Hozirgi T1 narxlar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            val yukTypes = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
            for (type in yukTypes) {
                YukNarxRow(
                    type = type,
                    entry = state.current[type],
                    value = inputs[type] ?: "",
                    onChange = { inputs[type] = it }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { vm.setPrices(inputs.toMap()); inputs.clear() },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputs.values.any { it.isNotBlank() }
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.group == "t") "T narxni saqlash" else "T1 narxni saqlash")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "yyyy-MM-dd HH:mm:ss" -> "dd.MM HH:mm" */
private fun fmtDate(d: String): String {
    return if (d.length >= 16) "${d.substring(8, 10)}.${d.substring(5, 7)} ${d.substring(11, 16)}" else d
}

@Composable
private fun YukNarxRow(
    type: TxType,
    entry: YukNarxEntity?,
    value: String,
    onChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.width(60.dp)) {
                Text(type.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("narx", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hozir: ${if (entry != null) "${entry.price.formatPrice()} so'm" else "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry != null) {
                    Text(
                        "🕒 Qo'yildi: ${fmtDate(entry.date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Yangi narx") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
    }
}
