package uz.daftar.app.ui.screen.alias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.data.db.entity.AliasEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasScreen(
    onBack: () -> Unit,
    vm: AliasViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🔁 Alias va Rename") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Orqaga")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(snackbarData = it) } }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Alias") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Rename") })
            }

            when (tab) {
                0 -> AliasTab(
                    state = state,
                    onAdd = vm::addAliasAction,
                    onDelete = vm::delete,
                    contentPadding = PaddingValues(16.dp)
                )
                1 -> RenameTab(
                    onRename = vm::renameAction,
                    contentPadding = PaddingValues(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AliasTab(
    state: AliasState,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    contentPadding: PaddingValues
) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "💡 Alias — eski nomdan yangiga ko'chirish (merge)",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "ziya → ziyafet, ziya bilan yozilgan barcha yozuvlar ziyafet'ga ko'chadi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = from,
            onValueChange = { from = it },
            label = { Text("Eski nom (alias)") },
            placeholder = { Text("ziya") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            label = { Text("Yangi nom (canon)") },
            placeholder = { Text("ziyafet") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onAdd(from, to)
                from = ""; to = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = from.isNotBlank() && to.isNotBlank()
        ) {
            Text("Alias qo'shish")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            "Mavjud aliaslar (${state.aliases.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        if (state.aliases.isEmpty()) {
            Text(
                "Hali alias yo'q",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.aliases, key = { it.userId.toString() + ":" + it.alias }) { item ->
                    AliasRow(item, onDelete = { onDelete(item.alias) })
                }
            }
        }
    }
}

@Composable
private fun AliasRow(item: AliasEntity, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.alias.capitalize() + "  →  " + item.canon.capitalize(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "O'chirish",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RenameTab(
    onRename: (String, String) -> Unit,
    contentPadding: PaddingValues
) {
    var oldName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "💡 Rename — mijoz nomini tuzatish",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alias'dan farqi: faqat yozuvlarni ko'chiradi, alias jadval'ga yozmaydi. " +
                            "Eski nomdan yangi nomga shunchaki ko'chiradi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = oldName,
            onValueChange = { oldName = it },
            label = { Text("Eski nom") },
            placeholder = { Text("ali") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Yangi nom") },
            placeholder = { Text("ali aka") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onRename(oldName, newName)
                oldName = ""; newName = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = oldName.isNotBlank() && newName.isNotBlank()
        ) {
            Text("Qayta nomlash")
        }
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
