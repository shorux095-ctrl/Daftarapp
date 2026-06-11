package uz.daftar.app.ui.screen.toliq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.ui.common.HomeButton
import kotlin.math.roundToLong

private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)

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
                title = { Text("\uD83D\uDCCB To'liq hisobot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    IconButton(onClick = vm::load) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Yangilash")
                    }
                    HomeButton()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Bugun / Shu oy ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == 0,
                    onClick = { vm.setMode(0) },
                    label = { Text("\uD83D\uDCC5 Bugun") }
                )
                FilterChip(
                    selected = state.mode == 1,
                    onClick = { vm.setMode(1) },
                    label = { Text("\uD83D\uDCC6 Shu oy") }
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text("Xato: ${state.error}", color = RED)
            } else {
                val r = state.report ?: return@Column

                Text(
                    r.rangeLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── 1) SOTILGAN YUKLAR ──
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("\uD83D\uDCE6 Sotilgan yuklar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        val cargo = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
                            .map { it to (r.totals[it] ?: 0.0) }
                            .filter { it.second != 0.0 }
                        if (cargo.isEmpty()) {
                            Text("Yuk sotilmagan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            cargo.forEach { (t, qty) ->
                                LineRow("\uD83D\uDCE6 ${t.label}", qty.formatQty())
                            }
                        }
                    }
                }

                // ── 2) PULLAR ──
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("\uD83D\uDCB5 Pullar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        LineRow("\uD83D\uDCB5 N narx (sotuv)", r.revenue.formatMoney())
                        LineRow("\uD83D\uDE9A T narx (tannarx)", r.tCost.formatMoney())
                        LineRow(
                            "\uD83D\uDCC8 Yalpi foyda (N\u2212T)",
                            r.grossProfit.formatMoney(),
                            color = if (r.grossProfit >= 0) GREEN else RED
                        )
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LineRow("\uD83D\uDCB0 To'langan (P)", r.payments.formatMoney())
                        val q = (r.totals[TxType.Q] ?: 0.0).roundToLong()
                        if (q != 0L) LineRow("\uD83D\uDCDD Qarz yozildi (Q)", q.formatMoney())
                        LineRow("\uD83D\uDCB8 Rasxod", r.expenses.formatMoney())
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LineRow(
                            "\u2705 Sof foyda",
                            r.profit.formatMoney(),
                            bold = true,
                            color = if (r.profit >= 0) GREEN else RED
                        )
                    }
                }

                // ── 3) STATISTIKA ──
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        LineRow("\uD83D\uDC65 Mijozlar", "${r.clientCount} ta")
                        LineRow("\uD83D\uDCDD Yozuvlar", "${r.transactionCount} ta")
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LineRow(label: String, value: String, bold: Boolean = false, color: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
