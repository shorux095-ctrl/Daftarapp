package uz.daftar.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.daftar.app.ui.screen.alias.AliasScreen
import uz.daftar.app.ui.screen.clienthistory.ClientHistoryScreen
import uz.daftar.app.ui.screen.clients.ClientsScreen
import uz.daftar.app.ui.screen.edit.EditTransactionScreen
import uz.daftar.app.ui.screen.karzina.KarzinaScreen
import uz.daftar.app.ui.screen.narx.ClientNarxScreen
import uz.daftar.app.ui.screen.newtx.NewTransactionScreen
import uz.daftar.app.ui.screen.rasxod.RasxodScreen
import uz.daftar.app.ui.screen.reminder.ReminderLimitScreen
import uz.daftar.app.ui.screen.reports.ReportsScreen
import uz.daftar.app.ui.screen.search.SearchScreen
import uz.daftar.app.ui.screen.settings.SettingsScreen
import uz.daftar.app.ui.screen.today.TodayScreen
import uz.daftar.app.ui.screen.yuknarx.YukNarxScreen

object Routes {
    const val TODAY = "today"
    const val CLIENTS = "clients"
    const val NEW_TX = "new_tx"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val ALIAS = "alias"
    const val SEARCH = "search"
    const val YUK_NARX = "yuk_narx"
    const val YUK_REPORT = "yuk_report"
    const val CLIENT_HISTORY = "client_history"
    const val QARZ = "qarz"
    const val EDIT_TX = "edit_tx"
    const val RASXOD = "rasxod"
    const val KARZINA = "karzina"
    const val CLIENT_NARX = "client_narx"
    const val REMINDER = "reminder"
    const val MANAGER = "manager"
    const val DASHBOARD = "dashboard"
    const val HELP = "help"
    const val ESLAT = "eslat"
    const val SKLAD = "sklad"
}

// ── DIAGNOSTIKA: ekran HAQIQATAN kompozitsiyaga kirdimi? ──
private var DIAG_LAST_IN: String = "start"
@androidx.compose.runtime.Composable
private fun DiagIn(name: String) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        DIAG_LAST_IN = name
        android.util.Log.w("daftar", "IN:$name")
        onDispose { android.util.Log.w("daftar", "OUT:$name") }
    }
}

@Composable
fun DaftarNavHost() {
    val nav = rememberNavController()
    // Har navigatsiyada (oldinga/orqaga) klaviatura va fokusni tozalaymiz.
    // Bu IME (klaviatura insets) sababli yuzaga keladigan OQ EKRAN bug'ini barcha ekranda oldini oladi.
    val imeFocusMgr = androidx.compose.ui.platform.LocalFocusManager.current
    val imeKb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, _ ->
            android.util.Log.w("daftar", "NAV → ${dest.route}")
            runCatching { imeKb?.hide(); imeFocusMgr.clearFocus(true) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        uz.daftar.app.ui.common.LocalGoHome provides {
            nav.popBackStack(Routes.TODAY, inclusive = false)
        }
    ) {
    Box(Modifier.fillMaxSize()) {
    NavHost(navController = nav, startDestination = Routes.TODAY) {

        composable(Routes.TODAY) { DiagIn("today");
            TodayScreen(
                onNewTx = { nav.navigate(Routes.NEW_TX) },
                onClients = { nav.navigate(Routes.CLIENTS) },
                onReports = { nav.navigate(Routes.REPORTS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onEditTx = { txId -> nav.navigate("${Routes.EDIT_TX}/$txId") },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onYukNarx = { nav.navigate(Routes.YUK_NARX) },
                onYukReport = { nav.navigate(Routes.YUK_REPORT) },
                onAlias = { nav.navigate(Routes.ALIAS) },
                onRasxod = { nav.navigate(Routes.RASXOD) },
                onKarzina = { nav.navigate(Routes.KARZINA) },
                onQarz = { nav.navigate(Routes.QARZ) },
                onManager = { nav.navigate(Routes.MANAGER) },
                onDashboard = { nav.navigate(Routes.DASHBOARD) },
                onHelp = { nav.navigate(Routes.HELP) },
                onEslat = { nav.navigate(Routes.ESLAT) },
                onSklad = { nav.navigate(Routes.SKLAD) }
            )
        }

        composable(Routes.NEW_TX) { DiagIn("new_tx");
            NewTransactionScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.CLIENTS) { DiagIn("clients");
            ClientsScreen(
                onBack = { nav.popBackStack() },
                onClientClick = { name ->
                    nav.navigate("${Routes.CLIENT_HISTORY}/$name")
                }
            )
        }

        composable(Routes.QARZ) { DiagIn("qarz");
            ClientsScreen(
                onBack = { nav.popBackStack() },
                onClientClick = { name ->
                    nav.navigate("${Routes.CLIENT_HISTORY}/$name")
                },
                debtorsOnly = true
            )
        }

        composable(
            route = "${Routes.CLIENT_HISTORY}/{clientName}",
            arguments = listOf(navArgument("clientName") { type = NavType.StringType })
        ) {
            ClientHistoryScreen(
                onBack = { nav.popBackStack() },
                onEditTx = { txId -> nav.navigate("${Routes.EDIT_TX}/$txId") },
                onSetNarx = { name -> nav.navigate("${Routes.CLIENT_NARX}/$name") }
            )
        }

        composable(
            route = "${Routes.CLIENT_NARX}/{clientName}",
            arguments = listOf(navArgument("clientName") { type = NavType.StringType })
        ) {
            ClientNarxScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = "${Routes.EDIT_TX}/{txId}",
            arguments = listOf(navArgument("txId") { type = NavType.StringType })
        ) {
            EditTransactionScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() }
            )
        }

        composable(Routes.REPORTS) { DiagIn("reports");
            ReportsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) { DiagIn("settings");
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onAlias = { nav.navigate(Routes.ALIAS) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onYukNarx = { nav.navigate(Routes.YUK_NARX) },
                onRasxod = { nav.navigate(Routes.RASXOD) },
                onKarzina = { nav.navigate(Routes.KARZINA) },
                onReminder = { nav.navigate(Routes.REMINDER) },
                onManager = { nav.navigate(Routes.MANAGER) }
            )
        }

        composable(Routes.ALIAS) { DiagIn("alias"); AliasScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SEARCH) { DiagIn("search"); SearchScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.YUK_NARX) { DiagIn("yuk_narx"); YukNarxScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.YUK_REPORT) { DiagIn("yuk_report"); uz.daftar.app.ui.screen.yukreport.YukReportScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RASXOD) { DiagIn("rasxod"); RasxodScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.KARZINA) { DiagIn("karzina"); KarzinaScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.REMINDER) { DiagIn("reminder"); ReminderLimitScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.MANAGER) { DiagIn("manager"); uz.daftar.app.ui.screen.manager.ManagerScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DASHBOARD) { DiagIn("dashboard"); uz.daftar.app.ui.screen.dashboard.DashboardScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HELP) { DiagIn("help"); uz.daftar.app.ui.screen.help.HelpScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ESLAT) { DiagIn("eslat"); uz.daftar.app.ui.screen.eslat.EslatScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SKLAD) { DiagIn("sklad"); uz.daftar.app.ui.screen.sklad.SkladScreen(onBack = { nav.popBackStack() }) }
    }
    // ── DIAGNOSTIKA: oq ekranda ham ko'rinadi — qaysi route + jonli sanagich ──
    // Agar oq ekran paytida shu yozuv KO'RINSA → o'sha "route" ekrani bo'sh chizyapti.
    // Agar yozuv ham YO'QOLSA → butun composition (root) o'lgan.
    val dbgEntry = nav.currentBackStackEntryAsState()
    val dbgRoute = dbgEntry.value?.destination?.route ?: "—"
    val dbgTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); dbgTick.intValue++ } }
    Text(
        "\u2B24 ${dbgRoute} \u00B7 in:${DIAG_LAST_IN} \u00B7 ${dbgTick.intValue}s",
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(3.dp)
            .background(Color(0x66000000))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color(0xFFFF5252),
        fontSize = 10.sp
    )
    }
    }
}
