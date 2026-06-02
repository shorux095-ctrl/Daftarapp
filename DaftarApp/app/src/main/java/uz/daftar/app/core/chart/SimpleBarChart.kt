package uz.daftar.app.core.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Oddiy ustunli diagramma — tashqi kutubxonasiz, Compose Canvas bilan chizilgan.
 * Vico o'rniga (build xavfini kamaytirish uchun). Keyinroq Vico'ga o'tish mumkin.
 */

data class BarData(
    val label: String,
    val value: Double,
    val color: Color
)

@Composable
fun SimpleBarChart(
    bars: List<BarData>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 180.dp
) {
    if (bars.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(
                "Ma'lumot yo'q",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxValue = max(bars.maxOf { it.value }, 1.0)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // Diagramma maydoni
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(top = 8.dp)
        ) {
            val barCount = bars.size
            val gap = size.width * 0.04f
            val totalGap = gap * (barCount + 1)
            val barWidth = (size.width - totalGap) / barCount
            val chartHeight = size.height

            bars.forEachIndexed { i, bar ->
                val barHeight = (bar.value / maxValue * chartHeight).toFloat()
                val x = gap + i * (barWidth + gap)
                val y = chartHeight - barHeight
                // Ustun
                drawRoundedBar(x, y, barWidth, barHeight, bar.color)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Yorliqlar (label + qiymat)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        bar.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = bar.color
                    )
                    Text(
                        formatShort(bar.value),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRoundedBar(x: Float, y: Float, width: Float, height: Float, color: Color) {
    if (height <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
    )
}

private fun formatShort(v: Double): String {
    val l = v.toLong()
    return when {
        l >= 1_000_000 -> "${l / 1_000_000}M"
        l >= 1_000 -> "${l / 1_000}k"
        else -> l.toString()
    }
}
