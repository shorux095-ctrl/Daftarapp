package uz.daftar.app.core.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/**
 * Oddiy matnli PDF (qarz xati). Kutubxonasiz — Android'ning o'z PdfDocument'i.
 * bodyLines: bo'sh joy bilan boshlanmagan qator = kun sarlavhasi (qalin),
 * bo'sh joy bilan boshlangani = oddiy yozuv.
 */
object DebtPdf {

    fun create(
        context: Context,
        title: String,
        headerLines: List<String>,
        bodyLines: List<String>,
        footerLines: List<String>
    ): File {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val left = 40f
        val top = 60f
        val bottom = h - 50f

        val titlePaint = Paint().apply {
            textSize = 18f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bold = Paint().apply {
            textSize = 12f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val normal = Paint().apply { textSize = 11f; isAntiAlias = true }
        val gray = Paint().apply { textSize = 9f; isAntiAlias = true; color = 0xFF777777.toInt() }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNum).create())
        var canvas = page.canvas
        var y = top

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNum).create())
            canvas = page.canvas
            y = top
        }

        fun line(text: String, p: Paint, dy: Float = 16f) {
            if (y > bottom) newPage()
            canvas.drawText(text, left, y, p)
            y += dy
        }

        line(title, titlePaint, 26f)
        headerLines.forEach { line(it, normal) }
        y += 8f
        bodyLines.forEach {
            if (it.startsWith(" ")) line(it, normal) else line(it, bold, 18f)
        }
        y += 10f
        footerLines.forEach { line(it, bold, 18f) }
        y += 14f
        line("Daftar ilovasida tayyorlandi", gray, 12f)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val safe = title.replace(Regex("[^A-Za-z0-9 _'-]"), "").take(40).trim().replace(" ", "_")
        val file = File(dir, "${safe}_${System.currentTimeMillis() / 1000}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
