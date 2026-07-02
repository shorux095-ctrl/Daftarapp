package uz.daftar.app.ui.screen.clienthistory

import uz.daftar.app.core.util.yukRangi
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.pdf.DebtPdf
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import java.util.Locale
import kotlin.math.roundToLong

private val GreenA = Color(0xFF1AA35A)
private val BlueA = Color(0xFF3B6FF6)
private val BlueB = Color(0xFF2451E0)
private val GreenB = Color(0xFF13B86C)
private val DebtRed = Color(0xFFE53935)
private val InkDark = Color(0xFF1A1A1A)
private val InkGray = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClientHistoryScreen(
    onBack: () -> Unit,
    onEditTx: (Long) -> Unit = {},
    onSetNarx: (String) -> Unit = {},
    vm: ClientHistoryViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Tahrir/boshqa ekrandan qaytganda ro'yxatni yangilash (o'zgarish darrov ko'rinsin)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    var toDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    val context = LocalContext.current

    // Har yozuvdan keyingi qoldiq (kelishгan tartibда, butun davr bo'yicha)
    val balanceMap = remember(state.transactions, state.priceByTx) {
        var b = 0.0
        val m = HashMap<Long, Double>()
        state.transactions.sortedBy { it.date }.forEach { tx ->
            when (TxType.fromCode(tx.type)) {
                TxType.P -> b -= tx.amount
                TxType.Q -> b += tx.amount
                TxType.A, TxType.B, TxType.C, TxType.D, TxType.K -> {
                    val p = state.priceByTx[tx.id]; if (p != null) b += tx.amount * p
                }
                else -> {}
            }
            m[tx.id] = b
        }
        m
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(state.clientName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching {
                            val mp = "%04d-%02d".format(state.selectedMonth.year, state.selectedMonth.monthValue)
                            val monthName = MONTHS_UZ[state.selectedMonth.monthValue - 1]
                            val txs = state.transactions.filter { it.date.startsWith(mp) }.sortedBy { it.date }
                            val body = mutableListOf<String>()
                            txs.groupBy { it.date.take(10) }.toSortedMap().forEach { (day, list) ->
                                body.add("${day.substring(8, 10)}.${day.substring(5, 7)}")
                                list.forEach { tx ->
                                    val unitPrice = state.priceByTx[tx.id]
                                    val ln = when {
                                        tx.type.equals("p", true) -> "To'lov (P): ${tx.amount.formatMoney()} so'm"
                                        tx.type.equals("q", true) -> "Qarz (Q): ${tx.amount.formatMoney()} so'm"
                                        unitPrice != null -> "${tx.type.uppercase()}: ${tx.amount.formatQty()} x ${unitPrice.formatQty()} = ${(tx.amount * unitPrice).formatMoney()} so'm"
                                        else -> "${tx.type.uppercase()}: ${tx.amount.formatQty()}"
                                    }
                                    body.add("    $ln")
                                }
                            }
                            if (body.isEmpty()) body.add("Bu oyda yozuv yo'q")
                            val mDebt = monthEndDebt(state.transactions, state.priceByTx, mp)
                            val now = java.time.LocalDate.now()
                            val disp = state.clientName.replaceFirstChar { it.uppercase() }
                            val file = DebtPdf.create(
                                context = context,
                                title = "QARZ XATI — $disp",
                                headerLines = listOf(
                                    "Davr: $monthName ${state.selectedMonth.year}",
                                    "Tayyorlandi: %02d.%02d.%04d".format(now.dayOfMonth, now.monthValue, now.year)
                                ),
                                bodyLines = body,
                                footerLines = listOf(
                                    "$monthName oxirida qarz: ${mDebt.formatMoney()} so'm",
                                    "UMUMIY QARZ (bugun): ${state.debt.formatMoney()} so'm"
                                )
                            )
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Qarz xatini yuborish"))
                        }.onFailure {
                            Toast.makeText(context, "PDF xato: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Qarz xati (PDF)")
                    }
                    IconButton(onClick = { onSetNarx(state.clientName) }) {
                        Icon(Icons.Outlined.AttachMoney, contentDescription = "N narx")
                    }
                    uz.daftar.app.ui.common.HomeButton()
                }
            )
        }
    ) { padding ->
        val monthPrefix = "%04d-%02d".format(state.selectedMonth.year, state.selectedMonth.monthValue)
        val monthLabel = MONTHS_UZ[state.selectedMonth.monthValue - 1]
        val monthDebt = remember(state.transactions, state.priceByTx, state.selectedMonth) {
            monthEndDebt(state.transactions, state.priceByTx, monthPrefix)
        }
        val monthTxs = state.transactions.filter { it.date.startsWith(monthPrefix) }.sortedBy { it.date }

        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // 1) Yashil header
                item("header") {
                    GreenHeader(state.clientName, monthLabel, state.selectedMonth.year, monthDebt)
                }
                // 2) Bo'lim sarlavhasi
                item("lbl") {
                    Text(
                        "📅 KUNLIK YOZUVLAR",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkGray,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
                    )
                }
                // 3) Timeline
                when {
                    state.isLoading -> item("load") {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    monthTxs.isEmpty() -> item("empty") {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("Bu oyda yozuv yo'q", color = InkGray)
                        }
                    }
                    else -> {
                        itemsIndexed(monthTxs, key = { _, tx -> tx.id }) { idx, tx ->
                            TimelineRow(
                                tx = tx,
                                unitPrice = state.priceByTx[tx.id],
                                balance = balanceMap[tx.id] ?: 0.0,
                                onClick = { onEditTx(tx.id) },
                                onLongClick = { toDelete = tx }
                            )
                        }
                        item("jami") { JamiHisobotCard(monthTxs, state.priceByTx, state.debt) }
                    }
                }
                // 4) Oldingi / Keyingi
                item("nav") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NavBtn("← Oldingi", Modifier.weight(1f)) { vm.prevMonth() }
                        NavBtn("Keyingi →", Modifier.weight(1f)) { vm.nextMonth() }
                    }
                }
            }

            if (state.error != null) {
                Text(
                    state.error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (state.debt > 0L) {
                PayButton(debt = state.debt) { amt -> vm.addEntry("p $amt") }
            }
            AddEntryBar(onSend = { vm.addEntry(it) })
        }
    }

    toDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("O'chirilsinmi?") },
            text = { Text("${tx.type.uppercase()}:${tx.amount.formatMoney()} (${tx.date.take(10)}) — bu yozuv o'chiriladi.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTransaction(tx.id); toDelete = null }) {
                    Text("O'chirish", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Bekor") } }
        )
    }
}

@Composable
private fun GreenHeader(name: String, monthLabel: String, year: Int, monthDebt: Long) {
    val disp = name.replaceFirstChar { it.uppercase() }
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(Brush.horizontalGradient(listOf(GreenA, GreenB)))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
    ) {
        Column {
            Text("$disp — $monthLabel $year", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    "$monthLabel qarzi: ${monthDebt.formatMoney()} so'm",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    tx: TransactionEntity,
    unitPrice: Double?,
    balance: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val type = TxType.fromCode(tx.type)
    val isPayment = type == TxType.P
    val isManualDebt = type == TxType.Q
    // YAGONA rang standarti (yukRangi): A=ko'k, B=sariq, C=yashil, P=QIZIL, D=teal, K=pushti, Q=kulrang
    val typeColor = yukRangi(tx.type)
    val desc = when {
        isPayment -> "P(pul): ${tx.amount.formatMoney()}"
        isManualDebt -> "Q(qarz): ${tx.amount.formatMoney()}"
        unitPrice != null -> "${tx.type.uppercase()}: ${tx.amount.formatQty()} × ${unitPrice.formatQty()} = ${(tx.amount * unitPrice).formatMoney()} so'm"
        else -> "${tx.type.uppercase()}: ${tx.amount.formatQty()}"
    }
    val day = if (tx.date.length >= 10) tx.date.substring(8, 10) else "--"
    val monIdx = (if (tx.date.length >= 7) tx.date.substring(5, 7).toIntOrNull() else null) ?: 1
    val monAbbr = MONTHS_UZ[(monIdx - 1).coerceIn(0, 11)].take(4).uppercase()
    val time = if (tx.date.length >= 16) tx.date.substring(11, 16) else ""
    val debtNow = balance.roundToLong()
    val balColor = if (debtNow > 0) DebtRed else GreenB

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline chizig'i + yashil nuqta
        Box(modifier = Modifier.width(24.dp).fillMaxHeight()) {
            Box(modifier = Modifier.width(2.dp).fillMaxHeight().align(Alignment.Center).background(GreenB.copy(alpha = 0.35f)))
            Box(modifier = Modifier.size(13.dp).align(Alignment.Center).clip(CircleShape).background(GreenB))
        }
        Spacer(Modifier.width(8.dp))
        // Rangli kun raqami (tur bo'yicha rang)
        Column(
            modifier = Modifier.width(46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(day, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = typeColor)
            Text(monAbbr, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = typeColor)
        }
        Spacer(Modifier.width(12.dp))
        // Tavsif + vaqt (tur rangida)
        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(desc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = typeColor)
            if (time.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text("🕐 $time", fontSize = 12.sp, color = Color(0xFF9AA0A6))
            }
        }
        Spacer(Modifier.width(8.dp))
        // O'ngда qizil qoldiq
        Column(horizontalAlignment = Alignment.End) {
            Text(debtNow.formatMoney(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = balColor)
            Text("so'm", fontSize = 11.sp, color = balColor)
        }
    }
    HorizontalDivider(color = Color(0x14000000))
}

@Composable
private fun JamiHisobotCard(
    txs: List<TransactionEntity>,
    priceByTx: Map<Long, Double?>,
    debt: Long
) {
    // Har yuk turi bo'yicha: soni (qty) va summasi (money) — Farq KERAK EMAS
    val cargoCodes = listOf("a", "b", "c", "d", "k")
    val perType = cargoCodes.mapNotNull { code ->
        val rows = txs.filter { it.type.equals(code, true) }
        if (rows.isEmpty()) return@mapNotNull null
        val qty = rows.sumOf { it.amount }
        val money = rows.sumOf { r -> val p = priceByTx[r.id]; if (p != null) r.amount * p else 0.0 }
        Triple(code.uppercase(), qty, money)
    }
    val pay = txs.filter { it.type.equals("p", true) }.sumOf { it.amount }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F8F5)).padding(14.dp)
    ) {
        Text("OLINGAN YUK", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkDark,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        if (perType.isEmpty()) {
            Text("Yuk olinmagan", fontSize = 12.sp, color = InkGray,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (c in perType) {
                    JamiCargoCol(c.first, c.second, c.third, cargoColor(c.first), Modifier.weight(1f))
                }
                // Pul (pul olganim) — yuklar yonida, QIZIL
                if (pay > 0.0) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(DebtRed.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Text("P", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DebtRed) }
                        Spacer(Modifier.height(5.dp))
                        Text("Pul", fontSize = 12.sp, color = InkGray)
                        Spacer(Modifier.height(2.dp))
                        Text("${pay.roundToLong().formatMoney()} so'm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DebtRed)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val jamiYuk = perType.sumOf { it.third }
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF374151).copy(alpha = 0.10f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("📦 Jami yuk: ${jamiYuk.roundToLong().formatMoney()} so'm",
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF374151))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DebtRed.copy(alpha = 0.10f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("💵 Pul: ${pay.roundToLong().formatMoney()} so'm",
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DebtRed)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (debt > 0) DebtRed.copy(alpha = 0.10f) else GreenB.copy(alpha = 0.12f))
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (debt > 0) "💳 Qarz: ${debt.formatMoney()} so'm" else "✅ Qarz yo'q",
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (debt > 0) DebtRed else GreenB
            )
        }
    }
}

@Composable
private fun JamiCargoCol(letter: String, qty: Double, money: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text(letter, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color) }
        Spacer(Modifier.height(5.dp))
        Text("${qty.formatQty()} dona", fontSize = 12.sp, color = InkGray)
        Spacer(Modifier.height(2.dp))
        Text("${money.roundToLong().formatMoney()} so'm", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun cargoColor(code: String) = yukRangi(code)

@Composable
private fun NavBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(GreenB.copy(alpha = 0.14f))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = GreenA, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun AddEntryBar(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = { if (text.isNotBlank()) { onSend(text); text = "" } }) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Saqlash")
        }
    }
}

@Composable
private fun PayButton(debt: Long, onPay: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Button(
        onClick = { show = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GreenA),
        shape = RoundedCornerShape(14.dp)
    ) { Text("💵 To'lov qilish (qarzni yopish)") }

    if (show) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("💵 To'lov qilish") },
            text = {
                Column {
                    Text("Joriy qarz: ${debt.formatMoney()} so'm", fontWeight = FontWeight.Bold, color = DebtRed)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("To'lov summasi (so'm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { amount = debt.toString() }) {
                        Text("Hammasini to'lash — ${debt.formatMoney()} so'm")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = amount.trim().replace(" ", "").replace(".", "").toLongOrNull()
                    if (v != null && v > 0) {
                        onPay(v.toString()); show = false
                    }
                }) { Text("Saqlash") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Bekor") } }
        )
    }
}

private val MONTHS_UZ = listOf(
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
)

/** Tanlangan oy OXIRIDAGI qarz qoldig'i (shu oygacha hammasi hisobida) */
private fun monthEndDebt(
    transactions: List<TransactionEntity>,
    priceByTx: Map<Long, Double?>,
    monthPrefix: String
): Long {
    var d = 0.0
    for (tx in transactions) {
        if (tx.date.take(7) > monthPrefix) continue
        when (TxType.fromCode(tx.type)) {
            TxType.P -> d -= tx.amount
            TxType.Q -> d += tx.amount
            TxType.A, TxType.B, TxType.C, TxType.D, TxType.K -> {
                val p = priceByTx[tx.id]; if (p != null) d += tx.amount * p
            }
            else -> {}
        }
    }
    return d.roundToLong()
}
