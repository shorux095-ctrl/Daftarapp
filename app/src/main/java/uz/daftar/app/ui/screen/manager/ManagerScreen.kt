package uz.daftar.app.ui.screen.manager

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.daftar.app.ui.common.HomeButton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerScreen(
    onBack: () -> Unit,
    vm: ManagerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmRestore by remember { mutableStateOf<BackupItem?>(null) }
    var pendingExportFile by remember { mutableStateOf<java.io.File?>(null) }

    // Toast xabarlar
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    // Tiklangach ilovani qayta ishga tushirish
    LaunchedEffect(state.restartNeeded) {
        if (state.restartNeeded) {
            kotlinx.coroutines.delay(1200)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    // SAF: tashqi joyga (joriy baza) saqlash
    val exportNewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { vm.manager.exportTo(uri) } }
                Toast.makeText(context, "✅ Tashqi nusxa saqlandi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF: mavjud ichki zaxirani tashqi joyga ko'chirish
    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val src = pendingExportFile
        if (uri != null && src != null) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { vm.manager.exportFileTo(src, uri) } }
                Toast.makeText(context, "✅ Nusxa saqlandi", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportFile = null
    }

    // SAF: tashqi fayldan tiklash
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { vm.manager.restoreFromUri(uri) }
                if (ok) {
                    Toast.makeText(context, "✅ Tiklandi — ilova qayta ishga tushadi", Toast.LENGTH_SHORT).show()
                    kotlinx.coroutines.delay(1200)
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                } else {
                    Toast.makeText(context, "❌ Bu fayl ilova zaxirasi emas. Eski bot .db uchun ☰ → Import ishlating", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val stampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🗂 Fayl menejeri") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                actions = { HomeButton() }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Amallar
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.createBackup() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text("💾 Zaxira yaratish (ichki)")
                }
                OutlinedButton(
                    onClick = {
                        val stamp = LocalDateTime.now().format(stampFmt)
                        exportNewLauncher.launch("daftar_$stamp.db")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Upload, null)
                    Spacer(Modifier.width(6.dp))
                    Text("📤 Tashqi joyga saqlash (Drive)")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Restore, null)
                    Spacer(Modifier.width(6.dp))
                    Text("📥 Fayldan tiklash")
                }
            }

            HorizontalDivider()
            Text(
                "Ichki zaxiralar (${state.backups.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            if (state.backups.isEmpty()) {
                Text(
                    "Hozircha zaxira yo'q. \"Zaxira yaratish\" tugmasini bosing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.backups, key = { it.name }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    item.dateLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${item.name}  •  ${item.sizeLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { confirmRestore = item }) {
                                        Icon(Icons.Outlined.Restore, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tiklash")
                                    }
                                    OutlinedButton(onClick = {
                                        pendingExportFile = item.file
                                        exportFileLauncher.launch(item.name)
                                    }) {
                                        Icon(Icons.Outlined.Upload, null, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { vm.deleteBackup(item) }) {
                                        Icon(
                                            Icons.Outlined.Delete, "O'chirish",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Tiklashни tasdiqlash
    confirmRestore?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Tiklansinmi?") },
            text = {
                Text("Hozirgi barcha ma'lumotlar ${item.dateLabel} dagi zaxira bilan almashtiriladi. Ilova qayta ishga tushadi.")
            },
            confirmButton = {
                Button(onClick = {
                    val it2 = item
                    confirmRestore = null
                    vm.restore(it2)
                }) { Text("Ha, tikla") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmRestore = null }) { Text("Bekor") }
            }
        )
    }
}
