package uz.daftar.app.ui.screen.today

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
    val dateReport: uz.daftar.app.domain.usecase.DateReport? = null
) {
    val isSelectionMode: Boolean get() = selected.isNotEmpty()
    val canSend: Boolean get() = (parsed.isNotEmpty() || isDeleteCommand) && !isSending
}

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
    private val getDateReport: uz.daftar.app.domain.usecase.GetDateReportUseCase
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
                _state.update {
                    it.copy(
                        filter = filterFlow.value,
                        isLoading = false,
                        transactions = txs,
                        totalByType = totals,
                        debtByClient = debts,
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
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false, previews = emptyList(), dateReport = null) }
            updateSuggestions("")
            return
        }
        // X-o'chirish komandasi tekshirish ("x" yoki "12.03 x" + ismlar)
        if (isDeleteCommandText(text)) {
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = true, previews = emptyList(), dateReport = null) }
            updateSuggestions("")
            return
        }
        // "bugun" / "kecha" / "DD.MM" yoki "DD.MM.YY" — sana hisoboti
        val trimmedLow = text.trim().lowercase()
        val dateForReport: LocalDate? = when {
            trimmedLow == "bugun" -> LocalDate.now()
            trimmedLow == "kecha" -> LocalDate.now().minusDays(1)
            else -> {
                val m = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?$""").matchEntire(trimmedLow)
                if (m != null) {
                    runCatching {
                        val d = m.groupValues[1].toInt()
                        val mo = m.groupValues[2].toInt()
                        val yr = m.groupValues[3]
                        val year = when {
                            yr.isEmpty() -> LocalDate.now().year
                            yr.length == 2 -> 2000 + yr.toInt()
                            else -> yr.toInt()
                        }
                        LocalDate.of(year, mo, d)
                    }.getOrNull()
                } else null
            }
        }
        if (dateForReport != null) {
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList()) }
            loadDateReport(dateForReport)
            return
        }
        // Multi-line — har qator alohida parsing
        val results = text.lines().filter { it.isNotBlank() }.map { DaftarParser.parse(it) }
        val parsedList = results.filterIsInstance<ParseResult.Success>().map { it.entry }
        val firstError = results.filterIsInstance<ParseResult.Failure>().firstOrNull()
        _state.update {
            it.copy(
                parsed = parsedList,
                isDeleteCommand = false,
                dateReport = null,
                errorMessage = if (parsedList.isEmpty()) firstError?.error?.message else null
            )
        }
        // Autocomplete — birinchi so'z (ism)
        val firstWord = text.trimStart().substringBefore(' ').substringBefore('\n').trim()
        val onlyName = firstWord.isNotBlank() && firstWord.all { it.isLetter() || it == '\'' }
        updateSuggestions(if (onlyName) firstWord else "")

        // Jonli tarix preview — ism yozilsa (parsing yozuv topa olmasa)
        loadPreviewIfName(text)
    }

    private var reportJob: Job? = null
    private fun loadDateReport(date: LocalDate) {
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            try {
                val report = getDateReport(userId, date)
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
        // Faqat ism-only qatorlarni olamiz (yuk/narx markeri bo'lmagan, 2+ harf)
        val nameLines = lines.filter { line ->
            !markerRe.containsMatchIn(line) &&
            line.length >= 2 &&
            line.all { it.isLetter() || it == ' ' || it == '\'' || it == '-' }
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
