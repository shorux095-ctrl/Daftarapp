package uz.daftar.app.core.parser

import uz.daftar.app.domain.model.TxType
import java.time.LocalDate

/**
 * Parser natijasi — "ali a10 n a20 t a25" kabi matnni tahlil qilgandan keyin.
 * Bot.py'dagi parser logikasi bilan bir xil.
 */
data class ParsedEntry(
    val clientName: String,             // "ali" — normalize qilingan
    val date: LocalDate?,               // Sana prefiksi bo'lsa (02.03 → bugungi yilda 02.03)
    val items: Map<TxType, Double>,     // {A: 10, B: 5, P: 100, Q: 50}
    val clientPrices: Map<TxType, Double>,   // n a20 → per-client A narxi 20
    val tPrices: Map<TxType, Double>,        // t a25 → T narx (global)
    val tOneTime: Map<TxType, Double>,       // t? a30 → bir martalik T narx
    val rawText: String                  // Asl matn
) {
    val hasYuk: Boolean
        get() = items.keys.any { it.isYuk }

    val hasPayment: Boolean
        get() = items.contains(TxType.P)

    /** Yozuv bo'sh emasligi (kamida bir item bor). */
    val isValid: Boolean
        get() = items.isNotEmpty() && clientName.isNotBlank()
}

/** Parser xatolari */
sealed class ParseError(val message: String) {
    object EmptyInput : ParseError("Bo'sh matn")
    object NoClientName : ParseError("Mijoz ismi topilmadi")
    object NoItems : ParseError("Yuk yoki to'lov topilmadi (a10, b5, p100 kabi)")
    data class InvalidFormat(val detail: String) : ParseError("Noto'g'ri format: $detail")
}

/** Parser javobi — yo natija, yo xato */
sealed class ParseResult {
    data class Success(val entry: ParsedEntry) : ParseResult()
    data class Failure(val error: ParseError) : ParseResult()
}
