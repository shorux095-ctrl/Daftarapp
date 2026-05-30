package uz.daftar.app.core.parser

import uz.daftar.app.domain.model.TxType
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.YearMonth

/**
 * Bot.py'dagi parser logikasi to'liq ko'chirilgan.
 *
 * Qabul qilinadigan formatlar:
 *   ali a10                       → A:10
 *   ali a10 b5                    → A:10, B:5
 *   ali a10 n a20                 → A:10, A uchun narx 20
 *   ali a10 t a25                 → A:10, A uchun T narx 25 (global)
 *   ali a10 t?a30                 → A:10, A uchun bir martalik T narx 30
 *   ali p100                      → To'lov 100
 *   ali q50                       → Qarz qoldig'i 50
 *   02.03 ali a10                 → 02.03 sanasi, A:10
 *   02.03.25 ali a10              → 02.03.2025 sanasi
 *   ali aka a10                   → Ko'p so'zli ism "ali aka", A:10
 */
object DaftarParser {

    private val DATE_PREFIX_RE = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?\s+(.+)$""")
    private val YUK_TYPES = setOf('a', 'b', 'c', 'd', 'k', 'p', 'q')
    private val NUM_TYPES = setOf('a', 'b', 'c', 'd', 'k')  // T narx faqat shu turlarda

    fun parse(input: String): ParseResult {
        val text = input.trim()
        if (text.isEmpty()) return ParseResult.Failure(ParseError.EmptyInput)

        // 1) Sana prefiksini ajratib olish
        val (date, restText) = extractDatePrefix(text)
        if (restText.isBlank()) return ParseResult.Failure(ParseError.NoClientName)

        // 2) Tokenlarni ajratish
        val parts = restText.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return ParseResult.Failure(ParseError.NoClientName)

        // 3) INLINE PARSING — n/t narx markerlari "split" qilmaydi, faqat keyingi tokenni iste'mol qiladi
        //    Shunda: ali a20 p200 n 20 q20 t a15 → A:20, P:200, N(A)=20, Q:20, T(A)=15 — hammasi to'g'ri
        val ismParts = mutableListOf<String>()
        val items = mutableMapOf<TxType, Double>()
        val clientPrices = mutableMapOf<TxType, Double>()
        val tPrices = mutableMapOf<TxType, Double>()
        val tOneTime = mutableMapOf<TxType, Double>()

        var i = 0
        var nameDone = false
        var lastCargoType: TxType? = null

        while (i < parts.size) {
            val p = parts[i]
            val pl = p.lowercase()

            // YUK / P / Q token (a10, b5, c4.3, p200, q50)
            val yukResult = parseYukToken(pl, parts, i)
            if (yukResult != null) {
                val (type, amount, advance) = yukResult
                items[type] = (items[type] ?: 0.0) + amount
                if (type in setOf(TxType.A, TxType.B, TxType.C, TxType.D, TxType.K)) {
                    lastCargoType = type
                }
                nameDone = true
                i += advance
                continue
            }

            // NARX MARKER (n, narx, t, tk, t1, t?, na20, ta25, t?a30, t1a30)
            val narxAdvance = tryParseNarxInline(
                pl, parts, i, clientPrices, tPrices, tOneTime, lastCargoType
            )
            if (narxAdvance > 0) {
                nameDone = true
                i += narxAdvance
                continue
            }

            // ISM qismi (yuk yoki marker emas, faqat ism boshlangunaqa)
            if (!nameDone && pl.isNotEmpty() && !pl[0].isDigit()) {
                ismParts.add(p)
                i++
                continue
            }

            // Noma'lum — o'tkazib yuboramiz
            i++
        }

        if (ismParts.isEmpty()) return ParseResult.Failure(ParseError.NoClientName)
        val clientName = normalizeName(ismParts.joinToString(" "))

        // Yuk ham, narx ham bo'lmasa — xato. Faqat narx bo'lsa (n c4.3) — ruxsat.
        if (items.isEmpty() && clientPrices.isEmpty() && tPrices.isEmpty() && tOneTime.isEmpty()) {
            return ParseResult.Failure(ParseError.NoItems)
        }

        return ParseResult.Success(
            ParsedEntry(
                clientName = clientName,
                date = date,
                items = items,
                clientPrices = clientPrices,
                tPrices = tPrices,
                tOneTime = tOneTime,
                rawText = input
            )
        )
    }

    /**
     * Bitta tokenни inline narx markeri sifatida talqin qilishga uringan.
     * Muvaffaqiyatли: iste'mol qilingan token sonini qaytaradi (1 yoki 2). Aks holda 0.
     */
    private fun tryParseNarxInline(
        pl: String,
        parts: List<String>,
        i: Int,
        clientPrices: MutableMap<TxType, Double>,
        tPrices: MutableMap<TxType, Double>,
        tOneTime: MutableMap<TxType, Double>,
        lastCargoType: TxType?
    ): Int {
        // "n" yoki "narx" — keyingi token N narx
        if (pl == "n" || pl == "narx") {
            if (i + 1 < parts.size) {
                val nxt = parts[i + 1].lowercase()
                val pn = parseNarxToken(nxt, parts, i + 1)
                if (pn != null) {
                    clientPrices[pn.first] = pn.second
                    return 1 + pn.third
                }
                // "n 20" — oxirgi yuk turi uchun N narx
                val amt = nxt.replace(",", ".").toDoubleOrNull()
                if (amt != null && lastCargoType != null) {
                    clientPrices[lastCargoType] = amt
                    return 2
                }
            }
            return 1
        }
        // "t" yoki "tk" — keyingi token T narx
        if (pl == "t" || pl == "tk") {
            if (i + 1 < parts.size) {
                val nxt = parts[i + 1].lowercase()
                val pn = parseNarxToken(nxt, parts, i + 1)
                if (pn != null) {
                    tPrices[pn.first] = pn.second
                    return 1 + pn.third
                }
                val amt = nxt.replace(",", ".").toDoubleOrNull()
                if (amt != null && lastCargoType != null) {
                    tPrices[lastCargoType] = amt
                    return 2
                }
            }
            return 1
        }
        // "t?" yoki "t1" yakka — bir martalik T
        if (pl == "t?" || pl == "t1") {
            if (i + 1 < parts.size) {
                val nxt = parts[i + 1].lowercase()
                val pn = parseNarxToken(nxt, parts, i + 1)
                if (pn != null) {
                    tOneTime[pn.first] = pn.second
                    return 1 + pn.third
                }
                val amt = nxt.replace(",", ".").toDoubleOrNull()
                if (amt != null && lastCargoType != null) {
                    tOneTime[lastCargoType] = amt
                    return 2
                }
            }
            return 1
        }
        // "na20" / "nb30" — N yakka token
        if (pl.length >= 3 && pl[0] == 'n' && pl[1] in NUM_TYPES) {
            val amt = pl.substring(2).replace(",", ".").toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(pl[1].toString())
                if (type != null) {
                    clientPrices[type] = amt
                    return 1
                }
            }
        }
        // "ta25" / "tb30" — T yakka token  (DIQQAT: "t?aX" va "t1aX" oldinroq aniqlanadi)
        if (pl.length >= 3 && pl[0] == 't' && pl[1] != '?' && pl[1] != '1' && pl[1] in NUM_TYPES) {
            val amt = pl.substring(2).replace(",", ".").toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(pl[1].toString())
                if (type != null) {
                    tPrices[type] = amt
                    return 1
                }
            }
        }
        // "t?a30" — bir martalik T yakka
        if (pl.length >= 4 && pl.startsWith("t?") && pl[2] in NUM_TYPES) {
            val amt = pl.substring(3).replace(",", ".").toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(pl[2].toString())
                if (type != null) {
                    tOneTime[type] = amt
                    return 1
                }
            }
        }
        // "t1a30" — bir martalik T yakka (muqobil)
        if (pl.length >= 4 && pl.startsWith("t1") && pl[2] in NUM_TYPES) {
            val amt = pl.substring(3).replace(",", ".").toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(pl[2].toString())
                if (type != null) {
                    tOneTime[type] = amt
                    return 1
                }
            }
        }

        return 0
    }

    /** "02.03" yoki "02.03.25" prefiksini topish va ajratish */
    private fun extractDatePrefix(text: String): Pair<LocalDate?, String> {
        val m = DATE_PREFIX_RE.matchEntire(text) ?: return Pair(null, text)
        val day = m.groupValues[1].toIntOrNull() ?: return Pair(null, text)
        val month = m.groupValues[2].toIntOrNull() ?: return Pair(null, text)
        val yearRaw = m.groupValues[3]
        val rest = m.groupValues[4]

        val year = when {
            yearRaw.isEmpty() -> Year.now().value
            yearRaw.length == 2 -> 2000 + yearRaw.toInt()
            else -> yearRaw.toIntOrNull() ?: return Pair(null, text)
        }
        // Sananing to'g'riligini tekshirish
        return try {
            val date = LocalDate.of(year, month, day)
            Pair(date, rest)
        } catch (e: Exception) {
            Pair(null, text)
        }
    }

    /** "n", "narx", "t", "tk", "t?" markerini topish */
    private fun findNarxIdx(parts: List<String>): Int {
        var yukSeen = false
        for ((i, t) in parts.withIndex()) {
            val tl = t.lowercase().trimStart('-')

            if (!yukSeen) {
                // a10, b5 kabi yuk tokenmi?
                if (tl.isNotEmpty() && tl[0] in YUK_TYPES) {
                    val rest = tl.substring(1)
                    if (rest.isNotEmpty() && rest.replace(".", "").all { it.isDigit() }) {
                        yukSeen = true
                        continue
                    }
                    if (tl in setOf("a","b","c","d","k","p","q") && i + 1 < parts.size) {
                        val nxt = parts[i + 1]
                        if (nxt.toDoubleOrNull() != null) {
                            yukSeen = true
                            continue
                        }
                    }
                }
                // T yoki T? boshida (yuk ko'rilmasdan) — bu yuk yo'q, faqat narx
                if (tl in setOf("t", "tk") || tl == "t1" ||
                    (tl.startsWith("t1") && tl.length >= 3 && tl[2] in NUM_TYPES) ||
                    tl.startsWith("t?")) {
                    return i
                }
                // "n"/"narx" yuk ko'rilmasdan ham — faqat narx o'rnatish (masalan "ali n c4.3")
                if (tl in setOf("n", "narx")) return i
                continue
            }

            // Yuk ko'rilganidan keyin narx markerlari
            if (tl in setOf("n", "narx")) return i
            if (tl in setOf("t", "tk")) return i
            if (tl == "t1" || (tl.startsWith("t1") && tl.length >= 3 && tl[2] in NUM_TYPES)) return i
            if (tl.startsWith("t?")) return i

            // na20, nb20 (n bilan boshlangan birikma)
            if (tl.startsWith("n") && tl.length >= 2 && tl[1] in NUM_TYPES) {
                if (tl.length > 2 && tl.substring(2).replace(".", "").all { it.isDigit() }) {
                    return i
                }
            }
        }
        return -1
    }

    /** "a10" yoki "a" + "10" kabi yuk tokenini ajratish.
     *  Qaytaradi: (Type, Amount, advanceBy) yoki null */
    private fun parseYukToken(pl: String, parts: List<String>, idx: Int): Triple<TxType, Double, Int>? {
        if (pl.isEmpty()) return null
        val firstChar = pl[0]
        if (firstChar !in YUK_TYPES) return null

        // "a10" formati
        if (pl.length >= 2) {
            val rest = pl.substring(1).replace(",", ".")
            val amt = rest.toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(firstChar.toString()) ?: return null
                return Triple(type, amt, 1)
            }
        }
        // "a" + " " + "10" formati
        if (pl.length == 1 && idx + 1 < parts.size) {
            val nxt = parts[idx + 1].replace(",", ".")
            val amt = nxt.toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(firstChar.toString()) ?: return null
                return Triple(type, amt, 2)
            }
        }
        return null
    }

    /** Narx qismini tahlil qilish */
    private fun parseNarxParts(
        narxParts: List<String>,
        clientPrices: MutableMap<TxType, Double>,
        tPrices: MutableMap<TxType, Double>,
        tOneTime: MutableMap<TxType, Double>
    ) {
        if (narxParts.isEmpty()) return

        var i = 0
        while (i < narxParts.size) {
            val token = narxParts[i].lowercase()

            // "n" yoki "narx" — keyingi tokenlar client narxi
            if (token in setOf("n", "narx")) {
                i++
                while (i < narxParts.size) {
                    val t2 = narxParts[i].lowercase()
                    // Yangi marker boshlanishi?
                    if (t2 in setOf("t", "tk", "n", "narx") || t2.startsWith("t?")) break
                    val parsed = parseNarxToken(t2, narxParts, i)
                    if (parsed != null) {
                        clientPrices[parsed.first] = parsed.second
                        i += parsed.third
                    } else {
                        i++
                    }
                }
                continue
            }

            // "t" yoki "tk" — T narx (global)
            if (token in setOf("t", "tk")) {
                i++
                while (i < narxParts.size) {
                    val t2 = narxParts[i].lowercase()
                    if (t2 in setOf("n", "narx") || t2.startsWith("t?")) break
                    val parsed = parseNarxToken(t2, narxParts, i)
                    if (parsed != null && parsed.first.code in setOf("a","b","c","d","k")) {
                        tPrices[parsed.first] = parsed.second
                        i += parsed.third
                    } else {
                        i++
                    }
                }
                continue
            }

            // "t1" yoki "t1a20" — bir martalik T narx (bot.py sintaksisi)
            if (token == "t1" || (token.startsWith("t1") && token.length >= 3 && token[2] in NUM_TYPES)) {
                if (token == "t1") {
                    i++
                    while (i < narxParts.size) {
                        val t2 = narxParts[i].lowercase()
                        if (t2 in setOf("n", "narx", "t", "tk")) break
                        val parsed = parseNarxToken(t2, narxParts, i)
                        if (parsed != null && parsed.first.code in setOf("a","b","c","d","k")) {
                            tOneTime[parsed.first] = parsed.second
                            i += parsed.third
                        } else {
                            i++
                        }
                    }
                } else {
                    // t1a20 formati
                    val rest = token.substring(2)
                    if (rest.isNotEmpty() && rest[0] in NUM_TYPES) {
                        val amt = rest.substring(1).replace(",", ".").toDoubleOrNull()
                        val type = TxType.fromCode(rest[0].toString())
                        if (amt != null && type != null) {
                            tOneTime[type] = amt
                        }
                    }
                    i++
                }
                continue
            }

            // "t?" yoki "t?a20" — bir martalik T narx (eski sintaksis, mos)
            if (token.startsWith("t?")) {
                if (token == "t?") {
                    i++
                    while (i < narxParts.size) {
                        val t2 = narxParts[i].lowercase()
                        if (t2 in setOf("n", "narx", "t", "tk")) break
                        val parsed = parseNarxToken(t2, narxParts, i)
                        if (parsed != null && parsed.first.code in setOf("a","b","c","d","k")) {
                            tOneTime[parsed.first] = parsed.second
                            i += parsed.third
                        } else {
                            i++
                        }
                    }
                } else {
                    // t?a20 formati
                    val rest = token.substring(2)
                    if (rest.isNotEmpty() && rest[0] in NUM_TYPES) {
                        val amt = rest.substring(1).replace(",", ".").toDoubleOrNull()
                        val type = TxType.fromCode(rest[0].toString())
                        if (amt != null && type != null) {
                            tOneTime[type] = amt
                        }
                    }
                    i++
                }
                continue
            }

            // "na20" formati — n bilan boshlangan, lekin alohida "n" yo'q
            if (token.startsWith("n") && token.length >= 3 && token[1] in NUM_TYPES) {
                val amt = token.substring(2).replace(",", ".").toDoubleOrNull()
                val type = TxType.fromCode(token[1].toString())
                if (amt != null && type != null) {
                    clientPrices[type] = amt
                }
                i++
                continue
            }

            // Hech qaysisiga to'g'ri kelmasa - keyingiga
            i++
        }
    }

    /** "a20" yoki "a" + " " + "20" formatidagi narx tokenini ajratish.
     *  Qaytaradi: (Type, Price, advanceBy) yoki null */
    private fun parseNarxToken(token: String, parts: List<String>, idx: Int): Triple<TxType, Double, Int>? {
        if (token.isEmpty()) return null

        // "a20" formati
        if (token.length >= 2 && token[0] in NUM_TYPES) {
            val amt = token.substring(1).replace(",", ".").toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(token[0].toString()) ?: return null
                return Triple(type, amt, 1)
            }
        }
        // "a" + " " + "20"
        if (token.length == 1 && token[0] in NUM_TYPES && idx + 1 < parts.size) {
            val amt = parts[idx + 1].replace(",", ".").toDoubleOrNull()
            if (amt != null) {
                val type = TxType.fromCode(token[0].toString()) ?: return null
                return Triple(type, amt, 2)
            }
        }
        return null
    }

    /** Ism normalizatsiya — bot.py'dagi norm_name() ga ekvivalent */
    fun normalizeName(name: String): String {
        var s = name.trim().lowercase()
        // Tipografik belgilar → oddiy apostrof
        for (ch in listOf('\u2018', '\u2019', '\u201a', '\u201b', '`', 'ʼ')) {
            s = s.replace(ch.toString(), "'")
        }
        // Tipografik qo'shtirnoq
        for (ch in listOf('\u201c', '\u201d', '\u00ab', '\u00bb')) {
            s = s.replace(ch.toString(), "\"")
        }
        // Raqamlar oldidagi bo'sh joyni olib tashlash: "ali 10" → "ali10"
        s = Regex("""\s+(\d)""").replace(s) { it.groupValues[1] }
        return s
    }
}
