package uz.daftar.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.daftar.app.core.security.LockManager
import uz.daftar.app.core.theme.DaftarTheme
import uz.daftar.app.ui.nav.DaftarNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lockManager: LockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Android 13+ — bildirishnoma ruxsatini so'rash (qarz eslatma uchun)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7777)
                }
            }
        }

        setContent {
            DaftarTheme {
                // Qulf holatini boshlang'ich tekshirish state orqali
                var unlocked by remember { mutableStateOf(false) }
                var lockRequired by remember { mutableStateOf<Boolean?>(null) }

                // Qulf yoqilganmi — tekshirish
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val enabled = lockManager.lockEnabled.first()
                    lockRequired = enabled
                    if (!enabled) {
                        unlocked = true
                    } else {
                        // Biometric so'rash
                        promptBiometric(
                            onSuccess = { unlocked = true },
                            onFail = { /* qulflangan holatda qoladi */ }
                        )
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        unlocked -> DaftarNavHost()
                        lockRequired == true -> LockedScreen(
                            onRetry = {
                                promptBiometric(
                                    onSuccess = { unlocked = true },
                                    onFail = {}
                                )
                            }
                        )
                        else -> { /* yuklanmoqda */ }
                    }
                }
            }
        }
    }

    private fun promptBiometric(onSuccess: () -> Unit, onFail: () -> Unit) {
        val bm = BiometricManager.from(this)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Qurilmada biometric yo'q — qulfni o'tkazib yuboramiz (bloklab qo'ymaslik uchun)
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFail()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Daftar — qulf")
            .setSubtitle("Kirish uchun barmoq izi yoki PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun LockedScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", style = MaterialTheme.typography.displayLarge)
        Text(
            "Daftar qulflangan",
            style = MaterialTheme.typography.titleLarge
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Button(onClick = onRetry) {
            Text("Qulfni ochish")
        }
    }
}
