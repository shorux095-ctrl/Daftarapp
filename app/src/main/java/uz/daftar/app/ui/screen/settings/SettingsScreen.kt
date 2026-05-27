package uz.daftar.app.ui.screen.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
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
    vm: SettingsViewModel = hiltViewModel()
) {
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val pinSet by vm.pinSet.collectAsStateWithLifecycle()
    var showPinDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                }
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
            SettingsItem(
                icon = Icons.Outlined.Search,
                title = "Qidirish",
                subtitle = "Mijoz, sana yoki oraliq bo'yicha",
                onClick = onSearch
            )
            SettingsItem(
                icon = Icons.Outlined.LocalShipping,
                title = "Yuk narxlari (T)",
                subtitle = "Global T narx — barcha mijozlar uchun",
                onClick = onYukNarx
            )
            SettingsItem(
                icon = Icons.Outlined.MoneyOff,
                title = "Rasxod",
                subtitle = "Kunlik xarajatlar",
                onClick = onRasxod
            )
            SettingsItem(
                icon = Icons.Outlined.Notifications,
                title = "Eslatma va Limit",
                subtitle = "Qarz eslatmasi va limit ogohlantirishi",
                onClick = onReminder
            )
            SettingsItem(
                icon = Icons.Outlined.Edit,
                title = "Alias va Rename",
                subtitle = "Mijoz nomlarini birlashtirish yoki o'zgartirish",
                onClick = onAlias
            )
            SettingsItem(
                icon = Icons.Outlined.Edit,
                title = "Karzina",
                subtitle = "O'chirilgan yozuvlarni tiklash",
                onClick = onKarzina
            )

            // Biometric toggle
            ToggleItem(
                icon = Icons.Outlined.Fingerprint,
                title = "Barmoq izi qulf",
                subtitle = "Ilovani ochishda barmoq izi yoki PIN so'raydi",
                checked = lockEnabled,
                onCheckedChange = { vm.setLockEnabled(it) }
            )

            // PIN kod
            SettingsItem(
                icon = Icons.Outlined.Lock,
                title = "PIN kod",
                subtitle = if (pinSet) "O'rnatilgan — o'zgartirish yoki o'chirish" else "Ilova uchun maxsus kod o'rnatish",
                onClick = { showPinDialog = true }
            )

            // Backup (CSV eksport)
            SettingsItem(
                icon = Icons.Outlined.Lock,
                title = "💾 Backup (zaxira)",
                subtitle = "Barcha yozuvlarni CSV faylga saqlash",
                onClick = {
                    val stamp = java.time.LocalDate.now().toString()
                    backupLauncher.launch("daftar_backup_$stamp.csv")
                }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Daftar — solo qarz va yuk daftari",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

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
