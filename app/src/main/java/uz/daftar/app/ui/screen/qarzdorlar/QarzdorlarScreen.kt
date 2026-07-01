package uz.daftar.app.ui.screen.qarzdorlar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.usecase.OverdueDebtor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QarzdorlarScreen(
    onBack: () -> Unit,
    onOpenClient: (String) -> Unit,
    vm: QarzdorlarViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("💳 Qarzdorlar") },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Xulosa
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Jami qarz", fontSize = 12.sp, color = Color(0xFF888888))
                    Text("${s.totalDebt.formatMoney()} so'm", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFEEC2)).padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("${s.debtors.size} qarzdor", fontWeight = FontWeight.SemiBold, color = Color(0xFF8A6D00)) }
            }
            Text(
                "  Eng yangi qarzlar (oz kun) yuqorida",
                fontSize = 11.sp, color = Color(0xFF9AA0A6),
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )

            if (s.debtors.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("✅ Qarzdor yo'q", fontSize = 16.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(s.debtors, key = { _, d -> d.client }) { i, d ->
                        DebtorRow(i + 1, d) { onOpenClient(d.client) }
                        HorizontalDivider(color = Color(0x0F000000))
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DebtorRow(rank: Int, d: OverdueDebtor, onClick: () -> Unit) {
    // Kun bo'yicha rang: oz kun = yashil, ko'p kun = qizil
    val dayColor = when {
        d.daysOverdue <= 14 -> Color(0xFF2E7D32)
        d.daysOverdue <= 30 -> Color(0xFFEF6C00)
        d.daysOverdue <= 59 -> Color(0xFFD84315)
        else -> Color(0xFFC62828)
    }
    val dayBg = when {
        d.daysOverdue <= 14 -> Color(0xFFE3F7E8)
        d.daysOverdue <= 30 -> Color(0xFFFFF0E0)
        d.daysOverdue <= 59 -> Color(0xFFFFE7DE)
        else -> Color(0xFFFFE0E0)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Yuk turi rangi (asosiy yuk turi): A=yashil, B=sariq, C=ko'k, D/K=binafsha
        val typeColor = when (d.topType) {
            "a" -> Color(0xFF2E7D32); "b" -> Color(0xFFF57F17); "c" -> Color(0xFF1565C0)
            "d", "k" -> Color(0xFF7B1FA2); else -> Color(0xFF9AA0A6)
        }
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(typeColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text(d.topType?.uppercase() ?: "$rank", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = typeColor) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(d.client.replaceFirstChar { it.uppercase() }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            Text("${d.debt.formatMoney()} so'm", fontSize = 14.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(dayBg).padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) { Text("${d.daysOverdue} kun", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = dayColor) }
    }
}
