package uz.daftar.app.ui.screen.statistika

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatistikaScreen(
    onBack: () -> Unit,
    vm: StatistikaViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📊 Statistika") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Qarz xulosasi (gradient)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFFF7043))))
                    .padding(18.dp)
            ) {
                Text("Jami qarz", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Text("${s.totalDebt.formatMoney()} so'm", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("${s.debtorCount} qarzdor • o'rtacha ${s.avgDebt.formatMoney()} so'm",
                    color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))

            // 3 ta asosiy raqam
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("👥", "Mijozlar", s.totalClients.toString(), Color(0xFFE3F2FD), Modifier.weight(1f))
                StatCard("📦", "Yuklar", s.cargoCount.toString(), Color(0xFFE8F5E9), Modifier.weight(1f))
                StatCard("🧾", "Yozuvlar", s.totalTx.toString(), Color(0xFFFFF3E0), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyCard("💵", "Jami to'lovlar", s.totalPayments, Color(0xFF1565C0), Modifier.weight(1f))
                MoneyCard("📌", "Qo'lda qarz", s.totalManualDebt, Color(0xFFC62828), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            // Eng faol kun
            Surface(color = Color(0xFFF1F8E9), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📅", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Eng faol kun", fontSize = 12.sp, color = Color(0xFF558B2F))
                        Text(s.busiestDay, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF33691E))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Top mijozlar
            Text("🏆 Eng ko'p yuk tashilganlar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            if (s.topClients.isEmpty()) {
                Text("Hali ma'lumot yo'q", color = Color.Gray, fontSize = 13.sp)
            } else {
                s.topClients.forEachIndexed { i, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (i == 0) Color(0xFFFFD54F) else Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("${item.count} marta", fontSize = 13.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Color(0x0F000000))
                }
            }
            if (s.firstDate.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("📆 Birinchi yozuv: ${s.firstDate}", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, bg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Text(label, fontSize = 11.sp, color = Color(0xFF666666), textAlign = TextAlign.Center)
    }
}

@Composable
private fun MoneyCard(emoji: String, label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f)).padding(14.dp)
    ) {
        Text("$emoji $label", fontSize = 12.sp, color = color)
        Spacer(Modifier.height(4.dp))
        Text("${value.formatMoney()} so'm", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
