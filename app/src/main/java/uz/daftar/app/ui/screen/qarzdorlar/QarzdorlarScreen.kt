package uz.daftar.app.ui.screen.qarzdorlar

import uz.daftar.app.core.util.yukRangi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import uz.daftar.app.core.pdf.DebtPdf
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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("💳 Qarzdorlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    // v150: 📄 PDF — qarzdorlar ro'yxatini ilovasiz ham ochiladigan faylga chiqarish
                    IconButton(onClick = {
                        runCatching {
                            val now = java.time.LocalDate.now()
                            val body = buildList {
                                s.debtors.forEachIndexed { i, d ->
                                    add("${i + 1}. ${d.client.replaceFirstChar { c -> c.uppercase() }}")
                                    add("   Qarz: ${d.debt.formatMoney()} so'm   (${d.daysOverdue} kun)")
                                }
                                if (s.debtors.isEmpty()) add("Qarzdor yo'q")
                            }
                            val file = DebtPdf.create(
                                context = context,
                                title = "QARZDORLAR RO'YXATI",
                                headerLines = listOf(
                                    "Sana: %02d.%02d.%04d".format(now.dayOfMonth, now.monthValue, now.year),
                                    "Tartib: " + if (s.rating) "eng katta qarz yuqorida" else "yangi qarzlar yuqorida",
                                    "Jami: ${s.debtors.size} qarzdor"
                                ),
                                bodyLines = body,
                                footerLines = listOf("JAMI QARZ: ${s.totalDebt.formatMoney()} so'm")
                            )
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Qarzdorlar PDF"))
                        }.onFailure {
                            android.widget.Toast.makeText(context, "PDF xato: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Icon(Icons.Outlined.PictureAsPdf, contentDescription = "PDF") }
                    uz.daftar.app.ui.common.HomeButton()
                }
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
            // v148: 🏆 Reyting / 📅 Kun rejimi
            Row(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) {
                FilterChip(
                    selected = s.rating,
                    onClick = { vm.setRating(true) },
                    label = { Text("🏆 Reyting") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = !s.rating,
                    onClick = { vm.setRating(false) },
                    label = { Text("📅 Kun bo'yicha") }
                )
            }
            Text(
                if (s.rating) "  Eng katta qarz yuqorida" else "  Eng yangi qarzlar (oz kun) yuqorida",
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
                        DebtorRow(i + 1, d, s.rating) { onOpenClient(d.client) }
                        HorizontalDivider(color = Color(0x0F000000))
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DebtorRow(rank: Int, d: OverdueDebtor, rating: Boolean, onClick: () -> Unit) {
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
        // v148: Reyting rejimida — o'rin (🥇🥈🥉 / #4...), Kun rejimida — yuk turi harfi
        val typeColor = yukRangi(d.topType)
        if (rating) {
            val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> null }
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (medal != null) Color(0xFFFFF6DC) else Color(0xFFF0F2F5)),
                contentAlignment = Alignment.Center
            ) {
                if (medal != null) Text(medal, fontSize = 16.sp)
                else Text("#$rank", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF5B6470))
            }
        } else {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(typeColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(d.topType?.uppercase() ?: "$rank", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = typeColor) }
        }
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
