package com.rishi.kiranaledger.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureAsPdf
import com.rishi.kiranaledger.bill.BillPdf
import com.rishi.kiranaledger.bill.StatementPdf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Entry
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.formatRupees
import kotlinx.coroutines.launch

@Composable
fun CustomerScreen(
    dao: LedgerDao,
    customerId: String,
    onBack: () -> Unit,
    onAddEntry: (gave: Boolean) -> Unit,
    onEditEntry: (entryId: String) -> Unit,
    onEditCustomer: () -> Unit,
    onNewBill: () -> Unit,
    onOpenBill: (billId: String) -> Unit,
    onOpenPhoto: (path: String) -> Unit,
) {
    val customer by dao.customer(customerId).collectAsState(initial = null)
    val entries by dao.entries(customerId).collectAsState(initial = emptyList())
    val c = customer ?: return
    val supplier = c.partyType == PartyType.SUPPLIER
    // Money owed, from the shop's point of view: positive = customer owes us / we owe the supplier.
    val owed = if (supplier) c.balancePaise else -c.balancePaise

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pickingDueDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.entry_deleted)
    val undoLabel = stringResource(R.string.undo)
    val whatsappMissing = stringResource(R.string.whatsapp_missing)
    val reminderText = stringResource(R.string.reminder_message, c.name, formatRupees(c.balancePaise))

    // Running balance after each entry (Khatabook shows this): entries are newest first,
    // so each one's balance is the current balance minus everything newer.
    val runningBalances = remember(entries, c.balancePaise) {
        var after = c.balancePaise
        entries.map { entry -> after.also { after -= entry.amountPaise } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BigButton(
                    text = stringResource(if (supplier) R.string.purchase else R.string.gave),
                    sub = stringResource(if (supplier) R.string.purchase_hint else R.string.gave_hint),
                    container = DueRedBg,
                    content = DueRed,
                    modifier = Modifier.weight(1f),
                    onClick = { onAddEntry(true) },
                )
                BigButton(
                    text = stringResource(if (supplier) R.string.pay_supplier else R.string.got),
                    sub = stringResource(if (supplier) R.string.pay_supplier_hint else R.string.got_hint),
                    container = GotGreenBg,
                    content = GotGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onAddEntry(false) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(title = c.name, onBack = onBack) {
                    IconButton(onClick = onEditCustomer) { Icon(Icons.Filled.Edit, contentDescription = stringResource(if (supplier) R.string.edit_supplier else R.string.edit_customer)) }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(if (supplier) R.string.delete_supplier else R.string.delete_customer), tint = DueRed)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        BalanceText(c.balancePaise, fontSizeSp = 34, supplier = supplier)
                        if (owed > 0) {
                            Text(stringResource(if (supplier) R.string.to_pay else R.string.due), fontSize = 15.sp)
                            if (!supplier) DueDateLine(c.dueDate)
                        }
                    }
                    HorizontalDivider()
                    // Round icon actions in rows of four, so every action is always on screen (no hidden scrolling).
                    // Bills, due dates and reminders are for customers; suppliers get Call only.
                    val actions = buildList<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> {
                        if (!supplier) add(Triple(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.new_bill), onNewBill))
                        if (!supplier && owed > 0) add(Triple(Icons.Filled.Event, stringResource(R.string.due_date)) { pickingDueDate = true })
                        if (c.phone != null) {
                            if (!supplier && owed > 0) {
                                add(Triple(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.remind)) {
                                    if (!openWhatsApp(context, c.phone, reminderText)) {
                                        Toast.makeText(context, whatsappMissing, Toast.LENGTH_SHORT).show()
                                    }
                                })
                            }
                            add(Triple(Icons.Filled.Call, stringResource(R.string.call)) { dial(context, c.phone) })
                        }
                        add(Triple(Icons.Filled.PictureAsPdf, stringResource(R.string.statement)) {
                            BillPdf.share(context, StatementPdf.create(context, c, entries))
                        })
                        c.bahiPhotoPath?.let { path -> add(Triple(Icons.Filled.Photo, stringResource(R.string.bahi_photo)) { onOpenPhoto(path) }) }
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
                        actions.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { (icon, label, onClick) -> QuickAction(icon, label, Modifier.weight(1f), onClick) }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item {
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.no_entries_yet), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(R.string.long_press_to_delete), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(entries, key = { _, e -> e.id }) { i, entry ->
                EntryRow(
                    entry,
                    supplier = supplier,
                    balanceAfter = runningBalances[i],
                    onClick = { if (entry.billId != null) onOpenBill(entry.billId) else onEditEntry(entry.id) },
                    onLongPress = {
                        scope.launch {
                            dao.deleteEntry(entry.id)
                            val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) dao.undoDeleteEntry(entry.id)
                        }
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(if (supplier) R.string.delete_supplier_title else R.string.delete_customer_title, c.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A pending balance is the one thing the shopkeeper must not lose by accident, so say it plainly.
                    if (c.balancePaise != 0L) {
                        Text(
                            stringResource(R.string.delete_party_balance_warning, formatRupees(c.balancePaise)),
                            color = DueRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(stringResource(R.string.delete_party_text), fontSize = 15.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        dao.deleteCustomer(c.id)
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete), color = DueRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickingDueDate) {
        DayPickerDialog(
            initialDay = c.dueDate ?: (today() + 7),
            futureOnly = true,
            onDismiss = { pickingDueDate = false },
            onPicked = { day ->
                scope.launch { dao.setDueDate(c.id, day) }
                pickingDueDate = false
            },
            extraAction = if (c.dueDate != null) {
                stringResource(R.string.remove_due_date) to {
                    scope.launch { dao.setDueDate(c.id, null) }
                    pickingDueDate = false
                }
            } else {
                null
            },
        )
    }
}

/** "Due 25 Oct" in red when the date has passed. */
@Composable
private fun DueDateLine(dueDate: Long?) {
    if (dueDate == null) return
    val overdue = dueDate < today()
    Text(
        stringResource(R.string.due_on, formatEntryDate(dueDate)),
        fontSize = 14.sp,
        color = if (overdue) DueRed else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (overdue) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(entry: Entry, supplier: Boolean, balanceAfter: Long, onClick: () -> Unit, onLongPress: () -> Unit) {
    // "Red" entries increase what is owed: a customer's Credit, or a Purchase from a supplier.
    val red = if (supplier) entry.amountPaise > 0 else entry.amountPaise < 0
    val owedAfter = if (supplier) balanceAfter else -balanceAfter
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatEntryDate(entry.entryDate), fontSize = 15.sp)
                    if (entry.fromBahi) {
                        Spacer(Modifier.width(8.dp))
                        BahiTag()
                    }
                }
                if (entry.note != null) Text(entry.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.running_balance, formatRupees(balanceAfter)),
                    fontSize = 12.sp,
                    color = if (owedAfter > 0) DueRed else GotGreen,
                )
            }
            val label = when {
                entry.fromBahi -> stringResource(R.string.opening_balance)
                red -> stringResource(if (supplier) R.string.purchase else R.string.gave)
                else -> stringResource(if (supplier) R.string.pay_supplier else R.string.got)
            }
            Text(
                "$label${if (label.endsWith("₹")) "" else " ₹"}${formatRupees(entry.amountPaise)}",
                color = if (red) DueRed else GotGreen,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
