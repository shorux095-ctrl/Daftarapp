package uz.daftar.app.core.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Bot.py'dagi fmt() ga ekvivalent.
 * 1000 → "1 000"
 * 1500000 → "1 500 000"
 *
 * Pul: ichida Long (tiyin) saqlanadi, UI'da formatlanadi.
 */
fun Long.formatMoney(): String {
    val nf = NumberFormat.getInstance(Locale("uz"))
    nf.isGroupingUsed = true
    return nf.format(this).replace(",", " ").replace("\u00A0", " ")
}

fun Double.formatMoney(): String {
    val nf = NumberFormat.getInstance(Locale("uz"))
    nf.isGroupingUsed = true
    nf.maximumFractionDigits = if (this == this.toLong().toDouble()) 0 else 2
    return nf.format(this).replace(",", " ").replace("\u00A0", " ")
}

fun Double.formatPrice(): String {
    return if (this == this.toLong().toDouble()) {
        this.toLong().formatMoney()
    } else {
        String.format(Locale.US, "%.2f", this)
            .let { it.toDoubleOrNull()?.formatMoney() ?: it }
    }
}
