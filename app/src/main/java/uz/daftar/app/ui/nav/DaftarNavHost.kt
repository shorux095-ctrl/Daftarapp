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
    const val TOLIQ = "toliq"
    const val GRAFIK = "grafik"
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
    // Tez-tez bosishni to'sish (rapid-nav race => OQ EKRAN). 400ms ichida 2-chi navigatsiya e'tiborsiz.
    val lastNav = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val canNav: () -> Boolean = {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastNav.longValue >= 400L) { lastNav.longValue = now; true } else false
    }
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
            if (canNav()) nav.popBackStack(Routes.TODAY, inclusive = false)
        }
    ) {
    Box(Modifier.fillMaxSize()) {
    NavHost(
        navController = nav,
        startDestination = Routes.TODAY,
        enterTransition = { androidx.compose.animation.EnterTransition.None },
        exitTransition = { androidx.compose.animation.ExitTransition.None },
        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
        popExitTransition = { androidx.compose.animation.ExitTransition.None }
    ) {

        composable(Routes.TODAY) { DiagIn("today");
            TodayScreen(
                onNewTx = { if (canNav()) nav.navigate(Routes.NEW_TX) },
                onClients = { if (canNav()) nav.navigate(Routes.CLIENTS) },
                onReports = { if (canNav()) nav.navigate(Routes.REPORTS) },
                onSettings = { if (canNav()) nav.navigate(Routes.SETTINGS) },
                onEditTx = { txId -> if (canNav()) nav.navigate("${Routes.EDIT_TX}/$txId") },
                onSearch = { if (canNav()) nav.navigate(Routes.SEARCH) },
                onYukNarx = { if (canNav()) nav.navigate(Routes.YUK_NARX) },
                onYukReport = { if (canNav()) nav.navigate(Routes.YUK_REPORT) },
                onToliq = { if (canNav()) nav.navigate(Routes.TOLIQ) },
                onGrafik = { if (canNav()) nav.navigate(Routes.GRAFIK) },
                onAlias = { if (canNav()) nav.navigate(Routes.ALIAS) },
                onRasxod = { if (canNav()) nav.navigate(Routes.RASXOD) },
                onKarzina = { if (canNav()) nav.navigate(Routes.KARZINA) },
                onQarz = { if (canNav()) nav.navigate(Routes.QARZ) },
                onManager = { if (canNav()) nav.navigate(Routes.MANAGER) },
                onDashboard = { if (canNav()) nav.navigate(Routes.DASHBOARD) },
                onHelp = { if (canNav()) nav.navigate(Routes.HELP) },
                onEslat = { if (canNav()) nav.navigate(Routes.ESLAT) },
                onSklad = { if (canNav()) nav.navigate(Routes.SKLAD) }
            )
        }

        composable(Routes.NEW_TX) { DiagIn("new_tx");
            NewTransactionScreen(onBack = { if (canNav()) nav.popBackStack() })
        }

        composable(Routes.CLIENTS) { DiagIn("clients");
            ClientsScreen(
                onBack = { if (canNav()) nav.popBackStack() },
                onClientClick = { name ->
                    if (canNav()) nav.navigate("${Routes.CLIENT_HISTORY}/$name")
                }
            )
        }

        composable(Routes.QARZ) { DiagIn("qarz");
            ClientsScreen(
                onBack = { if (canNav()) nav.popBackStack() },
                onClientClick = { name ->
                    if (canNav()) nav.navigate("${Routes.CLIENT_HISTORY}/$name")
                },
                debtorsOnly = true
            )
        }

        composable(
            route = "${Routes.CLIENT_HISTORY}/{clientName}",
            arguments = listOf(navArgument("clientName") { type = NavType.StringType })
        ) {
            ClientHistoryScreen(
                onBack = { if (canNav()) nav.popBackStack() },
                onEditTx = { txId -> if (canNav()) nav.navigate("${Routes.EDIT_TX}/$txId") },
                onSetNarx = { name -> if (canNav()) nav.navigate("${Routes.CLIENT_NARX}/$name") }
            )
        }

        composable(
            route = "${Routes.CLIENT_NARX}/{clientName}",
            arguments = listOf(navArgument("clientName") { type = NavType.StringType })
        ) {
            ClientNarxScreen(onBack = { if (canNav()) nav.popBackStack() })
        }

        composable(
            route = "${Routes.EDIT_TX}/{txId}",
            arguments = listOf(navArgument("txId") { type = NavType.StringType })
        ) {
            EditTransactionScreen(
                onBack = { if (canNav()) nav.popBackStack() },
                onSaved = { if (canNav()) nav.popBackStack() }
            )
        }

        composable(Routes.REPORTS) { DiagIn("reports");
            ReportsScreen(onBack = { if (canNav()) nav.popBackStack() })
        }

        composable(Routes.SETTINGS) { DiagIn("settings");
            SettingsScreen(
                onBack = { if (canNav()) nav.popBackStack() },
                onAlias = { if (canNav()) nav.navigate(Routes.ALIAS) },
                onSearch = { if (canNav()) nav.navigate(Routes.SEARCH) },
                onYukNarx = { if (canNav()) nav.navigate(Routes.YUK_NARX) },
                onRasxod = { if (canNav()) nav.navigate(Routes.RASXOD) },
                onKarzina = { if (canNav()) nav.navigate(Routes.KARZINA) },
                onReminder = { if (canNav()) nav.navigate(Routes.REMINDER) },
                onManager = { if (canNav()) nav.navigate(Routes.MANAGER) }
            )
        }

        composable(Routes.ALIAS) { DiagIn("alias"); AliasScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.SEARCH) { DiagIn("search"); SearchScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.YUK_NARX) { DiagIn("yuk_narx"); YukNarxScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.YUK_REPORT) { DiagIn("yuk_report"); uz.daftar.app.ui.screen.yukreport.YukReportScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.TOLIQ) { DiagIn("toliq"); uz.daftar.app.ui.screen.toliq.ToliqHisobotScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.GRAFIK) { DiagIn("grafik"); uz.daftar.app.ui.screen.grafik.GrafikScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.RASXOD) { DiagIn("rasxod"); RasxodScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.KARZINA) { DiagIn("karzina"); KarzinaScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.REMINDER) { DiagIn("reminder"); ReminderLimitScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.MANAGER) { DiagIn("manager"); uz.daftar.app.ui.screen.manager.ManagerScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.DASHBOARD) { DiagIn("dashboard"); uz.daftar.app.ui.screen.dashboard.DashboardScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.HELP) { DiagIn("help"); uz.daftar.app.ui.screen.help.HelpScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.ESLAT) { DiagIn("eslat"); uz.daftar.app.ui.screen.eslat.EslatScreen(onBack = { if (canNav()) nav.popBackStack() }) }
        composable(Routes.SKLAD) { DiagIn("sklad"); uz.daftar.app.ui.screen.sklad.SkladScreen(onBack = { if (canNav()) nav.popBackStack() }) }
    }
    // (diagnostika olib tashlandi)
    }
    }
}
