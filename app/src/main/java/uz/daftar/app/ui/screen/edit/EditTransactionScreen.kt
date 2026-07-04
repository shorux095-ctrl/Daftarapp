package uz.daftar.app.ui.screen.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.domain.model.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: EditTransactionViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSaved) { if (state.isSaved) onSaved() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("✏️ Yozuvni tahrirlash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState())
        ) {
            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.original == null -> Text(
                    "Xato: ${state.error}", color = MaterialTheme.colorScheme.error
                )
                state.original != null -> {
                    val orig = state.original!!
                    // Mijoz — YOZIB O'ZGARTIRSA BO'LADI (yordam: mavjud ismlardan tanlash)
                    OutlinedTextField(
                        value = state.clientName,
                        onValueChange = vm::setClientName,
                        label = { Text("Mijoz (ism)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val q = state.clientName.trim().lowercase()
                    val hints = if (q.isBlank()) emptyList()
                        else state.allNames.filter { it.contains(q) && it != q }.take(5)
                    if (hints.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                for (h in hints) {
                                    TextButton(
                                        onClick = { vm.setClientName(h) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "👤 " + h.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // Tur
                    Text("Tur", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRowChips(state.type) { vm.setType(it) }
                    Spacer(Modifier.height(16.dp))

                    // Sana
                    Text("Sana", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = { showDatePicker = true }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(state.date.format(fmt), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // Miqdor (yuk yoki pul)
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = vm::setAmount,
                        label = { Text(if (state.isCargo) "Yuk miqdori" else "Summa (pul)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = state.error != null
                    )
                    Spacer(Modifier.height(10.dp))
                    // 📝 IZOH — har yozuvga eslatma (ixtiyoriy)
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = vm::setNote,
                        label = { Text("📝 Izoh (ixtiyoriy)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Faqat yuk uchun: N narx, T narx, T1
                    if (state.isCargo) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.nNarx,
                            onValueChange = vm::setNNarx,
                            label = { Text("N narx — sotuv narxi") },
                            supportingText = { Text("Bu sanadan boshlab shu mijoz uchun narx") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.tNarx,
                            onValueChange = vm::setTNarx,
                            label = { Text("T narx — tannarx (ixtiyoriy)") },
                            supportingText = { Text("Bo'sh = global T narx ishlatiladi") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.isT1, onCheckedChange = { vm.setIsT1(it) })
                            Text("T1 tarif (ikkinchi tannarx)")
                        }
                    }

                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = vm::save,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.amount.isNotBlank()
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Saqlash")
                    }
                    Spacer(Modifier.height(10.dp))
                    // 🗑 SHU BITTA yozuvni o'chirish (takroriy yozuvlarni olib tashlash uchun)
                    var showDel by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showDel = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("🗑 Yozuvni o'chirish") }
                    if (showDel) {
                        AlertDialog(
                            onDismissRequest = { showDel = false },
                            title = { Text("Yozuv o'chirilsinmi?") },
                            text = { Text("Shu bitta yozuv butunlay o'chiriladi. Qarz avtomatik qayta hisoblanadi.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDel = false
                                    vm.delete { onBack() }
                                }) { Text("Ha, o'chirish", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { showDel = false }) { Text("Bekor") } }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        vm.setDate(d)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Bekor") } }
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(selected: TxType, onSelect: (TxType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K, TxType.P, TxType.Q)) {
            FilterChip(selected = selected == t, onClick = { onSelect(t) }, label = { Text(t.label) })
        }
    }
}
