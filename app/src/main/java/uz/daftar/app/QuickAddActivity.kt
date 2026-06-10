package uz.daftar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.core.parser.DaftarParser
import uz.daftar.app.core.parser.ParseResult
import uz.daftar.app.core.theme.DaftarTheme
import uz.daftar.app.domain.usecase.AddTransactionUseCase
import javax.inject.Inject

/**
 * Tezkor qo'shish — bosh ekran widgetidan ochiladi.
 * To'liq ilovaga kirmasdan, kichik oynada yozib saqlash imkonini beradi.
 * Chatdagi bilan BIR XIL parser + saqlash mantig'ini ishlatadi.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject lateinit var addTx: AddTransactionUseCase
    private val userId: Long = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DaftarTheme {
                var text by remember { mutableStateOf("") }
                var saving by remember { mutableStateOf(false) }

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
                        Column(Modifier.padding(16.dp)) {
                            Text("⚡ Tezkor qo'shish", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("ali aka a5 a10   yoki   ali p 50000") },
                                minLines = 2
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { finish() }) { Text("Bekor") }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    enabled = !saving,
                                    onClick = {
                                        if (text.isBlank()) return@Button
                                        saving = true
                                        lifecycleScope.launch {
                                            val n = withContext(Dispatchers.IO) { saveText(text) }
                                            android.widget.Toast.makeText(
                                                this@QuickAddActivity,
                                                if (n > 0) "✅ $n yozuv saqlandi" else "❌ Tushunarsiz format",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            if (n > 0) finish() else saving = false
                                        }
                                    }
                                ) { Text("Saqlash") }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun saveText(text: String): Int {
        var count = 0
        for (line in text.lines()) {
            if (line.isBlank()) continue
            val r = DaftarParser.parse(line)
            if (r is ParseResult.Success) {
                runCatching { addTx(userId, r.entry) }.onSuccess { count++ }
            }
        }
        return count
    }
}
