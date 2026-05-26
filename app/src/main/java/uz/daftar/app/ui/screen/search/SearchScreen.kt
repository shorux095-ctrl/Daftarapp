package uz.daftar.app.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.daftar.app.core.util.formatMoney
import uz.daftar.app.data.db.entity.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    vm: SearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🔍 Qidirish") },
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
                .padding(16.dp)
        ) {
            // Mode selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.mode == SearchMode.CLIENT,
                    onClick = { vm.setMode(SearchMode.CLIENT) },
                    label = { Text("Mijoz") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) }
                )
                FilterChip(
                    selected = state.mode == SearchMode.DATE_SINGLE,
                    onClick = { vm.setMode(SearchMode.DATE_SINGLE) },
                    label = { Text("Sana") },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) }
                )
                FilterChip(
                    selected = state.mode == SearchMode.DATE_RANGE,
                    onClick = { vm.setMode(SearchMode.DATE_RANGE) },
                    label = { Text("Oraliq") },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) }
                )
            }
            Spacer(Modifier.height(16.dp))

            when (state.mode) {
                SearchMode.CLIENT -> ClientSearchInputs(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                    onSearch = vm::performSearch
                )
                SearchMode.DATE_SINGLE -> DateSingleInputs(
                    date = state.dateFrom,
                    onDateChange = vm::setDateFrom,
                    onSearch = vm::performSearch
                )
                SearchMode.DATE_RANGE -> DateRangeInputs(
                    from = state.dateFrom,
                    to = state.dateTo,
                    onFromChange = vm::setDateFrom,
                    onToChange = vm::setDateTo,
                    onSearch = vm::performSearch
                )
            }
            Spacer(Modifier.height(16.dp))

            // Results
            when {
                state.isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.searched && state.results.isEmpty() -> Text(
                    "📭 Hech narsa topilmadi",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                state.results.isNotEmpty() -> Column {
                    Text(
                        "${state.results.size} ta yozuv",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.results, key = { it.id }) { tx ->
                            ResultRow(tx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientSearchInputs(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Mijoz nomi") },
        placeholder = { Text("ali (qisman ham bo'ladi)") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onSearch,
        modifier = Modifier.fillMaxWidth(),
        enabled = query.isNotBlank()
    ) { Text("Qidirish") }
}

@Composable
private fun DateSingleInputs(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onSearch: () -> Unit
) {
    DatePickerField(
        label = "Sana",
        date = date,
        onChange = onDateChange
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("Qidirish") }
}

@Composable
private fun DateRangeInputs(
    from: LocalDate,
    to: LocalDate,
    onFromChange: (LocalDate) -> Unit,
    onToChange: (LocalDate) -> Unit,
    onSearch: () -> Unit
) {
    DatePickerField(label = "Boshlang'ich", date = from, onChange = onFromChange)
    Spacer(Modifier.height(8.dp))
    DatePickerField(label = "Tugash", date = to, onChange = onToChange)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("Qidirish") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    OutlinedTextField(
        value = date.format(fmt),
        onValueChange = { },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        leadingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Kalendar")
            }
        }
    )

    if (showDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onChange(newDate)
                    }
                    showDialog = false
                }) { Text("Tanlash") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Bekor") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ResultRow(tx: TransactionEntity) {
    val typeLabel = tx.type.uppercase()
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.clientName.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$typeLabel: ${tx.amount.formatMoney()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatDate(tx.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(iso: String): String {
    return try {
        val clean = iso.replace("T", " ").substring(0, minOf(iso.length, 10))
        LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yy"))
    } catch (_: Exception) {
        iso.take(10)
    }
}
