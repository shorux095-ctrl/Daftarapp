package uz.daftar.app.ui.screen.today

import uz.daftar.app.core.util.formatMoney
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.core.parser.DaftarParser
import uz.daftar.app.core.parser.ParseResult
import uz.daftar.app.core.parser.ParsedEntry
import uz.daftar.app.core.template.TemplateStore
import uz.daftar.app.data.repository.TransactionRepository
import uz.daftar.app.domain.model.Transaction
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.AddTransactionUseCase
import uz.daftar.app.domain.usecase.CalculateDebtUseCase
import uz.daftar.app.domain.usecase.GetClientUnitPricesUseCase
import uz.daftar.app.domain.usecase.DeleteTransactionUseCase
import java.time.LocalDate
import javax.inject.Inject

/** Yuqori filter — qaysi yozuvlar ko'rsatiladi. */
enum class Filter(val label: String) {
    TODAY("Bugun"),
    YESTERDAY("Kecha"),
    YUK_ONLY("Yuk"),
    ALL_WEEK("Hammasi (hafta)")
}

/** Input'дa ism yozilgandа ko'rinadigan jonli tarix preview kartasi */
data class ClientPreview(
    val name: String,
    val debt: Long,
    val transactions: List<uz.daftar.app.data.db.entity.TransactionEntity>,
    val priceByTx: Map<Long, Double?>,
    val balanceAfter: Map<Long, Long>,
    val month: java.time.YearMonth = java.time.YearMonth.now()
)

data class TodayUiState(
    val filter: Filter = Filter.TODAY,
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val totalByType: Map<TxType, Double> = emptyMap(),
    val clientCount: Int = 0,

    /** Pastdagi yozish maydoni */
    val input: String = "",
    val parsed: List<ParsedEntry> = emptyList(),
    val errorMessage: String? = null,
    val isSending: Boolean = false,
    val justSentSummary: String? = null,

    /** Autocomplete takliflar (mijoz ismi) */
    val suggestions: List<String> = emptyList(),

    /** Tanlangan yozuvlar (multi-delete uchun) */
    val selected: Set<Long> = emptySet(),

    /** Har mijozning joriy qarzi (bubble'da ko'rsatish uchun) */
    val debtByClient: Map<String, Long> = emptyMap(),
    /** Har "save" (mijoz|vaqt) uchun o'sha paytdagi kumulyativ qarz */
    val debtBySave: Map<String, Long> = emptyMap(),

    /** Har mijozning narxlari (bubble'da [4.5] ko'rsatish uchun) */
    val priceByClient: Map<String, Map<TxType, Double>> = emptyMap(),

    /** Tezkor shablonlar */
    val templates: List<String> = emptyList(),

    /** Input "x" o'chirish komandasimi (x / 12.03 x + ismlar) */
    val isDeleteCommand: Boolean = false,

    // ───────── Jonli tarix preview (input'дa ism(lar) yozilganda) ─────────
    /** Topilgan mijozlar ro'yxati — har biri alohida tarix kartasiga ega */
    val previews: List<ClientPreview> = emptyList(),

    // ───────── Sana hisoboti (input'дa "bugun" / "kecha" / "15.05" yozilganda) ─────────
    val dateReport: uz.daftar.app.domain.usecase.DateReport? = null,

    // ───────── Matnli hisobot ("shu oy qarz" / "shu oy foyda") ─────────
    val textReport: TextReport? = null,

    // Ko'rinish buyrug'i (sana/tarix/foyda) — SEND bilan "qotirib" qo'yiladi
    val isViewCommand: Boolean = false,
    val pinnedView: Boolean = false,
    val globalPrice: GlobalPriceCmd? = null
) {
    val isSelectionMode: Boolean get() = selected.isNotEmpty()
    val canSend: Boolean get() = (parsed.isNotEmpty() || isDeleteCommand || isViewCommand || globalPrice != null) && !isSending
}

data class TextReport(val title: String, val body: String)
data class GlobalPriceCmd(val group: String, val prices: Map<String, Double>, val date: java.time.LocalDate?)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val addTx: AddTransactionUseCase,
    private val deleteTx: DeleteTransactionUseCase,
    private val calcDebt: CalculateDebtUseCase,
    private val templateStore: TemplateStore,
    private val getUnitPrices: GetClientUnitPricesUseCase,
    private val getHistory: uz.daftar.app.domain.usecase.GetClientHistoryUseCase,
    private val priceDao: uz.daftar.app.data.db.dao.PriceHistoryDao,
    private val getDateReport: uz.daftar.app.domain.usecase.GetDateReportUseCase,
    private val getMonthlyReport: uz.daftar.app.domain.usecase.GetMonthlyReportUseCase,
    private val getMonthClientDebt: uz.daftar.app.domain.usecase.GetMonthClientDebtUseCase,
    private val getClientProfit: uz.daftar.app.domain.usecase.GetClientProfitUseCase,
    private val getOverdue: uz.daftar.app.domain.usecase.GetOverdueDebtorsUseCase,
    private val setGlobalPrice: uz.daftar.app.domain.usecase.SetGlobalPriceUseCase,
    private val setYukNarx: uz.daftar.app.domain.usecase.SetYukNarxUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val filterFlow = MutableStateFlow(Filter.TODAY)
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var suggestJob: Job? = null
    private var previewJob: Job? = null

    // "12.03 x" yoki "x 12.03" formatini tanish
    private val DELETE_X_RE = Regex("""^(\d{1,2}\.\d{1,2})\s+x$|^x\s+(\d{1,2}\.\d{1,2})$""")

    init {
        // Tezkor shablonlarni kuzatish
        viewModelScope.launch {
            templateStore.templates.collectLatest { list ->
                _state.update { it.copy(templates = list) }
            }
        }
        // Filter o'zgarganida — DB observer'i ham o'zgaradi
        viewModelScope.launch {
            filterFlow.flatMapLatest { f ->
                val today = LocalDate.now()
                when (f) {
                    Filter.TODAY -> repo.observeBetween(userId, today, today.plusDays(1))
                    Filter.YESTERDAY -> repo.observeBetween(userId, today.minusDays(1), today)
                    Filter.YUK_ONLY -> repo.observeBetween(userId, today, today.plusDays(1))
                        .map { list -> list.filter { it.type.isYuk } }
                    Filter.ALL_WEEK -> repo.observeBetween(userId, today.minusDays(6), today.plusDays(1))
                }
            }.collectLatest { txs ->
                val totals = txs.groupBy { it.type }
                    .mapValues { (_, l) -> l.sumOf { it.amount } }
                // Har mijozning joriy qarzini hisoblash (bot'day ko'rsatish uchun)
                val clients = txs.map { it.clientName.lowercase() }.distinct()
                val debts = mutableMapOf<String, Long>()
                val prices = mutableMapOf<String, Map<TxType, Double>>()
                for (c in clients) {
                    debts[c] = runCatching { calcDebt(userId, c) }.getOrDefault(0L)
                    prices[c] = runCatching { getUnitPrices(userId, c) }.getOrDefault(emptyMap())
                }
                // Per-save kumulyativ qarz — joriy jami'дан orqaga yurib hisoblaymiz
                val debtBySave = mutableMapOf<String, Long>()
                for (c in clients) {
                    val ctxs = txs.filter { it.clientName.lowercase() == c }
                        .sortedBy { it.date }
                    if (ctxs.isEmpty()) continue
                    val pm = prices[c] ?: emptyMap()
                    fun effect(tx: uz.daftar.app.domain.model.Transaction): Double {
                        return when (tx.type) {
                            TxType.P -> -tx.amount
                            TxType.Q -> tx.amount
                            else -> {
                                val price = tx.tOverride ?: pm[tx.type]
                                if (price != null) tx.amount * price else 0.0
                            }
                        }
                    }
                    var cum = (debts[c] ?: 0L).toDouble()
                    val cumAfter = DoubleArray(ctxs.size)
                    for (i in ctxs.indices.reversed()) {
                        cumAfter[i] = cum
                        cum -= effect(ctxs[i])
                    }
                    for (i in ctxs.indices) {
                        val isLastOfSave = (i == ctxs.size - 1) || (ctxs[i].date != ctxs[i + 1].date)
                        if (isLastOfSave) {
                            debtBySave["$c|${ctxs[i].date}"] = Math.round(cumAfter[i])
                        }
                    }
                }
                _state.update {
                    it.copy(
                        filter = filterFlow.value,
                        isLoading = false,
                        transactions = txs,
                        totalByType = totals,
                        debtByClient = debts,
                        debtBySave = debtBySave,
                        priceByClient = prices,
                        clientCount = clients.size
                    )
                }
            }
        }
    }

    fun setFilter(f: Filter) {
        filterFlow.value = f
        _state.update { it.copy(filter = f) }
    }

    // ────────── Yozish ──────────

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, justSentSummary = null) }
        // Parser preview
        if (text.isBlank()) {
            _state.update {
                if (it.pinnedView) {
                    // Qotirilgan ko'rinish — saqlanadi, faqat input/parse tozalanadi
                    it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false, isViewCommand = false)
                } else {
                    it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false, isViewCommand = false, previews = emptyList(), dateReport = null, textReport = null)
                }
            }
            updateSuggestions("")
            return
        }
        // Yangi matn yozilyapti — pin bekor qilinadi (live preview)
        _state.update { it.copy(pinnedView = false, globalPrice = null) }
        // X-o'chirish komandasi tekshirish ("x" yoki "12.03 x" + ismlar)
        if (isDeleteCommandText(text)) {
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = true, previews = emptyList(), dateReport = null, textReport = null) }
            updateSuggestions("")
            return
        }
        // Global tannarx: "t a10 b20" yoki "t1 a16.5 b1.9" (mijozsiz)
        run {
            val tk = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tk.size >= 2 && (tk[0] == "t" || tk[0] == "t1")) {
                val prices = LinkedHashMap<String, Double>()
                var allOk = true
                for (tok in tk.drop(1)) {
                    if (tok.length >= 2 && tok[0] in "abcdk") {
                        val p = tok.substring(1).replace(",", ".").toDoubleOrNull()
                        if (p != null) prices[tok[0].toString()] = p else { allOk = false; break }
                    } else { allOk = false; break }
                }
                if (allOk && prices.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            parsed = emptyList(), errorMessage = null, isDeleteCommand = false,
                            isViewCommand = false, previews = emptyList(), dateReport = null,
                            textReport = null, globalPrice = GlobalPriceCmd(tk[0], prices, null)
                        )
                    }
                    updateSuggestions("")
                    return
                }
            }
        }
        // "bugun" / "kecha" / "DD.MM" + ixtiyoriy tur filtri ("30.05 a b c")
        val trimmedLow = text.trim().lowercase()

        // "shu oy qarz" / "oy qarz" va "shu oy foyda" / "oy foyda"
        when (trimmedLow) {
            "shu oy qarz", "oy qarz", "shu oy karz", "oy karz" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadMonthDebt()
                return
            }
            "shu oy foyda", "oy foyda" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadMonthProfit()
                return
            }
            "eslatma", "qarz eslatma", "eslatmalar", "muddat" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadOverdue()
                return
            }
        }

        // "<mijoz> foyda" — mijoz foydasi (oylik+yillik)
        run {
            val tk = trimmedLow.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tk.size >= 2 && tk.last() == "foyda") {
                val name = tk.dropLast(1).joinToString(" ")
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadClientProfit(name)
                return
            }
        }

        val tokens = trimmedLow.split(Regex("\\s+")).filter { it.isNotBlank() }
        val baseDate: LocalDate? = if (tokens.isEmpty()) null else when (tokens[0]) {
            "bugun" -> LocalDate.now()
            "kecha" -> LocalDate.now().minusDays(1)
            else -> {
                val m = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?$""").matchEntire(tokens[0])
                if (m != null) runCatching {
                    val d = m.groupValues[1].toInt()
                    val mo = m.groupValues[2].toInt()
                    val yr = m.groupValues[3]
                    val year = when {
                        yr.isEmpty() -> LocalDate.now().year
                        yr.length == 2 -> 2000 + yr.toInt()
                        else -> yr.toInt()
                    }
                    LocalDate.of(year, mo, d)
                }.getOrNull() else null
            }
        }
        if (baseDate != null) {
            val rest = tokens.drop(1)
            // "n" — N narx (sotilgan) rejimi; qolganlari tur filtri
            val useNarx = rest.contains("n")
            val filterLetters = rest.filter { it != "n" }
            // Qolgan tokenlar faqat YAKKA harf (a/b/c/d/k/p/q) yoki "n" bo'lsa — hisobot
            val isReport = rest.isEmpty() || rest.all {
                it == "n" || (it.length == 1 && it[0] in "abcdkpq")
            }
            if (isReport) {
                val types = if (filterLetters.isEmpty()) null else filterLetters.toSet()
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), textReport = null, isViewCommand = true) }
                loadDateReport(baseDate, types, useNarx)
                return
            }
            // Aks holda ("30.05 a10") — bu oddiy yozuv, pastga o'tadi
        }
        // Multi-line — har qator alohida parsing
        val results = text.lines().filter { it.isNotBlank() }.map { DaftarParser.parse(it) }
        val parsedList = results.filterIsInstance<ParseResult.Success>().map { it.entry }
        val firstError = results.filterIsInstance<ParseResult.Failure>().firstOrNull()
        // Faqat ism yozilganmi? (marker yo'q, harfli) — bu tarix ko'rinishi, xato emas
        val markerReCheck = Regex("""(^|\s)[abcdkpqnt](\d|\s|$)""", RegexOption.IGNORE_CASE)
        val isNameOnly = parsedList.isEmpty() && text.lines().any { ln ->
            val l = ln.trim()
            l.length >= 2 && l.any { it.isLetter() } && !markerReCheck.containsMatchIn(l) &&
                l.all { it.isLetterOrDigit() || it == ' ' || it == '\'' || it == '-' }
        }
        _state.update {
            it.copy(
                parsed = parsedList,
                isDeleteCommand = false,
                dateReport = null,
                textReport = null,
                isViewCommand = isNameOnly,
                errorMessage = if (parsedList.isEmpty() && !isNameOnly) firstError?.error?.message else null
            )
        }
        // Autocomplete — birinchi so'z (ism)
        val firstWord = text.trimStart().substringBefore(' ').substringBefore('\n').trim()
        val onlyName = firstWord.isNotBlank() && firstWord.all { it.isLetter() || it == '\'' }
        updateSuggestions(if (onlyName) firstWord else "")

        // Jonli tarix preview — ism yozilsa (parsing yozuv topa olmasa)
        loadPreviewIfName(text)
    }

    /** Tugmadan chaqiriladi — inputni tozalab, sana hisobotini ko'rsatadi */
    fun showDateReportButton(date: LocalDate, useNarx: Boolean = false) {
        _state.update {
            it.copy(input = "", parsed = emptyList(), previews = emptyList(), errorMessage = null, isDeleteCommand = false)
        }
        loadDateReport(date, null, useNarx)
    }

    /** Haftalik hisobot — joriy haftaning dushanbasidan yakshanbasigacha (T narx) */
    fun showWeekReport() {
        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        _state.update {
            it.copy(input = "", parsed = emptyList(), previews = emptyList(), errorMessage = null, isDeleteCommand = false)
        }
        loadDateReport(monday, null, false, sunday)
    }

    /** Sana hisobotини yopish */
    fun clearDateReport() {
        _state.update { it.copy(dateReport = null, pinnedView = false) }
    }

    /** Matnli hisobotни yopish */
    fun clearTextReport() {
        _state.update { it.copy(textReport = null, pinnedView = false) }
    }

    /** Tarix ko'rinishini yopish (X) */
    fun clearPreviews() {
        _state.update { it.copy(previews = emptyList(), pinnedView = false) }
    }

    private var monthJob: Job? = null

    /** "shu oy qarz" — joriy oyda har mijoz: yuk, to'lov, qoldi */
    private fun loadMonthDebt() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val now = LocalDate.now()
                val rep = getMonthClientDebt(userId, now.year, now.monthValue)
                val sb = StringBuilder()
                if (rep.rows.isEmpty()) {
                    sb.append("Bu oyda yozuv yo'q.")
                } else {
                    rep.rows.forEachIndexed { i, r ->
                        sb.append("${i + 1}. ${r.client}\n")
                        sb.append("    yuk: ${r.yuk.formatMoney()}   to'lov: ${r.tolov.formatMoney()}\n")
                        sb.append("    qoldi: ${r.qoldi.formatMoney()}\n")
                    }
                    sb.append("\n──────────\n")
                    sb.append("JAMI yuk:   ${rep.totalYuk.formatMoney()}\n")
                    sb.append("JAMI to'lov: ${rep.totalTolov.formatMoney()}\n")
                    sb.append("JAMI qoldi: ${rep.totalQoldi.formatMoney()} so'm")
                }
                _state.update {
                    it.copy(textReport = TextReport("📅 Shu oy qarz — ${rep.rangeLabel}", sb.toString()))
                }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Noma'lum xato")) }
            }
        }
    }

    /** "shu oy foyda" — joriy oy foydаси */
    private fun loadMonthProfit() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val now = LocalDate.now()
                val r = getMonthlyReport(userId, now.year, now.monthValue)
                val body = buildString {
                    append("💰 Daromad (N): ${r.revenue.formatMoney()}\n")
                    append("📦 Tannarx (T): ${r.tCost.formatMoney()}\n")
                    append("📈 Foyda (N−T): ${r.grossProfit.formatMoney()}\n")
                    append("📤 Rasxod:      ${r.expenses.formatMoney()}\n")
                    append("──────────\n")
                    append("🎯 Sof foyda:   ${r.profit.formatMoney()} so'm")
                }
                _state.update {
                    it.copy(textReport = TextReport("📈 Shu oy foyda — ${r.rangeLabel}", body))
                }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Noma'lum xato")) }
            }
        }
    }

    /** "<mijoz> foyda" — mijozdan ko'rilган foyda (oylik + yillik) */
    private fun loadClientProfit(name: String) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val r = getClientProfit(userId, name)
                val body = buildString {
                    append("Oylik (${r.year}):\n")
                    if (r.monthly.isEmpty()) append("  — yo'q\n")
                    else r.monthly.forEach { (m, v) -> append("  $m: ${v.formatMoney()}\n") }
                    append("\nYillik:\n")
                    if (r.yearly.isEmpty()) append("  — yo'q\n")
                    else r.yearly.forEach { (y, v) -> append("  $y: ${v.formatMoney()}\n") }
                    append("\n──────────\n")
                    append("${r.year} yil jami: ${r.totalThisYear.formatMoney()} so'm")
                }
                _state.update { it.copy(textReport = TextReport("📈 ${r.client} — foyda", body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Noma'lum xato")) }
            }
        }
    }

    /** "eslatma" — muddati o'tgan qarzdorlar (7/14/30/60 kun) */
    private fun loadOverdue() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val list = getOverdue(userId)
                val body = if (list.isEmpty()) {
                    "✅ Muddati o'tgan qarz yo'q."
                } else buildString {
                    fun bucket(title: String, items: List<uz.daftar.app.domain.usecase.OverdueDebtor>) {
                        if (items.isEmpty()) return
                        append("$title\n")
                        items.forEach { d ->
                            append("  • ${d.client} — ${d.debt.formatMoney()} so'm  (${d.daysOverdue} kun)\n")
                        }
                        append("\n")
                    }
                    bucket("🔴 60+ kun", list.filter { it.daysOverdue >= 60 })
                    bucket("🟠 30–59 kun", list.filter { it.daysOverdue in 30..59 })
                    bucket("🟡 14–29 kun", list.filter { it.daysOverdue in 14..29 })
                    bucket("🔵 7–13 kun", list.filter { it.daysOverdue in 7..13 })
                    bucket("⚪ 7 kundan kam", list.filter { it.daysOverdue < 7 })
                    val jami = list.sumOf { it.debt }
                    append("──────────\n")
                    append("JAMI qarz: ${jami.formatMoney()} so'm  (${list.size} mijoz)")
                }
                _state.update { it.copy(textReport = TextReport("⏰ Qarz eslatma", body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Noma'lum xato")) }
            }
        }
    }

    private var reportJob: Job? = null
    private fun loadDateReport(date: LocalDate, types: Set<String>? = null, useNarx: Boolean = false, endDate: LocalDate = date) {
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            try {
                val report = if (endDate != date) {
                    getDateReport.range(userId, date, endDate, types, useNarx)
                } else {
                    getDateReport(userId, date, types, useNarx)
                }
                _state.update { it.copy(dateReport = report) }
            } catch (e: Exception) {
                _state.update { it.copy(dateReport = null, errorMessage = e.message) }
            }
        }
    }

    private fun loadPreviewIfName(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(previews = emptyList()) }
            return
        }
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Marker bormi? (a10, b5, p100, n a20, t a15)
        val markerRe = Regex("""(^|\s)[abcdkpqnt](\d|\s|$)""", RegexOption.IGNORE_CASE)
        // Faqat ism-only qatorlarni olamiz (yuk/narx markeri bo'lmagan, 2+ belgi, kamida 1 harf)
        val nameLines = lines.filter { line ->
            !markerRe.containsMatchIn(line) &&
            line.length >= 2 &&
            line.any { it.isLetter() } &&
            line.all { it.isLetterOrDigit() || it == ' ' || it == '\'' || it == '-' }
        }
        if (nameLines.isEmpty()) {
            _state.update { it.copy(previews = emptyList()) }
            return
        }

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            try {
                // Oldingi previews'dаги oy holatini saqlash (foydalanuvchi ⬅️➡️ bosgan bo'lishi mumkin)
                val existingMonths: Map<String, java.time.YearMonth> =
                    _state.value.previews.associate { it.name.lowercase() to it.month }

                val list = mutableListOf<ClientPreview>()
                for (line in nameLines) {
                    val cn = DaftarParser.normalizeName(line)
                    val history = runCatching { getHistory(userId, cn) }.getOrNull() ?: continue
                    if (history.transactions.isEmpty()) continue
                    val allPrices = priceDao.getAllForClient(userId, cn)
                    val pricesByType = allPrices.groupBy { it.priceType }
                    val priceByTx = mutableMapOf<Long, Double?>()
                    for (tx in history.transactions) {
                        priceByTx[tx.id] = tx.tOverride ?: findPriceAtDate(pricesByType[tx.type], tx.date)
                    }
                    val asc = history.transactions.sortedBy { it.date }
                    var running = 0.0
                    val balAfter = mutableMapOf<Long, Long>()
                    for (tx in asc) {
                        when (tx.type.lowercase()) {
                            "p" -> { running -= tx.amount; balAfter[tx.id] = running.toLong() }
                            "q" -> running += tx.amount
                            else -> { val p = priceByTx[tx.id]; if (p != null) running += tx.amount * p }
                        }
                    }
                    list.add(
                        ClientPreview(
                            name = line,
                            debt = history.debt,
                            transactions = history.transactions.sortedByDescending { tx -> tx.date },
                            priceByTx = priceByTx,
                            balanceAfter = balAfter,
                            month = existingMonths[cn] ?: java.time.YearMonth.now()
                        )
                    )
                }
                _state.update { it.copy(previews = list) }
            } catch (e: Exception) {
                _state.update { it.copy(previews = emptyList()) }
            }
        }
    }

    private fun findPriceAtDate(prices: List<uz.daftar.app.data.db.entity.PriceHistoryEntity>?, atDate: String): Double? {
        if (prices.isNullOrEmpty()) return null
        var best: uz.daftar.app.data.db.entity.PriceHistoryEntity? = null
        for (p in prices.sortedBy { it.date }) {
            if (p.date <= atDate) best = p else break
        }
        return best?.price ?: prices.minByOrNull { it.date }?.price
    }

    /** Bitta preview kartasi uchun oldingi oy */
    fun prevPreviewMonth(name: String) {
        _state.update { s ->
            s.copy(previews = s.previews.map {
                if (it.name == name) it.copy(month = it.month.minusMonths(1)) else it
            })
        }
    }

    /** Bitta preview kartasi uchun keyingi oy */
    fun nextPreviewMonth(name: String) {
        _state.update { s ->
            s.copy(previews = s.previews.map {
                if (it.name == name) it.copy(month = it.month.plusMonths(1)) else it
            })
        }
    }

    private fun updateSuggestions(prefix: String) {
        suggestJob?.cancel()
        if (prefix.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            val list = repo.suggestClients(userId, prefix)
            // Faqat aniq prefiks mos kelmasligini ham qo'shamiz (foydalanuvchi ko'p variantni ko'rishi uchun)
            _state.update { it.copy(suggestions = list.filter { n -> n != prefix.lowercase() }) }
        }
    }

    /** Avtotaklif chip bosildi — input'da birinchi so'zni almashtiramiz */
    fun applySuggestion(name: String) {
        val cur = state.value.input
        val rest = cur.trimStart().substringAfter(' ', missingDelimiterValue = "")
        val newInput = if (rest.isBlank()) "$name " else "$name $rest"
        onInputChange(newInput)
    }

    fun send() {
        val s = state.value
        // X-o'chirish komandasi bo'lsa
        if (s.isDeleteCommand) {
            handleDeleteCommand(s.input)
            return
        }
        // Global tannarx (T / T1) — mijozsiz narx qo'yish
        val gp = s.globalPrice
        if (gp != null) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true) }
                try {
                    setGlobalPrice(userId, gp.group, gp.prices, gp.date)
                    val label = gp.prices.entries.joinToString(", ") { "${it.key.uppercase()}=${it.value}" }
                    _state.update {
                        it.copy(
                            isSending = false, input = "", globalPrice = null,
                            errorMessage = null, suggestions = emptyList(),
                            justSentSummary = "✅ Global ${gp.group.uppercase()} narx: $label"
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(isSending = false, errorMessage = "Xato: ${e.message}") }
                }
            }
            return
        }
        // Ko'rinish buyrug'i (tarix/hisobot/foyda) — yozuv emas: ko'rinishni QOTIRAMIZ
        if (s.parsed.isEmpty() && s.isViewCommand) {
            _state.update {
                it.copy(
                    input = "",
                    isViewCommand = false,
                    pinnedView = true,
                    errorMessage = null,
                    suggestions = emptyList()
                )
            }
            return
        }
        if (s.parsed.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            var savedCount = 0
            try {
                for (entry in s.parsed) {
                    addTx(userId, entry)
                    savedCount++
                }
                _state.update {
                    it.copy(
                        isSending = false,
                        input = "",
                        parsed = emptyList(),
                        isDeleteCommand = false,
                        errorMessage = null,
                        suggestions = emptyList(),
                        // Saqlangach qotirilgan hisobot/tarix yopiladi — yangi yozuv uy ekranida ko'rinsin
                        previews = emptyList(),
                        dateReport = null,
                        textReport = null,
                        pinnedView = false,
                        isViewCommand = false,
                        justSentSummary = "✅ $savedCount ta yozuv saqlandi"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, errorMessage = "Xato: ${e.message}") }
            }
        }
    }

    // ────────── X bilan o'chirish (matn komandasi) ──────────

    /** "x", "DD.MM x", "x DD.MM", "x NAME", "x NAME a10", "DD.MM\nx NAME" — barchasini taniydi */
    private fun isDeleteCommandText(text: String): Boolean {
        val lines = text.lines().map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return false
        val first = lines.first()
        // Eski: "x" yolg'iz yoki "DD.MM x" / "x DD.MM" + keyingi qatorlar
        if (lines.size >= 2 && (first == "x" || DELETE_X_RE.matches(first))) return true
        // Yangi: biror qator "x NAME" bilan boshlansa
        if (lines.any { it.startsWith("x ") }) return true
        // Birinchi qator sana, keyingilar "x NAME"
        if (Regex("""^\d{1,2}\.\d{1,2}$""").matches(first) &&
            lines.drop(1).any { it.startsWith("x ") }) return true
        return false
    }

    private fun handleDeleteCommand(text: String) {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return
        var date: LocalDate = LocalDate.now()

        // Birinchi qatorni tahlil qilish — sana aniqlash, qaerdan boshlash
        val firstLower = rawLines.first().lowercase()
        val datePureMatch = Regex("""^(\d{1,2})\.(\d{1,2})$""").matchEntire(firstLower)
        val dateXMatch = DELETE_X_RE.matchEntire(firstLower)
        val startIdx = when {
            datePureMatch != null -> {
                runCatching {
                    date = LocalDate.now()
                        .withMonth(datePureMatch.groupValues[2].toInt())
                        .withDayOfMonth(datePureMatch.groupValues[1].toInt())
                }
                1
            }
            dateXMatch != null -> {
                val dm = dateXMatch.groupValues[1].ifBlank { dateXMatch.groupValues[2] }
                runCatching {
                    val parts = dm.split(".")
                    date = LocalDate.now()
                        .withMonth(parts[1].toInt())
                        .withDayOfMonth(parts[0].toInt())
                }
                1
            }
            firstLower == "x" -> 1
            else -> 0
        }

        // Har qatorni o'chirish komandasiga ajratamiz: (name, type?, amount?)
        data class DelCmd(val name: String, val type: TxType?, val amount: Double?)
        val deletions = mutableListOf<DelCmd>()
        for (i in startIdx until rawLines.size) {
            val line = rawLines[i].lowercase()
            val rest = if (line.startsWith("x ") || line == "x") {
                line.removePrefix("x").trim()
            } else line.trim()
            if (rest.isEmpty()) continue
            val tokens = rest.split(Regex("\\s+"))
            // Oxirgi token "a10" / "a" / "c3.5" bo'lishi mumkin
            val lastToken = tokens.last()
            val typeAmountMatch = Regex("""^([a-z])(\d+(?:[.,]\d+)?)?$""").matchEntire(lastToken)
            val validTypes = setOf("a", "b", "c", "d", "k", "p", "q")
            if (typeAmountMatch != null && typeAmountMatch.groupValues[1] in validTypes && tokens.size > 1) {
                val typeCode = typeAmountMatch.groupValues[1]
                val type = TxType.fromCode(typeCode)
                val amt = typeAmountMatch.groupValues[2].replace(",", ".").toDoubleOrNull()
                val name = tokens.dropLast(1).joinToString(" ")
                if (name.isNotEmpty()) deletions.add(DelCmd(name, type, amt))
            } else {
                deletions.add(DelCmd(rest, null, null))
            }
        }
        if (deletions.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            var count = 0
            try {
                val start = date.atStartOfDay()
                val end = date.plusDays(1).atStartOfDay()
                val dayTxs = repo.getRange(userId, start, end)
                for (cmd in deletions) {
                    val cn = DaftarParser.normalizeName(cmd.name)
                    var matching = dayTxs.filter { it.clientName.lowercase() == cn }
                    if (cmd.type != null) matching = matching.filter { it.type == cmd.type }
                    if (cmd.amount != null) matching = matching.filter { it.amount == cmd.amount }
                    if (matching.isNotEmpty()) {
                        repo.deleteByIds(matching.map { it.id })
                        count += matching.size
                    }
                }
                _state.update {
                    it.copy(
                        isSending = false,
                        input = "",
                        parsed = emptyList(),
                        isDeleteCommand = false,
                        suggestions = emptyList(),
                        justSentSummary = "🗑 $count ta yozuv o'chirildi (${date.dayOfMonth}.${date.monthValue})"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSending = false, errorMessage = "O'chirishda xato: ${e.message}") }
            }
        }
    }

    fun clearSentSummary() {
        _state.update { it.copy(justSentSummary = null) }
    }

    // ────────── Tanlash va o'chirish ──────────

    fun toggleSelect(id: Long) {
        _state.update {
            if (id in it.selected) it.copy(selected = it.selected - id)
            else it.copy(selected = it.selected + id)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selected = it.transactions.map { tx -> tx.id }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun deleteSelected() {
        val ids = state.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { deleteTx(it) }
                _state.update {
                    it.copy(
                        selected = emptySet(),
                        justSentSummary = "🗑 ${ids.size} ta yozuv o'chirildi"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "O'chirishda xato: ${e.message}") }
            }
        }
    }

    /** Bitta yozuvni o'chirish (swipe uchun) — karzinaga tushadi */
    fun deleteOne(id: Long) {
        viewModelScope.launch {
            try {
                deleteTx(id)
                _state.update { it.copy(justSentSummary = "🗑 O'chirildi (karzinaga)") }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "O'chirishda xato: ${e.message}") }
            }
        }
    }

    // ───── Tezkor shablon ─────
    /** Joriy yozuvni shablon sifatida saqlash */
    fun saveCurrentAsTemplate() {
        val text = state.value.input.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            templateStore.add(text)
            _state.update { it.copy(justSentSummary = "⭐ Shablon saqlandi") }
        }
    }

    /** Shablonni input maydoniga qo'yish */
    fun applyTemplate(text: String) {
        onInputChange(text)
    }

    /** Shablonni o'chirish */
    fun removeTemplate(text: String) {
        viewModelScope.launch { templateStore.remove(text) }
    }
}
