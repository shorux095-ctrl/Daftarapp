package uz.daftar.app.ui.screen.yukreport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.domain.usecase.YukReport

/* ───────────── Ranglar ───────────── */
private val TBlue = Color(0xFF2563EB)
private val TRing = Color(0xFF3B82F6)
private val TBg = Color(0xFFEFF4FF)
private val NGreen = Color(0xFF16A34A)
private val NRing = Color(0xFF22C55E)
private val NBg = Color(0xFFECFBF1)
private val PPurple = Color(0xFF7C3AED)
private val PRing = Color(0xFF8B5CF6)
private val PBg = Color(0xFFF3EFFE)
private val FarqRed = Color(0xFFDC2626)
private val FarqBg = Color(0xFFFFF1F1)
private val HeaderBlue = Color(0xFF3366FF)
private val LineGray = Color(0xFFE9ECF1)

private fun farqStr(v: Long): String = if (v > 0) "+${v.formatMoney()}" else v.formatMoney()
private fun farqColor(v: Long): Color = if (v >= 0) TBlue else FarqRed

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YukReportScreen(
    onBack: () -> Unit,
    vm: YukReportViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    val periodLabel = if (state.yearly) state.year.toString()
    else state.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) +
        " " + state.month.year

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("📦 Yuk hisoboti") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Toggle qatori: Oylik/Yillik + Pul/Soni ──
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Segmented(
                    left = "Oylik", right = "📊 Yillik",
                    rightSelected = state.yearly,
                    onLeft = vm::showMonthly, onRight = vm::showYearly
                )
                PillButton("💰 Pul (T/N/P) ▾", !state.counts, vm::showMoney)
                PillButton("📦 Soni (ABCDK)", state.counts, vm::showCounts)
            }

            // ── Oy/Yil navigatsiyasi:  ‹  May 2026  ›  ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavSquare(Icons.Filled.KeyboardArrowLeft, vm::prev)
                Spacer(Modifier.width(18.dp))
                Text(periodLabel, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                NavSquare(Icons.Filled.KeyboardArrowRight, vm::next)
            }

            if (state.counts) {
                // ===== SONI (ABCDK) — eski ko'rinish (o'zgartirilmagan) =====
                Text(
                    state.countReport?.title ?: "",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CountHeader()
                HorizontalDivider()
                val cr = state.countReport
                when {
                    state.isLoading && cr == null -> Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    cr == null || cr.rows.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { Text("Bu davrda yuk yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(items = cr.rows, key = { it.label }) { row -> CountRow(row) }
                        }
                        HorizontalDivider(thickness = 2.dp)
                        CountTotalRow(cr.totals)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                // ===== PUL (T/N/P) — yangi chiroyli dizayn =====
                when {
                    state.isLoading && state.report == null -> Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    state.report == null || state.report!!.rows.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { Text("Bu davrda yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    else -> {
                        val report = state.report!!
                        val dayCount = report.rows.size
                        val coverDenom = if (state.yearly) 12 else state.month.lengthOfMonth()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { CardsRow(report, dayCount, coverDenom) }
                            item { StatsCard(report) }
                            item { TableCard(report) }
                            item { JamiCard(report) }
                        }
                    }
                }
            }
        }
    }
}

/* ───────────── Yuqori 4 ta karta (ring) ───────────── */
@Composable
private fun CardsRow(report: YukReport, dayCount: Int, coverDenom: Int) {
    val d = if (dayCount > 0) dayCount else 1
    val avgT = report.jamiT / d
    val avgN = report.jamiN / d
    val avgP = report.jamiP / d
    val avgFarq = report.jamiFarq / d
    val cd = if (coverDenom > 0) coverDenom else 1
    val tCov = report.rows.count { it.tTotal > 0 }.toFloat() / cd
    val nCov = report.rows.count { it.nTotal > 0 }.toFloat() / cd
    val pCov = report.rows.count { it.pTotal > 0 }.toFloat() / cd

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RingCard(Modifier.weight(1f), "T", report.jamiT.formatMoney(), TBlue, TBg, TRing, tCov, avgT.formatMoney(), TBlue)
        RingCard(Modifier.weight(1f), "N", report.jamiN.formatMoney(), NGreen, NBg, NRing, nCov, avgN.formatMoney(), NGreen)
        RingCard(Modifier.weight(1f), "P", report.jamiP.formatMoney(), PPurple, PBg, PRing, pCov, avgP.formatMoney(), PPurple)
        RingCard(Modifier.weight(1f), "Farq", farqStr(report.jamiFarq), farqColor(report.jamiFarq), FarqBg, FarqRed, null, farqStr(avgFarq), farqColor(avgFarq))
    }
}

@Composable
private fun RingCard(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color,
    bg: Color,
    ringColor: Color,
    fraction: Float?,
    avg: String,
    avgColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, ringColor.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(label, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(62.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val tl = Offset(inset, inset)
                drawArc(
                    color = ringColor.copy(alpha = 0.16f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = tl, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (fraction != null) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false, topLeft = tl, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                if (fraction != null) "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%" else "—",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor
            )
        }
        Text("O'rtacha / kun", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(avg, color = avgColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/* ───────────── Statistika qatori ───────────── */
@Composable
private fun StatsCard(report: YukReport) {
    val posDays = report.rows.count { it.farq > 0 }
    val negDays = report.rows.count { it.farq < 0 }
    val maxRow = report.rows.maxByOrNull { it.farq }
    val minRow = report.rows.minByOrNull { it.farq }
    val maxF = maxRow?.farq ?: 0L
    val minF = minRow?.farq ?: 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, LineGray, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(NGreen),
                contentAlignment = Alignment.Center
            ) { Text("↗", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Column {
                Text("Ijobiy kunlar", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text("$posDays kun", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier.weight(1.1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(FarqRed),
                contentAlignment = Alignment.Center
            ) { Text("↘", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Column {
                Text("Salbiy kunlar", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text("$negDays kun", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1.2f)) {
            Text("Eng katta ijobiy farq", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(
                if (maxF > 0) "+${maxF.formatMoney()}" else "—",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NGreen, maxLines = 1, softWrap = false
            )
            if (maxF > 0) Text(maxRow?.label ?: "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1.2f)) {
            Text("Eng katta salbiy farq", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(
                if (minF < 0) minF.formatMoney() else "—",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FarqRed, maxLines = 1, softWrap = false
            )
            if (minF < 0) Text(minRow?.label ?: "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ───────────── Jadval (chiziqli) ───────────── */
@Composable
private fun TableCard(report: YukReport) {
    val maxT = (report.rows.maxOfOrNull { it.tTotal } ?: 0L).coerceAtLeast(1L)
    val maxN = (report.rows.maxOfOrNull { it.nTotal } ?: 0L).coerceAtLeast(1L)
    val maxP = (report.rows.maxOfOrNull { it.pTotal } ?: 0L).coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineGray, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(HeaderBlue).padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HCell("Sana", 1.0f, TextAlign.Start)
            HCell("T", 1.4f, TextAlign.Start)
            HCell("N", 1.4f, TextAlign.Start)
            HCell("P", 1.4f, TextAlign.Start)
            HCell("Farq", 1.05f, TextAlign.End)
        }
        report.rows.forEachIndexed { i, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label, modifier = Modifier.weight(1.0f),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
                BarCell(row.tTotal, maxT, TRing, 1.4f)
                BarCell(row.nTotal, maxN, NRing, 1.4f)
                BarCell(row.pTotal, maxP, PRing, 1.4f)
                Text(
                    farqStr(row.farq), modifier = Modifier.weight(1.05f),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End, color = farqColor(row.farq), maxLines = 1, softWrap = false
                )
            }
            if (i < report.rows.size - 1) HorizontalDivider(color = LineGray)
        }
    }
}

@Composable
private fun RowScope.BarCell(value: Long, max: Long, color: Color, weight: Float) {
    val frac = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.weight(weight).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            value.formatMoney(), modifier = Modifier.width(40.dp),
            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End, maxLines = 1, softWrap = false
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.weight(1f).height(6.dp)
                .clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.14f))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(frac).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}

@Composable
private fun RowScope.HCell(text: String, weight: Float, align: TextAlign) {
    Text(
        text, modifier = Modifier.weight(weight),
        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = align
    )
}

/* ───────────── JAMI karta ───────────── */
@Composable
private fun JamiCard(report: YukReport) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFEFF3FF))
            .border(1.dp, HeaderBlue.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("JAMI", modifier = Modifier.width(54.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        JamiCol(Modifier.weight(1f), "T", report.jamiT.formatMoney(), TBlue)
        JamiCol(Modifier.weight(1f), "N", report.jamiN.formatMoney(), NGreen)
        JamiCol(Modifier.weight(1f), "P", report.jamiP.formatMoney(), PPurple)
        JamiCol(Modifier.weight(1f), "Farq", farqStr(report.jamiFarq), farqColor(report.jamiFarq))
    }
}

@Composable
private fun JamiCol(modifier: Modifier, label: String, value: String, color: Color) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

/* ───────────── Kichik yordamchilar ───────────── */
@Composable
private fun NavSquare(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .border(1.dp, LineGray, RoundedCornerShape(12.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Segmented(
    left: String, right: String,
    rightSelected: Boolean,
    onLeft: () -> Unit, onRight: () -> Unit
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFFEFF1F5)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SegItem(left, !rightSelected, onLeft)
        SegItem(right, rightSelected, onRight)
    }
}

@Composable
private fun SegItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TBlue else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color(0xFF374151),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun PillButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Color(0xFFEFF4FF) else Color.White)
            .border(1.dp, if (selected) TBlue.copy(alpha = 0.4f) else Color(0xFFE2E5EA), RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color(0xFF1F2937), fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
    }
}

/* ───────────── SONI (ABCDK) — eski yordamchilar (o'zgartirilmagan) ───────────── */
@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    align: TextAlign = TextAlign.End
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align
    )
}

@Composable
private fun CountHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Cell("Sana", weight = 1.2f, bold = true, align = TextAlign.Start)
        Cell("A", weight = 1f, bold = true)
        Cell("B", weight = 1f, bold = true)
        Cell("C", weight = 1f, bold = true)
        Cell("D", weight = 1f, bold = true)
        Cell("K", weight = 1f, bold = true)
    }
}

@Composable
private fun CountRow(row: uz.daftar.app.domain.usecase.YukCountRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Cell(row.label, weight = 1.2f, align = TextAlign.Start)
        for (t in listOf("a", "b", "c", "d", "k")) {
            val v = row.counts[t] ?: 0.0
            Cell(if (v > 0) fmtCount(v) else "—", weight = 1f)
        }
    }
}

@Composable
private fun CountTotalRow(totals: Map<String, Double>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Cell("∑", weight = 1.2f, bold = true, align = TextAlign.Start)
        for (t in listOf("a", "b", "c", "d", "k")) {
            val v = totals[t] ?: 0.0
            Cell(if (v > 0) fmtCount(v) else "—", weight = 1f, bold = true)
        }
    }
}

private fun fmtCount(v: Double): String = v.formatQty()
