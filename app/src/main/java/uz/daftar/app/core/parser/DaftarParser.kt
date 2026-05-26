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

        // 3) Narx marker indeksini topish (n / narx / t / t?)
        val narxIdx = findNarxIdx(parts)
        val yozuvParts = if (narxIdx == -1) parts else parts.subList(0, narxIdx)
        val narxParts = if (narxIdx == -1) emptyList() else parts.subList(narxIdx, parts.size)

        // 4) Yozuv qismidan ism va itemlarni ajratish
        val ismParts = mutableListOf<String>()
        val items = mutableMapOf<TxType, Double>()
        var i = 0
        while (i < yozuvParts.size) {
            val p = yozuvParts[i]
            val pl = p.lowercase()

            // a10, b5, p100, q50 kabi yuk tokenmi?
            val yukResult = parseYukToken(pl, yozuvParts, i)
            if (yukResult != null) {
                val (type, amount, advance) = yukResult
                items[type] = (items[type] ?: 0.0) + amount
                i += advance
                continue
            }

            // Ism qismimi?
            if (pl.isNotEmpty() && !pl[0].isDigit()) {
                ismParts.add(p)
                i++
                continue
            }
            // Boshqa hech narsaga to'g'ri kelmasa - skip
            i++
        }

        if (ismParts.isEmpty()) return ParseResult.Failure(ParseError.NoClientName)
        if (items.isEmpty()) return ParseResult.Failure(ParseError.NoItems)

        val clientName = normalizeName(ismParts.joinToString(" "))

        // 5) Narx qismini tahlil qilish (n / t / t?)
        val clientPrices = mutableMapOf<TxType, Double>()
        val tPrices = mutableMapOf<TxType, Double>()
        val tOneTime = mutableMapOf<TxType, Double>()
        parseNarxParts(narxParts, clientPrices, tPrices, tOneTime)

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
                if (tl in setOf("t", "tk") || tl == "t1" || tl.startsWith("t?")) {
                    return i
                }
                continue
            }

            // Yuk ko'rilganidan keyin narx markerlari
            if (tl in setOf("n", "narx")) return i
            if (tl in setOf("t", "tk")) return i
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

            // "t?" yoki "t?a20" — bir martalik T narx
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
