package uz.daftar.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAlias: () -> Unit = {},
    onSearch: () -> Unit = {},
    onYukNarx: () -> Unit = {},
    onRasxod: () -> Unit = {},
    onKarzina: () -> Unit = {},
    onReminder: () -> Unit = {},
    onManager: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel()
) {
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val pinSet by vm.pinSet.collectAsStateWithLifecycle()
    val importMsg by vm.importMsg.collectAsStateWithLifecycle()
    var showPinDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Import launcher (.db yoki .csv)
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        val tmp = java.io.File(context.cacheDir, "import_tmp.bin")
                        context.contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { o -> inp.copyTo(o) } }
                        val header = ByteArray(16)
                        tmp.inputStream().use { it.read(header) }
                        if (String(header).startsWith("SQLite format 3")) tmp.absolutePath to true
                        else tmp.readText() to false
                    }.getOrNull()
                }
                if (res != null) { if (res.second) vm.importDb(res.first) else vm.importCsv(res.first) }
            }
        }
    }

    // Import natijasi — Toast
    androidx.compose.runtime.LaunchedEffect(importMsg) {
        importMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            if (!it.startsWith("⏳")) vm.clearImportMsg()
        }
    }

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val csv = vm.buildBackupCsv()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray())
                        }
                    }
                    android.widget.Toast.makeText(context, "✅ Backup saqlandi", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Xato: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("⚙️ Sozlamalar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { uz.daftar.app.ui.common.HomeButton() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1) Import (eski bot .db yoki .csv)
            SettingsItem(
                icon = Icons.Outlined.Download,
                title = "📥 Import",
                subtitle = "Eski bot .db yoki .csv fayldan ma'lumot ko'chirish",
                onClick = { importLauncher.launch("*/*") }
            )
            // 2) Zaxira (DB backup / fayldan tiklash)
            SettingsItem(
                icon = Icons.Outlined.Backup,
                title = "🗂 Zaxira",
                subtitle = "Bazani zaxiralash yoki fayldan tiklash",
                onClick = onManager
            )
            // 3) Alias
            SettingsItem(
                icon = Icons.Outlined.Edit,
                title = "Alias va Rename",
                subtitle = "Mijoz nomlarini birlashtirish yoki o'zgartirish",
                onClick = onAlias
            )

            // 4) Barmoq izi qulf
            ToggleItem(
                icon = Icons.Outlined.Fingerprint,
                title = "Barmoq izi qulf",
                subtitle = "Ilovani ochishda barmoq izi yoki PIN so'raydi",
                checked = lockEnabled,
                onCheckedChange = { vm.setLockEnabled(it) }
            )

            // 5) PIN kod
            SettingsItem(
                icon = Icons.Outlined.Lock,
                title = "PIN kod",
                subtitle = if (pinSet) "O'rnatilgan — o'zgartirish yoki o'chirish" else "Ilova uchun maxsus kod o'rnatish",
                onClick = { showPinDialog = true }
            )

            // 6) Backup (CSV eksport)
            SettingsItem(
                icon = Icons.Outlined.Save,
                title = "💾 Backup",
                subtitle = "Barcha yozuvlarni CSV faylga saqlash",
                onClick = {
                    val stamp = java.time.LocalDate.now().toString()
                    backupLauncher.launch("daftar_backup_$stamp.csv")
                }
            )

            // 7) Karzina
            SettingsItem(
                icon = Icons.Outlined.Delete,
                title = "Karzina",
                subtitle = "O'chirilgan yozuvlarni tiklash",
                onClick = onKarzina
            )

            // 8) Xato loglari — oq ekran/xato sababini ko'rish (Termuxdek)
            SettingsItem(
                icon = Icons.Outlined.BugReport,
                title = "🐞 Xato loglari",
                subtitle = "Oq ekran yoki xato sababini ko'rish",
                onClick = {
                    scope.launch {
                        logText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val sb = StringBuilder()
                            runCatching {
                                val f = java.io.File(context.filesDir, "last_crash.txt")
                                if (f.exists()) sb.append("=== OXIRGI CRASH ===\n").append(f.readText()).append("\n\n")
                            }
                            runCatching {
                                val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "500"))
                                sb.append("=== LOGCAT (oxirgi) ===\n").append(p.inputStream.bufferedReader().readText())
                            }
                            runCatching {
                                val f = java.io.File(context.filesDir, "app_log.txt")
                                if (f.exists()) sb.append("\n\n=== app_log.txt ===\n").append(f.readText())
                            }
                            if (sb.isBlank()) "Log bo'sh — hali xato yozilmagan." else sb.toString()
                        }
                        showLogDialog = true
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Daftar — solo qarz va yuk daftari",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            if (showLogDialog) {
                AlertDialog(
                    onDismissRequest = { showLogDialog = false },
                    title = { Text("🐞 Xato loglari") },
                    text = {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                logText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(380.dp)
                                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching {
                                val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clip.setPrimaryClip(android.content.ClipData.newPlainText("log", logText))
                                android.widget.Toast.makeText(context, "Nusxa olindi", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("📋 Nusxa olish") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            runCatching {
                                java.io.File(context.filesDir, "app_log.txt").delete()
                                java.io.File(context.filesDir, "last_crash.txt").delete()
                            }
                            showLogDialog = false
                        }) { Text("Tozalash") }
                    }
                )
            }

            if (showPinDialog) {
                PinDialog(
                    pinSet = pinSet,
                    onSave = { newPin -> vm.setPin(newPin); showPinDialog = false },
                    onRemove = { vm.setPin(null); showPinDialog = false },
                    onDismiss = { showPinDialog = false }
                )
            }
        }
    }
}

@Composable
private fun PinDialog(
    pinSet: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pinSet) "PIN kodni o'zgartirish" else "PIN kod o'rnatish") },
        text = {
            Column {
                Text(
                    "4-6 raqamli kod kiriting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (pin.length >= 4) onSave(pin) },
                enabled = pin.length >= 4
            ) { Text("Saqlash") }
        },
        dismissButton = {
            if (pinSet) {
                TextButton(onClick = onRemove) { Text("O'chirish") }
            } else {
                TextButton(onClick = onDismiss) { Text("Bekor") }
            }
        }
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
