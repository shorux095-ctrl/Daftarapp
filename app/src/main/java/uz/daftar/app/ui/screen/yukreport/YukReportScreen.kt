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
import uz.daftar.app.domain.usecase.YukCountReport
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
private val DOrange = Color(0xFFEA580C)
private val DRing = Color(0xFFF97316)
private val DBg = Color(0xFFFFF3EA)
private val KTeal = Color(0xFF0D9488)
private val KRing = Color(0xFF14B8A6)
private val KBg = Color(0xFFEAFBF8)
private val FarqRed = Color(0xFFDC2626)
private val NeutralBg = Color(0xFFF3F5F8)
private val NeutralRing = Color(0xFF9AA4B2)
private val HeaderBlue = Color(0xFF3366FF)
private val LineGray = Color(0xFFE9ECF1)

// Farq: musbat → YASHIL, manfiy → qizil
private fun farqStr(v: Long): String = if (v > 0) "+${v.formatMoney()}" else v.formatMoney()
private fun farqColor(v: Long): Color = if (v >= 0) NGreen else FarqRed

// A B C D K ranglari: (matn, halqa, fon)
private fun typeColors(t: String): Triple<Color, Color, Color> = when (t) {
    "a" -> Triple(TBlue, TRing, TBg)
    "b" -> Triple(NGreen, NRing, NBg)
    "c" -> Triple(PPurple, PRing, PBg)
    "d" -> Triple(DOrange, DRing, DBg)
    else -> Triple(KTeal, KRing, KBg)
}

private val TYPES = listOf("a", "b", "c", "d", "k")
private val LETTERS = listOf("A", "B", "C", "D", "K")

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

            // ── Toggle qatori (BIR QATOR): Oylik/Yillik + Pul + Soni ──
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Segmented(
                    left = "Oylik", right = "📊 Yillik",
                    rightSelected = state.yearly,
                    onLeft = vm::showMonthly, onRight = vm::showYearly
                )
                PillButton("💰 Pul (T/N/P) ▾", !state.counts, vm::showMoney)
                PillButton("📦 Soni", state.counts, vm::showCounts)
            }

            // ── Oy/Yil navigatsiyasi:  ‹  June 2026  ›  ──
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
                // ===== SONI (ABCDK) — yangi chiroyli dizayn =====
                val cr = state.countReport
                when {
                    state.isLoading && cr == null -> Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    cr == null || cr.rows.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) { Text("Bu davrda yuk yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    else -> {
                        val coverDenom = if (state.yearly) 12 else state.month.lengthOfMonth()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { SoniCardsRow(cr, coverDenom) }
                            item { SoniTableCard(cr) }
                            item { SoniJamiCard(cr) }
                        }
                    }
                }
            } else {
                // ===== PUL (T/N/P) — chiroyli dizayn =====
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

/* ═════════════ PUL (T/N/P) ═════════════ */

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
        RingCard(Modifier.weight(1f), "Farq", farqStr(report.jamiFarq), farqColor(report.jamiFarq), NeutralBg, NeutralRing, null, farqStr(avgFarq), farqColor(avgFarq))
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
        RingCanvas(fraction, ringColor, valueColor)
        Text("O'rtacha / kun", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(avg, color = avgColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun RingCanvas(fraction: Float?, ringColor: Color, textColor: Color, diameter: Int = 62) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter.dp)) {
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
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor
        )
    }
}

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

/* ═════════════ SONI (A B C D K) ═════════════ */

@Composable
private fun SoniCardsRow(cr: YukCountReport, coverDenom: Int) {
    val cd = if (coverDenom > 0) coverDenom else 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TYPES.forEachIndexed { i, t ->
            val (c, r, b) = typeColors(t)
            val total = cr.totals[t] ?: 0.0
            val cov = cr.rows.count { (it.counts[t] ?: 0.0) > 0 }.toFloat() / cd
            SoniCard(Modifier.weight(1f), LETTERS[i], total, c, b, r, cov)
        }
    }
}

@Composable
private fun SoniCard(
    modifier: Modifier,
    letter: String,
    total: Double,
    color: Color,
    bg: Color,
    ringColor: Color,
    fraction: Float
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, ringColor.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(letter, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        RingCanvas(fraction, ringColor, color, diameter = 44)
        Text(
            if (total > 0) total.formatQty() else "—",
            color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun SoniTableCard(cr: YukCountReport) {
    val maxes = TYPES.associateWith { t ->
        (cr.rows.maxOfOrNull { it.counts[t] ?: 0.0 } ?: 0.0).coerceAtLeast(1.0)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineGray, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(HeaderBlue).padding(horizontal = 8.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HCell("Sana", 1.1f, TextAlign.Start)
            LETTERS.forEach { HCell(it, 1f, TextAlign.End) }
        }
        cr.rows.forEachIndexed { i, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label, modifier = Modifier.weight(1.1f),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
                TYPES.forEach { t ->
                    val v = row.counts[t] ?: 0.0
                    val (c, _, _) = typeColors(t)
                    SoniCell(v, maxes[t] ?: 1.0, c, 1f)
                }
            }
            if (i < cr.rows.size - 1) HorizontalDivider(color = LineGray)
        }
    }
}

@Composable
private fun RowScope.SoniCell(value: Double, max: Double, color: Color, weight: Float) {
    val frac = (value / max).coerceIn(0.0, 1.0).toFloat()
    Column(
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp)
    ) {
        Text(
            if (value > 0) value.formatQty() else "—",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End, maxLines = 1, softWrap = false
        )
        if (value > 0) {
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(frac).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(color)
                )
            }
        }
    }
}

@Composable
private fun SoniJamiCard(cr: YukCountReport) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFEFF3FF))
            .border(1.dp, HeaderBlue.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("∑", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        TYPES.forEachIndexed { i, t ->
            val (c, _, _) = typeColors(t)
            val v = cr.totals[t] ?: 0.0
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (v > 0) v.formatQty() else "—",
                    color = c, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false
                )
                Text(LETTERS[i], color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

/* ═════════════ Umumiy kichik yordamchilar ═════════════ */

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
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color(0xFF374151),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
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
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color(0xFF1F2937), fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
    }
}
