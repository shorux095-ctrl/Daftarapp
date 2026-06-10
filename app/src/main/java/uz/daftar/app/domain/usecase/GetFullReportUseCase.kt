package uz.daftar.app.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.daftar.app.data.db.dao.ClientPriceDao
import uz.daftar.app.data.db.dao.TransactionDao
import uz.daftar.app.domain.model.TxType
import javax.inject.Inject
import kotlin.math.roundToLong

/** Bitta mijoz qatori: qarz + N narxlar (sotuv). */
data class FullReportRow(
    val name: String,
    val debt: Long,
    val nA: Double?, val nB: Double?, val nC: Double?, val nD: Double?, val nK: Double?
)

/** To'liq hisobot — barcha ma'lumot bir joyda. */
data class FullReport(
    val rows: List<FullReportRow>,
    // Global T narx (tannarx)
    val tA: Double?, val tB: Double?, val tC: Double?, val tD: Double?, val tK: Double?,
    val totalDebt: Long,      // jami qarz
    val totalPaid: Long,      // jami to'lov (P)
    val clientCount: Int,     // mijozlar soni
    val qtyA: Double, val qtyB: Double, val qtyC: Double, val qtyD: Double, val qtyK: Double // yuklar jami
)

/**
 * To'liq hisobotni TEZ hisoblaydi: GetAllClientsUseCase (2 so'rov) + client_prices (1)
 * + transactions (1) + global T narx. N+1 yo'q.
 */
class GetFullReportUseCase @Inject constructor(
    private val getAllClients: GetAllClientsUseCase,
    private val clientPriceDao: ClientPriceDao,
    private val txDao: TransactionDao,
    private val getCurrentYukNarx: GetCurrentYukNarxUseCase
) {
    suspend operator fun invoke(userId: Long): FullReport = withContext(Dispatchers.Default) {
        val clients = getAllClients(userId)
        val cps = clientPriceDao.getAllForUser(userId).associateBy { it.clientName.lowercase() }
        val allTx = txDao.getAllForUser(userId)
        val gt = getCurrentYukNarx(userId, "t")

        val totalPaid = allTx.filter { it.type == TxType.P.code }.sumOf { it.amount }.roundToLong()
        val totalDebt = clients.sumOf { it.debt }
        fun qty(code: String) = allTx.filter { it.type == code }.sumOf { it.amount }

        val rows = clients.map { c ->
            val cp = cps[c.name.lowercase()]
            FullReportRow(
                name = c.name,
                debt = c.debt,
                nA = cp?.aPrice, nB = cp?.bPrice, nC = cp?.cPrice, nD = cp?.dPrice, nK = cp?.kPrice
            )
        }

        FullReport(
            rows = rows,
            tA = gt[TxType.A]?.price, tB = gt[TxType.B]?.price, tC = gt[TxType.C]?.price,
            tD = gt[TxType.D]?.price, tK = gt[TxType.K]?.price,
            totalDebt = totalDebt,
            totalPaid = totalPaid,
            clientCount = clients.size,
            qtyA = qty("a"), qtyB = qty("b"), qtyC = qty("c"), qtyD = qty("d"), qtyK = qty("k")
        )
    }
}
