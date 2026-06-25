package uz.daftar.app.ui.screen.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcScreen(onBack: () -> Unit) {
    var expr by remember { mutableStateOf(CalcStore.expr) }
    var memory by remember { mutableStateOf(CalcStore.memory) }
    LaunchedEffect(expr) { CalcStore.expr = expr }
    LaunchedEffect(memory) { CalcStore.memory = memory }
    val res = remember(expr) { evalExpr(expr) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🧮 Kalkulyator") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                expr.ifBlank { "0" },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontSize = 34.sp,
                maxLines = 2
            )
            Text(
                if (res != null && expr.isNotBlank()) "= ${fmt(res)}" else " ",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary
            )
            if (memory != 0.0) {
                Text(
                    "M = ${fmt(memory)}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(8.dp))

            // Xotira qatori: MC tozalash, MR chaqirish, M+ qo'shish, M- ayirish
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("MC", "MR", "M+", "M-").forEach { mk ->
                    Button(
                        onClick = {
                            when (mk) {
                                "MC" -> memory = 0.0
                                "MR" -> {
                                    val m = if (memory % 1.0 == 0.0) memory.toLong().toString() else memory.toString()
                                    expr += m
                                }
                                "M+" -> { val v = evalExpr(expr); if (v != null) memory += v }
                                "M-" -> { val v = evalExpr(expr); if (v != null) memory -= v }
                            }
                        },
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Text(mk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            val rows = listOf(
                listOf("C", "⌫", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", "00", ".", "=")
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { k ->
                        val isOp = k in listOf("C", "⌫", "%", "÷", "×", "-", "+", "=")
                        Button(
                            onClick = {
                                when (k) {
                                    "C" -> expr = ""
                                    "⌫" -> expr = expr.dropLast(1)
                                    "=" -> if (res != null) expr = plain(res)
                                    else -> expr += k
                                }
                            },
                            modifier = Modifier.weight(1f).height(62.dp),
                            colors = if (isOp) ButtonDefaults.filledTonalButtonColors()
                                     else ButtonDefaults.buttonColors(
                                         containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                         contentColor = MaterialTheme.colorScheme.onSurface
                                     )
                        ) {
                            Text(k, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Natijani chiroyli ko'rsatish: 12 500 yoki 12 500.25 */
private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15)
        String.format(Locale.US, "%,.0f", v).replace(",", " ")
    else String.format(Locale.US, "%,.2f", v).replace(",", " ")

/** "=" bosilganda ifoda o'rniga qo'yiladigan oddiy ko'rinish */
private fun plain(v: Double): String =
    if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString()
    else v.toString()

/** + - × ÷ % (postfiks) — ustunlik bilan hisoblaydi. Xato bo'lsa null. */
private fun evalExpr(s: String): Double? {
    if (s.isBlank()) return null
    // Tokenlash
    val toks = mutableListOf<String>()
    val num = StringBuilder()
    for (ch in s) {
        when {
            ch.isDigit() || ch == '.' -> num.append(ch)
            ch == '+' || ch == '-' || ch == '×' || ch == '÷' || ch == '%' -> {
                if (num.isNotEmpty()) { toks.add(num.toString()); num.clear() }
                toks.add(ch.toString())
            }
            else -> return null
        }
    }
    if (num.isNotEmpty()) toks.add(num.toString())
    if (toks.isEmpty()) return null

    // % postfiks: oldingi sonni 100 ga bo'lish
    val t2 = mutableListOf<String>()
    for (t in toks) {
        if (t == "%") {
            val last = t2.removeLastOrNull()?.toDoubleOrNull() ?: return null
            t2.add((last / 100.0).toString())
        } else t2.add(t)
    }

    val vals = ArrayDeque<Double>()
    val ops = ArrayDeque<Char>()
    fun prec(c: Char) = if (c == '+' || c == '-') 1 else 2
    fun apply(): Boolean {
        if (vals.size < 2 || ops.isEmpty()) return false
        val b = vals.removeLast(); val a = vals.removeLast(); val op = ops.removeLast()
        val r = when (op) {
            '+' -> a + b
            '-' -> a - b
            '×' -> a * b
            else -> if (b == 0.0) return false else a / b
        }
        vals.addLast(r); return true
    }

    var expectNum = true
    var i = 0
    while (i < t2.size) {
        val t = t2[i]
        val d = t.toDoubleOrNull()
        if (d != null) {
            if (!expectNum) return null
            vals.addLast(d); expectNum = false
        } else {
            val c = t[0]
            if (expectNum && c == '-') {
                val nx = t2.getOrNull(i + 1)?.toDoubleOrNull() ?: return null
                vals.addLast(-nx); i++; expectNum = false
            } else {
                if (expectNum) return null
                while (ops.isNotEmpty() && prec(ops.last()) >= prec(c)) { if (!apply()) return null }
                ops.addLast(c); expectNum = true
            }
        }
        i++
    }
    if (expectNum) return null
    while (ops.isNotEmpty()) { if (!apply()) return null }
    val r = vals.lastOrNull() ?: return null
    return if (r.isNaN() || r.isInfinite()) null else r
}

/** Kalkulator holatini navigatsiya orasida saqlaydi (chiqib-kirganda yo'qolmaydi). */
object CalcStore {
    var expr: String = ""
    var memory: Double = 0.0
}
