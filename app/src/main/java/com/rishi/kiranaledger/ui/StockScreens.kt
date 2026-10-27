package com.rishi.kiranaledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.GstLink
import com.rishi.kiranaledger.data.Item
import com.rishi.kiranaledger.data.gstLink
import com.rishi.kiranaledger.data.StockDao
import com.rishi.kiranaledger.data.StockReason
import com.rishi.kiranaledger.data.UNITS
import com.rishi.kiranaledger.data.itemKey
import com.rishi.kiranaledger.data.lineAmountPaise
import com.rishi.kiranaledger.formatQuantity
import com.rishi.kiranaledger.formatRupees
import com.rishi.kiranaledger.parseRupeesToPaise
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Stock tab: low-stock warning, stock value, and every item with its quantity. */
@Composable
fun StockTab(stockDao: StockDao, onOpenItem: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val items by remember(query) { stockDao.items(query.trim()) }.collectAsState(initial = emptyList())
    val lowCount by stockDao.lowStockCount().collectAsState(initial = 0)
    val stockValue = items.filter { it.trackStock && it.stockQty > 0 }.sumOf { lineAmountPaise(it.stockQty, it.purchasePricePaise) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lowCount > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = DueRedBg), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = DueRed)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            LocalContext.current.resources.getQuantityString(R.plurals.low_stock_count, lowCount, lowCount),
                            color = DueRed,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        if (stockValue > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.stock_value), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("₹" + formatRupees(stockValue), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_items) + " / " + stringResource(R.string.hsn)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (items.isEmpty() && query.isBlank()) {
            item { Text(stringResource(R.string.no_items_yet), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        // An HSN code typed in search groups every product saved under each matching HSN.
        val hsnQuery = query.trim().let { q -> q.isNotEmpty() && q.all(Char::isDigit) }
        val groups = if (hsnQuery) items.groupBy { it.hsnCode?.takeIf { h -> h.startsWith(query.trim()) } } else mapOf(null to items)
        groups.forEach { (hsn, group) ->
            if (hsn != null) {
                item { HsnBadge(hsn, group.first().cgstMilli) }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    group.forEachIndexed { i, item ->
                        ItemRow(item, onClick = { onOpenItem(item.nameKey) })
                        if (i < group.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.name)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(item.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("₹${formatRupees(item.lastPricePaise)} / ${item.unit}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.hsnCode?.let { HsnBadge(it, item.cgstMilli) }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (item.trackStock) {
                Text(
                    "${formatQuantity(item.stockQty)} ${item.unit}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isLow) DueRed else GotGreen,
                )
                if (item.isLow) Text(stringResource(R.string.low), fontSize = 12.sp, color = DueRed)
            } else {
                Text(stringResource(R.string.not_tracked), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Add an item ([key] null) or edit one: prices, unit, stock tracking, Stock in / out, and its history. */
@Composable
fun ItemScreen(stockDao: StockDao, key: String?, onDone: () -> Unit) {
    val existing by remember(key) { key?.let { stockDao.item(it) } ?: flowOf(null) }.collectAsState(initial = null)
    val moves by remember(key) { key?.let { stockDao.moves(it) } ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(UNITS.first()) }
    var salePrice by rememberSaveable { mutableStateOf("") }
    var purchasePrice by rememberSaveable { mutableStateOf("") }
    var track by rememberSaveable { mutableStateOf(true) }
    var openingQty by rememberSaveable { mutableStateOf("") }
    var minStock by rememberSaveable { mutableStateOf("") }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var gst by remember { mutableStateOf<GstLink?>(null) }
    var showError by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf<Boolean?>(null) } // true = stock in, false = stock out
    var nameTaken by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(existing) {
        val e = existing ?: return@LaunchedEffect
        if (!prefilled) {
            name = e.name
            unit = e.unit
            salePrice = if (e.lastPricePaise > 0) formatRupees(e.lastPricePaise).replace(",", "") else ""
            purchasePrice = if (e.purchasePricePaise > 0) formatRupees(e.purchasePricePaise).replace(",", "") else ""
            track = e.trackStock
            gst = e.gstLink()
            minStock = if (e.minStock > 0) formatQuantity(e.minStock) else ""
            prefilled = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BigButton(
                text = stringResource(R.string.save),
                icon = Icons.Filled.Check,
                container = BrandSoft,
                content = Brand,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                onClick = {
                    val sale = parseRupeesToPaise(salePrice) ?: 0L
                    if (name.isBlank()) showError = true else scope.launch {
                        val k = itemKey(name)
                        // A corrected name must not clash with another item already in stock.
                        if (k != key && stockDao.itemOnce(k) != null && key != null) {
                            nameTaken = true
                            return@launch
                        }
                        val old = existing ?: stockDao.itemOnce(k)
                        val item = (old ?: Item(nameKey = k, name = name.trim(), unit = unit, lastPricePaise = sale, useCount = 0)).copy(
                            nameKey = k,
                            name = name.trim(),
                            unit = unit,
                            lastPricePaise = sale,
                            purchasePricePaise = parseRupeesToPaise(purchasePrice) ?: 0L,
                            trackStock = track,
                            minStock = minStock.toDoubleOrNull() ?: 0.0,
                            updatedAt = System.currentTimeMillis(),
                            hsnCode = gst?.choice?.hsnCode,
                            gstSchedule = gst?.choice?.schedule,
                            gstEntryId = gst?.choice?.entryId,
                            productNature = gst?.choice?.nature,
                            cgstMilli = gst?.cgstMilli ?: 0,
                            priceIncludesTax = false,
                        )
                        if (key != null) stockDao.renameAndSave(key, item) else stockDao.upsert(item)
                        // Opening stock for a new item goes in as a logged Stock in.
                        val opening = openingQty.toDoubleOrNull() ?: 0.0
                        if (key == null && track && opening > 0) stockDao.move(k, opening, StockReason.PURCHASE)
                        onDone()
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(title = if (key == null) stringResource(R.string.add_item_stock) else existing?.name.orEmpty(), onBack = onDone) {
                    if (key != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_item), tint = DueRed)
                        }
                    }
                }
            }
            // The name can be corrected later too (a typo made while billing, say).
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        showError = false
                        nameTaken = false
                    },
                    label = { Text(stringResource(R.string.item_name)) },
                    isError = (showError && name.isBlank()) || nameTaken,
                    supportingText = if (nameTaken) ({ Text(stringResource(R.string.item_name_taken)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Current stock with Stock in / Stock out, for items already being tracked.
            existing?.takeIf { it.trackStock }?.let { e ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.current_stock), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${formatQuantity(e.stockQty)} ${e.unit}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (e.isLow) DueRed else GotGreen,
                            )
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { moveDialog = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Text(" " + stringResource(R.string.stock_in))
                                }
                                OutlinedButton(onClick = { moveDialog = false }) {
                                    Icon(Icons.Filled.Remove, contentDescription = null)
                                    Text(" " + stringResource(R.string.stock_out))
                                }
                            }
                        }
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(UNITS) { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) }
                }
            }
            item { PriceField(stringResource(R.string.sale_price) + " / " + unit, salePrice) { salePrice = it } }
            item { PriceField(stringResource(R.string.purchase_price) + " / " + unit, purchasePrice) { purchasePrice = it } }
            item { GstSection(gst) { gst = it } }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.track_stock), fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(checked = track, onCheckedChange = { track = it })
                }
            }
            if (track) {
                if (key == null) {
                    item { QtyField(stringResource(R.string.current_stock) + " ($unit)", openingQty) { openingQty = it } }
                }
                item { QtyField(stringResource(R.string.min_stock) + " ($unit)", minStock) { minStock = it } }
            }
            if (moves.isNotEmpty()) {
                item { Text(stringResource(R.string.stock_history), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        moves.forEachIndexed { i, m ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    val reason = when (m.reason) {
                                        StockReason.SALE -> R.string.reason_sale
                                        StockReason.PURCHASE -> R.string.reason_purchase
                                        StockReason.ADJUST -> R.string.reason_adjust
                                        StockReason.BILL_DELETED -> R.string.reason_bill_deleted
                                        StockReason.BILL_EDITED -> R.string.reason_bill_edited
                                    }
                                    Text(stringResource(reason), fontSize = 15.sp)
                                    val day = Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                                    Text(formatEntryDate(day), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    (if (m.quantity > 0) "+" else "−") + formatQuantity(abs(m.quantity)),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (m.quantity > 0) GotGreen else DueRed,
                                )
                            }
                            if (i < moves.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && key != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_item_title, existing?.name.orEmpty())) },
            text = { Text(stringResource(R.string.delete_item_text), fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        stockDao.deleteItem(key)
                        onDone()
                    }
                }) { Text(stringResource(R.string.delete), color = DueRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    moveDialog?.let { stockIn ->
        var qty by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { moveDialog = null },
            title = { Text(stringResource(if (stockIn) R.string.stock_in else R.string.stock_out)) },
            text = { QtyField(stringResource(R.string.how_many) + " ($unit)", qty) { qty = it } },
            confirmButton = {
                TextButton(onClick = {
                    val q = qty.toDoubleOrNull()?.takeIf { it > 0 }
                    if (q != null && key != null) {
                        scope.launch { stockDao.move(key, if (stockIn) q else -q, if (stockIn) StockReason.PURCHASE else StockReason.ADJUST) }
                    }
                    moveDialog = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { moveDialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PriceField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(9)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun QtyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(8)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
