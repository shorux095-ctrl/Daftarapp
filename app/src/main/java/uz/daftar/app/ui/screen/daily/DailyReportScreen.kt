package uz.daftar.app.ui.screen.daily
import uz.daftar.app.core.util.yukRangi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.ui.common.HomeButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val GreenA = Color(0xFF1AA35A)
private val GreenB = Color(0xFF13B86C)
private val BlueP = Color(0xFF1565C0)
private val RedQ = Color(0xFFE53935)
private val InkDark = Color(0xFF1A1A1A)
private val InkGray = Color(0xFF6B7280)

private val WEEKDAYS_UZ = listOf(
    "Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    onBack: () -> Unit,
    vm: DailyReportViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    val d = state.date
    val dateStr = "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
    val weekday = WEEKDAYS_UZ[(d.dayOfWeek.value - 1).coerceIn(0, 6)]

    androidx.compose.material3.Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("\uD83D\uDCC5 Kunlik hisobot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Outlined.DateRange, contentDescription = "Kalendar")
                    }
                    HomeButton()
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ⬅️ sana, hafta kuni ➡️
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevDay() }, modifier = Modifier.size(48.dp)) {
                    Text("⬅️", fontSize = 26.sp)
                }
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(GreenB.copy(alpha = 0.10f))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dateStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkDark)
                    Text(weekday, fontSize = 12.sp, color = InkGray)
                }
                IconButton(onClick = { vm.nextDay() }, modifier = Modifier.size(48.dp)) {
                    Text("➡️", fontSize = 26.sp)
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("❌ ${state.error}", color = RedQ)
                }
                state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu kunda yozuv yo'q", color = InkGray)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    itemsIndexed(state.lines, key = { _, ln -> ln.clientName }) { idx, ln ->
                        DayRow(idx + 1, ln)
                    }
                    item("totals") { TotalsCard(state) }
                    item("sp") { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }

    if (showPicker) {
        val initMs = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { ms ->
                        val ld = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.setDate(ld)
                    }
                    showPicker = false
                }) { Text("Tanlash") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Bekor") } }
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun DayRow(num: Int, ln: DayClientLine) {
    val name = ln.clientName.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
    val detail = buildAnnotatedString {
        ln.cargo.forEachIndexed { i, c ->
            if (i > 0) append("    ")
            withStyle(SpanStyle(color = yukRangi(c.type), fontWeight = FontWeight.SemiBold)) {
                append("${c.type}:${c.qty.formatQty()}")
            }
            if (c.price != null) {
                withStyle(SpanStyle(color = InkGray)) { append(" [${c.price.formatQty()}]") }
            }
        }
        if (ln.payment > 0) {
            if (length > 0) append("    ")
            withStyle(SpanStyle(color = yukRangi("p"), fontWeight = FontWeight.Bold)) {
                append("P:${ln.payment.formatMoney()}")
            }
        }
        if (ln.manualDebt > 0) {
            if (length > 0) append("    ")
            withStyle(SpanStyle(color = yukRangi("q"), fontWeight = FontWeight.Bold)) {
                append("Q:${ln.manualDebt.formatMoney()}")
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$num. $name", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkDark)
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(detail, fontSize = 14.sp)
        }
    }
    HorizontalDivider(color = Color(0x0F000000))
}

@Composable
private fun TotalsCard(state: DailyReportState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F8F5)).padding(16.dp)
    ) {
        Text("\uD83D\uDCCA JAMI", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = InkDark,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        state.totalsByType.forEach { (type, qty) ->
            TotalRow("\uD83D\uDCE6 $type yuk", qty.formatQty(), InkDark)
        }
        if (state.totalsByType.isNotEmpty()) {
            TotalRow("\uD83D\uDCB0 Yuk summasi", "${state.totalCargoValue.formatMoney()} so'm", GreenA)
        }
        TotalRow("\uD83D\uDCB5 To'lov (P)", "${state.totalPayment.formatMoney()} so'm", BlueP)
        if (state.totalManualDebt > 0) {
            TotalRow("\uD83D\uDCDD Qarz (Q)", "${state.totalManualDebt.formatMoney()} so'm", RedQ)
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0x14000000))
        Spacer(Modifier.height(6.dp))
        TotalRow("\uD83D\uDC65 Mijozlar", "${state.clientCount} ta", InkGray, bold = true)
    }
}

@Composable
private fun TotalRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = InkDark)
        Text(value, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = color)
    }
}
