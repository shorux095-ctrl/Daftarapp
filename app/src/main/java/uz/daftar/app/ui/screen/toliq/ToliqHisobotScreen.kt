package uz.daftar.app.ui.screen.toliq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
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

private val PURPLE = Color(0xFF5E50EE)
private val GREEN = Color(0xFF1E9E57)
private val RED = Color(0xFFE53935)
private val INK = Color(0xFF1A1A1A)
private val GRAY = Color(0xFF6B7280)

private fun cargoColor(t: TxType): Color = when (t) {
    TxType.A -> Color(0xFFEF7C3B)
    TxType.B -> Color(0xFFE0A800)
    TxType.C -> Color(0xFF1E9E57)
    TxType.D -> Color(0xFF2D7FF0)
    TxType.K -> Color(0xFF8E5BE0)
    else -> GRAY
}

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Bugun / Shu oy togglelar ──
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Toggle("\uD83D\uDCC5 Bugun", state.mode == 0, Modifier.weight(1f)) { vm.setMode(0) }
                Toggle("\uD83D\uDCC6 Shu oy", state.mode == 1, Modifier.weight(1f)) { vm.setMode(1) }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text("Xato: ${state.error}", color = RED)
            } else {
                val r = state.report ?: return@Column

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.rangeLabel, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = INK,
                        modifier = Modifier.weight(1f))
                    Text("\uD83D\uDDD3\uFE0F", fontSize = 18.sp)
                }

                // ── 1) SOTILGAN YUKLAR ──
                TintCard(Color(0xFFFFF7F1)) {
                    Text("\uD83D\uDCE6 Sotilgan yuklar", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = INK)
                    Spacer(Modifier.height(12.dp))
                    val cargo = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
                        .map { it to (r.totals[it] ?: 0.0) }
                        .filter { it.second != 0.0 }
                    if (cargo.isEmpty()) {
                        Text("Yuk sotilmagan", color = GRAY)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            cargo.forEach { (t, qty) ->
                                CargoCircle(t.code.uppercase(), qty.formatQty(), cargoColor(t))
                            }
                        }
                    }
                }

                // ── 2) PULLAR ──
                TintCard(Color(0xFFF1F9F4)) {
                    Text("\uD83D\uDCB5 Pullar", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = INK)
                    Spacer(Modifier.height(10.dp))
                    MoneyRow("\uD83D\uDCB5 N narx (sotuv)", r.revenue.formatMoney())
                    MoneyRow("\uD83D\uDE9A T narx (tannarx)", r.tCost.formatMoney())
                    MoneyRow(
                        "\uD83D\uDCC8 Yalpi foyda (N\u2212T)", r.grossProfit.formatMoney(),
                        color = if (r.grossProfit >= 0) GREEN else RED
                    )
                    HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Color(0x14000000))
                    MoneyRow("\uD83D\uDCB0 To'langan (P)", r.payments.formatMoney())
                    val q = (r.totals[TxType.Q] ?: 0.0).roundToLong()
                    if (q != 0L) MoneyRow("\uD83D\uDCDD Qarz yozildi (Q)", q.formatMoney())
                    MoneyRow("\uD83D\uDCB8 Rasxod", r.expenses.formatMoney())
                    MoneyRow("\uD83D\uDE9B Yuk rasxodi", r.yukRasxodi.formatMoney(), color = if (r.yukRasxodi > 0) RED else INK)
                    Spacer(Modifier.height(10.dp))
                    // Sof foyda — ajratilgan qator
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (r.profit >= 0) GREEN.copy(alpha = 0.12f) else RED.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (r.profit >= 0) "\u2705 Sof foyda" else "\u274C Sof foyda",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (r.profit >= 0) GREEN else RED
                            )
                            Text(
                                r.profit.formatMoney(), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (r.profit >= 0) GREEN else RED
                            )
                        }
                    }
                }

                // ── 3) MIJOZLAR & YOZUVLAR ──
                TintCard(Color(0xFFF4F2FD)) {
                    Text("\uD83D\uDC65 Mijozlar & Yozuvlar", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = INK)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill("\uD83D\uDC64", "Mijozlar", "${r.clientCount} ta", Modifier.weight(1f))
                        StatPill("\uD83D\uDCDD", "Yozuvlar", "${r.transactionCount} ta", Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Toggle(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val base = modifier.clip(RoundedCornerShape(14.dp))
    val withBg = if (selected) base.background(PURPLE)
    else base.background(Color.White).border(1.dp, Color(0xFFE3E3EA), RoundedCornerShape(14.dp))
    Box(
        modifier = withBg.clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else Color(0xFF555555), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun TintCard(bg: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(16.dp),
        content = content
    )
}

@Composable
private fun CargoCircle(letter: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(54.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(count, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun MoneyRow(label: String, value: String, color: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = INK)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color ?: INK)
    }
}

@Composable
private fun StatPill(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(PURPLE.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 17.sp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 12.sp, color = GRAY)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PURPLE)
        }
    }
}
