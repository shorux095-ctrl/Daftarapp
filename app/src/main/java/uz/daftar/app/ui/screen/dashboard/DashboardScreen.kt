package uz.daftar.app.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import uz.daftar.app.core.util.formatMoney

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit = {},
    onNewTx: () -> Unit = {},
    onDaftar: () -> Unit = {},
    onDebtors: () -> Unit = {},
    onReports: () -> Unit = {},
    onProfile: () -> Unit = {},
    vm: DashboardViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Bosh sahifa") })
                NavigationBarItem(selected = false, onClick = onDaftar, icon = { Icon(Icons.Filled.Book, null) }, label = { Text("Daftar") })
                NavigationBarItem(selected = false, onClick = onReports, icon = { Icon(Icons.Filled.BarChart, null) }, label = { Text("Hisobot") })
                NavigationBarItem(selected = false, onClick = onProfile, icon = { Icon(Icons.Filled.Person, null) }, label = { Text("Profil") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTx, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, contentDescription = "Yangi yozuv")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Sarlavha
            Text("Bosh sahifa 👋", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            // Gradient balans kartasi
            Surface(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF8E7CF0)))
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Jami qarz", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${state.totalDebt.formatMoney()} so'm",
                            color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(state.title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Tezkor amallar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Yangi\nyozuv", Icons.Filled.Add, MaterialTheme.colorScheme.primary, Modifier.weight(1f), onNewTx)
                QuickAction("Qarz\u00addorlar", Icons.Filled.CreditCard, Red, Modifier.weight(1f), onDebtors)
                QuickAction("Hisobot", Icons.Filled.BarChart, Color(0xFF00897B), Modifier.weight(1f), onReports)
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Foyda", state.totalFoyda, if (state.totalFoyda >= 0) Green else Red, Modifier.weight(1f))
                StatCard("Savdo", state.totalSavdo, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard("To'lov", state.totalTolov, Color(0xFF00897B), Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Eng ko'p qarzi bor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onDebtors) { Text("Barchasi") }
            }
            Spacer(Modifier.height(6.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.debtors.isEmpty()) {
                Text("Qarzdor yo'q ✅", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val maxDebt = state.debtors.first().debt.coerceAtLeast(1)
                state.debtors.take(6).forEach { d ->
                    DebtBar(d.client, d.debt, d.debt.toFloat() / maxDebt.toFloat())
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun StatCard(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value.formatMoney(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
        }
    }
}

@Composable
private fun DebtBar(name: String, debt: Long, fraction: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text("${debt.formatMoney()} so'm", color = Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            )
        }
    }
}
