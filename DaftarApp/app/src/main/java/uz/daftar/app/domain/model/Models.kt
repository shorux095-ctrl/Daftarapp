package uz.daftar.app.domain.model

import java.time.LocalDateTime

/** Tranzaksiya turi — bot.py'dagi shifrlar */
enum class TxType(val code: String, val label: String) {
    A("a", "A"),
    B("b", "B"),
    C("c", "C"),
    D("d", "D"),
    K("k", "K"),
    P("p", "To'lov"),
    Q("q", "Qarz");

    val isYuk: Boolean get() = this in setOf(A, B, C, D, K, Q)
    val isPayment: Boolean get() = this == P

    companion object {
        fun fromCode(code: String): TxType? = entries.firstOrNull { it.code == code.lowercase() }
    }
}

/** Bitta tranzaksiya — UI uchun */
data class Transaction(
    val id: Long,
    val clientName: String,
    val type: TxType,
    val amount: Double,
    val date: LocalDateTime,
    val tOverride: Double? = null
)

/** Kunlik hisobot — bot.py'dagi rep_daily */
data class DailyReport(
    val date: java.time.LocalDate,
    val rows: List<DailyRow>,
    val totalByType: Map<TxType, Double>,
    val totalRevenue: Long
) {
    data class DailyRow(
        val rowNumber: Int,
        val clientName: String,
        val items: Map<TxType, Double>,
        val price: Double?
    )
}

/** Mijoz hozirgi holati */
data class ClientSummary(
    val name: String,
    val debt: Long,
    val daysSinceLastYuk: Int,
    val daysSinceLastPayment: Int?
)
