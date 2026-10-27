package com.rishi.kiranaledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Switch
import com.rishi.kiranaledger.AppLock
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.ShopSettings
import com.rishi.kiranaledger.data.BillDao
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.data.StockDao

enum class MainTab { KHATA, BILLS, STOCK, MORE }

/** The app's home: a tab bar at the bottom (Khata, Bills, More) like Khatabook, with one floating action per tab. */
@Composable
fun MainScreen(
    dao: LedgerDao,
    billDao: BillDao,
    stockDao: StockDao,
    onOpenCustomer: (String) -> Unit,
    onOpenItem: (key: String?) -> Unit,
    onAddCustomer: (PartyType) -> Unit,
    onNewBill: () -> Unit,
    onOpenBill: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenGalla: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.KHATA) }
    var khataType by rememberSaveable { mutableStateOf(PartyType.CUSTOMER) }
    var showLanguage by remember { mutableStateOf(false) }
    val shopName = ShopSettings(LocalContext.current).name

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Brand)
                Spacer(Modifier.width(10.dp))
                Text(
                    shopName.ifBlank { stringResource(R.string.app_name) },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showLanguage = true }) {
                    Icon(Icons.Filled.Translate, contentDescription = stringResource(R.string.language))
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TabItem(MainTab.KHATA, tab, Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.khata)) { tab = it }
                TabItem(MainTab.BILLS, tab, Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.bills)) { tab = it }
                TabItem(MainTab.STOCK, tab, Icons.Filled.Inventory2, stringResource(R.string.stock)) { tab = it }
                TabItem(MainTab.MORE, tab, Icons.Filled.Menu, stringResource(R.string.more)) { tab = it }
            }
        },
        floatingActionButton = {
            when (tab) {
                MainTab.KHATA -> Fab(
                    stringResource(if (khataType == PartyType.SUPPLIER) R.string.new_supplier else R.string.new_customer),
                ) { onAddCustomer(khataType) }
                MainTab.BILLS -> Fab(stringResource(R.string.new_bill), onNewBill)
                MainTab.STOCK -> Fab(stringResource(R.string.add_item_stock)) { onOpenItem(null) }
                MainTab.MORE -> Unit
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.KHATA -> KhataTab(dao, khataType, { khataType = it }, onOpenCustomer, onOpenCollections)
                MainTab.BILLS -> BillsTab(billDao, onOpenBill)
                MainTab.STOCK -> StockTab(stockDao) { onOpenItem(it) }
                MainTab.MORE -> MoreTab(onOpenGalla = onOpenGalla, onOpenReports = onOpenReports, onOpenExpenses = onOpenExpenses, onOpenBackup = onOpenBackup, onOpenSettings = onOpenSettings, onOpenLanguage = { showLanguage = true })
            }
        }
    }
    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
}

@Composable
private fun RowScope.TabItem(
    item: MainTab,
    selected: MainTab,
    icon: ImageVector,
    label: String,
    onSelect: (MainTab) -> Unit,
) {
    NavigationBarItem(
        selected = item == selected,
        onClick = { onSelect(item) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, fontSize = 13.sp) },
    )
}

@Composable
private fun Fab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Brand,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(label, fontSize = 17.sp, maxLines = 1) },
    )
}

/** More tab: expenses, shop details, language, and (soon) stock, galla, reports, backup and app lock. */
@Composable
private fun MoreTab(
    onOpenGalla: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLanguage: () -> Unit,
) {
    val context = LocalContext.current
    var lockOn by remember { mutableStateOf(AppLock.isEnabled(context)) }
    val lockUnavailable = stringResource(R.string.app_lock_unavailable)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            MoreRow(Icons.Filled.PointOfSale, stringResource(R.string.galla), onOpenGalla)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            MoreRow(Icons.Filled.BarChart, stringResource(R.string.reports), onOpenReports)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            MoreRow(Icons.Filled.Payments, stringResource(R.string.expenses), onOpenExpenses)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            MoreRow(Icons.Filled.CloudUpload, stringResource(R.string.backup), onOpenBackup)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Brand)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(stringResource(R.string.app_lock), fontSize = 17.sp)
                    Text(stringResource(R.string.app_lock_body), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = lockOn, onCheckedChange = { on ->
                    if (on && !AppLock.canUse(context)) {
                        Toast.makeText(context, lockUnavailable, Toast.LENGTH_LONG).show()
                    } else {
                        AppLock.setEnabled(context, on)
                        lockOn = on
                    }
                })
            }
            HorizontalDivider(Modifier.padding(start = 56.dp))
            MoreRow(Icons.Filled.Storefront, stringResource(R.string.shop_details_short), onOpenSettings)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            MoreRow(Icons.Filled.Translate, stringResource(R.string.language), onOpenLanguage)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Brand)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
