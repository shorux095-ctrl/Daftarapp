package uz.daftar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.core.parser.DaftarParser
import uz.daftar.app.core.parser.ParseResult
import uz.daftar.app.core.theme.DaftarTheme
import uz.daftar.app.core.voice.rememberVoiceInput
import uz.daftar.app.data.repository.TransactionRepository
import uz.daftar.app.domain.usecase.AddTransactionUseCase
import javax.inject.Inject

/**
 * Tezkor qo'shish — bosh ekran widgetidan ochiladi.
 * To'liq ilovaga kirmasdan, kichik oynada yozib saqlash imkonini beradi.
 * Chatdagi bilan BIR XIL parser + saqlash mantig'ini ishlatadi.
 * v142: 🎤 ovozli kiritish + saqlashdan oldin HA/YO'Q tasdiq (adashmaslik uchun).
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject lateinit var addTx: AddTransactionUseCase
    @Inject lateinit var repo: TransactionRepository
    @Inject lateinit var db: uz.daftar.app.data.db.DaftarDatabase
    @Inject lateinit var chatStore: uz.daftar.app.core.chat.ChatStore
    private val userId: Long = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DaftarTheme {
                var tfv by remember { mutableStateOf(TextFieldValue("")) }
                var saving by remember { mutableStateOf(false) }
                var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
                // v142: ovozdan kelgan matn — saqlashdan OLDIN tasdiq so'raladi
                var voiceConfirm by remember { mutableStateOf<String?>(null) }
                val voice = rememberVoiceInput { spoken -> voiceConfirm = spoken.trim() }

                // Oxirgi qatordagi ism bo'yicha takliflar (chatdagi bilan bir xil)
                LaunchedEffect(tfv.text) {
                    delay(120)
                    val lastLine = tfv.text.substringAfterLast('\n').trimStart()
                    val word = lastLine.substringBefore(' ').trim()
                    suggestions = if (word.isNotBlank() && word.all { it.isLetter() || it == '\'' })
                        runCatching { repo.suggestClients(userId, word) }.getOrDefault(emptyList())
                            .filter { it != word.lowercase() }.take(6)
                    else emptyList()
                }

                fun doSave(text: String) {
                    if (text.isBlank() || saving) return
                    saving = true
                    lifecycleScope.launch {
                        val n = withContext(Dispatchers.IO) { saveText(text) }
                        android.widget.Toast.makeText(
                            this@QuickAddActivity,
                            if (n > 0) "✅ $n yozuv saqlandi" else "❌ Tushunarsiz format",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        if (n > 0) finish() else { saving = false; voiceConfirm = null }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .clickable(enabled = false) {}
                    ) {
                        val vc = voiceConfirm
                        if (vc != null) {
                            // ───── 🎤 OVOZ TASDIG'I: Ha / Yo'q ─────
                            Column(Modifier.padding(16.dp)) {
                                Text("🎤 Eshitildi — saqlaymi?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF2F4F8),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "\u201C$vc\u201D",
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                // Qatorlar tekshiruvi: ✅ tushunarli / ❓ tushunarsiz
                                val checks = vc.lines().filter { it.isNotBlank() }
                                    .map { it.trim() to (DaftarParser.parse(it.trim()) is ParseResult.Success) }
                                val okCount = checks.count { it.second }
                                checks.forEach { (ln, ok) ->
                                    Text(
                                        (if (ok) "✅ " else "❓ ") + ln,
                                        fontSize = 14.sp,
                                        color = if (ok) Color(0xFF1AA35A) else Color(0xFFD32F2F)
                                    )
                                }
                                if (okCount == 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Format tushunarsiz (masalan: \"dvorik a10\"). Yo'q — tahrirlash.",
                                        fontSize = 12.sp, color = Color(0xFFD32F2F)
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = {
                                        // YO'Q: matn maydonga tushadi — qo'lda tuzatib olish mumkin
                                        tfv = TextFieldValue(vc, selection = TextRange(vc.length))
                                        voiceConfirm = null
                                    }) { Text("❌ Yo'q, tahrirlash") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        enabled = okCount > 0 && !saving,
                                        onClick = { doSave(vc) }
                                    ) { Text("✅ Ha, saqlash") }
                                }
                            }
                        } else {
                            // ───── ODDIY REJIM: yozish + 🎤 ─────
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "⚡ Tezkor qo'shish",
                                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { voice("uz-UZ") }) {
                                        Text("🎤", fontSize = 22.sp)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = tfv,
                                    onValueChange = { tfv = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                                if (suggestions.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        suggestions.forEach { name ->
                                            SuggestionChip(
                                                onClick = {
                                                    val cur = tfv.text
                                                    val prefix = if (cur.contains('\n')) cur.substringBeforeLast('\n') + "\n" else ""
                                                    val lastLine = cur.substringAfterLast('\n').trimStart()
                                                    // Yozilgan ism qismini topamiz (ko'p so'zli ismlar ham)
                                                    val lname = name.lowercase()
                                                    var typed = ""
                                                    for (w in lastLine.split(" ")) {
                                                        if (w.isBlank()) break
                                                        val cand = if (typed.isEmpty()) w else "$typed $w"
                                                        if (lname.startsWith(cand.lowercase())) typed = cand else break
                                                    }
                                                    val rest = if (typed.isEmpty()) lastLine.substringAfter(' ', missingDelimiterValue = "")
                                                               else lastLine.removePrefix(typed).trimStart()
                                                    val tail = if (rest.isBlank()) "$name " else "$name $rest"
                                                    val newText = prefix + tail
                                                    tfv = TextFieldValue(newText, selection = TextRange(newText.length))
                                                },
                                                label = { Text(name.replaceFirstChar { it.uppercase() }) }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { finish() }) { Text("Bekor") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        enabled = !saving,
                                        onClick = { doSave(tfv.text) }
                                    ) { Text("Saqlash") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun saveText(text: String): Int {
        // v152: ATOMIK — barcha qatorlar bitta tranzaksiyada (yarim saqlash bo'lmaydi)
        var count = 0
        db.withTransaction {
            for (line in text.lines()) {
                if (line.isBlank()) continue
                val r = DaftarParser.parse(line)
                if (r is ParseResult.Success) {
                    addTx(userId, r.entry)
                    count++
                    runCatching { chatStore.addPending(line.trim()) }
                }
            }
        }
        return count
    }
}
