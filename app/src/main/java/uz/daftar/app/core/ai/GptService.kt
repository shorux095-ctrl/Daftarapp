package uz.daftar.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Bitta provayder ta'rifi. models — sinash tartibida: biri o'chirilgan bo'lsa keyingisi */
data class AiProvider(
    val id: String,
    val displayName: String,
    val kind: String,   // "gemini" yoki "openai"
    val url: String,
    val models: List<String>
)

object AiProviders {
    // Fallback tartibi: biri tugasa keyingisi
    val ALL = listOf(
        AiProvider(
            "groq", "Groq", "openai",
            "https://api.groq.com/openai/v1/chat/completions",
            listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "llama-3.1-8b-instant")
        ),
        AiProvider(
            "cerebras", "Cerebras", "openai",
            "https://api.cerebras.ai/v1/chat/completions",
            listOf("gpt-oss-120b", "llama-4-scout-17b-16e-instruct", "qwen-3-32b", "llama3.1-8b")
        ),
        AiProvider(
            "gemini", "Gemini", "gemini",
            "https://generativelanguage.googleapis.com/v1beta/models",
            listOf("gemini-2.5-flash", "gemini-2.0-flash")
        ),
        AiProvider(
            "openrouter", "OpenRouter", "openai",
            "https://openrouter.ai/api/v1/chat/completions",
            listOf("meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free", "deepseek/deepseek-chat:free")
        )
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id }
    val ids: List<String> get() = ALL.map { it.id }
}

/**
 * Ko'p provayderli GPT — kalit bor provayderlarni navbatma-navbat sinaydi.
 * Model o'chirilgan bo'lsa (404) — shu provayderning keyingi modelini sinaydi.
 * Kalit xato (401) yoki limit (429) bo'lsa — keyingi provayderga o'tadi.
 */
class GptService @Inject constructor(
    private val ai: AiSettings
) {
    suspend fun ask(prompt: String): String = withContext(Dispatchers.IO) {
        val configured = AiProviders.ALL.filter { ai.getKey(it.id).isNotBlank() }
        if (configured.isEmpty()) {
            return@withContext "⚠️ Avval kalit kiriting:\n\n• gpt kalit gemini <KALIT>\n• gpt kalit groq <KALIT>\n• gpt kalit cerebras <KALIT>\n• gpt kalit openrouter <KALIT>\n\nKamida bittasi yetarli. Bepul: aistudio.google.com/apikey (Gemini), console.groq.com (Groq)."
        }
        val errors = StringBuilder()
        for (p in configured) {
            val key = ai.getKey(p.id)
            var lastErr = "xato"
            for (model in p.models) {
                val out = runCatching {
                    if (p.kind == "gemini") callGemini(p, key, model, prompt)
                    else callOpenAi(p, key, model, prompt)
                }.getOrElse { Out.Err("tarmoq: ${it.message ?: "ulanish xatosi"}") }
                when (out) {
                    is Out.Ok -> return@withContext out.text
                    is Out.Err -> {
                        lastErr = out.msg
                        // Faqat "model topilmadi" (404) holatida keyingi MODELNI sinaymiz.
                        // 401/429 va boshqalarda model almashtirish foyda bermaydi — keyingi PROVAYDER.
                        if (!out.msg.startsWith("404")) break
                    }
                }
            }
            errors.append("• ${p.displayName}: $lastErr\n")
        }
        return@withContext "❌ Hamma provayder javob bermadi:\n$errors\nMaslahat: 401 — yangi kalit oling; 429 — keyinroq urining; boshqa provayder kalitini ham qo'shing (gpt kalit groq <KALIT>)."
    }

    private sealed class Out {
        data class Ok(val text: String) : Out()
        data class Err(val msg: String) : Out()
    }

    private fun errText(code: Int): String = when (code) {
        401 -> "401 — kalit noto'g'ri yoki bekor qilingan. Yangi kalit olib qayta kiriting"
        403 -> "403 — ruxsat berilmagan (kalitni tekshiring)"
        404 -> "404 — model topilmadi"
        429 -> "429 — kunlik limit tugadi, keyinroq urining"
        else -> "xato $code"
    }

    private fun open(urlStr: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 20000; readTimeout = 45000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

    private fun callOpenAi(p: AiProvider, key: String, model: String, prompt: String): Out {
        val conn = open(p.url)
        conn.setRequestProperty("Authorization", "Bearer $key")
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.4)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) return Out.Err(errText(code))
        val text = JSONObject(resp).optJSONArray("choices")
            ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
        return if (text.isBlank()) Out.Err("bo'sh javob") else Out.Ok(text)
    }

    private fun callGemini(p: AiProvider, key: String, model: String, prompt: String): Out {
        val conn = open("${p.url}/$model:generateContent?key=$key")
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("temperature", 0.4))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) return Out.Err(errText(code))
        val text = JSONObject(resp).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
        return if (text.isBlank()) Out.Err("bo'sh javob") else Out.Ok(text)
    }
}
