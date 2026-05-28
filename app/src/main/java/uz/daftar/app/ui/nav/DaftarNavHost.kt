package uz.daftar.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    const val CLIENT_HISTORY = "client_history"
    const val EDIT_TX = "edit_tx"
    const val RASXOD = "rasxod"
    const val KARZINA = "karzina"
    const val CLIENT_NARX = "client_narx"
    const val REMINDER = "reminder"
}

@Composable
fun DaftarNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TODAY) {

        composable(Routes.TODAY) {
            TodayScreen(
                onNewTx = { nav.navigate(Routes.NEW_TX) },
                onClients = { nav.navigate(Routes.CLIENTS) },
                onReports = { nav.navigate(Routes.REPORTS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onEditTx = { txId -> nav.navigate("${Routes.EDIT_TX}/$txId") },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onYukNarx = { nav.navigate(Routes.YUK_NARX) },
                onAlias = { nav.navigate(Routes.ALIAS) },
                onRasxod = { nav.navigate(Routes.RASXOD) },
                onKarzina = { nav.navigate(Routes.KARZINA) }
            )
        }

        composable(Routes.NEW_TX) {
            NewTransactionScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.CLIENTS) {
            ClientsScreen(
                onBack = { nav.popBackStack() },
                onClientClick = { name ->
                    nav.navigate("${Routes.CLIENT_HISTORY}/$name")
                }
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

        composable(Routes.REPORTS) {
            ReportsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onAlias = { nav.navigate(Routes.ALIAS) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onYukNarx = { nav.navigate(Routes.YUK_NARX) },
                onRasxod = { nav.navigate(Routes.RASXOD) },
                onKarzina = { nav.navigate(Routes.KARZINA) },
                onReminder = { nav.navigate(Routes.REMINDER) }
            )
        }

        composable(Routes.ALIAS) { AliasScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SEARCH) { SearchScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.YUK_NARX) { YukNarxScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RASXOD) { RasxodScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.KARZINA) { KarzinaScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.REMINDER) { ReminderLimitScreen(onBack = { nav.popBackStack() }) }
    }
}
