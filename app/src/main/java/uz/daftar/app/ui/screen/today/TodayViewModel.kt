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
    val isDeleteCommand: Boolean = false
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
    private val getUnitPrices: GetClientUnitPricesUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val filterFlow = MutableStateFlow(Filter.TODAY)
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var suggestJob: Job? = null

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
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false) }
            updateSuggestions("")
            return
        }
        // X-o'chirish komandasi tekshirish ("x" yoki "12.03 x" + ismlar)
        if (isDeleteCommandText(text)) {
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = true) }
            updateSuggestions("")
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
                errorMessage = if (parsedList.isEmpty()) firstError?.error?.message else null
            )
        }
        // Autocomplete — birinchi so'z (ism)
        val firstWord = text.trimStart().substringBefore(' ').substringBefore('\n').trim()
        // Faqat ism yozayotgan paytda ko'rsatamiz (raqamga o'tmagan)
        val onlyName = firstWord.isNotBlank() && firstWord.all { it.isLetter() || it == '\'' }
        updateSuggestions(if (onlyName) firstWord else "")
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

    /** Birinchi qator "x" yoki "12.03 x" / "x 12.03", keyin ismlar bo'lsa true */
    private fun isDeleteCommandText(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return false
        val first = lines[0].lowercase()
        return first == "x" || DELETE_X_RE.matches(first)
    }

    private fun handleDeleteCommand(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return
        val first = lines[0].lowercase()
        // Sanani aniqlash
        val date: LocalDate = run {
            val m = DELETE_X_RE.find(first)
            if (m != null) {
                val dm = m.groupValues[1].ifBlank { m.groupValues[2] }
                try {
                    val parts = dm.split(".")
                    val d = parts[0].toInt()
                    val mo = parts[1].toInt()
                    LocalDate.now().withMonth(mo).withDayOfMonth(d)
                } catch (e: Exception) {
                    LocalDate.now()
                }
            } else LocalDate.now()
        }
        val names = lines.drop(1)
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            var count = 0
            try {
                val start = date.atStartOfDay()
                val end = date.plusDays(1).atStartOfDay()
                val dayTxs = repo.getRange(userId, start, end)
                for (rawName in names) {
                    val cn = DaftarParser.normalizeName(rawName)
                    val ids = dayTxs.filter { it.clientName.lowercase() == cn }.map { it.id }
                    if (ids.isNotEmpty()) {
                        repo.deleteByIds(ids)
                        count += ids.size
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
