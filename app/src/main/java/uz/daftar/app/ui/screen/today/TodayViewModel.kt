package uz.daftar.app.ui.screen.today

import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.domain.usecase.DeleteToKarzinaUseCase
import uz.daftar.app.core.util.formatQty
import uz.daftar.app.core.util.formatPrice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
    val debtorCount: Int = 0,   // v176: toolbar 💳 yonidagi son
    val scrollTick: Long = 0L,  // v181: karta ochilganda pastga MAJBURIY scroll (hajm o'zgarmasa ham)
    val isLoading: Boolean = true,
    val restored: Boolean = false,
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
    val quickFills: List<String> = emptyList(),   // mijozning oxirgi yuk/puli — tez to'ldirish

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
    /** 🎤 Ovoz orqali yozuv — HA/YO'Q tasdiq kutilmoqda */
    val voiceConfirm: String? = null,

    /** Input "x" o'chirish komandasimi (x / 12.03 x + ismlar) */
    val isDeleteCommand: Boolean = false,
    /** "delete 02.06" — kun bo'yicha o'chirish (Send bosilganda tasdiq so'raladi) */
    val deleteAllDate: java.time.LocalDate? = null,
    /** Tasdiq oynasi ko'rsatilishi kerak bo'lgan sana (Ha/Yo'q) */
    val confirmDeleteDate: java.time.LocalDate? = null,
    /** "ochir ali" — mijozning butun tarixini o'chirish (Send → tasdiq) */
    val deleteClientName: String? = null,
    /** Tasdiq oynasi ko'rsatilishi kerak bo'lgan mijoz (Ha/Yo'q) */
    val confirmDeleteClient: String? = null,
    /** "r100 gaz" — kutilayotgan rasxod (Send → saqlash) */
    val rasxodAmount: Double? = null,
    val rasxodNote: String = "",
    /** Tarix kartasida "Qarzni yopish" tasdig'i (mijoz nomi + qarz) */
    val confirmCloseDebt: Pair<String, Long>? = null,

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
    val globalPrice: GlobalPriceCmd? = null,
    val t1set: List<T1SetOp>? = null,
    val aiQuery: String? = null,
    val isEditUndo: Boolean = false,

    /** Bot-uslubidagi chat oqimi — har yozuv/javob alohida xabar */
    val chat: List<ChatItem> = emptyList()
) {
    val isSelectionMode: Boolean get() = selected.isNotEmpty()
    val canSend: Boolean get() = (parsed.isNotEmpty() || isDeleteCommand || isViewCommand || globalPrice != null || t1set != null || aiQuery != null || isEditUndo || deleteAllDate != null || deleteClientName != null || rasxodAmount != null) && !isSending
}

data class TextReport(val title: String, val body: String)
data class GlobalPriceCmd(val group: String, val prices: Map<String, Double>, val date: java.time.LocalDate?)
data class T1SetOp(val client: String, val type: String?, val start: java.time.LocalDate, val end: java.time.LocalDate)

/** "✅ Saqlandi" kartasi uchun ma'lumot */
@Immutable data class SavedLine(val type: String, val main: String, val sub: String)
@Immutable data class SavedInfo(val name: String, val dateLabel: String, val timeLabel: String, val lines: List<SavedLine>, val debt: Long)

/** Chat oqimidagi bitta xabar */
@Immutable
sealed interface ChatItem {
    val id: Long
    val ts: Long
    /** Foydalanuvchi yozgan matn (o'ng tomon) */
    @Immutable data class User(override val id: Long, val text: String, override val ts: Long = System.currentTimeMillis()) : ChatItem
    /** Oddiy javob matni — ✅ Saqlandi, narx yangilandi, global narx, xato (chap tomon) */
    @Immutable data class Info(override val id: Long, val text: String, override val ts: Long = System.currentTimeMillis()) : ChatItem
    /** Sana hisoboti (chap) */
    @Immutable data class DateRep(override val id: Long, val report: uz.daftar.app.domain.usecase.DateReport, override val ts: Long = System.currentTimeMillis()) : ChatItem
    /** Matnli hisobot: foyda/qarz/eslatma (chap) */
    @Immutable data class TextRep(override val id: Long, val report: TextReport, override val ts: Long = System.currentTimeMillis()) : ChatItem
    /** Mijoz tarixi (chap) */
    @Immutable data class History(override val id: Long, val preview: ClientPreview, override val ts: Long = System.currentTimeMillis()) : ChatItem
    @Immutable data class DebtRep(override val id: Long, val debtors: List<uz.daftar.app.domain.usecase.OverdueDebtor>, override val ts: Long = System.currentTimeMillis()) : ChatItem
    /** ✅ Saqlandi — chiroyli karta (chap) */
    @Immutable data class Saved(override val id: Long, val info: SavedInfo, override val ts: Long = System.currentTimeMillis()) : ChatItem
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val addTx: AddTransactionUseCase,
    private val delToKarzina: DeleteToKarzinaUseCase,
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
    private val setT1Tier: uz.daftar.app.domain.usecase.SetT1TierUseCase,
    private val gptService: uz.daftar.app.core.ai.GptService,
    private val aiSettings: uz.daftar.app.core.ai.AiSettings,
    private val undoLast: uz.daftar.app.domain.usecase.UndoLastUseCase,
    private val editByMatch: uz.daftar.app.domain.usecase.EditByMatchUseCase,
    private val chatStore: uz.daftar.app.core.chat.ChatStore,
    private val setYukNarx: uz.daftar.app.domain.usecase.SetYukNarxUseCase,
    private val getCurrentYukNarx: uz.daftar.app.domain.usecase.GetCurrentYukNarxUseCase,
    private val addRasxod: uz.daftar.app.domain.usecase.AddRasxodUseCase,
    private val getRasxodRange: uz.daftar.app.domain.usecase.GetRasxodRangeUseCase,
    private val getRasxodTotal: uz.daftar.app.domain.usecase.GetRasxodTotalUseCase,
    private val importOldDb: uz.daftar.app.domain.usecase.ImportOldDbUseCase
) : ViewModel() {

    private val userId: Long = 1L

    // v155: butun ilova bo'ylab yagona zona (Asia/Tashkent) — sana yozishda telefon zonasiga bog'liq bo'lmaslik uchun
    private val APP_ZONE = java.time.ZoneId.of("Asia/Tashkent")
    private fun today(): LocalDate = LocalDate.now(APP_ZONE)
    private fun nowStamp(): String =
        java.time.LocalDateTime.now(APP_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    private val filterFlow = MutableStateFlow(Filter.TODAY)
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var suggestJob: Job? = null
    private var previewJob: Job? = null

    // "12.03 x" yoki "x 12.03" formatini tanish
    private val DELETE_X_RE = Regex("""^(\d{1,2}\.\d{1,2})\s+x$|^x\s+(\d{1,2}\.\d{1,2})$""")

    private var restored = false

    init {
        restoreChat()
        // Har qanday tranzaksiya o'zgarsa (edit/o'chirish ham) — History kartalarni jonli DB bilan yangilash
        viewModelScope.launch {
            repo.observeBetween(userId, java.time.LocalDate.of(2000, 1, 1), java.time.LocalDate.of(2100, 1, 1))
                .collectLatest {
                    getOverdue.invalidate()   // har yozuv o'zgarganda qarz keshini yangilaymiz (eskirmasin)
                    getDateReport.invalidate()   // hisobot keshini ham yangilaymiz
                    kotlinx.coroutines.delay(300)   // ketma-ket saqlashlarni jamlaymiz
                    refreshHistoryCards()
                    refreshDebtorCount()   // v176: qarz o'zgarsa — toolbar soni ham yangilanadi
                }
        }
        // v153: NARX o'zgarishini alohida kuzatamiz — "n c4.1" faqat price_history'ni o'zgartiradi,
        // transactions o'zgarmaydi, shuning uchun oldingi kuzatuvchi hisobotni yangilamas edi (narx eski ko'rinardi).
        viewModelScope.launch {
            priceDao.observePriceCount(userId)
                .drop(1)   // dastlabki qiymat — yangilanish emas
                .collectLatest {
                    getOverdue.invalidate()
                    getDateReport.invalidate()
                    kotlinx.coroutines.delay(300)
                    refreshHistoryCards()
                }
        }
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
                val clientCount = txs.map { it.clientName.lowercase() }.distinct().size
                // TEZLIK: debtByClient/debtBySave/priceByClient olib tashlandi —
                // UI'da hech qayerda o'qilmasdi, lekin har mijoz uchun 2 DB so'rovi
                // (calcDebt + getUnitPrices) ochilishni juda sekinlashtirardi.
                _state.update {
                    it.copy(
                        filter = filterFlow.value,
                        isLoading = false,
                        transactions = txs,
                        totalByType = totals,
                        clientCount = clientCount
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

    private var chatIdCounter = 0L
    private fun nextChatId(): Long = ++chatIdCounter
    private fun appendChat(vararg items: ChatItem) {
        _state.update { it.copy(chat = it.chat + items.toList()) }
        persistChat()
    }

    /** Chatni telefonga saqlaydi (matn ko'rinishida). */
    private fun persistChat() {
        // TEZLIK: chat cheksiz o'smasin — faqat oxirgi 120 yozuv saqlanadi (eski data bazada qoladi)
        val full = _state.value.chat
        val snapshot = if (full.size > 120) full.takeLast(120) else full
        viewModelScope.launch {
            runCatching { chatStore.save(serializeChat(snapshot)) }
        }
    }

    /** Chatdagi History kartalarni JONLI DB bilan qayta yasaydi (edit/o'chirishdan keyin eski qiymat qolmasin). */
    /** 🔄 Bosh ekran yangilash — kartalarni jonli DB bilan qayta yasaydi. */
    fun refresh() {
        viewModelScope.launch {
            // Dastlabki yuklash tugamaguncha kutamiz (widjet yozuvi 2 marta qo'shilmasin / sakramasin)
            if (!restored) return@launch
            // Avval widjet yozuvlarini chatga qo'shamiz, keyin kartalarni yangilaymiz
            val pend = runCatching { chatStore.drainPending() }.getOrDefault(emptyList())
            for (line in pend) {
                appendChat(widgetSavedItem(line))
            }
            if (pend.isNotEmpty()) persistChat()
            refreshHistoryCards()
            // v172: Mijozlar/Qarzdorlar'dan bosilgan mijoz — YANGI KARTA sifatida ochiladi
            runCatching { chatStore.consumePendingOpenClient() }.getOrNull()?.let { openClientHistory(it) }
            refreshDebtorCount()   // v176
            // v160: fondan qaytganda (yoki 08:00 kelganda) ham kechki hisobot tekshiriladi
            runCatching { maybeShowAutoReports() }
            // v158: fondan qaytganda (yoki 10:00 kelganda) ham eslatma tekshiriladi.
            runCatching { ensureDebtReminder() }
        }
    }

    /** Widjetdan qo'shilgan yozuvlarni chatda "✅ Saqlandi" sifatida ko'rsatish. */
    private suspend fun drainPendingWidget() {
        val pend = runCatching { chatStore.drainPending() }.getOrDefault(emptyList())
        if (pend.isEmpty()) return
        for (line in pend) {
            appendChat(widgetSavedItem(line))
        }
        persistChat()
    }

    /** v145: widjet yozuvi ham ilovadagi kabi CHIROYLI KARTA bo'ladi, vaqt yonida "widjet" yozuvi bilan. */
    private suspend fun widgetSavedItem(line: String): ChatItem {
        val entry = (runCatching { DaftarParser.parse(line) }.getOrNull() as? ParseResult.Success)?.entry
        if (entry == null || !entry.isValid)
            return ChatItem.Info(nextChatId(), "✅ Saqlandi (widjet):\n$line")
        val cn = entry.clientName.lowercase()
        val prices = runCatching { getUnitPrices(userId, cn) }.getOrDefault(emptyMap())
        val debt = runCatching { calcDebt(userId, cn) }.getOrDefault(0L)
        val lines = entry.items.map { (t, amt) ->
            when (t) {
                TxType.P -> SavedLine("p", "P: ${amt.formatMoney()}", "Pul kirimi")
                TxType.Q -> SavedLine("q", "Q: ${amt.formatMoney()}", "Qo'lda qarz")
                else -> {
                    val p = prices[t]
                    if (p != null) SavedLine(t.code, "${t.code.uppercase()}: ${amt.formatQty()} × ${p.formatQty()} = ${(amt * p).formatMoney()} so'm", "Yuk")
                    else SavedLine(t.code, "${t.code.uppercase()}: ${amt.formatQty()}", "Narx yo'q")
                }
            }
        }
        val info = SavedInfo(
            entry.clientName.replaceFirstChar { it.uppercase() },
            (entry.date ?: java.time.LocalDate.now()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")),
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " · 📲 widjet",
            lines, debt
        )
        return ChatItem.Saved(nextChatId(), info)
    }

    private fun refreshHistoryCards() {
        viewModelScope.launch {
            val histories = _state.value.chat.filterIsInstance<ChatItem.History>()
            if (histories.isEmpty()) return@launch
            // TEZLIK: har tarix kartasini PARALLEL hisoblaymiz (sekvensial emas)
            val freshById = HashMap<Long, ClientPreview>()
            histories.map { h ->
                async { h.id to runCatching { buildClientPreview(h.preview.name, h.preview.month) }.getOrNull() }
            }.awaitAll().forEach { (id, preview) -> if (preview != null) freshById[id] = preview }
            // ATOMAR yangilash: orada kelgan xabarlar ("✅ Saqlandi" va h.k.) YO'QOLMAYDI
            _state.update { st ->
                st.copy(chat = st.chat.map { item ->
                    if (item is ChatItem.History) {
                        val fresh = freshById[item.id]
                        if (fresh != null) ChatItem.History(item.id, fresh, item.ts) else item
                    } else item
                })
            }
            persistChat()
        }
    }

    private fun serializeChat(chat: List<ChatItem>): String {
        val arr = org.json.JSONArray()
        for (c in chat) {
            val o = org.json.JSONObject()
            o.put("ts", c.ts)
            when (c) {
                is ChatItem.User -> { o.put("k", "u"); o.put("t", c.text) }
                is ChatItem.Info -> { o.put("k", "i"); o.put("t", c.text) }
                is ChatItem.TextRep -> { o.put("k", "i"); o.put("t", "${c.report.title}\n${c.report.body}") }
                is ChatItem.DateRep -> {
                    // Bitta kunlik hisobot — sana saqlanib, ochilganda to'liq tortiladi (tarixdek)
                    if (c.report.title.contains("–")) {
                        o.put("k", "i"); o.put("t", dateReportText(c.report))  // haftalik/oylik — matn
                    } else {
                        o.put("k", "d")
                        o.put("date", c.report.date.toString())
                        o.put("narx", c.report.useNarx)
                    }
                }
                is ChatItem.History -> {
                    o.put("k", "h")
                    o.put("name", c.preview.name)
                    o.put("month", c.preview.month.toString())
                }
                is ChatItem.DebtRep -> { o.put("k", "debt") }
                is ChatItem.Saved -> {
                    // v140: karta MA'LUMOT sifatida saqlanadi — qayta ochilganda ham chiroyli karta chiqadi
                    o.put("k", "s")
                    o.put("name", c.info.name)
                    o.put("dl", c.info.dateLabel)
                    o.put("tl", c.info.timeLabel)
                    o.put("debt", c.info.debt)
                    val la = org.json.JSONArray()
                    c.info.lines.forEach { ln ->
                        val lo = org.json.JSONObject()
                        lo.put("t", ln.type); lo.put("m", ln.main); lo.put("s", ln.sub)
                        la.put(lo)
                    }
                    o.put("lines", la)
                }
            }
            arr.put(o)
        }
        return arr.toString()
    }

    /** Sana hisobotini to'liq matnga aylantiradi (saqlash uchun — yo'qolmasin). */
    private fun dateReportText(r: uz.daftar.app.domain.usecase.DateReport): String {
        val sb = StringBuilder()
        sb.append("📅 ${r.title}")
        r.clientLines.forEachIndexed { i, cl ->
            val parts = cl.entries.joinToString("  ") { e ->
                val p = if (e.price != null) " [${e.price.formatPrice()}]" else ""
                "${e.type.code.uppercase()}:${e.amount.formatQty()}$p"
            }
            sb.append("\n${i + 1}. ${cl.clientName}  $parts")
        }
        sb.append("\n— JAMI —")
        for ((type, amt) in r.totalsByType) {
            if (amt == 0.0) continue
            val rev = r.revenueByType[type] ?: 0.0
            sb.append("\n${type.code.uppercase()} ${amt.formatQty()} = ${rev.toLong().formatMoney()} so'm")
        }
        sb.append("\nDaromad: ${r.totalRevenue.toLong().formatMoney()} so'm")
        sb.append("\nTo'lov: ${r.totalPayments.toLong().formatMoney()} so'm")
        return sb.toString()
    }

    /** Saqlangan chatni tiklaydi. Tarix to'liq karta sifatida qayta tortiladi. */
    private suspend fun deserializeChat(json: String): List<ChatItem> = kotlinx.coroutines.coroutineScope {
        val arr = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return@coroutineScope emptyList()
        // Faqat oxirgi 120 ta yozuvni tiklaymiz (juda katta tarix sekinlashtirmasin / oq ekran bo'lmasin)
        val startAt = if (arr.length() > 120) arr.length() - 120 else 0
        // TEZLIK: har karta uchun DB so'rovlar KETMA-KET emas, PARALLEL ishlaydi (tartib saqlanadi).
        // ID'lar tartibda oldindan beriladi — parallel bo'lsa ham chat tartibi buzilmaydi.
        val deferreds = (startAt until arr.length()).map { i ->
            val id = nextChatId()
            async {
                // Har bir element alohida himoyalangan — bittasi buzuq bo'lsa, qolganlari tiklanadi
                runCatching {
                    val o = arr.getJSONObject(i)
                    val k = o.optString("k", if (o.has("u")) { if (o.optBoolean("u")) "u" else "i" } else "i")
                    val ts = o.optLong("ts", System.currentTimeMillis())
                    when (k) {
                        "h" -> {
                            val name = o.optString("name", "")
                            val month = runCatching { java.time.YearMonth.parse(o.optString("month")) }.getOrNull()
                            if (name.isNotBlank()) {
                                val cp = runCatching { buildClientPreview(name, month) }.getOrNull()
                                if (cp != null) ChatItem.History(id, cp, ts)
                                else ChatItem.Info(id, "👤 ${name.replaceFirstChar { it.uppercase() }} — tarix", ts)
                            } else null
                        }
                        "d" -> {
                            val ds = o.optString("date")
                            val narx = o.optBoolean("narx", false)
                            val date = runCatching { LocalDate.parse(ds) }.getOrNull()
                            if (date != null) {
                                val r = runCatching { getDateReport(userId, date, null, narx) }.getOrNull()
                                if (r != null) ChatItem.DateRep(id, r, ts) else null
                            } else null
                        }
                        "u" -> { val t = o.optString("t"); if (t.isNotBlank()) ChatItem.User(id, t, ts) else null }
                        "s" -> {
                            // v140: Saqlandi kartasi to'liq tiklanadi
                            val name = o.optString("name")
                            if (name.isBlank()) null else {
                                val la = o.optJSONArray("lines")
                                val lines = mutableListOf<SavedLine>()
                                if (la != null) for (j in 0 until la.length()) {
                                    val lo = la.optJSONObject(j) ?: continue
                                    lines.add(SavedLine(lo.optString("t", "c"), lo.optString("m"), lo.optString("s")))
                                }
                                ChatItem.Saved(id, SavedInfo(name, o.optString("dl"), o.optString("tl"), lines, o.optLong("debt", 0L)), ts)
                            }
                        }
                        "debt" -> {
                            val l = runCatching { getOverdue(userId).filter { it.daysOverdue >= 10 } }.getOrNull()
                            if (!l.isNullOrEmpty()) ChatItem.DebtRep(id, l, ts) else null
                        }
                        else -> { val t = o.optString("t"); if (t.isNotBlank()) ChatItem.Info(id, t, ts) else null }
                    }
                }.getOrNull()
            }
        }
        deferreds.awaitAll().filterNotNull()
    }

    /** Ilova ochilganda saqlangan chatni tiklaydi (qayta generatsiya yo'q). */
    private fun restoreChat() {
        viewModelScope.launch {
            // v149: XAVFSIZ REJIM — ochilishda 2 marta qulagan bo'lsa, chat tarixi O'TKAZIB yuboriladi
            // (buzilgan bitta karta ilovani butunlay qulflay olmasin). Baza (yozuvlar) BUT UN saqlangan.
            val safeMode = runCatching { chatStore.consumeSafeMode() }.getOrDefault(false)
            if (safeMode) {
                appendChat(
                    ChatItem.Info(
                        nextChatId(),
                        "⚠️ Xavfsiz rejim: ilova 2 marta ketma-ket qulagani uchun chat tarixi vaqtincha o'tkazib yuborildi.\n" +
                        "Yozuvlaringiz (baza) to'liq saqlangan. Muammo sababi: Sozlamalar → 🐞 Xato loglari → 📤 Ulashish."
                    )
                )
                _state.update { it.copy(restored = true) }
                restored = true
                runCatching { ensureDebtReminder() }
                return@launch
            }
            // Og'ir disk o'qish + JSON parse — fon thread'da (Main qotmasin, tez ochilsin)
            val list = withContext(Dispatchers.Default) {
                val json = runCatching { chatStore.load() }.getOrDefault("")
                val full = if (json.isNotBlank()) runCatching { deserializeChat(json) }.getOrDefault(emptyList()) else emptyList()
                if (full.size > 120) full.takeLast(120) else full   // TEZLIK: faqat oxirgi 120 yozuv
            }
            if (list.isNotEmpty()) _state.update { it.copy(chat = list) }
            _state.update { it.copy(restored = true) }   // chat tayyor (yoki bo'sh) — endi ko'rsatish mumkin
            runCatching { maybeShowAutoReports() }
            drainPendingWidget()
            // refreshHistoryCards() OLIB TASHLANDI: deserializeChat endi kartalarni yangi (jonli DB) yasaydi,
            // jonli o'zgarishlarni observer kuzatadi — bu yerda takroriy so'rov shart emas (tezroq, miltillamaydi)
            runCatching { ensureDebtReminder() }
            restored = true
        }
    }

    /** Kuniga bir marta: kechagi (+ dushanba haftalik, 1-sanada oylik) hisobotni chatga qo'yadi. Soat 08:00 dan keyin. */
    private suspend fun maybeShowAutoReports() {
        val today = today()
        // v160: soat 08:00 dan keyin (ertalab) — kechagi hisobot chiqadi
        if (java.time.LocalTime.now(APP_ZONE).hour < 8) return
        val last = runCatching { chatStore.getLastReportDate() }.getOrDefault("")
        if (last == today.toString()) return

        // Kechagi hisobot
        runCatching {
            val y = today.minusDays(1)
            val r = getDateReport(userId, y, null, false)
            if (r.clientLines.isNotEmpty() || r.totalPayments > 0) {
                val labeled = r.copy(title = "Kecha · " + y.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")))
                appendChat(ChatItem.DateRep(nextChatId(), labeled))
            }
        }
        // Dushanba — o'tgan haftalik
        if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
            runCatching {
                val prevMon = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY)
                val prevSun = prevMon.plusDays(6)
                val r = getDateReport.range(userId, prevMon, prevSun, null, false)
                if (r.clientLines.isNotEmpty() || r.totalPayments > 0) {
                    appendChat(ChatItem.Info(nextChatId(), "📅 Haftalik hisobot\n" + dateReportText(r)))
                }
            }
        }
        // Oyning 1-sanasi — o'tgan oylik
        if (today.dayOfMonth == 1) {
            runCatching {
                val prev = today.minusMonths(1)
                val mStart = prev.withDayOfMonth(1)
                val mEnd = prev.withDayOfMonth(prev.lengthOfMonth())
                val r = getDateReport.range(userId, mStart, mEnd, null, false)
                if (r.clientLines.isNotEmpty() || r.totalPayments > 0) {
                    // MUHIM: r.title ichida "–" bor (01.06–30.06) — saqlashda diapazon deb tanilishi uchun uni yo'qotmaymiz
                    val titled = r.copy(title = "Oylik: " + r.title)
                    appendChat(ChatItem.DateRep(nextChatId(), titled))
                }
            }
        }
        // ⏰ Qarz eslatmasi — chiroyli karta (kuniga bir marta)
        // Qarz eslatmasi bu yerdan OLIB TASHLANDI — endi ensureDebtReminder kuniga 1 marta
        // (soat 10:00 dan keyin) ko'rsatadi, takror bo'lmasin
        runCatching { chatStore.setLastReportDate(today.toString()) }
    }

    /** Qarz eslatmasi: KUNIGA 1 MARTA, soat 10:00 dan keyin birinchi ochilishda chiqadi. */
    private suspend fun ensureDebtReminder() {
        // v177: ASOSIY TUZATISH — "bugun ko'rsatilgan" belgisiga ishonmaymiz (Worker ham o'sha belgini
        // qo'yadi va karta yo'qolib ketardi). Endi CHATNING O'ZINI tekshiramiz: bugungi DebtRep bormi?
        val hour = java.time.LocalTime.now(APP_ZONE).hour
        if (hour < 10) return

        val todayD = today()
        val zone = APP_ZONE
        val alreadyToday = _state.value.chat.any { item ->
            item is ChatItem.DebtRep &&
                java.time.Instant.ofEpochMilli(item.ts).atZone(zone).toLocalDate() == todayD
        }
        if (alreadyToday) return   // bugungi eslatma allaqachon chatda turibdi

        val all = runCatching { getOverdue(userId) }.getOrNull() ?: return
        val list = all.filter { it.daysOverdue >= 10 }
        if (list.isNotEmpty()) {
            appendChat(ChatItem.DebtRep(nextChatId(), list))
            persistChat()
            runCatching { chatStore.setLastDebtRemDate(todayD.toString()) }
        }
    }

    // ── t1set parser ──
    private val dateTokenRe = Regex("""^\d{1,2}\.\d{1,2}(-\d{1,2}\.\d{1,2})?$""")

    private fun isDateToken(t: String) = dateTokenRe.matches(t)

    private fun parseDmToDate(dm: String): LocalDate? = runCatching {
        val p = dm.split("."); LocalDate.now().withMonth(p[1].toInt()).withDayOfMonth(p[0].toInt())
    }.getOrNull()

    private fun parseDateOrRange(tok: String): Pair<LocalDate, LocalDate>? {
        return if (tok.contains("-")) {
            val (a, b) = tok.split("-", limit = 2)
            val s = parseDmToDate(a); val e = parseDmToDate(b)
            if (s != null && e != null) s to e else null
        } else {
            val d = parseDmToDate(tok); if (d != null) d to d else null
        }
    }

    /** "t1set Ali c 15.05", "t1aset Ali", "22.05 t1set Ali", ko'p qatorli (sana 1-qatorda) */
    private fun parseT1Set(input: String): List<T1SetOp>? {
        val lines = input.trim().split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        var prefix: Pair<LocalDate, LocalDate>? = null
        var cmdLines = lines
        if (lines.size >= 2 && isDateToken(lines[0])) {
            prefix = parseDateOrRange(lines[0]); cmdLines = lines.drop(1)
        }
        val ops = mutableListOf<T1SetOp>()
        for (line in cmdLines) {
            val op = parseT1Line(line, prefix) ?: return null
            ops.add(op)
        }
        return ops.ifEmpty { null }
    }

    private fun parseT1Line(line: String, prefixDate: Pair<LocalDate, LocalDate>?): T1SetOp? {
        val tk = line.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tk.isEmpty()) return null
        var i = 0
        var date: Pair<LocalDate, LocalDate>? = prefixDate
        // boshida sana bo'lishi mumkin: "22.05 t1set ..."
        if (isDateToken(tk[0])) { date = parseDateOrRange(tk[0]); i = 1 }
        if (i >= tk.size) return null
        var type: String? = when (tk[i]) {
            "t1set" -> null
            "t1aset" -> "a"
            "t1bset" -> "b"
            "t1cset" -> "c"
            else -> return null
        }
        i++
        // mijoz nomi — tur harfi yoki sana boshlanguncha
        val nameTokens = mutableListOf<String>()
        while (i < tk.size) {
            val t = tk[i]
            if ((t.length == 1 && t[0] in "abcdk") || isDateToken(t)) break
            nameTokens.add(t); i++
        }
        if (nameTokens.isEmpty()) return null
        val name = nameTokens.joinToString(" ")
        // qolgan tokenlar: ixtiyoriy tur va/yoki sana
        while (i < tk.size) {
            val t = tk[i]
            if (type == null && t.length == 1 && t[0] in "abcdk") type = t
            else if (isDateToken(t)) date = parseDateOrRange(t)
            i++
        }
        val (s, e) = date ?: (LocalDate.now() to LocalDate.now())
        return T1SetOp(name, type, s, e)
    }

    /** Chatdagi bitta xabarni o'chirish (✕ tugma) */
    fun removeChat(id: Long) {
        _state.update { it.copy(chat = it.chat.filterNot { c -> c.id == id }) }
        persistChat()
    }

    /** Oxirgi crash matnini chatda ko'rsatadi (dasturchiga yuborish uchun). */
    fun showCrashLog(text: String) {
        appendChat(ChatItem.Info(nextChatId(), "⚠️ Oxirgi xato (shu xabarni skrinshot qilib yuboring):\n\n" + text.take(1800)))
    }

    /** Eski bot CSV (client,type,amount,date,t_override) — ilovaga import qiladi (fon thread'da). */
    fun importCsv(content: String) {
        viewModelScope.launch {
            val (count, bad) = withContext(Dispatchers.IO) {
                val entities = mutableListOf<uz.daftar.app.data.db.entity.TransactionEntity>()
                var b = 0
                val lines = content.lines()
                for ((idx, line) in lines.withIndex()) {
                    if (line.isBlank()) continue
                    if (idx == 0 && line.lowercase().startsWith("client")) continue
                    runCatching {
                        val p = line.split(",")
                        val client = p.getOrNull(0)?.trim().orEmpty()
                        val type = p.getOrNull(1)?.trim()?.lowercase().orEmpty()
                        val amount = p.getOrNull(2)?.trim()?.replace(",", ".")?.toDoubleOrNull()
                        val date = p.getOrNull(3)?.trim()?.replace("T", " ").orEmpty()
                        val tov = p.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }?.replace(",", ".")?.toDoubleOrNull()
                        if (client.isNotBlank() && type.isNotBlank() && amount != null && date.isNotBlank()) {
                            entities.add(uz.daftar.app.data.db.entity.TransactionEntity(userId = userId, clientName = client, type = type, amount = amount, date = date, tOverride = tov))
                        } else b++
                    }.onFailure { b++ }
                }
                runCatching { repo.importTransactions(entities) }
                entities.size to b
            }
            appendChat(ChatItem.Info(nextChatId(), "📥 CSV import: $count ta yozuv qo'shildi" + if (bad > 0) " ($bad ta o'tkazib yuborildi)" else ""))
        }
    }

    /** Eski bot .db (SQLite) faylini to'liq import qiladi (fon thread'da — ilova qotmaydi). */
    fun importDb(path: String) {
        viewModelScope.launch {
            appendChat(ChatItem.Info(nextChatId(), "⏳ Import boshlandi... (biroz kuting)"))
            val r = withContext(Dispatchers.IO) {
                runCatching { importOldDb(userId, path) }.getOrNull()
            }
            if (r == null || !r.ok) {
                appendChat(ChatItem.Info(nextChatId(), "❌ Faylni o'qib bo'lmadi. To'g'ri eski bot .db faylini tanlang."))
                return@launch
            }
            appendChat(ChatItem.Info(nextChatId(),
                "📥 Import tugadi (eski import ma'lumotlari tozalanib, qaytadan yozildi):\n" +
                "• Yozuvlar: ${r.tx} ta\n" +
                "• Narx tarixi: ${r.price} ta\n" +
                "• Mijoz narxlari: ${r.clientPrice} ta\n" +
                "• Yuk narx: ${r.yukNarx} ta\n" +
                "• Rasxod: ${r.rasxod} ta\n" +
                "• Aliaslar: ${r.alias} ta\n\n" +
                "Mijozni yozib tekshiring (masalan: ali)."))
        }
    }

    /** Tarix kartasida "Qarzni yopish" — tasdiq oynasini ochadi (debt > 0 bo'lsa). */
    fun requestCloseDebt(name: String, debt: Long) {
        if (debt <= 0) return
        _state.update { it.copy(confirmCloseDebt = name to debt) }
    }

    /** v164: yashil tugma — qarzni TO'LIQ yopadi (dialogsiz, to'g'ridan-to'g'ri to'lov yoziladi) */
    fun closeDebtFull(name: String, debt: Long) {
        if (debt <= 0) return
        viewModelScope.launch {
            val cp = runCatching { buildClientPreview(name, null) }.getOrNull()
            val storedName = cp?.transactions?.firstOrNull()?.clientName ?: DaftarParser.normalizeName(name)
            runCatching {
                repo.insertTransaction(uz.daftar.app.data.db.entity.TransactionEntity(
                    userId = userId, clientName = storedName, type = "p", amount = debt.toDouble(), date = nowStamp()))
            }
            appendChat(ChatItem.Info(nextChatId(), "💰 ${name.replaceFirstChar { it.uppercase() }} qarzi yopildi: ${debt.formatMoney()} so'm to'lov"))
            persistChat()
            refreshHistoryCards()
        }
    }

    /**
     * v169: ORALIQ SANA hisoboti. "01.05 20.05" — hamma yuk+pul; "01.05 20.05 a c" — faqat A,C;
     * "01.05 20.05 pul" — faqat pullar. Yil: joriy yil (agar oy kelajakda bo'lsa — o'tgan yil).
     */
    private suspend fun buildRangeReport(d1: Int, m1: Int, d2: Int, m2: Int, filterRaw: String, typed: String) {
        val nowY = today().year
        fun mk(d: Int, m: Int): LocalDate {
            val mm = m.coerceIn(1, 12)
            return LocalDate.of(nowY, mm, d.coerceIn(1, java.time.YearMonth.of(nowY, mm).lengthOfMonth()))
        }
        var from = mk(d1, m1); var to = mk(d2, m2)
        if (to.isBefore(from)) { val t = from; from = to; to = t }
        // Filtr: harflar (a,b,c,d,k) yoki "pul"/"p" yoki MIJOZ ISMI yoki bo'sh (hammasi)
        val tokens = filterRaw.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cargoAll = listOf("a", "b", "c", "d", "k")
        val full = tokens.any { it == "toliq" || it == "to'liq" || it == "hammasi" }  // v182: to'liq ro'yxat
        // v169: 2+ harfli so'zlar (pul emas) — mijoz ismi filtri ("01.05 25.05 ali a")
        val nameTokens = tokens.filter { it.length >= 2 && it != "pul" && it != "toliq" && it != "to'liq" && it != "hammasi" && it !in cargoAll && it.all { c -> c.isLetter() || c == '\'' } }
        val clientFilter = nameTokens.joinToString(" ")
        val wantPul = tokens.any { it == "pul" || it == "p" } || tokens.none { it in cargoAll || it == "pul" || it == "p" }
        val wantTypes = tokens.filter { it in cargoAll }.ifEmpty { if (tokens.any { it == "pul" || it == "p" }) emptyList() else cargoAll }

        val txsAll = repo.getRange(userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val txs = if (clientFilter.isBlank()) txsAll
                  else txsAll.filter { it.clientName.lowercase().contains(clientFilter.lowercase()) }
        val cargo = txs.filter { it.type.code in wantTypes }  // v170: type = TxType enum, .code = "a".."k"
        val pays = if (wantPul) txs.filter { it.type == TxType.P } else emptyList()

        val sb = StringBuilder()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM")
        sb.append("📊 ${from.format(fmt)} – ${to.format(fmt)} hisobot")
        if (clientFilter.isNotBlank()) sb.append(" · ${clientFilter.replaceFirstChar { it.uppercase() }}")
        if (wantTypes.size in 1 until cargoAll.size) sb.append(" (${wantTypes.joinToString(", ") { it.uppercase() }})")
        else if (wantTypes.isEmpty()) sb.append(" (faqat pul)")
        sb.append("\n")

        if (wantTypes.isNotEmpty()) {
            // Turlar bo'yicha jami soni
            sb.append("\n📦 YUKLAR (soni):\n")
            var anyC = false
            for (t in wantTypes) {
                val sum = cargo.filter { it.type.code == t }.sumOf { it.amount }
                if (sum > 0) { sb.append("  ${t.uppercase()}: ${sum.formatQty()} dona\n"); anyC = true }
            }
            if (!anyC) sb.append("  (yuk yo'q)\n")
            // Mijozlar kesimi
            val byClient = cargo.groupBy { it.clientName.lowercase() }
            if (byClient.isNotEmpty()) {
                val sortedAll = byClient.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }
                val show = if (full) sortedAll else sortedAll.take(10)   // v182: default TOP-10
                sb.append("\n👥 MIJOZLAR (${byClient.size} ta):\n")
                show.forEachIndexed { i, (cn, list) ->
                    val parts = wantTypes.mapNotNull { t ->
                        val q = list.filter { it.type.code == t }.sumOf { it.amount }
                        if (q > 0) "${t.uppercase()}:${q.formatQty()}" else null
                    }
                    if (parts.isNotEmpty())
                        sb.append("  ${i + 1}. ${cn.replaceFirstChar { it.uppercase() }}  ${parts.joinToString(" ")}\n")
                }
                if (!full && byClient.size > 10)
                    sb.append("  … yana ${byClient.size - 10} mijoz — TO'LIQ uchun: \"$typed toliq\"\n")
                // v182: OXIRIDA JAMI — har doim ko'rinadi
                sb.append("\n📊 JAMI:\n")
                for (t in wantTypes) {
                    val sum = cargo.filter { it.type.code == t }.sumOf { it.amount }
                    if (sum > 0) sb.append("  ${t.uppercase()}: ${sum.formatQty()} dona\n")
                }
                sb.append("  Mijozlar: ${byClient.size} ta\n")
            }
        }
        if (wantPul) {
            val totalP = pays.sumOf { it.amount }
            sb.append("\n💵 PUL: ${Math.round(totalP).toDouble().formatMoney()} so'm (${pays.size} ta to'lov)\n")
            if (wantTypes.isEmpty() && pays.isNotEmpty()) {
                val byC = pays.groupBy { it.clientName.lowercase() }
                    .entries.sortedByDescending { e -> e.value.sumOf { it.amount } }.take(40)
                byC.forEachIndexed { i, (cn, list) ->
                    sb.append("  ${i + 1}. ${cn.replaceFirstChar { it.uppercase() }}  ${Math.round(list.sumOf { it.amount }).toDouble().formatMoney()}\n")
                }
            }
        }
        appendChat(ChatItem.User(nextChatId(), typed))
        appendChat(ChatItem.Info(nextChatId(), sb.toString().trimEnd()))
        persistChat()
    }

    /** v176: 🔔 toolbar tugmasi — qarz eslatma oynasini DARROV ko'rsatadi (10/15/30/60/90 kun) */
    fun showDebtReminder() {
        viewModelScope.launch {
            val list = runCatching { getOverdue(userId).filter { it.daysOverdue >= 10 } }.getOrDefault(emptyList())
            if (list.isEmpty()) {
                appendChat(ChatItem.Info(nextChatId(), "\u2705 10 kundan oshgan qarzdor yo'q."))
            } else {
                // eskisini olib tashlaymiz — dublikat bo'lmasin
                _state.update { st -> st.copy(chat = st.chat.filterNot { it is ChatItem.DebtRep }) }
                appendChat(ChatItem.DebtRep(nextChatId(), list))
            }
            persistChat()
        }
    }

    /** v176: qarzdorlar sonini yangilash (toolbar 💳 yonida) */
    private fun refreshDebtorCount() {
        viewModelScope.launch {
            val n = runCatching { getOverdue(userId).size }.getOrDefault(0)
            _state.update { it.copy(debtorCount = n) }
        }
    }

    /** v169: hisobotda ism bosilsa — tarix kartasi chatga qo'shiladi */
    fun openClientHistory(name: String) {
        viewModelScope.launch {
            val cp = runCatching { buildClientPreview(name, null) }.getOrNull()
            // v173: agar shu mijozning kartasi allaqachon ochiq bo'lsa — eskisini olib tashlaymiz (dublikat bo'lmasin),
            // yangisini oxiriga qo'yamiz — auto-scroll pastga tushadi va darrov ko'rinadi.
            _state.update { st ->
                st.copy(chat = st.chat.filterNot { it is ChatItem.History && it.preview.name.equals(name, ignoreCase = true) })
            }
            if (cp != null) appendChat(ChatItem.History(nextChatId(), cp))
            else appendChat(ChatItem.Info(nextChatId(), "👤 ${name.replaceFirstChar { it.uppercase() }} — yozuv topilmadi"))
            persistChat()
            // v181: MAJBURIY scroll — eski karta o'chirilib yangisi qo'shilsa hajm o'zgarmaydi,
            // avto-scroll ishlamasdi (shuning uchun 3-4 marta bosishga to'g'ri kelardi)
            _state.update { it.copy(scrollTick = it.scrollTick + 1) }
        }
    }

    fun cancelCloseDebt() { _state.update { it.copy(confirmCloseDebt = null) } }

    /** "Ha" — qarz miqdorida P (to'lov) yozadi, qarz 0 bo'ladi, hisobotga tushadi. */
    fun confirmCloseDebt(payAmount: Long? = null) {
        val pair = _state.value.confirmCloseDebt ?: return
        val (name, debt) = pair
        val pay = (payAmount ?: debt).coerceIn(1L, debt)  // qisman yoki to'liq
        viewModelScope.launch {
            val cp = runCatching { buildClientPreview(name, null) }.getOrNull()
            val storedName = cp?.transactions?.firstOrNull()?.clientName ?: DaftarParser.normalizeName(name)
            val nowStr = nowStamp()  // v155: Asia/Tashkent
            runCatching {
                repo.insertTransaction(uz.daftar.app.data.db.entity.TransactionEntity(
                    userId = userId, clientName = storedName, type = "p", amount = pay.toDouble(), date = nowStr))
            }
            _state.update { it.copy(confirmCloseDebt = null) }
            val qoldi = debt - pay
            val msg = if (qoldi > 0)
                "💰 ${name.replaceFirstChar { it.uppercase() }} — ${pay.formatMoney()} so'm to'lov. Qoldi: ${qoldi.formatMoney()} so'm"
            else
                "💰 ${name.replaceFirstChar { it.uppercase() }} qarzi yopildi: ${pay.formatMoney()} so'm to'lov"
            appendChat(ChatItem.Info(nextChatId(), msg))
            // Ochiq tarix kartalarini yangilash
            val fresh = runCatching { buildClientPreview(name, null) }.getOrNull()
            if (fresh != null) {
                _state.update { st ->
                    st.copy(chat = st.chat.map { c ->
                        if (c is ChatItem.History && c.preview.name.equals(name, true)) c.copy(preview = fresh) else c
                    })
                }
                persistChat()
            }
        }
    }

    /** "delete 02.06" / "delete bugun" / "01.06 x" / "x 01.06" — sanani aniqlaydi (aks holda null). */
    private fun parseDeleteAllDate(text: String): java.time.LocalDate? {
        val tk = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tk.size != 2) return null
        val today = java.time.LocalDate.now()
        fun parseDmy(s: String): java.time.LocalDate? {
            val m = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?$""").find(s) ?: return null
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].let { if (it.isBlank()) today.year else if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            return runCatching { java.time.LocalDate.of(y, mo, d) }.getOrNull()
        }
        // "delete <date>" / "o'chir <date>"
        if (tk[0] in setOf("delete", "o'chir", "ochir", "tozala")) {
            return when (tk[1]) {
                "bugun" -> today
                "kecha" -> today.minusDays(1)
                else -> parseDmy(tk[1])
            }
        }
        // "01.06 x" yoki "x 01.06" — butun kun
        if (tk[1] == "x") return parseDmy(tk[0])
        if (tk[0] == "x") return parseDmy(tk[1])
        return null
    }

    /** Send bosilganda — tasdiq oynasini ochadi (hali o'chirmaydi). */
    private fun requestDeleteAll() {
        val d = _state.value.deleteAllDate ?: return
        _state.update { it.copy(confirmDeleteDate = d, deleteAllDate = null, input = "", parsed = emptyList()) }
    }

    /** "Yo'q" — bekor qiladi. */
    fun cancelDeleteAll() { _state.update { it.copy(confirmDeleteDate = null, confirmDeleteClient = null) } }

    /** "Ha" — o'sha kunning BARCHA yozuvlarini o'chiradi (faqat shu sana). */
    fun confirmDeleteAll() {
        val d = _state.value.confirmDeleteDate ?: return
        viewModelScope.launch {
            val n = runCatching { delToKarzina.byDate(userId, d) }.getOrDefault(0)
            _state.update { it.copy(confirmDeleteDate = null) }
            val label = d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            appendChat(ChatItem.Info(nextChatId(), if (n > 0) "🗑 $label — $n ta yozuv karzinaga tushdi" else "$label — o'chiriladigan yozuv yo'q"))
        }
    }

    /** Send bosilganda — mijoz o'chirish tasdig'ini ochadi. */
    private fun requestDeleteClient() {
        val name = _state.value.deleteClientName ?: return
        _state.update { it.copy(confirmDeleteClient = name, deleteClientName = null, input = "", parsed = emptyList()) }
    }

    /** "Ha" — mijozning BUTUN tarixini o'chiradi. */
    fun confirmDeleteClient() {
        val name = _state.value.confirmDeleteClient ?: return
        viewModelScope.launch {
            val cn = DaftarParser.normalizeName(name)
            val n = runCatching { delToKarzina.byClient(userId, cn) }.getOrDefault(0)
            _state.update { it.copy(confirmDeleteClient = null) }
            val disp = name.replaceFirstChar { it.uppercase() }
            appendChat(ChatItem.Info(nextChatId(), if (n > 0) "🗑 $disp — butun tarix karzinaga tushdi ($n ta yozuv)" else "$disp — o'chiriladigan yozuv yo'q"))
        }
    }

    /** Chatdagi tarix kartasida oyni surish (⬅️/➡️) — DB'dan yangi ma'lumot tortadi */
    fun shiftHistoryMonth(id: Long, delta: Int) {
        val item = _state.value.chat.firstOrNull { it is ChatItem.History && it.id == id } as? ChatItem.History ?: return
        val newMonth = item.preview.month.plusMonths(delta.toLong())
        viewModelScope.launch {
            val fresh = buildClientPreview(item.preview.name, newMonth) ?: item.preview.copy(month = newMonth)
            _state.update { st ->
                st.copy(chat = st.chat.map { c ->
                    if (c is ChatItem.History && c.id == id) c.copy(preview = fresh) else c
                })
            }
            persistChat()
        }
    }

    /** Ilova ochilganda bugungi yozuvlarni chatga "✅ Saqlandi" sifatida tiklaydi */
    private fun seedTodayChat() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val txs = runCatching {
                repo.getRange(userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
            }.getOrDefault(emptyList())
            if (txs.isEmpty()) return@launch
            val saves = txs.groupBy { it.clientName.lowercase() to it.date }
                .entries.sortedBy { it.value.first().date }
            val items = mutableListOf<ChatItem>()
            for (e in saves) {
                items.add(ChatItem.Saved(nextChatId(), buildSavedInfoFromTxs(e.value)))
            }
            _state.update { st -> st.copy(chat = items + st.chat) }
        }
    }

    private suspend fun runEdit(raw: String): String {
        val tk = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tk.size < 4) return "Format: edit <mijoz> <eski> <yangi>\nMasalan: edit ali a10 a15"
        val oldTok = tk[tk.size - 2]; val newTok = tk[tk.size - 1]
        val name = tk.subList(1, tk.size - 2).joinToString(" ")
        val old = parseTypeAmount(oldTok) ?: return "Noto'g'ri: $oldTok (masalan a10)"
        val new = parseTypeAmount(newTok) ?: return "Noto'g'ri: $newTok (masalan a15)"
        return editByMatch(userId, name, old.first, old.second, new.first, new.second).message
    }

    private fun parseTypeAmount(tok: String): Pair<TxType, Double>? {
        if (tok.length < 2) return null
        val t = TxType.fromCode(tok[0].lowercaseChar().toString()) ?: return null
        val amt = tok.substring(1).replace(",", ".").toDoubleOrNull() ?: return null
        return t to amt
    }

    /** GPT buyrug'ini bajaradi: kalit / yordam / prognoz / oddiy savol (ma'lumot konteksti bilan) */
    private suspend fun handleAi(query: String): String {
        val q = query.trim()
        if (q.lowercase().startsWith("kalit")) {
            val rest = q.substring(5).trim()
            if (rest.isBlank()) return "⚠️ Kalitni yozing:\ngpt kalit <provider> <KALIT>\nMasalan: gpt kalit groq gsk_...\nProvayderlar: gemini, groq, cerebras, openrouter"
            val parts = rest.split(Regex("\\s+"), limit = 2)
            val maybe = parts[0].lowercase()
            val provider: String
            val key: String
            if (maybe in uz.daftar.app.core.ai.AiProviders.ids && parts.size == 2) {
                provider = maybe; key = parts[1].trim()
            } else {
                provider = "gemini"; key = rest
            }
            if (key.isBlank()) return "⚠️ Kalit bo'sh."
            if (key.equals("ochir", true) || key.equals("o'chir", true)) {
                aiSettings.setKey(provider, "")
                return "🗑 $provider kaliti o'chirildi."
            }
            // Nusxalashda tushib qolgan probel/yangi qatorlarni tozalaymiz
            val clean = key.replace(Regex("\\s+"), "")
            aiSettings.setKey(provider, clean)
            return "✅ $provider kaliti saqlandi (${clean.take(8)}…, ${clean.length} belgi).\nTekshirish: gpt holat"
        }
        if (q.lowercase() == "holat") {
            val sb = StringBuilder("🔑 Kalitlar holati:\n")
            for (p in uz.daftar.app.core.ai.AiProviders.ALL) {
                val k = aiSettings.getKey(p.id)
                sb.append(
                    if (k.isBlank()) "• ${p.displayName}: yo'q\n"
                    else "• ${p.displayName}: ${k.take(8)}… (${k.length} belgi)\n"
                )
            }
            sb.append("\nQo'shish: gpt kalit <provider> <KALIT>\nO'chirish: gpt kalit <provider> ochir")
            return sb.toString()
        }
        if (q.lowercase() == "yordam") {
            return "🤖 GPT yordam:\n\n• gpt kalit <provider> <KALIT>\n   provayderlar: gemini, groq, cerebras, openrouter\n   (bir nechtasini qo'shsangiz — biri tugasa keyingisi ishlaydi)\n• gpt holat — qaysi kalitlar saqlanganini ko'rish\n• gpt <savol> — biznesingiz bo'yicha savol\n• prognoz — keyingi oy taxmini\n\nBepul kalit: aistudio.google.com/apikey (Gemini), console.groq.com (Groq), cloud.cerebras.ai (Cerebras), openrouter.ai (OpenRouter)"
        }
        val context = runCatching { buildBusinessContext() }.getOrDefault("")
        val prompt = if (q.lowercase() == "prognoz") {
            "Sen cargo/yuk biznesi buxgalteri yordamchisisan. Quyidagi ma'lumotga asoslanib keyingi oy uchun qisqa, real prognoz ber (daromad, foyda, qarz xavfi). O'zbek tilida, qisqa.\n\n$context"
        } else {
            "Sen cargo/yuk biznesi buxgalteri yordamchisisan. Faqat quyidagi ma'lumotga asoslanib, O'zbek tilida qisqa va ANIQ javob ber. Ma'lumotda bo'lmagan narsani taxmin qilma.\n\n$context\n\nSAVOL: $q"
        }
        return gptService.ask(prompt)
    }

    /** Joriy biznes holatini matn sifatida yig'adi (GPT konteksti uchun) */
    private suspend fun buildBusinessContext(): String {
        val now = LocalDate.now()
        val sb = StringBuilder()
        sb.append("Bugun: $now\n")
        runCatching {
            val r = getMonthlyReport(userId, now.year, now.monthValue)
            sb.append("Shu oy — daromad: ${r.revenue}, tannarx: ${r.tCost}, foyda: ${r.grossProfit}, to'lov: ${r.payments}, rasxod: ${r.expenses}, sof foyda: ${r.profit}, ${r.transactionCount} yozuv, ${r.clientCount} mijoz.\n")
        }
        runCatching {
            val overdue = getOverdue(userId)
            val total = overdue.sumOf { it.debt }
            sb.append("Umumiy qarz: $total so'm, ${overdue.size} qarzdor.\n")
            val top = overdue.sortedByDescending { it.debt }.take(5)
            if (top.isNotEmpty()) {
                sb.append("Eng katta qarzdorlar:\n")
                top.forEach { sb.append("  - ${it.client}: ${it.debt} so'm (${it.daysOverdue} kun)\n") }
            }
        }
        return sb.toString()
    }

    private suspend fun buildSavedTextFromTxs(txs: List<Transaction>): String {
        val first = txs.first()
        val cn = first.clientName.lowercase()
        val prices = runCatching { getUnitPrices(userId, cn) }.getOrDefault(emptyMap())
        val debt = runCatching { calcDebt(userId, cn) }.getOrDefault(0L)
        val nameCap = first.clientName.replaceFirstChar { it.uppercase() }
        val sb = StringBuilder()
        sb.append("✅ Saqlandi\n")
        sb.append("📅 ").append(first.date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")))
            .append("      🕒 ").append(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))).append("\n")
        sb.append(nameCap).append("\n")
        for (tx in txs) {
            when (tx.type) {
                TxType.P -> sb.append("  P: ${tx.amount.formatMoney()}\n")
                TxType.Q -> sb.append("  Q: ${tx.amount.formatMoney()}\n")
                else -> {
                    val p = prices[tx.type]  // N narx (tannarx emas)
                    if (p != null) sb.append("  ${tx.type.code.uppercase()}: ${tx.amount.formatQty()} × ${p.formatQty()} = ${(tx.amount * p).formatMoney()}\n")
                    else sb.append("  ${tx.type.code.uppercase()}: ${tx.amount.formatQty()}  (narx yo'q)\n")
                }
            }
        }
        sb.append("💳 Qarz: ${debt.formatMoney()} so'm")
        return sb.toString()
    }

    private suspend fun buildSavedInfoFromTxs(txs: List<Transaction>): SavedInfo {
        val first = txs.first()
        val cn = first.clientName.lowercase()
        val prices = runCatching { getUnitPrices(userId, cn) }.getOrDefault(emptyMap())
        val debt = runCatching { calcDebt(userId, cn) }.getOrDefault(0L)
        val nameCap = first.clientName.replaceFirstChar { it.uppercase() }
        val lines = txs.map { tx ->
            when (tx.type) {
                TxType.P -> SavedLine("p", "P: ${tx.amount.formatMoney()}", "Pul kirimi")
                TxType.Q -> SavedLine("q", "Q: ${tx.amount.formatMoney()}", "Qo'lda qarz")
                else -> {
                    val p = prices[tx.type]
                    if (p != null) SavedLine(tx.type.code, "${tx.type.code.uppercase()}: ${tx.amount.formatQty()} × ${p.formatQty()} = ${(tx.amount * p).formatMoney()} so'm", "Yuk")
                    else SavedLine(tx.type.code, "${tx.type.code.uppercase()}: ${tx.amount.formatQty()}", "Narx yo'q")
                }
            }
        }
        return SavedInfo(
            nameCap,
            first.date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")),
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            lines, debt
        )
    }

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
        _state.update { it.copy(pinnedView = false, globalPrice = null, t1set = null, aiQuery = null, isEditUndo = false, deleteAllDate = null, deleteClientName = null, rasxodAmount = null) }
        // "r100 gaz" / "r 100 gaz" / "rasxod 100 gaz" — rasxod (xarajat)
        run {
            val m = Regex("""^(?:r|rasxod)\s*(\d+(?:[.,]\d+)?)\s*(.*)$""", RegexOption.IGNORE_CASE).matchEntire(text.trim())
            if (m != null) {
                val amt = m.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (amt != null && amt > 0) {
                    _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false, isViewCommand = false, previews = emptyList(), dateReport = null, textReport = null, rasxodAmount = amt, rasxodNote = m.groupValues[2].trim()) }
                    updateSuggestions("")
                    return
                }
            }
        }
        // "delete 02.06" / "o'chir bugun" — kun bo'yicha BARCHA yozuvlarni o'chirish (tasdiq bilan)
        run {
            val d = parseDeleteAllDate(text.trim())
            if (d != null) {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false, isViewCommand = false, previews = emptyList(), dateReport = null, textReport = null, deleteAllDate = d) }
                updateSuggestions("")
                return
            }
        }
        // "ochir ali" / "o'chir ali" — mijozning BUTUN tarixini o'chirish (tasdiq bilan)
        run {
            val tk = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tk.size >= 2 && tk[0] in setOf("ochir", "o'chir") &&
                !Regex("""^\d{1,2}\.\d{1,2}.*""").matches(tk[1]) && tk[1] !in setOf("bugun", "kecha")) {
                val name = tk.drop(1).joinToString(" ")
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = false, isViewCommand = false, previews = emptyList(), dateReport = null, textReport = null, deleteClientName = name) }
                // v138: "ochir NAME" da ham ism yordami chiqsin
                suggestJob?.cancel()
                suggestJob = viewModelScope.launch {
                    val list = repo.suggestClients(userId, name)
                    _state.update { it.copy(suggestions = list, quickFills = emptyList()) }
                }
                return
            }
        }
        // edit / undo / bekor
        run {
            val low = text.trim().lowercase()
            if (low == "undo" || low == "bekor" || low.startsWith("edit ")) {
                _state.update {
                    it.copy(
                        parsed = emptyList(), errorMessage = null, isDeleteCommand = false,
                        isViewCommand = false, previews = emptyList(), dateReport = null,
                        textReport = null, globalPrice = null, t1set = null, aiQuery = null,
                        isEditUndo = true
                    )
                }
                updateSuggestions("")
                return
            }
        }
        // GPT / prognoz
        run {
            val low = text.trim().lowercase()
            if (low == "gpt" || low.startsWith("gpt ") || low == "prognoz") {
                val q = if (low == "prognoz") "prognoz"
                        else text.trim().drop(3).trim().ifBlank { "yordam" }
                _state.update {
                    it.copy(
                        parsed = emptyList(), errorMessage = null, isDeleteCommand = false,
                        isViewCommand = false, previews = emptyList(), dateReport = null,
                        textReport = null, globalPrice = null, t1set = null, aiQuery = q
                    )
                }
                updateSuggestions("")
                return
            }
        }
        // X-o'chirish komandasi tekshirish ("x" yoki "12.03 x" + ismlar)
        if (isDeleteCommandText(text)) {
            _state.update { it.copy(parsed = emptyList(), errorMessage = null, isDeleteCommand = true, previews = emptyList(), dateReport = null, textReport = null) }
            // X-o'chirishda HAM ism yordami chiqsin: oxirgi qatordan x/sana so'zlarini tashlab, ism qismini olamiz
            val lastLn = text.substringAfterLast('\n').trim()
            val parts = lastLn.split(Regex("\\s+")).filter { it.isNotBlank() }
            val dRe = Regex("""^\d{1,2}[.,]\d{1,2}([.,]\d{2,4})?$""")
            val delW = setOf("x", "ochir", "o'chir", "ochirish")
            var i = 0
            while (i < parts.size && (dRe.matches(parts[i]) || parts[i].lowercase() in delW)) i++
            val nameWords = parts.drop(i).takeWhile { w -> w.first().isLetter() && w.all { it.isLetter() || it == '\'' || it == '-' } }
            // To'g'ridan-to'g'ri to'ldiramiz: aniq mos ism ham ko'rinadi (tasdiq sifatida)
            val dq = nameWords.joinToString(" ").trim()
            suggestJob?.cancel()
            if (dq.isBlank()) {
                _state.update { it.copy(suggestions = emptyList(), quickFills = emptyList()) }
            } else {
                suggestJob = viewModelScope.launch {
                    val list = repo.suggestClients(userId, dq)
                    _state.update { it.copy(suggestions = list, quickFills = emptyList()) }
                }
            }
            return
        }
        // t1set / t1aset / t1bset / t1cset — mavjud yozuvlarni T1 ga o'tkazish
        val t1ops = parseT1Set(text)
        if (t1ops != null) {
            _state.update {
                it.copy(
                    parsed = emptyList(), errorMessage = null, isDeleteCommand = false,
                    isViewCommand = false, previews = emptyList(), dateReport = null,
                    textReport = null, globalPrice = null, t1set = t1ops
                )
            }
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
            "solishtir", "solishtirish", "taqqosla" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadCompare(); return
            }
            "foyda top", "top foyda" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadProfitTop(); return
            }
            "top", "top 5", "top5", "qarzdor top" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadTopDebtors(); return
            }
            "faol" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadActivity(true); return
            }
            "nofaol", "g'oyib" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadActivity(false); return
            }
            "narx tarix", "t tarix", "narxlar" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadPriceHistory(); return
            }
            "rasxod", "rasxod bugun", "harajat" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadRasxod("bugun"); return
            }
            "rasxod oy", "rasxod oylik" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadRasxod("oy"); return
            }
            "rasxod yil", "rasxod yillik" -> {
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadRasxod("yil"); return
            }
        }

        // "qarz tahlil <mijoz>" — bitta mijoz qarzi tahlili
        run {
            val tk = trimmedLow.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tk.size >= 3 && tk[0] == "qarz" && tk[1] == "tahlil") {
                val name = tk.drop(2).joinToString(" ")
                _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                loadDebtAnalysis(name); return
            }
            // "narx korish <mijoz>" yoki "narx <mijoz>"
            if (tk.size >= 2 && tk[0] == "narx") {
                if (tk[1] == "tarix" || tk[1] == "tarixi") {
                    _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                    loadPriceHistory(); return
                }
                val name = (if (tk[1] == "korish") tk.drop(2) else tk.drop(1)).joinToString(" ")
                if (name.isNotBlank()) {
                    _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), dateReport = null, isDeleteCommand = false, isViewCommand = true) }
                    loadClientPrices(name); return
                }
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

        // ── SANA ORALIG'I: "01.05 25.05 [ism] [a b c / p / n]" ──────────────
        run {
            val dRe = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?$""")
            fun toDate(s: String): LocalDate? {
                val m = dRe.matchEntire(s) ?: return null
                return runCatching {
                    val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
                    val yr = m.groupValues[3]
                    val year = when { yr.isEmpty() -> LocalDate.now().year; yr.length == 2 -> 2000 + yr.toInt(); else -> yr.toInt() }
                    LocalDate.of(year, mo, d)
                }.getOrNull()
            }
            if (tokens.size >= 2) {
                val d1 = toDate(tokens[0]); val d2 = toDate(tokens[1])
                if (d1 != null && d2 != null && !d2.isBefore(d1)) {
                    val rest = tokens.drop(2)
                    val useNarx = rest.contains("n")
                    val letters = rest.filter { it != "n" && it.length == 1 && it[0] in "abcdkpq" }
                    // lettersdan tashqari hamma narsa — ism
                    val nameParts = rest.filter { it != "n" && !(it.length == 1 && it[0] in "abcdkpq") }
                    // rest faqat harf/n yoki ism + harf bo'lishi mumkin
                    val onlyLettersOrName = rest.all {
                        it == "n" || (it.length == 1 && it[0] in "abcdkpq") || it.all { c -> c.isLetter() || c == '\'' || c == '-' }
                    }
                    if (onlyLettersOrName) {
                        val types = if (letters.isEmpty()) null else letters.toSet()
                        val clientFilter = if (nameParts.isEmpty()) null else nameParts.joinToString(" ")
                        _state.update { it.copy(parsed = emptyList(), errorMessage = null, previews = emptyList(), textReport = null, isViewCommand = true) }
                        loadDateReport(d1, types, useNarx, endDate = d2, clientFilter = clientFilter)
                        updateSuggestions("")
                        return
                    }
                }
            }
        }

        // "bugun" / "kecha" / "DD.MM" + ixtiyoriy tur filtri ("30.05 a b c")
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
        // Multi-line — har qator alohida parsing.
        // MUHIM: "12.06" kabi FAQAT-SANA qatori yozuv emas — u pastdagi HAMMA qatorga
        // shu sanani beradi (bitta sana bilan 5-10 odamni birdan qo'shish uchun).
        // Yangi sana qatori chiqsa — undan keyingilarga yangi sana qo'llanadi.
        // Qatorda o'z sanasi bo'lsa ("13.06 ali a10") — o'sha saqlanadi.
        val pureDateRe = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?$""")
        fun pureDate(s: String): LocalDate? {
            val m = pureDateRe.matchEntire(s) ?: return null
            return runCatching {
                val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
                val yr = m.groupValues[3]
                val year = when { yr.isEmpty() -> LocalDate.now().year; yr.length == 2 -> 2000 + yr.toInt(); else -> yr.toInt() }
                LocalDate.of(year, mo, d)
            }.getOrNull()
        }
        val results: List<ParseResult> = run {
            var curDate: LocalDate? = null
            val acc = mutableListOf<ParseResult>()
            for (ln in text.lines().map { it.trim() }.filter { it.isNotBlank() }) {
                val dOnly = pureDate(ln)
                if (dOnly != null) { curDate = dOnly; continue }  // sana qatori — entry emas
                val r = DaftarParser.parse(ln)
                acc.add(
                    if (r is ParseResult.Success && r.entry.date == null && curDate != null)
                        ParseResult.Success(r.entry.copy(date = curDate))
                    else r
                )
            }
            acc
        }
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
        // v141: "Ali a" kabi ism oxiri yuk-harfiga o'xshasa ham — AYNAN shu nomli mijoz bo'lsa TARIX
        if (parsedList.isEmpty() && !isNameOnly && !text.contains('\n')) {
            val cand = text.trim()
            val okChars = cand.length >= 2 && cand.any { it.isLetter() } &&
                cand.all { it.isLetterOrDigit() || it == ' ' || it == '\'' || it == '-' }
            if (okChars) {
                previewJob?.cancel()
                previewJob = viewModelScope.launch {
                    val cp = runCatching { buildClientPreview(cand, null) }.getOrNull()
                    if (cp != null && cp.name.equals(cand, ignoreCase = true) &&
                        _state.value.input.trim().equals(cand, ignoreCase = true)
                    ) {
                        _state.update { it.copy(isViewCommand = true, errorMessage = null, previews = listOf(cp)) }
                    }
                }
            }
        }
        // Autocomplete — OXIRGI qatordagi birinchi so'z (har bir qatorda ishlaydi)
        val lastLine0 = text.substringAfterLast('\n').trimStart()
        // Sana (15.06) yoki o'chirish so'zi (x/ochir) bo'lsa — ulardan keyingi so'z ISM
        val partsLL = lastLine0.split(' ').filter { it.isNotBlank() }
        val dateRe = Regex("""^\d{1,2}[.,]\d{1,2}([.,]\d{2,4})?$""")
        val delWords = setOf("x", "ochir", "o'chir", "ochirish")
        var wi = 0
        while (wi < partsLL.size && (dateRe.matches(partsLL[wi]) || partsLL[wi].lowercase() in delWords)) wi++
        // v141: ism BIR NECHTA so'zdan iborat bo'lishi mumkin ("dom oshtoncha") — harfli so'zlar ketma-ket olinadi
        val nameWords = mutableListOf<String>()
        var wj = wi
        while (wj < partsLL.size) {
            val w = partsLL[wj]
            if (w.first().isLetter() && w.all { it.isLetter() || it == '\'' || it == '-' }) { nameWords.add(w); wj++ } else break
        }
        val nameWord = nameWords.joinToString(" ")
        val onlyName = nameWord.isNotBlank()
        updateSuggestions(if (onlyName) nameWord else "")

        // Jonli tarix preview — ism yozilsa (parsing yozuv topa olmasa)
        loadPreviewIfName(text)
    }

    /** Tugmadan chaqiriladi — inputni tozalab, sana hisobotini ko'rsatadi */
    fun showDateReportButton(date: LocalDate, useNarx: Boolean = false) {
        val label = date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
        viewModelScope.launch {
            appendChat(ChatItem.User(nextChatId(), label))
            val report = runCatching { getDateReport(userId, date, null, useNarx) }.getOrNull()
            if (report != null && (report.clientLines.isNotEmpty() || report.totalPayments > 0))
                appendChat(ChatItem.DateRep(nextChatId(), report))
            else appendChat(ChatItem.Info(nextChatId(), "❌ $label — bu kunda yozuv yo'q"))
            _state.update { it.copy(input = "", parsed = emptyList(), errorMessage = null) }
        }
    }

    /** Haftalik hisobot — joriy haftaning dushanbasidan yakshanbasigacha (T narx) */
    fun showWeekReport() {
        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        viewModelScope.launch {
            appendChat(ChatItem.User(nextChatId(), "hafta"))
            val report = runCatching { getDateReport.range(userId, monday, sunday, null, false) }.getOrNull()
            if (report != null) appendChat(ChatItem.DateRep(nextChatId(), report))
            else appendChat(ChatItem.Info(nextChatId(), "Hisobot topilmadi"))
            _state.update { it.copy(input = "", parsed = emptyList(), errorMessage = null) }
        }
    }

    /** Sana hisobotини yopish */
    fun clearDateReport() {
        _state.update { it.copy(dateReport = null, pinnedView = false) }
    }

    /** "solishtir" — shu oy vs o'tgan oy */
    private fun loadCompare() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val now = LocalDate.now()
                val cur = getMonthlyReport(userId, now.year, now.monthValue)
                val pm = now.minusMonths(1)
                val prev = getMonthlyReport(userId, pm.year, pm.monthValue)
                fun line(label: String, a: Long, b: Long): String {
                    val diff = a - b
                    val arrow = if (diff > 0) "🔼" else if (diff < 0) "🔽" else "➡️"
                    return "$label\n  Shu oy: ${a.formatMoney()}\n  O'tgan: ${b.formatMoney()}\n  $arrow ${diff.formatMoney()}"
                }
                val body = listOf(
                    line("💰 Daromad", cur.revenue, prev.revenue),
                    line("📈 Foyda", cur.grossProfit, prev.grossProfit),
                    line("💳 To'lov", cur.payments, prev.payments),
                    line("✅ Sof foyda", cur.profit, prev.profit)
                ).joinToString("\n\n")
                _state.update { it.copy(textReport = TextReport("📊 Solishtirish (${pm.monthValue}.${pm.year} ↔ ${now.monthValue}.${now.year})", body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "foyda top" — eng ko'p foyda bergan mijozlar (shu yil) */
    private fun loadProfitTop() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val names = runCatching { repo.observeClientNames(userId).first() }.getOrDefault(emptyList())
                val rows = mutableListOf<Pair<String, Long>>()
                for (n in names) {
                    val p = runCatching { getClientProfit(userId, n).totalThisYear }.getOrDefault(0L)
                    if (p != 0L) rows.add(n to p)
                }
                rows.sortByDescending { it.second }
                val body = if (rows.isEmpty()) "Ma'lumot yo'q."
                else rows.take(10).mapIndexed { i, (n, p) -> "${i + 1}. ${n.replaceFirstChar { it.uppercase() }} — ${p.formatMoney()} so'm" }.joinToString("\n")
                _state.update { it.copy(textReport = TextReport("🏆 Foyda — top mijozlar (shu yil)", body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "top 5" — eng katta qarzdorlar */
    private fun loadTopDebtors() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val list = getOverdue(userId).sortedByDescending { it.debt }.take(5)
                val body = if (list.isEmpty()) "Qarzdor yo'q 🎉"
                else list.mapIndexed { i, d -> "${i + 1}. ${d.client.replaceFirstChar { it.uppercase() }} — ${d.debt.formatMoney()} so'm (${d.daysOverdue} kun)" }.joinToString("\n")
                _state.update { it.copy(textReport = TextReport("🔝 Top 5 qarzdor", body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "faol" / "nofaol" — oxirgi 30 kunda harakat bor/yo'q mijozlar */
    private fun loadActivity(active: Boolean) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val names = runCatching { repo.observeClientNames(userId).first() }.getOrDefault(emptyList())
                val limit = LocalDate.now().minusDays(30).toString()  // "yyyy-MM-dd"
                val rows = mutableListOf<Pair<String, String>>()
                for (n in names) {
                    val cn = DaftarParser.normalizeName(n)
                    val h = runCatching { getHistory(userId, cn) }.getOrNull() ?: continue
                    val last = h.transactions.maxByOrNull { it.date }?.date ?: continue
                    val isActive = last.take(10) >= limit
                    if (isActive == active) rows.add(n to last.take(10))
                }
                rows.sortByDescending { it.second }
                val title = if (active) "🟢 Faol mijozlar (30 kun)" else "🔴 Nofaol mijozlar (30+ kun)"
                val body = if (rows.isEmpty()) "Mijoz yo'q."
                else rows.take(20).joinToString("\n") { (n, d) ->
                    val dm = if (d.length >= 10) "${d.substring(8, 10)}.${d.substring(5, 7)}.${d.substring(0, 4)}" else d
                    "• ${n.replaceFirstChar { it.uppercase() }} — oxirgi: $dm"
                }
                _state.update { it.copy(textReport = TextReport(title, body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "narx tarix" — hozirgi T va T1 narxlar + qachon qo'yilgani */
    private fun loadPriceHistory() {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                fun fmt(d: String) = if (d.length >= 16) "${d.substring(8, 10)}.${d.substring(5, 7)} ${d.substring(11, 16)}" else d
                val sb = StringBuilder()
                for (group in listOf("t", "t1")) {
                    sb.append(if (group == "t") "— T narx —\n" else "\n— T1 narx —\n")
                    val cur = getCurrentYukNarx(userId, group)
                    var any = false
                    for ((type, e) in cur) {
                        if (e != null) {
                            sb.append("${type.code.uppercase()}: ${e.price.formatPrice()} so'm  🕒 ${fmt(e.date)}\n")
                            any = true
                        }
                    }
                    if (!any) sb.append("  — yo'q\n")
                }
                _state.update { it.copy(textReport = TextReport("💲 Narx tarixi", sb.toString().trim())) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "narx korish <mijoz>" — mijozning N narxlari */
    private fun loadClientPrices(name: String) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val cn = DaftarParser.normalizeName(name)
                val all = priceDao.getAllForClient(userId, cn)
                fun fmt(d: String) = if (d.length >= 16) "${d.substring(8, 10)}.${d.substring(5, 7)} ${d.substring(11, 16)}" else d
                val body = if (all.isEmpty()) "Bu mijozga alohida narx qo'yilmagan (global T narx ishlatiladi)."
                else all.groupBy { it.priceType }.toSortedMap().map { (type, list) ->
                    val latest = list.maxByOrNull { it.date }!!
                    "${type.uppercase()}: ${latest.price.formatPrice()} so'm  🕒 ${fmt(latest.date)}"
                }.joinToString("\n")
                _state.update { it.copy(textReport = TextReport("💲 ${name.replaceFirstChar { it.uppercase() }} — narxlari", body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "qarz tahlil <mijoz>" — oylar bo'yicha qarz qanday o'sgani */
    private fun loadDebtAnalysis(name: String) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val cp = buildClientPreview(name, null)
                if (cp == null) {
                    _state.update { it.copy(textReport = TextReport("📉 $name", "Bu mijozda yozuv yo'q.")) }
                    return@launch
                }
                val byMonth = sortedMapOf<String, Double>()
                for (tx in cp.transactions.sortedBy { it.date }) {
                    val ym = tx.date.take(7)
                    val delta = when (tx.type.lowercase()) {
                        "p" -> -tx.amount
                        "q" -> tx.amount
                        else -> (cp.priceByTx[tx.id] ?: 0.0) * tx.amount
                    }
                    byMonth[ym] = (byMonth[ym] ?: 0.0) + delta
                }
                var running = 0.0
                val sb = StringBuilder()
                for ((ym, delta) in byMonth) {
                    running += delta
                    val sign = if (delta >= 0) "+" else ""
                    sb.append("$ym:  $sign${delta.toLong().formatMoney()}  →  jami ${running.toLong().formatMoney()}\n")
                }
                sb.append("\n💳 Hozirgi qarz: ${cp.debt.formatMoney()} so'm")
                _state.update { it.copy(textReport = TextReport("📉 ${name.replaceFirstChar { it.uppercase() }} — qarz tahlili", sb.toString())) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
    }

    /** "rasxod" / "rasxod oy" / "rasxod yil" — xarajatlar ro'yxati + JAMI */
    private fun loadRasxod(period: String) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val from: LocalDate; val title: String
                when (period) {
                    "oy" -> { from = today.withDayOfMonth(1); title = "💸 Rasxod — ${today.monthValue}.${today.year}" }
                    "yil" -> { from = today.withDayOfYear(1); title = "💸 Rasxod — ${today.year}-yil" }
                    else -> { from = today; title = "💸 Rasxod — bugun" }
                }
                val list = getRasxodRange(userId, from, today)
                val total = getRasxodTotal(userId, from, today)
                val body = if (list.isEmpty()) "Rasxod yo'q."
                else list.sortedByDescending { it.date }.joinToString("\n") { r ->
                    val d = if (r.date.length >= 10) "${r.date.substring(8, 10)}.${r.date.substring(5, 7)}" else r.date
                    "• $d  ${r.amount.toLong().formatMoney()} so'm" + if (r.note.isNotBlank()) " — ${r.note}" else ""
                } + "\n\n— JAMI: ${total.formatMoney()} so'm"
                _state.update { it.copy(textReport = TextReport(title, body)) }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Xato")) }
            }
        }
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
                val list = getOverdue(userId).filter { it.daysOverdue >= 10 }
                if (list.isEmpty()) {
                    appendChat(ChatItem.Info(nextChatId(), "✅ 10 kundan oshgan qarzdor yo'q."))
                } else {
                    appendChat(ChatItem.DebtRep(nextChatId(), list))
                }
            } catch (e: Exception) {
                _state.update { it.copy(textReport = TextReport("Xatolik", e.message ?: "Noma'lum xato")) }
            }
        }
    }

    private var reportJob: Job? = null
    private fun loadDateReport(date: LocalDate, types: Set<String>? = null, useNarx: Boolean = false, endDate: LocalDate = date, clientFilter: String? = null) {
        reportJob?.cancel()
        reportJob = viewModelScope.launch {
            try {
                val report = if (endDate != date || clientFilter != null) {
                    getDateReport.range(userId, date, endDate, types, useNarx, clientFilter)
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

        // Marker bormi? (a10, b5, p100, n a20, t a15) — YOLG'IZ harf + raqam/probel (so'z ichidagi harf emas)
        val markerRe = Regex("""(^|\s)[abcdkpqnt]\d""", RegexOption.IGNORE_CASE)
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
                    val cp = buildClientPreview(line, existingMonths[cn]) ?: continue
                    list.add(cp)
                }
                _state.update { it.copy(previews = list) }
            } catch (e: Exception) {
                _state.update { it.copy(previews = emptyList()) }
            }
        }
    }

    /** Mijoz tarixini DB'dan yangi tortib ClientPreview quradi. month=null bo'lsa — ma'lumot bor eng oxirgi oy. */
    private suspend fun buildClientPreview(displayName: String, month: java.time.YearMonth?): ClientPreview? {
        val cn = DaftarParser.normalizeName(displayName)
        val history = runCatching { getHistory(userId, cn) }.getOrNull() ?: return null
        if (history.transactions.isEmpty()) return null
        // Oy ko'rsatilmagan bo'lsa — yozuv bor eng oxirgi oyni tanlaymiz (bo'sh oy ochilmasin)
        val effMonth = month ?: run {
            val latest = history.transactions.maxByOrNull { it.date }?.date
            if (latest != null && latest.length >= 7)
                runCatching { java.time.YearMonth.parse(latest.take(7)) }.getOrNull() ?: java.time.YearMonth.now()
            else java.time.YearMonth.now()
        }
        val allPrices = priceDao.getAllForClient(userId, cn)
        val pricesByType = allPrices.groupBy { it.priceType }
        val priceByTx = mutableMapOf<Long, Double?>()
        for (tx in history.transactions) {
            priceByTx[tx.id] = findPriceAtDate(pricesByType[tx.type], tx.date)  // N narx (tannarx emas)
        }
        val asc = history.transactions.sortedBy { it.date }
        var running = 0.0
        val balAfter = mutableMapOf<Long, Long>()
        for ((i, tx) in asc.withIndex()) {
            when (tx.type.lowercase()) {
                "p" -> { running -= tx.amount }
                "q" -> running += tx.amount
                else -> { val p = priceByTx[tx.id]; if (p != null) running += tx.amount * p }
            }
            // v175: OXIRGI yozuvning balansi = umumiy qarz (calcDebt bilan aynan bir xil).
            // Aks holda kasrli narxlarda kunlik balans (1000) va tugmadagi qarz (1002) farq qilardi.
            balAfter[tx.id] = if (i == asc.size - 1) history.debt else Math.round(running)
        }
        return ClientPreview(
            name = displayName,
            debt = history.debt,
            transactions = history.transactions.sortedByDescending { tx -> tx.date },
            priceByTx = priceByTx,
            balanceAfter = balAfter,
            month = effMonth
        )
    }

    private fun findPriceAtDate(prices: List<uz.daftar.app.data.db.entity.PriceHistoryEntity>?, atDate: String): Double? {
        if (prices.isNullOrEmpty()) return null
        val day = atDate.take(10)
        var best: uz.daftar.app.data.db.entity.PriceHistoryEntity? = null
        for (p in prices.sortedBy { it.date }) {
            if (p.date.take(10) <= day) best = p else break
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
            _state.update { it.copy(suggestions = emptyList(), quickFills = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            val list = repo.suggestClients(userId, prefix)
            val names = list.filter { n -> n != prefix.lowercase() }
            // 💡 Tez to'ldirish: aniq mijoz aniqlansa — oxirgi yuk + pulini taklif qilamiz
            val exact = prefix.trim().lowercase()
            val matchName = when {
                list.any { it.equals(exact, ignoreCase = true) } -> exact
                list.size == 1 -> list[0]
                else -> null
            }
            val fills = if (matchName != null) computeQuickFills(matchName) else emptyList()
            _state.update { it.copy(suggestions = names, quickFills = fills) }
        }
    }

    /** Mijozning OXIRGI yuk (A/B/C/D/K) va OXIRGI to'lovini "a20","p500" ko'rinishida qaytaradi */
    private suspend fun computeQuickFills(clientName: String): List<String> {
        val txs = runCatching { repo.getByClient(userId, clientName.lowercase()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.date }
        val fills = mutableListOf<String>()
        val cargo = setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
        txs.firstOrNull { it.type in cargo }?.let { fills.add(it.type.code + fmtAmt(it.amount)) }
        txs.firstOrNull { it.type == TxType.P }?.let { fills.add("p" + fmtAmt(it.amount)) }
        return fills
    }

    private fun fmtAmt(a: Double): String =
        if (a == Math.floor(a) && !a.isInfinite()) a.toLong().toString() else a.toString()

    /** Tez to'ldirish chipi: joriy qatorga qo'shadi (masalan "muborak" → "muborak a20") */
    fun applyQuickFill(fill: String) {
        val cur = state.value.input.trimEnd()
        // v146: chip'dan keyin 1 ta probel — keyingi yozuv zich yopishmasin
        val newInput = if (cur.isEmpty()) "$fill " else "$cur $fill "
        onInputChange(newInput)
    }

    /** Avtotaklif chip bosildi — input'da birinchi so'zni almashtiramiz */
    fun applySuggestion(name: String) {
        val cur = state.value.input
        val prefix = if (cur.contains('\n')) cur.substringBeforeLast('\n') + "\n" else ""
        var lastLine = cur.substringAfterLast('\n').trimStart()
        // Sana prefiksi bo'lsa ("19.06 olim sh") — sanani saqlab qolamiz, ism faqat sanadan keyin almashadi
        var datePrefix = ""
        val dm = Regex("""^(\d{1,2}[.,]\d{1,2}(?:[.,]\d{2,4})?)\s+(.*)$""").matchEntire(lastLine)
        if (dm != null) {
            datePrefix = dm.groupValues[1] + " "
            lastLine = dm.groupValues[2]
        }
        // v138: o'chirish so'zi bo'lsa ("x dv" / "ochir dv") — so'z saqlanadi, faqat ism almashadi
        var delPrefix = ""
        val dw = Regex("""^(x|ochir|o'chir|ochirish)\s+(.*)$""", RegexOption.IGNORE_CASE).matchEntire(lastLine)
        if (dw != null) {
            delPrefix = dw.groupValues[1] + " "
            lastLine = dw.groupValues[2]
        }
        // Yozilgan ism qismini topamiz (ko'p so'zli ismlar uchun ham): "olim shash" -> butun shu qism almashadi
        val lname = name.lowercase()
        var typed = ""
        for (w in lastLine.split(" ")) {
            if (w.isBlank()) break
            val cand = if (typed.isEmpty()) w else "$typed $w"
            if (lname.startsWith(cand.lowercase())) typed = cand else break
        }
        val rest = if (typed.isEmpty()) lastLine.substringAfter(' ', missingDelimiterValue = "")
                   else lastLine.removePrefix(typed).trimStart()
        val tail = if (rest.isBlank()) "$name " else "$name $rest"
        onInputChange(prefix + datePrefix + delPrefix + tail)
    }

    /**
     * 🎤 Ovoz natijasi:
     * 1) Yozuv (ism + yuk/pul) tushunarli bo'lsa — HA/YO'Q tasdiq oynasi.
     * 2) Aniq mijoz nomi bo'lsa — tarixni darhol ko'rsatadi.
     * 3) Aks holda — matn maydonga tushadi, qo'lda tuzatasiz.
     */
    fun onVoiceInput(spoken: String) {
        val t = spoken.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            // 1) "rasxod 200" / "rasxod 200 gaz" / "rasxod 200 magazin" → to'g'ridan rasxodga
            val rm = Regex("""^(?:rasxod|rasxot|rashod|r)\s+(\d+(?:[.,]\d+)?)\s*(.*)$""", RegexOption.IGNORE_CASE)
                .matchEntire(t)
            if (rm != null) {
                val amt = rm.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (amt != null && amt > 0) {
                    val note = rm.groupValues[2].trim()
                    runCatching { addRasxod(userId, amt, note) }
                    appendChat(ChatItem.Info(nextChatId(),
                        "💸 Rasxod saqlandi: ${amt.toLong().formatMoney()}" + (if (note.isNotBlank()) "  $note" else "") + " · 🕒 " + java.time.LocalTime.now(APP_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))))
                    persistChat()
                    onInputChange("")
                    return@launch
                }
            }

            val lines = t.lines().map { it.trim() }.filter { it.isNotBlank() }
            val allParsed = lines.isNotEmpty() && lines.all { ln ->
                val r = DaftarParser.parse(ln)
                r is ParseResult.Success && r.entry.items.isNotEmpty()
            }
            if (allParsed) {
                onInputChange(t)
                _state.update { it.copy(voiceConfirm = t) }
                return@launch
            }
            // 2) Aniq mijoz nomi — tarixni DARHOL ko'rsatish (yozuvda qoldirmaymiz)
            fun norm(x: String) = x.lowercase().replace("'", "").replace("`", "")
                .replace(Regex("[.,!?]"), "").replace(Regex("\\s+"), " ").trim()
            val tn = norm(t)
            // Ovoz ko'p so'z qaytarishi mumkin ("olim shashlik") — ism odatda birinchi so'z(lar)
            val firstWord = tn.substringBefore(' ').trim()
            val names = runCatching { repo.allClientNames(userId) }.getOrDefault(emptyList())
            val exact = names.firstOrNull { norm(it) == tn }                          // to'liq mos
                ?: names.filter { tn == norm(it) || tn.startsWith(norm(it) + " ") }    // ism + qo'shimcha so'z
                    .maxByOrNull { it.length }
                ?: names.firstOrNull { norm(it) == firstWord }                         // birinchi so'z = ism
                ?: names.firstOrNull { norm(it).startsWith(tn) && tn.length >= 2 }     // qisman yozilgan
                ?: names.firstOrNull { tn.length >= 3 && norm(it).contains(tn) }       // ichida
            val nameToShow = exact ?: firstWord.ifBlank { t }
            // Tarixni to'g'ridan-to'g'ri DB'dan tortib chatga qo'shamiz
            // (send() bilan poyga bo'lmasin — previews asinxron yuklanadi)
            val cp = runCatching { buildClientPreview(nameToShow, null) }.getOrNull()
            if (cp != null) {
                val cap = cp.name.replaceFirstChar { c -> c.uppercase() }
                appendChat(
                    ChatItem.User(nextChatId(), cap),
                    ChatItem.History(nextChatId(), cp)
                )
                persistChat()
                onInputChange("")
                return@launch
            }
            // Tarix topilmadi — matnni maydonga qo'yamiz, qo'lda tuzatasiz
            onInputChange(exact ?: t)
        }
    }

    fun voiceConfirmYes() {
        _state.update { it.copy(voiceConfirm = null) }
        send()
    }

    fun voiceConfirmNo() {
        // Matn maydonда qoladi — xohlasangiz qo'lda tuzatib yuborasiz
        _state.update { it.copy(voiceConfirm = null) }
    }

    fun send() {
        val s = state.value
        val raw = s.input.trim()
        // v169: ORALIQ SANA hisoboti — "01.05 20.05" (+ ixtiyoriy filtr: "a c" / "pul" / bo'sh=hammasi)
        val rangeM = Regex("""^(\d{1,2})[.](\d{1,2})\s+(\d{1,2})[.](\d{1,2})(?:\s+(.+))?$""")
            .matchEntire(raw)
        if (rangeM != null) {
            val d1 = rangeM.groupValues[1].toIntOrNull(); val m1 = rangeM.groupValues[2].toIntOrNull()
            val d2 = rangeM.groupValues[3].toIntOrNull(); val m2 = rangeM.groupValues[4].toIntOrNull()
            val filterRaw = rangeM.groupValues[5].trim().lowercase()
            if (d1 != null && m1 != null && d2 != null && m2 != null) {
                viewModelScope.launch {
                    runCatching { buildRangeReport(d1, m1, d2, m2, filterRaw, raw) }
                        .onFailure { appendChat(ChatItem.Info(nextChatId(), "❓ Oraliq hisobotda xato: ${it.message}")) }
                }
                _state.update { it.copy(input = "", parsed = emptyList(), errorMessage = null, suggestions = emptyList(), quickFills = emptyList(), isViewCommand = false, previews = emptyList()) }
                return
            }
        }
        // 0) "delete 02.06" — tasdiq so'raydi (hali o'chirmaydi)
        if (s.deleteAllDate != null) {
            requestDeleteAll()
            return
        }
        // 0b) "ochir ali" — mijoz tarixini o'chirish tasdig'i
        if (s.deleteClientName != null) {
            requestDeleteClient()
            return
        }
        // 0c) Rasxod saqlash ("r100 gaz")
        if (s.rasxodAmount != null) {
            val amt = s.rasxodAmount
            val note = s.rasxodNote
            val typed = s.input.trim()
            viewModelScope.launch {
                runCatching { addRasxod(userId, amt, note) }
                _state.update { it.copy(input = "", parsed = emptyList(), rasxodAmount = null, rasxodNote = "") }
                appendChat(ChatItem.User(nextChatId(), typed))
                appendChat(ChatItem.Info(nextChatId(), "💸 Rasxod saqlandi: ${amt.toLong().formatMoney()} so'm" + (if (note.isNotBlank()) " — $note" else "") + " · 🕒 " + java.time.LocalTime.now(APP_ZONE).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))))
            }
            return
        }
        // 1) X-o'chirish komandasi
        if (s.isDeleteCommand) {
            handleDeleteCommand(s.input)
            return
        }
        // 2) Global tannarx (T / T1)
        val gp = s.globalPrice
        if (gp != null) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true) }
                try {
                    setGlobalPrice(userId, gp.group, gp.prices, gp.date)
                    val label = gp.prices.entries.joinToString(", ") { "${it.key.uppercase()}=${it.value}" }
                    appendChat(
                        ChatItem.User(nextChatId(), raw),
                        ChatItem.Info(nextChatId(), "✅ Global ${gp.group.uppercase()} narx yangilandi\n$label")
                    )
                    _state.update { it.copy(isSending = false, input = "", globalPrice = null, errorMessage = null, suggestions = emptyList(), quickFills = emptyList(), dateReport = null, textReport = null, previews = emptyList(), isViewCommand = false) }
                } catch (e: Exception) {
                    appendChat(ChatItem.Info(nextChatId(), "❌ Xato: ${e.message}"))
                    _state.update { it.copy(isSending = false) }
                }
            }
            return
        }
        // 2.5) t1set — mavjud yozuvlarni T1 ga o'tkazish
        val ops = s.t1set
        if (ops != null) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true) }
                try {
                    appendChat(ChatItem.User(nextChatId(), raw))
                    var total = 0
                    val df = java.time.format.DateTimeFormatter.ofPattern("dd.MM")
                    val lines = StringBuilder("✅ T1 tarifga o'tkazildi\n")
                    for (op in ops) {
                        val n = setT1Tier(userId, op.client, op.type, op.start, op.end)
                        total += n
                        val typeLabel = op.type?.uppercase() ?: "hammasi"
                        val dateLabel = if (op.start == op.end) op.start.format(df) else "${op.start.format(df)}–${op.end.format(df)}"
                        val nameCap = op.client.replaceFirstChar { it.uppercase() }
                        lines.append("• $nameCap ($typeLabel, $dateLabel): $n yozuv\n")
                    }
                    lines.append("Jami: $total yozuv")
                    appendChat(ChatItem.Info(nextChatId(), lines.toString().trimEnd()))
                    _state.update { it.copy(isSending = false, input = "", t1set = null, errorMessage = null, suggestions = emptyList(), quickFills = emptyList()) }
                } catch (e: Exception) {
                    appendChat(ChatItem.Info(nextChatId(), "❌ Xato: ${e.message}"))
                    _state.update { it.copy(isSending = false) }
                }
            }
            return
        }
        // 2.7) GPT / prognoz
        val aiq = s.aiQuery
        if (aiq != null) {
            appendChat(ChatItem.User(nextChatId(), raw))
            _state.update { it.copy(isSending = true, input = "", aiQuery = null, errorMessage = null, suggestions = emptyList(), quickFills = emptyList()) }
            viewModelScope.launch {
                val answer = handleAi(aiq)
                appendChat(ChatItem.Info(nextChatId(), answer))
                _state.update { it.copy(isSending = false) }
            }
            return
        }
        // 2.8) edit / undo / bekor
        if (s.isEditUndo) {
            val low = raw.lowercase()
            appendChat(ChatItem.User(nextChatId(), raw))
            _state.update { it.copy(isSending = true, input = "", isEditUndo = false, errorMessage = null, suggestions = emptyList(), quickFills = emptyList()) }
            viewModelScope.launch {
                val msg = try {
                    if (low == "undo" || low == "bekor") {
                        val name = undoLast(userId)
                        if (name == null) "Bekor qilinadigan yozuv yo'q."
                        else "✅ Oxirgi yozuv bekor qilindi: ${name.replaceFirstChar { it.uppercase() }}"
                    } else {
                        runEdit(raw)
                    }
                } catch (e: Exception) {
                    "❌ Xato: ${e.message}"
                }
                appendChat(ChatItem.Info(nextChatId(), msg))
                _state.update { it.copy(isSending = false) }
            }
            return
        }
        // 3) Yozuv (tranzaksiya yoki narx) — chatga ✅ Saqlandi qo'shamiz
        if (s.parsed.isNotEmpty()) {
            viewModelScope.launch {
                _state.update { it.copy(isSending = true) }
                try {
                    appendChat(ChatItem.User(nextChatId(), raw))
                    // ATOMIK saqlash (@Transaction): hamma yozuv BIRGA — telefon o'chsa yarim qolmaydi
                    addTx.invokeAll(userId, s.parsed)
                    for (entry in s.parsed) {
                        appendChat(ChatItem.Saved(nextChatId(), buildSavedInfoFromEntry(entry)))
                    }
                    _state.update { it.copy(isSending = false, input = "", parsed = emptyList(), isDeleteCommand = false, errorMessage = null, suggestions = emptyList(), quickFills = emptyList(), dateReport = null, textReport = null, previews = emptyList(), pinnedView = false, isViewCommand = false) }
                } catch (e: Exception) {
                    appendChat(ChatItem.Info(nextChatId(), "❌ Xato: ${e.message}"))
                    _state.update { it.copy(isSending = false) }
                }
            }
            return
        }
        // 4) Ko'rinish natijalari (jonli yuklangan) — chatga qo'shamiz (stack bo'ladi)
        if (s.dateReport != null) {
            appendChat(ChatItem.User(nextChatId(), raw), ChatItem.DateRep(nextChatId(), s.dateReport))
            _state.update { it.copy(input = "", dateReport = null, isViewCommand = false, errorMessage = null, suggestions = emptyList(), quickFills = emptyList()) }
            return
        }
        if (s.textReport != null) {
            appendChat(ChatItem.User(nextChatId(), raw), ChatItem.TextRep(nextChatId(), s.textReport))
            _state.update { it.copy(input = "", textReport = null, isViewCommand = false, errorMessage = null, suggestions = emptyList(), quickFills = emptyList()) }
            return
        }
        if (s.previews.isNotEmpty()) {
            val items = mutableListOf<ChatItem>(ChatItem.User(nextChatId(), raw))
            s.previews.forEach { items.add(ChatItem.History(nextChatId(), it)) }
            appendChat(*items.toTypedArray())
            _state.update { it.copy(input = "", previews = emptyList(), isViewCommand = false, errorMessage = null, suggestions = emptyList(), quickFills = emptyList()) }
            return
        }
    }

    /** ✅ Saqlandi matnini bot uslubida quradi (narx + qarz bilan) */
    private suspend fun buildSavedText(entry: uz.daftar.app.core.parser.ParsedEntry): String {
        val cn = entry.clientName.lowercase()
        val prices = runCatching { getUnitPrices(userId, cn) }.getOrDefault(emptyMap())
        val debt = runCatching { calcDebt(userId, cn) }.getOrDefault(0L)
        val nameCap = entry.clientName.replaceFirstChar { it.uppercase() }
        val sb = StringBuilder()
        sb.append("✅ Saqlandi\n")
        sb.append("📅 ").append(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))).append("\n")
        sb.append(nameCap).append("\n")
        val cargo = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
        var hasItem = false
        for (t in cargo) {
            val q = entry.items[t] ?: continue
            hasItem = true
            val p = prices[t]
            if (p != null) sb.append("  ${t.code.uppercase()}: ${q.formatQty()} × ${p.formatQty()} = ${(q * p).formatMoney()}\n")
            else sb.append("  ${t.code.uppercase()}: ${q.formatQty()}  (narx yo'q)\n")
        }
        entry.items[TxType.P]?.let { sb.append("  P: ${it.formatMoney()}\n") }
        entry.items[TxType.Q]?.let { sb.append("  Q: ${it.formatMoney()}\n") }
        if (!hasItem && entry.clientPrices.isNotEmpty()) {
            sb.append("  💲 Narx yangilandi: ")
            sb.append(entry.clientPrices.entries.joinToString(", ") { "${it.key.code.uppercase()}=${it.value.formatQty()}" })
            sb.append("\n")
        }
        sb.append("💳 Qarz: ${debt.formatMoney()} so'm")
        return sb.toString()
    }

    /** ✅ Saqlandi — chiroyli karta uchun struktura (ParsedEntry'dan) */
    private suspend fun buildSavedInfoFromEntry(entry: uz.daftar.app.core.parser.ParsedEntry): SavedInfo {
        val cn = entry.clientName.lowercase()
        val prices = runCatching { getUnitPrices(userId, cn) }.getOrDefault(emptyMap())
        val debt = runCatching { calcDebt(userId, cn) }.getOrDefault(0L)
        val nameCap = entry.clientName.replaceFirstChar { it.uppercase() }
        val lines = mutableListOf<SavedLine>()
        val cargo = listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
        for (t in cargo) {
            val q = entry.items[t] ?: continue
            val p = prices[t]
            if (p != null) lines.add(SavedLine(t.code, "${t.code.uppercase()}: ${q.formatQty()} × ${p.formatQty()} = ${(q * p).formatMoney()} so'm", "Yuk"))
            else lines.add(SavedLine(t.code, "${t.code.uppercase()}: ${q.formatQty()}", "Narx yo'q"))
        }
        entry.items[TxType.P]?.let { lines.add(SavedLine("p", "P: ${it.formatMoney()}", "Pul kirimi")) }
        entry.items[TxType.Q]?.let { lines.add(SavedLine("q", "Q: ${it.formatMoney()}", "Qo'lda qarz")) }
        if (lines.isEmpty() && entry.clientPrices.isNotEmpty()) {
            val pr = entry.clientPrices.entries.joinToString(", ") { "${it.key.code.uppercase()}=${it.value.formatQty()}" }
            lines.add(SavedLine("c", "Narx: $pr", "Narx yangilandi"))
        }
        // SANA: yozuvning HAQIQIY sanasini ko'rsatamiz (bugun bo'lmasa ham) — chalkashlik bo'lmasin
        val effDate = entry.date ?: LocalDate.now()
        return SavedInfo(
            nameCap,
            effDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")),
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            lines, debt
        )
    }

    /** "x", "DD.MM x", "x DD.MM", "x NAME", "x NAME a10", "DD.MM\nx NAME" — barchasini taniydi */
    private fun isDeleteCommandText(text: String): Boolean {
        val lines = text.lines().map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return false
        val first = lines.first()
        // Eski: "x" yolg'iz yoki "DD.MM x" / "x DD.MM" + keyingi qatorlar
        if (lines.size >= 2 && (first == "x" || DELETE_X_RE.matches(first))) return true
        // Yangi: biror qator "x NAME" bilan boshlansa
        if (lines.any { it.startsWith("x ") }) return true
        // Yangi: "DD.MM x NAME" bir qatorda (masalan: 09.06 x moxon)
        if (Regex("""^\d{1,2}[.,]\d{1,2}(?:[.,]\d{2,4})?\s+x\s+.+$""").matches(first)) return true
        // Birinchi qator sana, keyingilar "x NAME"
        if (Regex("""^\d{1,2}\.\d{1,2}$""").matches(first) &&
            lines.drop(1).any { it.startsWith("x ") }) return true
        return false
    }

    private fun handleDeleteCommand(text: String) {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (rawLines.isEmpty()) return
        var date: LocalDate = today()

        // Birinchi qatorni tahlil qilish — sana aniqlash, qaerdan boshlash
        val firstLower = rawLines.first().lowercase()
        val dateXNameRe = Regex("""^(\d{1,2})[.,](\d{1,2})(?:[.,](\d{2,4}))?\s+x\s+(.+)$""")
        val dateXNameMatch = dateXNameRe.matchEntire(firstLower)
        val datePureMatch = Regex("""^(\d{1,2})\.(\d{1,2})$""").matchEntire(firstLower)
        val dateXMatch = DELETE_X_RE.matchEntire(firstLower)
        val startIdx = when {
            dateXNameMatch != null -> {
                runCatching {
                    // v155: withMonth().withDayOfMonth() 31-kunli oyda crash berardi — LocalDate.of xavfsiz
                    val mm = dateXNameMatch.groupValues[2].toInt()
                    val dd = dateXNameMatch.groupValues[1].toInt()
                    date = LocalDate.of(today().year, mm, dd)
                }
                // "09.06 x moxon" → birinchi qatorni "x moxon" ga aylantiramiz
                rawLines[0] = "x " + dateXNameMatch.groupValues[4].trim()
                0
            }
            datePureMatch != null -> {
                runCatching {
                    val mm = datePureMatch.groupValues[2].toInt()
                    val dd = datePureMatch.groupValues[1].toInt()
                    date = LocalDate.of(today().year, mm, dd)
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
                        // 🗑 KARZINAGA (7 kun tiklanadi) — to'g'ridan-to'g'ri o'chirmaymiz
                        matching.forEach { runCatching { delToKarzina(userId, it.id) } }
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
                ids.forEach { delToKarzina(userId, it) }
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
                delToKarzina(userId, id)
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
