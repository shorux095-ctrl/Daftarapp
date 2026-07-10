package uz.daftar.app.ui.screen.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.daftar.app.data.db.dao.PriceHistoryDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.data.db.entity.PriceHistoryEntity
import uz.daftar.app.data.db.entity.TransactionEntity
import uz.daftar.app.domain.model.TxType
import uz.daftar.app.domain.usecase.EditTransactionUseCase
import uz.daftar.app.domain.usecase.DeleteToKarzinaUseCase
import java.time.LocalDate
import javax.inject.Inject

data class EditState(
    val isLoading: Boolean = true,
    val original: TransactionEntity? = null,
    val type: TxType = TxType.A,
    val amount: String = "",
    val clientName: String = "",
    val allNames: List<String> = emptyList(),
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val nNarx: String = "",
    val origNarx: String = "",  // v154: yuklanishdagi asl narx — o'zgarmasa qayta yozilmaydi (dublikat oldini oladi)
    val tNarx: String = "",
    val isT1: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val isCargo: Boolean get() = type in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
}

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val txDao: TransactionDao,
    private val priceDao: PriceHistoryDao,
    private val editUC: EditTransactionUseCase,
    private val deleteToKarzina: DeleteToKarzinaUseCase
) : ViewModel() {

    private val userId: Long = 1L
    private val txId: Long = savedState.get<String>("txId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    init { load() }

    private fun fmtNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun load() {
        viewModelScope.launch {
            try {
                // v152: butun bazani emas, faqat kerakli yozuvni olamiz (katta bazada ham tez)
                val tx = txDao.getById(txId)
                if (tx == null) {
                    _state.update { it.copy(isLoading = false, error = "Yozuv topilmadi") }
                    return@launch
                }
                val type = TxType.fromCode(tx.type) ?: TxType.A
                val day = runCatching { LocalDate.parse(tx.date.take(10)) }.getOrDefault(LocalDate.now())
                var nNarx = ""
                if (type in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                    val p = runCatching { priceDao.getPriceAt(userId, tx.clientName, tx.type, tx.date) }.getOrNull()
                    if (p != null) nNarx = fmtNum(p)
                }
                val names = runCatching { txDao.getAllClientNames(userId) }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        isLoading = false,
                        original = tx,
                        type = type,
                        amount = fmtNum(tx.amount),
                        clientName = tx.clientName,
                        allNames = names,
                        note = tx.note ?: "",
                        date = day,
                        nNarx = nNarx,
                        origNarx = nNarx,
                        tNarx = tx.tOverride?.let { v -> fmtNum(v) } ?: "",
                        isT1 = tx.costTier == "t1"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setType(t: TxType) = _state.update { it.copy(type = t) }
    fun setClientName(s: String) = _state.update { it.copy(clientName = s) }
    fun setNote(s: String) = _state.update { it.copy(note = s) }

    /** Yozuvni KARZINAGA ko'chiradi (7 kun ichida tiklash mumkin). */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { deleteToKarzina(userId, txId) }
            onDone()
        }
    }

    fun setAmount(s: String) = _state.update { it.copy(amount = s) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun setNNarx(s: String) = _state.update { it.copy(nNarx = s) }
    fun setTNarx(s: String) = _state.update { it.copy(tNarx = s) }
    fun setIsT1(b: Boolean) = _state.update { it.copy(isT1 = b) }

    fun save() = saveInternal(asNew = false)

    /** ➕ Joriy formani YANGI yozuv sifatida QO'SHADI — asl yozuv o'zgarmaydi (pul/yuk qo'shish uchun) */
    fun saveAsNew() = saveInternal(asNew = true)

    /** v144: ➕ dialogdan — TANLANGAN tur va miqdor bilan YANGI yozuv qo'shadi. Asl yozuvga TEGMAYDI. */
    fun addNew(type: TxType, amountStr: String, note: String = "", narxStr: String = "") {
        val s = state.value
        val orig = s.original ?: return
        val amt = amountStr.replace(",", ".").toDoubleOrNull()
        if (amt == null || amt <= 0) {
            _state.update { it.copy(error = "Noto'g'ri miqdor") }
            return
        }
        val timePart = if (orig.date.length > 10) orig.date.substring(10) else " 12:00:00"
        val newDate = s.date.toString() + timePart
        val cleanName = uz.daftar.app.core.parser.DaftarParser.normalizeName(s.clientName).ifBlank { orig.clientName }
        viewModelScope.launch {
            try {
                editUC(
                    id = 0L,
                    userId = orig.userId,
                    clientName = cleanName,
                    note = note.trim().ifBlank { null },
                    type = type,
                    amount = amt,
                    date = newDate,
                    tOverride = null,
                    costTier = null
                )
                // v169: yangi yukka NARX ham qo'yish mumkin (shu sanadan boshlab shu mijoz uchun)
                val isCargoType = type in listOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)
                val narx = narxStr.replace(",", ".").toDoubleOrNull()
                if (isCargoType && narx != null && narx > 0) {
                    priceDao.insert(
                        PriceHistoryEntity(
                            userId = orig.userId,
                            clientName = cleanName.lowercase(),
                            priceType = type.code,
                            price = narx,
                            date = newDate
                        )
                    )
                }
                _state.update { it.copy(isSaved = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun saveInternal(asNew: Boolean) {
        val s = state.value
        val orig = s.original ?: return
        val amt = s.amount.replace(",", ".").toDoubleOrNull()
        if (amt == null || amt <= 0) {
            _state.update { it.copy(error = "Noto'g'ri miqdor") }
            return
        }
        val timePart = if (orig.date.length > 10) orig.date.substring(10) else " 12:00:00"
        val newDate = s.date.toString() + timePart
        val tOv = s.tNarx.replace(",", ".").toDoubleOrNull()

        // v152: ism parserdagi kabi NORMALIZE qilinadi (bitta mijoz ikkiga bo'linmasin)
        val cleanName = uz.daftar.app.core.parser.DaftarParser.normalizeName(s.clientName).ifBlank { orig.clientName }
        viewModelScope.launch {
            try {
                editUC(
                    id = if (asNew) 0L else orig.id,
                    userId = orig.userId,
                    clientName = cleanName,
                    note = s.note.trim().ifBlank { null },
                    type = s.type,
                    amount = amt,
                    date = newDate,
                    tOverride = if (s.isCargo) tOv else null,
                    costTier = if (s.isCargo && s.isT1) "t1" else null
                )
                if (s.isCargo) {
                    val n = s.nNarx.replace(",", ".").toDoubleOrNull()
                    // v154: narx FAQAT o'zgarganda yoziladi (dublikat qatorlar to'planmaydi)
                    val changed = s.nNarx.trim() != s.origNarx.trim()
                    if (n != null && n > 0 && changed) {
                        priceDao.insert(
                            PriceHistoryEntity(
                                userId = orig.userId,
                                // v152: narx YANGI (tahrirlangan) ismga yoziladi — eski xato: orig.clientName
                                clientName = cleanName.lowercase(),
                                priceType = s.type.code,
                                price = n,
                                date = newDate
                            )
                        )
                    }
                }
                _state.update { it.copy(isSaved = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
