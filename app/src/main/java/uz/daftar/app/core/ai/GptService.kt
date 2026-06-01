package uz.daftar.app.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** Bitta provayder ta'rifi */
data class AiProvider(
    val id: String,
    val displayName: String,
    val kind: String,   // "gemini" yoki "openai"
    val url: String,
    val model: String
)

object AiProviders {
    // Fallback tartibi: biri tugasa keyingisi
    val ALL = listOf(
        AiProvider("groq", "Groq", "openai", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
        AiProvider("cerebras", "Cerebras", "openai", "https://api.cerebras.ai/v1/chat/completions", "llama-3.3-70b"),
        AiProvider("gemini", "Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent", "gemini-2.0-flash"),
        AiProvider("openrouter", "OpenRouter", "openai", "https://openrouter.ai/api/v1/chat/completions", "meta-llama/llama-3.3-70b-instruct:free")
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id }
    val ids: List<String> get() = ALL.map { it.id }
}

/**
 * Ko'p provayderli GPT — kalit bor provayderlarni navbatma-navbat sinaydi.
 * Biri limit/xato bersa, keyingisiga o'tadi. Tashqi kutubxonasiz.
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
            val res = runCatching {
                if (p.kind == "gemini") callGemini(p, key, prompt) else callOpenAi(p, key, prompt)
            }.getOrElse { Result.failure(it.message ?: "xato") }
            when (res) {
                is Out.Ok -> return@withContext res.text
                is Out.Err -> errors.append("• ${p.displayName}: ${res.msg}\n")
            }
        }
        return@withContext "❌ Hamma provayder javob bermadi:\n$errors\nKalitlarni tekshiring yoki keyinroq urinib ko'ring."
    }

    private sealed class Out {
        data class Ok(val text: String) : Out()
        data class Err(val msg: String) : Out()
    }

    private fun Result<Out>.getOrElse(block: (Throwable) -> Out): Out =
        fold({ it }, { block(it) })

    private fun open(urlStr: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 20000; readTimeout = 45000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

    private fun callOpenAi(p: AiProvider, key: String, prompt: String): Out {
        val conn = open(p.url)
        conn.setRequestProperty("Authorization", "Bearer $key")
        val body = JSONObject().apply {
            put("model", p.model)
            put("temperature", 0.4)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) return Out.Err("xato $code")
        val text = JSONObject(resp).optJSONArray("choices")
            ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
        return if (text.isBlank()) Out.Err("bo'sh javob") else Out.Ok(text)
    }

    private fun callGemini(p: AiProvider, key: String, prompt: String): Out {
        val conn = open("${p.url}?key=$key")
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("temperature", 0.4))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) return Out.Err("xato $code")
        val text = JSONObject(resp).optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
        return if (text.isBlank()) Out.Err("bo'sh javob") else Out.Ok(text)
    }
}
