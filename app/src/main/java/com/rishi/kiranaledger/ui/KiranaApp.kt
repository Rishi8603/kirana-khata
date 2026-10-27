package com.rishi.kiranaledger.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rishi.kiranaledger.data.BillDao
import com.rishi.kiranaledger.data.ExpenseDao
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.data.ReportDao
import com.rishi.kiranaledger.data.StockDao

private const val NEW_BILL = "newbill?customer={customer}"

/**
 * Screen map:
 *   home -> customer/{id} -> entry/{id}/{gave}
 *   home -> add -> customer/{id}
 *   home (tabs: Khata, Bills, More) | customer -> newbill -> bill/{id}
 *   home -> bill/{id};  home -> settings
 */
@Composable
fun KiranaApp(dao: LedgerDao, billDao: BillDao, expenseDao: ExpenseDao, stockDao: StockDao, reportDao: ReportDao) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            MainScreen(
                dao = dao,
                billDao = billDao,
                stockDao = stockDao,
                onOpenCustomer = { id -> nav.navigate("customer/$id") },
                onOpenItem = { key -> nav.navigate(if (key == null) "item" else "item?key=${Uri.encode(key)}") },
                onAddCustomer = { type -> nav.navigate("add?type=${type.name}") },
                onNewBill = { nav.navigate("newbill") },
                onOpenBill = { id -> nav.navigate("bill/$id") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenCollections = { nav.navigate("collections") },
                onOpenExpenses = { nav.navigate("expenses") },
                onOpenGalla = { nav.navigate("galla") },
                onOpenReports = { nav.navigate("reports") },
                onOpenBackup = { nav.navigate("backup") },
            )
        }
        composable("customer/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            CustomerScreen(
                dao = dao,
                customerId = id,
                onBack = { nav.popBackStack() },
                onAddEntry = { gave -> nav.navigate("entry/$id/$gave") },
                onEditEntry = { entryId -> nav.navigate("editentry/$entryId") },
                onEditCustomer = { nav.navigate("editcustomer/$id") },
                onNewBill = { nav.navigate("newbill?customer=$id") },
                onOpenBill = { billId -> nav.navigate("bill/$billId") },
                onOpenPhoto = { path -> nav.navigate("photo?path=${Uri.encode(path)}") },
            )
        }
        composable(
            "photo?path={path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            PhotoScreen(path = entry.arguments?.getString("path").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
        composable("entry/{id}/{gave}") { entry ->
            AmountScreen(
                dao = dao,
                customerId = entry.arguments?.getString("id").orEmpty(),
                gave = entry.arguments?.getString("gave") == "true",
                editEntryId = null,
                onDone = { nav.popBackStack() },
            )
        }
        composable("editentry/{entryId}") { entry ->
            AmountScreen(
                dao = dao,
                customerId = null,
                gave = true,
                editEntryId = entry.arguments?.getString("entryId"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            "add?type={type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = PartyType.CUSTOMER.name }),
        ) { entry ->
            AddCustomerScreen(
                dao = dao,
                editCustomerId = null,
                partyType = PartyType.valueOf(entry.arguments?.getString("type") ?: PartyType.CUSTOMER.name),
                onBack = { nav.popBackStack() },
                // Replace the form with the new customer's page, so Back returns to home.
                onSaved = { id -> nav.navigate("customer/$id") { popUpTo("home") } },
            )
        }
        composable("editcustomer/{id}") { entry ->
            AddCustomerScreen(
                dao = dao,
                editCustomerId = entry.arguments?.getString("id"),
                partyType = PartyType.CUSTOMER, // replaced by the saved type once loaded
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(
            "item?key={key}",
            arguments = listOf(navArgument("key") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ItemScreen(stockDao = stockDao, key = entry.arguments?.getString("key"), onDone = { nav.popBackStack() })
        }
        composable("galla") { GallaScreen(reportDao = reportDao, onBack = { nav.popBackStack() }) }
        composable("reports") {
            ReportsScreen(reportDao = reportDao, onBack = { nav.popBackStack() }, onOpenCustomer = { id -> nav.navigate("customer/$id") })
        }
        composable("expenses") {
            ExpensesScreen(
                expenseDao = expenseDao,
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("expense") },
                onEdit = { id -> nav.navigate("expense?id=$id") },
            )
        }
        composable(
            "expense?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            AddExpenseScreen(expenseDao = expenseDao, editId = entry.arguments?.getString("id"), onDone = { nav.popBackStack() })
        }
        composable("collections") {
            CollectionsScreen(dao = dao, onBack = { nav.popBackStack() }, onOpenCustomer = { id -> nav.navigate("customer/$id") })
        }
        composable(
            NEW_BILL,
            arguments = listOf(navArgument("customer") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            BillScreen(
                ledgerDao = dao,
                billDao = billDao,
                presetCustomerId = entry.arguments?.getString("customer"),
                onBack = { nav.popBackStack() },
                // Replace the form with the saved bill, ready to share.
                onSaved = { billId -> nav.navigate("bill/$billId") { popUpTo(NEW_BILL) { inclusive = true } } },
            )
        }
        composable("bill/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            BillDetailScreen(
                billDao = billDao,
                billId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("editbill/$id") },
            )
        }
        composable("editbill/{id}") { entry ->
            BillScreen(
                ledgerDao = dao,
                billDao = billDao,
                presetCustomerId = null,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
                editBillId = entry.arguments?.getString("id"),
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
