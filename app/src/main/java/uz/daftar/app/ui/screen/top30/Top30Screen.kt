package uz.daftar.app.ui.screen.top30

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.dao.YukNarxDao
import javax.inject.Inject

/** v183: TOP 30 — oy bo'yicha eng ko'p YUK olganlar / PUL berganlar + FOYDA */

data class TypeStat(val qty: Double = 0.0, val money: Double = 0.0, val foyda: Double = 0.0)

data class TopRow(
    val name: String,
    val qty: Double,       // jami dona (yuk tab)
    val money: Double,     // yuk summasi (N) yoki to'lov summasi
    val foyda: Double,     // (N - T) * dona
    val types: Map<String, TypeStat> = emptyMap()   // v185: tur kesimi (a/b/c/d/k)
)

data class Top30State(
    val month: java.time.YearMonth = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Tashkent")),
    val yearly: Boolean = false,            // v184: Oy / Yil rejimi
    val tab: Int = 0,                       // 0 = 📦 Yuk, 1 = 💵 Pul, 2 = 🆕 Yangi klient
    val typeFilter: String = "",            // v185: Yuk tabida "" = hammasi, "a".."k" = bitta tur
    val yukRows: List<TopRow> = emptyList(),
    val pulRows: List<TopRow> = emptyList(),
    val newRows: List<TopRow> = emptyList(),  // v184: davrda BIRINCHI yozuvi bo'lgan mijozlar
    val isLoading: Boolean = true
)

@HiltViewModel
class Top30ViewModel @Inject constructor(
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val yukDao: YukNarxDao
) : ViewModel() {
    private val _state = MutableStateFlow(Top30State())
    val state = _state.asStateFlow()
    private val userId = 1L

    init { load() }

    fun setTab(t: Int) = _state.update { it.copy(tab = t) }
    fun setTypeFilter(t: String) = _state.update { it.copy(typeFilter = t) }
    fun setYearly(y: Boolean) { _state.update { it.copy(yearly = y) }; load() }
    fun prevMonth() { _state.update { it.copy(month = if (it.yearly) it.month.minusYears(1) else it.month.minusMonths(1)) }; load() }
    fun nextMonth() { _state.update { it.copy(month = if (it.yearly) it.month.plusYears(1) else it.month.plusMonths(1)) }; load() }

    private fun priceAt(list: List<Pair<String, Double>>?, date: String): Double? {
        if (list.isNullOrEmpty()) return null
        val day = date.take(10)
        var best: Double? = null
        for ((d, p) in list) { if (d.take(10) <= day) best = p else break }
        return best ?: list.first().second
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val m = _state.value.month
            val yearly = _state.value.yearly
            val start = (if (yearly) m.atDay(1).withDayOfYear(1) else m.atDay(1)).toString() + " 00:00:00"
            val end = (if (yearly) m.atDay(1).withDayOfYear(1).plusYears(1) else m.plusMonths(1).atDay(1)).toString() + " 00:00:00"
            val result = withContext(Dispatchers.IO) {
                val txs = txDao.getRange(userId, start, end)
                val cargoCodes = setOf("a", "b", "c", "d", "k")

                // Global T narx tarixi (T va T1) — sana bo'yicha
                val allYuk = runCatching { yukDao.getAllGlobal(userId) }.getOrDefault(emptyList())
                val tHist = allYuk.filter { it.priceGroup == "t" }.groupBy { it.type.lowercase() }
                    .mapValues { e -> e.value.sortedBy { it.date }.map { it.date to it.price } }
                val t1Hist = allYuk.filter { it.priceGroup == "t1" }.groupBy { it.type.lowercase() }
                    .mapValues { e -> e.value.sortedBy { it.date }.map { it.date to it.price } }

                // Mijoz N narx tarixi (bir so'rovda hammasi)
                val allPrices = runCatching { priceDao.getAllForUser(userId) }.getOrDefault(emptyList())
                val nHist = allPrices.groupBy { it.clientName.lowercase() to it.priceType.lowercase() }
                    .mapValues { e -> e.value.sortedBy { it.date }.map { it.date to it.price } }

                val yukMap = HashMap<String, TopRow>()
                val pulMap = HashMap<String, Double>()
                for (tx in txs) {
                    val cn = tx.clientName.lowercase()
                    val code = tx.type.lowercase()
                    when {
                        code == "p" -> pulMap[cn] = (pulMap[cn] ?: 0.0) + tx.amount
                        code in cargoCodes -> {
                            val n = priceAt(nHist[cn to code], tx.date)
                            val tGlob = if (tx.costTier == "t1")
                                (priceAt(t1Hist[code], tx.date) ?: priceAt(tHist[code], tx.date))
                            else priceAt(tHist[code], tx.date)
                            val t = tx.tOverride ?: tGlob
                            val money = if (n != null) tx.amount * n else 0.0
                            val f = if (n != null && t != null) tx.amount * (n - t) else 0.0
                            val prev = yukMap[cn] ?: TopRow(cn, 0.0, 0.0, 0.0)
                            val ts = prev.types[code] ?: TypeStat()
                            yukMap[cn] = prev.copy(
                                qty = prev.qty + tx.amount, money = prev.money + money, foyda = prev.foyda + f,
                                types = prev.types + (code to TypeStat(ts.qty + tx.amount, ts.money + money, ts.foyda + f))
                            )
                        }
                    }
                }
                val yukRows = yukMap.values.sortedByDescending { it.money }.take(50)   // v184: TOP 50
                val pulRows = pulMap.entries.sortedByDescending { it.value }.take(50)
                    .map { TopRow(it.key, 0.0, it.value, 0.0) }
                // v188: 💰 FOYDA tabi — HAMMA mijoz, eng ko'p foyda keltirgan yuqorida (aniq hisob)
                val newRows = yukMap.values.sortedByDescending { it.foyda }.take(50)
                Triple(yukRows, pulRows, newRows)
            }
            _state.update { it.copy(yukRows = result.first, pulRows = result.second, newRows = result.third, isLoading = false) }
        }
    }
}

private val MONTHS_T30 = listOf("Yanvar","Fevral","Mart","Aprel","May","Iyun","Iyul","Avgust","Sentabr","Oktabr","Noyabr","Dekabr")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Top30Screen(
    onBack: () -> Unit,
    onOpenClient: (String) -> Unit = {},
    vm: Top30ViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val gold = Color(0xFFF9A825)
    val green = Color(0xFF2E7D32)
    val red = Color(0xFFD32F2F)
    val ink = Color(0xFF1A1A1A)
    val gray = Color(0xFF8A8A8A)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("\uD83C\uDFC6 TOP 50") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Oy tanlash
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = { vm.prevMonth() }) { Text("\u2190") }
                Text(
                    if (s.yearly) "${s.month.year}-yil" else "${MONTHS_T30[s.month.monthValue - 1]} ${s.month.year}",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                FilledTonalButton(onClick = { vm.nextMonth() }) { Text("\u2192") }
            }
            // v184: Oy / Yil rejimi
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = !s.yearly, onClick = { vm.setYearly(false) },
                    label = { Text("Oy") }, modifier = Modifier.weight(1f))
                FilterChip(selected = s.yearly, onClick = { vm.setYearly(true) },
                    label = { Text("Yil") }, modifier = Modifier.weight(1f))
            }
            // Tablar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = s.tab == 0, onClick = { vm.setTab(0) },
                    label = { Text("\uD83D\uDCE6 Yuk", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                FilterChip(selected = s.tab == 1, onClick = { vm.setTab(1) },
                    label = { Text("\uD83D\uDCB5 Pul", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                FilterChip(selected = s.tab == 2, onClick = { vm.setTab(2) },
                    label = { Text("\uD83D\uDCB0 Foyda", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
            }
            // v185: Yuk tabida TUR filtri — qaysi mijoz qaysi turdan ko'p olganini ko'rish
            if (s.tab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = s.typeFilter == "", onClick = { vm.setTypeFilter("") },
                        label = { Text("Hamma", fontSize = 11.sp) }, modifier = Modifier.weight(1.3f))
                    listOf("a", "b", "c", "d", "k").forEach { t ->
                        FilterChip(selected = s.typeFilter == t, onClick = { vm.setTypeFilter(t) },
                            label = { Text(t.uppercase(), fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            var detailRow by remember { mutableStateOf<TopRow?>(null) }
            detailRow?.let { r ->
                AlertDialog(
                    onDismissRequest = { detailRow = null },
                    title = { Text(r.name.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(if (s.yearly) "${s.month.year}-yil bo'yicha:" else "${MONTHS_T30[s.month.monthValue - 1]} oyi bo'yicha:",
                                fontSize = 12.sp, color = gray)
                            Spacer(Modifier.height(8.dp))
                            // v185: QAYSI YUKDAN QANCHA FOYDA — tur kesimi
                            if (r.types.isEmpty()) {
                                Text("Bu davrda yuk olmagan", fontSize = 13.sp, color = gray)
                            } else {
                                r.types.entries.sortedByDescending { it.value.foyda }.forEach { (code, ts) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(code.uppercase(), fontWeight = FontWeight.Bold, color = ink,
                                            modifier = Modifier.width(26.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("${ts.qty.formatQty()} dona \u00b7 ${Math.round(ts.money).toDouble().formatMoney()} so'm", fontSize = 13.sp, color = ink)
                                            Text("foyda: ${Math.round(ts.foyda).toDouble().formatMoney()} so'm",
                                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                                color = if (ts.foyda >= 0) green else red)
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Text("JAMI foyda: ${Math.round(r.foyda).toDouble().formatMoney()} so'm",
                                    fontWeight = FontWeight.Bold, color = if (r.foyda >= 0) green else red)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { val n = r.name; detailRow = null; onOpenClient(n) }) { Text("Tarixni ochish") }
                    },
                    dismissButton = { TextButton(onClick = { detailRow = null }) { Text("Yopish") } }
                )
            }
            if (s.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val rows = when (s.tab) {
                    0 -> if (s.typeFilter.isBlank()) s.yukRows
                         else s.yukRows.mapNotNull { r ->
                             val ts = r.types[s.typeFilter] ?: return@mapNotNull null
                             r.copy(qty = ts.qty, money = ts.money, foyda = ts.foyda)
                         }.sortedByDescending { it.money }
                    1 -> s.pulRows
                    else -> s.newRows
                }
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Bu oyda ma'lumot yo'q", color = gray)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        itemsIndexed(rows, key = { _, r -> r.name }) { i, r ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { detailRow = r },   // v185: dialog — qaysi yukdan qancha foyda
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Reyting doirasi (1-3 medal)
                                    Box(
                                        modifier = Modifier.size(34.dp).clip(CircleShape)
                                            .background(if (i < 3) gold.copy(alpha = 0.18f) else Color(0xFFEDEFF5)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            when (i) { 0 -> "\uD83E\uDD47"; 1 -> "\uD83E\uDD48"; 2 -> "\uD83E\uDD49"; else -> "${i + 1}" },
                                            fontSize = if (i < 3) 16.sp else 13.sp,
                                            fontWeight = FontWeight.Bold, color = ink
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            r.name.replaceFirstChar { it.uppercase() },
                                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink
                                        )
                                        if (s.tab != 1 && r.qty > 0) {
                                            Text("${r.qty.formatQty()} dona", fontSize = 11.sp, color = gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        // v188: Foyda tabida FOYDA katta ko'rinadi
                                        val mainVal = if (s.tab == 2) r.foyda else r.money
                                        Text(
                                            "${Math.round(mainVal).toDouble().formatMoney()} so'm",
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            color = if (s.tab == 1) red else if (mainVal >= 0) green else red
                                        )
                                        if (s.tab == 2) {
                                            Text("yuk: ${Math.round(r.money).toDouble().formatMoney()}",
                                                fontSize = 11.sp, color = gray)
                                        } else if (s.tab != 1) {
                                            val fCol = if (r.foyda >= 0) green else red
                                            Text(
                                                "foyda: ${Math.round(r.foyda).toDouble().formatMoney()}",
                                                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fCol
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}
