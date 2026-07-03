package uz.daftar.app.ui.screen.profil

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uz.daftar.app.ui.screen.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilScreen(
    onBack: () -> Unit,
    onManager: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val autoBackupOn by vm.autoBackup.collectAsStateWithLifecycle()
    val driveEmail by vm.driveEmail.collectAsStateWithLifecycle()
    val driveMsg by vm.driveMsg.collectAsStateWithLifecycle()
    val driveBusy by vm.driveBusy.collectAsStateWithLifecycle()
    val tgConfigured by vm.tgConfigured.collectAsStateWithLifecycle()
    val tgMsg by vm.tgMsg.collectAsStateWithLifecycle()
    val tgBusy by vm.tgBusy.collectAsStateWithLifecycle()
    val tgTokenInit by vm.tgToken.collectAsStateWithLifecycle()
    val tgChatInit by vm.tgChat.collectAsStateWithLifecycle()
    var tgToken by remember(tgTokenInit) { mutableStateOf(tgTokenInit) }
    var tgChat by remember(tgChatInit) { mutableStateOf(tgChatInit) }
    var showDriveSignOut by remember { mutableStateOf(false) }
    var showTgClear by remember { mutableStateOf(false) }

    val driveSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        runCatching {
            com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .getResult(com.google.android.gms.common.api.ApiException::class.java)
        }.onSuccess { vm.onSignedIn() }
            .onFailure { vm.onSignInFailed(it.message ?: "bekor qilindi") }
    }
    androidx.compose.runtime.LaunchedEffect(driveMsg) {
        driveMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            vm.clearDriveMsg()
        }
    }
    androidx.compose.runtime.LaunchedEffect(tgMsg) {
        tgMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            vm.clearTgMsg()
        }
    }
    val autoBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.enableAutoBackup(uri)
            android.widget.Toast.makeText(context, "☁️ Avto-zaxira yoqildi", android.widget.Toast.LENGTH_SHORT).show()
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
                title = { Text("👤 Profil") },
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
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "👤 Profil — zaxira va hisob",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )

            // ☁️ Drive avto-sinxron holati — TEPADA (qachon saqlangani shu yerda ko'rinadi)
            run {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val lastSync = remember {
                    ctx.getSharedPreferences("drive_sync", android.content.Context.MODE_PRIVATE).getLong("last_sync", 0L)
                }
                val fresh = lastSync > 0L && (System.currentTimeMillis() - lastSync) < 2 * 60 * 60 * 1000L
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (fresh) "🟢 Drive avto-sinxron: ISHLAYAPTI" else "🔴 Drive avto-sinxron: kutilmoqda",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (lastSync > 0L)
                                "Oxirgi saqlash: " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync)) + " · har 30 daqiqada"
                            else
                                "Hali sinxron bo'lmagan — internet bo'lganda avtomatik boshlanadi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Zaxira (DB backup / fayldan tiklash)
            SettingsItem(
                icon = Icons.Outlined.Backup,
                title = "🗂 Zaxira",
                subtitle = "Bazani zaxiralash yoki fayldan tiklash",
                onClick = onManager
            )
            // Avto-zaxira (fayl)
            ToggleItem(
                icon = Icons.Outlined.Backup,
                title = "☁️ Avto-zaxira (fayl)",
                subtitle = if (autoBackupOn) "Yoqilgan ✓ — chiqishda avto saqlanadi" else "Faylni Google Drive'da yarating — bulutga avto saqlanadi",
                checked = autoBackupOn,
                onCheckedChange = { on -> if (on) autoBackupLauncher.launch("daftar_avto_backup.db") else vm.disableAutoBackup() }
            )
            // Google Drive — TELEGRAMDEK yig'ma: bosganda ichida ochiladi
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    var gOpen by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (driveEmail == null) driveSignInLauncher.launch(vm.signInClientIntent())
                            else gOpen = !gOpen
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (driveEmail != null) "☁️ Google Drive ✓" else "☁️ Google Drive zaxira",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    driveBusy -> "Saqlanmoqda…"
                                    driveEmail != null && !gOpen -> "$driveEmail · sozlash uchun bosing"
                                    driveEmail != null -> "$driveEmail — chiqishda avto, 40 kun saqlanadi"
                                    else -> "Google bilan kiring — bulutga avto-zaxira"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (driveEmail != null) Text(if (gOpen) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (gOpen && driveEmail != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.backupNowDrive() },
                            enabled = !driveBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (driveBusy) "Saqlanmoqda…" else "💾 Hozir Drive'ga zaxiralash") }
                        TextButton(
                            onClick = { showDriveSignOut = true }
                        ) { Text("Google hisobidan chiqish", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (showDriveSignOut) {
                AlertDialog(
                    onDismissRequest = { showDriveSignOut = false },
                    title = { Text("Hisobdan chiqish?") },
                    text = { Text("Google hisobidan chiqsangiz, avtomatik zaxira to'xtaydi. Ma'lumotlaringiz telefonda va Drive'da saqlanib qoladi. Qayta kirsangiz davom etadi.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDriveSignOut = false
                            vm.signOutDrive()
                        }) { Text("Ha, chiqish", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDriveSignOut = false }) { Text("Bekor qilish") }
                    }
                )
            }

            // Telegram zaxira — botingizga bazani (.db) yuboradi
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    var tgOpen by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { tgOpen = !tgOpen },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (tgConfigured) "\uD83D\uDCE8 Telegram zaxira \u2713" else "\uD83D\uDCE8 Telegram zaxira",
                                fontWeight = FontWeight.Bold
                            )
                            if (!tgOpen && tgConfigured) Text(
                                "ID: $tgChat · sozlash uchun bosing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(if (tgOpen) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tgOpen) {
                    Text(
                        "Bot token + chat_id kiriting \u2014 baza har kuni Telegramga yuboriladi. Telefon yo'qolsa ham ma'lumot saqlanadi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tgToken, onValueChange = { tgToken = it },
                        label = { Text("Bot token") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tgChat, onValueChange = { tgChat = it },
                        label = { Text("Chat ID (sizning ID)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.saveTelegram(tgToken, tgChat) }) { Text("Saqlash") }
                        OutlinedButton(
                            onClick = { vm.backupNowTelegram() },
                            enabled = tgConfigured && !tgBusy
                        ) { Text(if (tgBusy) "Yuborilmoqda\u2026" else "\uD83D\uDCE8 Hozir yubor") }
                    }
                    if (tgConfigured) {
                        TextButton(onClick = { showTgClear = true }) { Text("O'chirish", color = MaterialTheme.colorScheme.error) }
                    }
                    }   // tgOpen yig'ma blok tugadi
                    if (showTgClear) {
                        AlertDialog(
                            onDismissRequest = { showTgClear = false },
                            title = { Text("Telegram zaxira o'chirilsinmi?") },
                            text = { Text("Bot token va Chat ID o'chiriladi \u2014 Telegramga avto-zaxira to'xtaydi. Ma'lumotlaringiz telefonda saqlanib qoladi. Qayta kiritsangiz davom etadi.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showTgClear = false
                                    vm.clearTgConfig()
                                }) { Text("Ha, o'chirish", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTgClear = false }) { Text("Bekor") }
                            }
                        )
                    }
                }
            }

            // Drive'dan yangilash
            SettingsItem(
                icon = Icons.Outlined.Download,
                title = "☁️ Drive'dan yangilash",
                subtitle = "2-telefon: so'nggi ma'lumotni Drive'dan oladi",
                onClick = { vm.refreshFromDrive() }
            )

            // Backup (CSV eksport)
            SettingsItem(
                icon = Icons.Outlined.Save,
                title = "💾 Backup (CSV)",
                subtitle = "Barcha yozuvlarni CSV faylga saqlash",
                onClick = {
                    val stamp = java.time.LocalDate.now().toString()
                    backupLauncher.launch("daftar_backup_$stamp.csv")
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
