package uz.daftar.app.ui.screen.bashorat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter

private val DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BashoratScreen(
    onBack: () -> Unit,
    onOpenClient: (String) -> Unit = {},
    vm: BashoratViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val filtered = remember(state.all, state.query, state.filter) {
        val q = state.query.trim().lowercase()
        state.all.filter { p ->
            (q.isEmpty() || p.clientName.lowercase().contains(q)) &&
                when (state.filter) {
                    PredFilter.ALL -> true
                    PredFilter.OVERDUE -> p.status == PredStatus.OVERDUE
                    PredFilter.TODAY -> p.status == PredStatus.TODAY
                    PredFilter.TOMORROW -> p.status == PredStatus.TOMORROW
                    PredFilter.WEEK -> p.status == PredStatus.TODAY || p.status == PredStatus.TOMORROW || p.status == PredStatus.SOON
                }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🔮 Mijoz bashorati") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            Spacer(Modifier.height(8.dp))

            // Qidiruv
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Mijoz ismi bilan qidirish") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // Tezkor xulosa (gradient sarlavha)
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.horizontalGradient(listOf(Color(0xFF6D5BD0), Color(0xFF8A7BE8)))
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryCol("Kechikkan", state.countOverdue, Color(0xFFFFD2CC))
                        SummaryCol("Bugun", state.countToday, Color(0xFFCFE4FF))
                        SummaryCol("Ertaga", state.countTomorrow, Color(0xFFCDF3DA))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Filtr chiplar (count bilan)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill("Hammasi", state.all.size, state.filter == PredFilter.ALL) { vm.setFilter(PredFilter.ALL) }
                FilterPill("Kechikkan", state.countOverdue, state.filter == PredFilter.OVERDUE) { vm.setFilter(PredFilter.OVERDUE) }
                FilterPill("Bugun", state.countToday, state.filter == PredFilter.TODAY) { vm.setFilter(PredFilter.TODAY) }
                FilterPill("Ertaga", state.countTomorrow, state.filter == PredFilter.TOMORROW) { vm.setFilter(PredFilter.TOMORROW) }
                FilterPill("Bu hafta", state.countWeek, state.filter == PredFilter.WEEK) { vm.setFilter(PredFilter.WEEK) }
            }

            Spacer(Modifier.height(10.dp))

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                filtered.isEmpty() -> {
                    val msg = when {
                        state.query.isNotBlank() -> "🔍 \"${state.query}\" bo'yicha mijoz topilmadi"
                        state.all.isEmpty() -> "Hali bashorat uchun ma'lumot yo'q.\nKamida 3 marta yuk olgan mijozlar bu yerda ko'rinadi."
                        else -> "Bu toifada mijoz yo'q"
                    }
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) {
                        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(filtered, key = { it.clientName }) { p ->
                            PredCard(p) { onOpenClient(p.clientName) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCol(label: String, count: Int, labelColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = labelColor, fontSize = 12.sp)
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFF6D5BD0) else Color(0xFFEFEDF8)
    val fg = if (selected) Color.White else Color(0xFF4A4458)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            "$label ($count)",
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PredCard(p: ClientPrediction, onClick: () -> Unit) {
    val accent = statusColor(p.status)
    val (badgeText, badgeBg) = badgeFor(p)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Text(p.clientName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(3.dp))
                Text(
                    "Har ~${p.avgInterval} kunda • ${p.eventCount} marta" + if (!p.reliable) " • taxminiy" else "",
                    fontSize = 13.sp, color = Color(0xFF6B7280)
                )
                Text(
                    "Oxirgi: ${p.daysSinceLast} kun oldin · ${p.lastDate.format(DMY)}",
                    fontSize = 12.sp, color = Color(0xFF9CA3AF)
                )
            }
            Column(
                modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = badgeBg) {
                    Text(
                        badgeText,
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("→ ${p.nextExpected.format(DMY)}", fontSize = 11.sp, color = Color(0xFF9CA3AF))
            }
        }
    }
}

private fun statusColor(s: PredStatus): Color = when (s) {
    PredStatus.OVERDUE -> Color(0xFFD32F2F)
    PredStatus.TODAY -> Color(0xFF1565C0)
    PredStatus.TOMORROW -> Color(0xFF1B873F)
    PredStatus.SOON -> Color(0xFF9A6B00)
    PredStatus.LATER -> Color(0xFF5B6470)
}

private fun badgeFor(p: ClientPrediction): Pair<String, Color> = when (p.status) {
    PredStatus.OVERDUE -> "Kechikdi ${-p.daysUntilNext} kun" to Color(0xFFFFE1DE)
    PredStatus.TODAY -> "Bugun" to Color(0xFFDBE9FF)
    PredStatus.TOMORROW -> "Ertaga" to Color(0xFFD4F3DE)
    PredStatus.SOON -> "${p.daysUntilNext} kundan keyin" to Color(0xFFFFEFC7)
    PredStatus.LATER -> "${p.daysUntilNext} kundan keyin" to Color(0xFFEAEDF1)
}
