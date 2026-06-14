package uz.daftar.app.core.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Tizimning ovozli kiritish dialogini (Google) ochadi va natijani matn sifatida qaytaradi.
 * Mikrofon ruxsatini tizim dialogi o'zi so'raydi — bizning manifestda RECORD_AUDIO shart emas.
 *
 * Foydalanish:
 *   val voice = rememberVoiceInput { text -> note = text }
 *   IconButton(onClick = { voice("uz-UZ") }) { Icon(Icons.Outlined.Mic, null) }
 *
 * @return (lang) -> Unit  — "uz-UZ" yoki "ru-RU"
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) onText(text)
        }
    }
    return { lang ->
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra("android.speech.extra.PREFER_OFFLINE", false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Gapiring…")
        }
        runCatching { launcher.launch(intent) }.onFailure {
            android.widget.Toast.makeText(
                context, "Ovozli kiritish mavjud emas", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
