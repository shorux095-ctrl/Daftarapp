package uz.daftar.app.core.util

import androidx.compose.ui.graphics.Color
import uz.daftar.app.domain.model.TxType

/**
 * YUK TURLARI UCHUN YAGONA RANG STANDARTI.
 * Butun ilova (qarzdor, mijoz, tarix, hisobot, bosh ekran) SHU funksiyani ishlatadi —
 * shunda hech qayerda rang adashmaydi.
 *
 * A = ko'k, B = sariq, C = yashil, P = qizil, D = to'q ko'k-yashil, K = pushti, Q = kulrang
 */
fun yukRangi(code: String?): Color = when (code?.trim()?.lowercase()) {
    "a" -> Color(0xFF1565C0)   // ko'k
    "b" -> Color(0xFFF9A825)   // sariq
    "c" -> Color(0xFF2E7D32)   // yashil
    "d" -> Color(0xFF00838F)   // to'q ko'k-yashil
    "k" -> Color(0xFFC2185B)   // pushti
    "p" -> Color(0xFFD32F2F)   // qizil
    "q" -> Color(0xFF616161)   // kulrang
    else -> Color(0xFF9AA0A6)  // noma'lum — och kulrang
}

fun yukRangi(type: TxType): Color = yukRangi(type.code)
