package uz.daftar.app.ui.screen.clienthistory

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.util.Locale

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
            DebtCard(debt = state.debt, txCount = state.transactions.size)

            // Oy navigatsiyasi ⬅️ May 2026 ➡️
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevMonth() }) { Text("⬅️", style = MaterialTheme.typography.titleLarge) }
                Text(
                    "${MONTHS_UZ[state.selectedMonth.monthValue - 1]} ${state.selectedMonth.year}",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { vm.nextMonth() }) { Text("➡️", style = MaterialTheme.typography.titleLarge) }
            }

            val monthPrefix = "%04d-%02d".format(state.selectedMonth.year, state.selectedMonth.monthValue)
            val monthTxs = state.transactions.filter { it.date.startsWith(monthPrefix) }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                monthTxs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bu oyda yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    // Kun bo'yicha guruh (eng yangi tepada)
                    val byDay = monthTxs.groupBy { it.date.take(10) }
                        .toSortedMap(compareByDescending { it })
                    byDay.forEach { (day, dayTxs) ->
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
private fun DebtCard(debt: Long, txCount: Int) {
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
        unitPrice != null -> "${tx.type.uppercase()}: ${tx.amount.formatMoney()} × ${unitPrice.formatMoney()} = ${(tx.amount * unitPrice).formatMoney()} so'm"
        else -> "${tx.type.uppercase()}: ${tx.amount.formatMoney()}"
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
                    "${t.label}: ${amt.formatMoney()}",
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
