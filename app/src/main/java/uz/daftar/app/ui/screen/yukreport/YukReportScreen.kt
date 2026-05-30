package uz.daftar.app.ui.screen.yukreport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import uz.daftar.app.domain.usecase.YukReportRow

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun YukReportScreen(
    onBack: () -> Unit,
    vm: YukReportViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

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

            // ── Tugmalar qatori: Oylik/Yillik + ⬅️➡️ ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = vm::prev) {
                    Text("⬅️", fontSize = 20.sp)
                }
                FilterChip(
                    selected = !state.yearly,
                    onClick = vm::showMonthly,
                    label = { Text("Oylik") }
                )
                FilterChip(
                    selected = state.yearly,
                    onClick = vm::showYearly,
                    label = { Text("📊 Yillik") }
                )
                IconButton(onClick = vm::next) {
                    Text("➡️", fontSize = 20.sp)
                }
            }

            // ── Sarlavha ──
            Text(
                state.report?.title ?: "",
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // ── Jadval sarlavhasi ──
            TableHeader()
            HorizontalDivider()

            when {
                state.isLoading && state.report == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.report == null || state.report!!.rows.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bu davrда yozuv yo'q", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> {
                    val report = state.report!!
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(items = report.rows, key = { it.label }) { row ->
                            TableRow(row)
                        }
                    }
                    HorizontalDivider(thickness = 2.dp)
                    // JAMI
                    JamiRow(report.jamiT, report.jamiN, report.jamiP, report.jamiFarq)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Cell("Sana", weight = 1.1f, bold = true, align = TextAlign.Start)
        Cell("T", weight = 1f, bold = true)
        Cell("N", weight = 1f, bold = true)
        Cell("P", weight = 1f, bold = true)
        Cell("Farq", weight = 1.2f, bold = true)
    }
}

@Composable
private fun TableRow(row: YukReportRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Cell(row.label, weight = 1.1f, align = TextAlign.Start)
        Cell(row.tTotal.toString(), weight = 1f)
        Cell(row.nTotal.toString(), weight = 1f)
        Cell(row.pTotal.toString(), weight = 1f)
        FarqCell(row.farq, weight = 1.2f)
    }
}

@Composable
private fun JamiRow(t: Long, n: Long, p: Long, farq: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Cell("JAMI", weight = 1.1f, bold = true, align = TextAlign.Start)
        Cell(t.toString(), weight = 1f, bold = true)
        Cell(n.toString(), weight = 1f, bold = true)
        Cell(p.toString(), weight = 1f, bold = true)
        FarqCell(farq, weight = 1.2f, bold = true)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
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
private fun androidx.compose.foundation.layout.RowScope.FarqCell(
    farq: Long,
    weight: Float,
    bold: Boolean = false
) {
    val str = if (farq >= 0) "+$farq" else "$farq"
    Text(
        str,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.End,
        color = if (farq >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}
