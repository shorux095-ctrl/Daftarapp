package uz.daftar.app.core.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Raqam formatlash:
 *  - PUL (formatMoney): tiyinsiz, butunga yaxlitlanadi, minglik probel bilan → "5 640"
 *  - MIQDOR/NARX (formatQty): kasr NUQTA bilan → "818.23", "4.5", butun qismi probel bilan → "1 818.23"
 *
 * DIQQAT: ilgari Locale("uz") kasr vergulini (818,23) probelga almashtirib "818 23" qilardi — xato edi.
 */

/** Butun sonni minglik probel bilan: 1500000 → "1 500 000" */
fun Long.formatMoney(): String {
    val neg = this < 0
    val s = abs(this).toString()
    val sb = StringBuilder()
    val n = s.length
    for (i in s.indices) {
        if (i > 0 && (n - i) % 3 == 0) sb.append(' ')
        sb.append(s[i])
    }
    return (if (neg) "-" else "") + sb.toString()
}

/** PUL — tiyinsiz (butunga yaxlitlanadi): 5640.04 → "5 640" */
fun Double.formatMoney(): String = this.roundToLong().formatMoney()

/** MIQDOR/NARX — kasr nuqta bilan, ortiqcha nollarsiz: 818.23 → "818.23", 4.50 → "4.5", 30.0 → "30" */
fun Double.formatQty(): String {
    val rounded = (this * 100.0).roundToLong() / 100.0
    val whole = rounded.toLong()
    val fracInt = ((abs(rounded) - abs(whole)) * 100.0).roundToLong().toInt()
    val wholeStr = whole.formatMoney()
    if (fracInt == 0) return wholeStr
    val fracStr = fracInt.toString().padStart(2, '0').trimEnd('0')
    return "$wholeStr.$fracStr"
}

/** Narx — miqdor kabi (kasr nuqta bilan) */
fun Double.formatPrice(): String = this.formatQty()
