package uz.daftar.app.ui.screen.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.ui.common.HomeButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val green = Color(0xFF2E7D32)
    val red = Color(0xFFC62828)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📊 Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { HomeButton() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Davr tanlash
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.period == DashPeriod.MONTH,
                    onClick = { vm.setPeriod(DashPeriod.MONTH) },
                    label = { Text("📅 Oy (kunlik)") }
                )
                FilterChip(
                    selected = state.period == DashPeriod.YEAR,
                    onClick = { vm.setPeriod(DashPeriod.YEAR) },
                    label = { Text("🗓 Yil (oylik)") }
                )
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Xulosa kartalar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Foyda", state.totalFoyda, if (state.totalFoyda >= 0) green else red, Modifier.weight(1f))
                StatCard("Savdo", state.totalSavdo, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard("To'lov", state.totalTolov, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Foyda grafigi  •  ${state.title}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            // Grafik
            if (state.points.all { it.foyda == 0L }) {
                Text(
                    "Bu davrda ma'lumot yo'q.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                BarChart(
                    points = state.points,
                    positiveColor = green,
                    negativeColor = red,
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            // Ro'yxat
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.points, key = { it.label }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(p.label, fontFamily = FontFamily.Monospace)
                        Text(
                            (if (p.foyda >= 0) "+" else "") + p.foyda.formatMoney(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (p.foyda >= 0) green else red
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value.formatMoney(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BarChart(
    points: List<DashPoint>,
    positiveColor: Color,
    negativeColor: Color,
    modifier: Modifier = Modifier
) {
    val maxAbs = (points.maxOfOrNull { kotlin.math.abs(it.foyda) } ?: 1L).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        val n = points.size
        if (n == 0) return@Canvas
        val gap = size.width * 0.02f
        val barW = (size.width - gap * (n + 1)) / n
        val zeroY = size.height / 2f
        points.forEachIndexed { i, p ->
            val x = gap + i * (barW + gap)
            val h = (kotlin.math.abs(p.foyda).toFloat() / maxAbs) * (size.height / 2f - 4f)
            val top = if (p.foyda >= 0) zeroY - h else zeroY
            drawRect(
                color = if (p.foyda >= 0) positiveColor else negativeColor,
                topLeft = Offset(x, top),
                size = Size(barW, h.coerceAtLeast(1f))
            )
        }
        // 0 chizig'i
        drawRect(
            color = Color.Gray,
            topLeft = Offset(0f, zeroY - 0.5f),
            size = Size(size.width, 1f)
        )
    }
}
