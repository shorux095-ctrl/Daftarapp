package uz.daftar.app.ui.screen.toliq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatPrice
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.domain.usecase.FullReport
import uz.daftar.app.domain.usecase.FullReportRow

private fun p(v: Double?): String = v?.formatPrice() ?: "—"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ToliqHisobotScreen(
    onBack: () -> Unit,
    vm: ToliqHisobotViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📋 To'liq hisobot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Yangilash") }; uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    "Xato: ${state.error}",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
                state.report != null -> ReportBody(state.report!!)
            }
        }
    }
}

@Composable
private fun ReportBody(r: FullReport) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ── UMUMIY ──
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("📊 Umumiy", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    StatRow("Mijozlar soni", "${r.clientCount}")
                    StatRow(
                        "Jami qarz",
                        r.totalDebt.formatMoney(),
                        valueColor = if (r.totalDebt > 0) Color(0xFFD32F2F)
                        else if (r.totalDebt < 0) Color(0xFF2E7D32) else null
                    )
                    StatRow("Jami to'lov (P)", r.totalPaid.formatMoney(), valueColor = Color(0xFF2E7D32))
                }
            }
        }

        // ── GLOBAL T NARX ──
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("🏷️ Global T narx (tannarx)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A: ${p(r.tA)}   B: ${p(r.tB)}   C: ${p(r.tC)}   D: ${p(r.tD)}   K: ${p(r.tK)}",
                        fontSize = 15.sp
                    )
                }
            }
        }

        // ── YUKLAR JAMI ──
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("📦 Yuklar jami (soni)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A: ${r.qtyA.formatQty()}   B: ${r.qtyB.formatQty()}   C: ${r.qtyC.formatQty()}   D: ${r.qtyD.formatQty()}   K: ${r.qtyK.formatQty()}",
                        fontSize = 15.sp
                    )
                }
            }
        }

        item {
            Text(
                "👥 Mijozlar — qarz + N narx",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }

        items(r.rows) { row -> ClientCard(row) }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ClientCard(row: FullReportRow) {
    val debtColor = when {
        row.debt > 0 -> Color(0xFFD32F2F)
        row.debt < 0 -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.name.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(row.debt.formatMoney(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = debtColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "N: A:${p(row.nA)}  B:${p(row.nB)}  C:${p(row.nC)}  D:${p(row.nD)}  K:${p(row.nK)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
