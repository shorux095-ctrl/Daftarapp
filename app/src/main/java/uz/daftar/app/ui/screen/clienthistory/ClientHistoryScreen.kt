package uz.daftar.app.ui.screen.clienthistory

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import uz.daftar.app.core.pdf.DebtPdf
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.theme.DebtColor
import uz.daftar.app.core.theme.PaidColor
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ClientHistoryScreen(
    onBack: () -> Unit,
    onEditTx: (Long) -> Unit = {},
    onSetNarx: (String) -> Unit = {},
    vm: ClientHistoryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(state.clientName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching {
                            val mp = "%04d-%02d".format(state.selectedMonth.year, state.selectedMonth.monthValue)
                            val monthName = MONTHS_UZ[state.selectedMonth.monthValue - 1]
                            val txs = state.transactions
                                .filter { it.date.startsWith(mp) }
                                .sortedBy { it.date }
                            val body = mutableListOf<String>()
                            txs.groupBy { it.date.take(10) }.toSortedMap().forEach { (day, list) ->
                                body.add("${day.substring(8, 10)}.${day.substring(5, 7)}")
                                list.forEach { tx ->
                                    val unitPrice = state.priceByTx[tx.id]
                                    val ln = when {
                                        tx.type.equals("p", true) -> "To'lov (P): ${tx.amount.formatMoney()} so'm"
                                        tx.type.equals("q", true) -> "Qarz (Q): ${tx.amount.formatMoney()} so'm"
                                        unitPrice != null -> "${tx.type.uppercase()}: ${tx.amount.formatQty()} x ${unitPrice.formatQty()} = ${(tx.amount * unitPrice).formatMoney()} so'm"
                                        else -> "${tx.type.uppercase()}: ${tx.amount.formatQty()}"
                                    }
                                    body.add("    $ln")
                                }
                            }
                            if (body.isEmpty()) body.add("Bu oyda yozuv yo'q")
                            val mDebt = monthEndDebt(state.transactions, state.priceByTx, mp)
                            val now = java.time.LocalDate.now()
                            val disp = state.clientName.replaceFirstChar { it.uppercase() }
                            val file = DebtPdf.create(
                                context = context,
                                title = "QARZ XATI — $disp",
                                headerLines = listOf(
                                    "Davr: $monthName ${state.selectedMonth.year}",
                                    "Tayyorlandi: %02d.%02d.%04d".format(now.dayOfMonth, now.monthValue, now.year)
                                ),
                                bodyLines = body,
                                footerLines = listOf(
                                    "$monthName oxirida qarz: ${mDebt.formatMoney()} so'm",
                                    "UMUMIY QARZ (bugun): ${state.debt.formatMoney()} so'm"
                                )
                            )
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Qarz xatini yuborish"))
                        }.onFailure {
                            Toast.makeText(context, "PDF xato: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Qarz xati (PDF)")
                    }
                    IconButton(onClick = { onSetNarx(state.clientName) }) {
                        Icon(
                            Icons.Outlined.AttachMoney,
                            contentDescription = "N narx"
                        )
                    }
                    uz.daftar.app.ui.common.HomeButton()
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val monthPrefix = "%04d-%02d".format(state.selectedMonth.year, state.selectedMonth.monthValue)
            // Tanlangan OY OXIRIDAGI qarz qoldig'i (shu oygacha hammasi hisobida)
            val monthDebt = androidx.compose.runtime.remember(state.transactions, state.priceByTx, state.selectedMonth) {
                monthEndDebt(state.transactions, state.priceByTx, monthPrefix)
            }
            DebtCard(
                debt = state.debt,
                txCount = state.transactions.size,
                monthLabel = MONTHS_UZ[state.selectedMonth.monthValue - 1],
                monthDebt = monthDebt
            )

            // Oy navigatsiyasi ⬅️ May 2026 ➡️
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevMonth() }, modifier = Modifier.size(52.dp)) { Text("⬅️", fontSize = 30.sp) }
                Text(
                    "${MONTHS_UZ[state.selectedMonth.monthValue - 1]} ${state.selectedMonth.year}",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { vm.nextMonth() }, modifier = Modifier.size(52.dp)) { Text("➡️", fontSize = 30.sp) }
            }

            val monthTxs = state.transactions.filter { it.date.startsWith(monthPrefix) }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                monthTxs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bu oyda yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                ) {
                    // Kun bo'yicha guruh (eng yangi tepada)
                    val byDay = monthTxs.groupBy { it.date.take(10) }
                        .toSortedMap(compareByDescending { it })
                    byDay.forEach { (day, dayTxsRaw) ->
                        val dayTxs = dayTxsRaw.sortedBy { it.date }
                        item(key = "day-$day") {
                            Text(
                                "📅 ${day.substring(8, 10)}.${day.substring(5, 7)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(dayTxs, key = { it.id }) { tx ->
                            HistoryRow(
                                tx = tx,
                                unitPrice = state.priceByTx[tx.id],
                                balanceAfter = state.balanceAfterPayment[tx.id],
                                onClick = { onEditTx(tx.id) },
                                onLongClick = { toDelete = tx }
                            )
                        }
                    }
                    // Oylik JAMI
                    item(key = "summary") {
                        MonthSummaryCard(monthTxs, state.priceByTx, state.debt)
                    }
                }
            }

            // ➕ Shu yerning o'zida yozish (ism kerak emas)
            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            AddEntryBar(onSend = { vm.addEntry(it) })
        }
    }

    toDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("O'chirilsinmi?") },
            text = {
                Text("${tx.type.uppercase()}:${tx.amount.formatMoney()} (${tx.date.take(10)}) — bu yozuv o'chiriladi.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTransaction(tx.id)
                    toDelete = null
                }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Bekor") }
            }
        )
    }
}

@Composable
private fun DebtCard(debt: Long, txCount: Int, monthLabel: String, monthDebt: Long) {
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "💳 Qarz",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${debt.formatMoney()} so'm",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (debt > 0) DebtColor else PaidColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$txCount ta yozuv",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "📅 $monthLabel oxirida: ${monthDebt.formatMoney()} so'm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (monthDebt > 0) DebtColor else PaidColor
            )
        }
    }
}

/** Pastdagi yozish paneli — "a5", "p 50000", "05.06 a5 a10" */
@Composable
private fun AddEntryBar(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("a5 · p 50000 · 05.06 a5") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = {
            if (text.isNotBlank()) { onSend(text); text = "" }
        }) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Saqlash")
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    tx: TransactionEntity,
    unitPrice: Double?,
    balanceAfter: Long?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val type = TxType.fromCode(tx.type)
    val isPayment = type == TxType.P
    val mainText = when {
        isPayment -> "P(pul): ${tx.amount.formatMoney()}"
        unitPrice != null -> "${tx.type.uppercase()}: ${tx.amount.formatQty()} × ${unitPrice.formatQty()} = ${(tx.amount * unitPrice).formatMoney()} so'm"
        else -> "${tx.type.uppercase()}: ${tx.amount.formatQty()}"
    }
    // To'lov uchun: → 💳 Qoldi / ✅ 0 / 💚 Ortiq
    val balanceText: String? = if (isPayment && balanceAfter != null) {
        when {
            balanceAfter > 0 -> "→ 💳 Qoldi: ${balanceAfter.formatMoney()}"
            balanceAfter == 0L -> "→ ✅ 0"
            else -> "→ 💚 Ortiq: ${(-balanceAfter).formatMoney()}"
        }
    } else null
    val time = if (tx.date.length >= 16) tx.date.substring(11, 16) else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mainText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPayment) PaidColor else MaterialTheme.colorScheme.onSurface
            )
            if (balanceText != null) {
                Text(
                    balanceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        balanceAfter!! > 0 -> DebtColor
                        balanceAfter == 0L -> PaidColor
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthSummaryCard(
    txs: List<TransactionEntity>,
    priceByTx: Map<Long, Double?>,
    debt: Long
) {
    val byType = txs.groupBy { it.type }.mapValues { (_, l) -> l.sumOf { it.amount } }
    val payTotal = txs.filter { it.type.lowercase() == "p" }.sumOf { it.amount }
    var revenue = 0.0
    for (tx in txs) {
        val t = TxType.fromCode(tx.type) ?: continue
        if (t in setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
            val p = priceByTx[tx.id]
            if (p != null) revenue += tx.amount * p
        }
    }
    Card(
        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("📊 JAMI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            for (t in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                val amt = byType[t.code] ?: 0.0
                if (amt > 0) Text(
                    "${t.label}: ${amt.formatQty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (payTotal > 0) Text(
                "P(pul): ${payTotal.formatMoney()}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "J: ${revenue.formatMoney()} so'm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (debt > 0) "💳 Qarz: ${debt.formatMoney()} so'm" else "✅ Qarz yo'q",
                fontWeight = FontWeight.Bold,
                color = if (debt > 0) DebtColor else PaidColor
            )
        }
    }
}

private val MONTHS_UZ = listOf(
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
)

/** Tanlangan oy OXIRIDAGI qarz qoldig'i (shu oygacha hammasi hisobida) */
private fun monthEndDebt(
    transactions: List<TransactionEntity>,
    priceByTx: Map<Long, Double>,
    monthPrefix: String
): Long {
    var d = 0.0
    for (tx in transactions) {
        if (tx.date.take(7) > monthPrefix) continue
        when (TxType.fromCode(tx.type)) {
            TxType.P -> d -= tx.amount
            TxType.Q -> d += tx.amount
            TxType.A, TxType.B, TxType.C, TxType.D, TxType.K -> {
                val p = priceByTx[tx.id]
                if (p != null) d += tx.amount * p
            }
            else -> {}
        }
    }
    return d.roundToLong()
}
