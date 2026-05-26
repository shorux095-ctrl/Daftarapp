package uz.daftar.app.ui.screen.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.PeriodReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    vm: ReportsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📊 Hisobotlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Period tabs
            TabRow(selectedTabIndex = state.period.ordinal) {
                Tab(
                    selected = state.period == ReportPeriod.DAY,
                    onClick = { vm.setPeriod(ReportPeriod.DAY) },
                    text = { Text("Kun") }
                )
                Tab(
                    selected = state.period == ReportPeriod.MONTH,
                    onClick = { vm.setPeriod(ReportPeriod.MONTH) },
                    text = { Text("Oy") }
                )
                Tab(
                    selected = state.period == ReportPeriod.YEAR,
                    onClick = { vm.setPeriod(ReportPeriod.YEAR) },
                    text = { Text("Yil") }
                )
            }
            Spacer(Modifier.height(12.dp))

            // Date picker button
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = when (state.period) {
                            ReportPeriod.DAY -> state.date.format(fmt)
                            ReportPeriod.MONTH -> state.date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                            ReportPeriod.YEAR -> state.date.year.toString()
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text("Xato: ${state.error}", color = MaterialTheme.colorScheme.error)
                state.report != null -> ReportView(state.report!!)
                else -> Text("Hisobot yo'q")
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
                    pickerState.selectedDateMillis?.let { millis ->
                        vm.setDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("Tanlash") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Bekor") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ReportView(rep: PeriodReport) {
    Column {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(rep.title, style = MaterialTheme.typography.labelLarge)
                Text(
                    rep.rangeLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Yozuvlar", style = MaterialTheme.typography.labelMedium)
                        Text("${rep.transactionCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Mijozlar", style = MaterialTheme.typography.labelMedium)
                        Text("${rep.clientCount}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Yuk totals — diagramma bilan
        if (rep.totals.isNotEmpty()) {
            Text("Yuk turlari bo'yicha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // Ustunli diagramma
            val chartBars = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
                .mapNotNull { type ->
                    val amt = rep.totals[type] ?: 0.0
                    if (amt > 0) uz.daftar.app.core.chart.BarData(
                        label = type.label,
                        value = amt,
                        color = barColor(type)
                    ) else null
                }
            if (chartBars.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        uz.daftar.app.core.chart.SimpleBarChart(bars = chartBars)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            for (type in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                val amt = rep.totals[type] ?: continue
                if (amt <= 0) continue
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(type.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        Text(amt.formatMoney(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Money summary
        Text("Moliyaviy xulosa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        MoneyRow("💰 Daromad", rep.revenue, MaterialTheme.colorScheme.primary)
        MoneyRow("💵 To'lov olindi", rep.payments, MaterialTheme.colorScheme.tertiary)
        MoneyRow("📤 Rasxod", rep.expenses, MaterialTheme.colorScheme.error)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        MoneyRow(
            "🎯 Sof foyda",
            rep.profit,
            if (rep.profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            bold = true
        )
    }
}

@Composable
private fun MoneyRow(label: String, amount: Long, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        Text(
            "${amount.formatMoney()} so'm",
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Har yuk turi uchun diagramma rangi. */
private fun barColor(type: TxType): androidx.compose.ui.graphics.Color = when (type) {
    TxType.A -> androidx.compose.ui.graphics.Color(0xFF1565C0)  // ko'k
    TxType.B -> androidx.compose.ui.graphics.Color(0xFF2E7D32)  // yashil
    TxType.C -> androidx.compose.ui.graphics.Color(0xFFE65100)  // to'q sariq
    TxType.D -> androidx.compose.ui.graphics.Color(0xFF6A1B9A)  // siyohrang
    TxType.K -> androidx.compose.ui.graphics.Color(0xFF00838F)  // moviy
    else -> androidx.compose.ui.graphics.Color(0xFF757575)      // kulrang
}
