package uz.daftar.app.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import javax.inject.Inject

/** Bitta oy nuqtasi: daromad, sof foyda, to'lov. */
data class MonthPoint(
    val label: String,     // "6.26"
    val year: Int,
    val month: Int,
    val revenue: Long,
    val profit: Long,
    val payments: Long
)

/** Oxirgi N oy trendi (har oy uchun GetMonthlyReportUseCase). */
class GetMonthlyTrendUseCase @Inject constructor(
    private val getMonthly: GetMonthlyReportUseCase
) {
    suspend operator fun invoke(userId: Long, months: Int = 6): List<MonthPoint> =
        withContext(Dispatchers.Default) {
            val now = YearMonth.now()
            (months - 1 downTo 0).map { back ->
                val ym = now.minusMonths(back.toLong())
                val r = runCatching { getMonthly(userId, ym.year, ym.monthValue) }.getOrNull()
                MonthPoint(
                    label = SHORT_UZ[ym.monthValue - 1],
                    year = ym.year,
                    month = ym.monthValue,
                    revenue = r?.revenue ?: 0L,
                    profit = r?.profit ?: 0L,
                    payments = r?.payments ?: 0L
                )
            }
        }

    private companion object {
        val SHORT_UZ = listOf("Yan", "Fev", "Mar", "Apr", "May", "Iyn", "Iyl", "Avg", "Sen", "Okt", "Noy", "Dek")
    }
}
