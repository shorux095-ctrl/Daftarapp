package uz.daftar.app.ui.screen.grafik

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.usecase.MonthPoint

private val BLUE = Color(0xFF1976D2)
private val GREEN = Color(0xFF2E7D32)
private val REDC = Color(0xFFD32F2F)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GrafikScreen(
    onBack: () -> Unit,
    vm: GrafikViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📈 Grafik") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, contentDescription = "Yangilash") }
                    uz.daftar.app.ui.common.HomeButton()
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    "Xato: ${state.error}",
                    Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
                else -> Content(state, vm)
            }
        }
    }
}

@Composable
private fun Content(state: GrafikState, vm: GrafikViewModel) {
    val points = state.points
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Davr tanlash
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.months == 6, onClick = { vm.setMonths(6) }, label = { Text("6 oy") })
            FilterChip(selected = state.months == 12, onClick = { vm.setMonths(12) }, label = { Text("12 oy") })
        }

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(BLUE, "Daromad")
            LegendDot(GREEN, "Foyda")
        }

        // Grafik
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                val maxV = remember(points) {
                    val m = points.flatMap { listOf(it.revenue, it.profit) }.maxOrNull() ?: 0L
                    maxOf(m, 1L).toFloat()
                }
                Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                    val n = points.size
                    if (n == 0) return@Canvas
                    val groupW = size.width / n
                    val barW = groupW * 0.28f
                    val gap = groupW * 0.06f
                    val h = size.height
                    points.forEachIndexed { i, p ->
                        val cx = groupW * i + groupW / 2f
                        val rh = (p.revenue.coerceAtLeast(0L).toFloat() / maxV) * (h * 0.92f)
                        drawRect(BLUE, Offset(cx - barW - gap / 2f, h - rh), Size(barW, rh))
                        val ph = (p.profit.coerceAtLeast(0L).toFloat() / maxV) * (h * 0.92f)
                        drawRect(GREEN, Offset(cx + gap / 2f, h - ph), Size(barW, ph))
                    }
                }
                Spacer(Modifier.height(4.dp))
                // X o'qi yorliqlari
                Row(Modifier.fillMaxWidth()) {
                    points.forEach { p ->
                        Text(
                            p.label,
                            Modifier.weight(1f),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Aniq qiymatlar
        Text("Oylik qiymatlar", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        points.reversed().forEach { p -> MonthRow(p) }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).padding(0.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawRect(color) }
        }
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun MonthRow(p: MonthPoint) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${monthName(p.month)} ${p.year}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daromad", fontSize = 13.sp, color = BLUE)
                Text(p.revenue.formatMoney(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BLUE)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Foyda", fontSize = 13.sp, color = if (p.profit < 0) REDC else GREEN)
                Text(p.profit.formatMoney(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (p.profit < 0) REDC else GREEN)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("To'lov (P)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.payments.formatMoney(), fontSize = 13.sp)
            }
        }
    }
}

private val MONTHS = listOf("Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun", "Iyul", "Avgust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr")
private fun monthName(m: Int): String = MONTHS.getOrElse(m - 1) { m.toString() }
