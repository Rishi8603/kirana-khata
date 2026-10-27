package com.rishi.kiranaledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillDao
import com.rishi.kiranaledger.data.BillItem
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PaymentMode
import com.rishi.kiranaledger.data.roundToRupee
import com.rishi.kiranaledger.ShopSettings
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.GstSplit
import com.rishi.kiranaledger.data.gstSplit
import androidx.compose.ui.platform.LocalContext
import com.rishi.kiranaledger.formatQuantity
import com.rishi.kiranaledger.formatRupees
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.rishi.kiranaledger.data.GstChoice
import com.rishi.kiranaledger.data.GstLink
import com.rishi.kiranaledger.data.gstLink
import com.rishi.kiranaledger.data.itemKey

private enum class PayChoice { FULL, CREDIT, PART }

/** A line's GST: charged only by a regular GST shop on a line that has a rate. */
fun lineSplit(line: DraftLine, mode: GstMode): GstSplit =
    if (mode == GstMode.REGULAR && line.gst != null && line.gst.cgstMilli > 0) {
        gstSplit(line.amountPaise, line.gst.cgstMilli, priceIncludesTax = false) // Rate × Qty, GST added on top
    } else {
        GstSplit(line.amountPaise, 0, 0)
    }

/**
 * Make a bill. [presetCustomerId] is set when opened from a customer's page.
 * With [editBillId] it opens that bill (within 24 hours of making it) for changes; the bill number stays the same.
 */
@Composable
fun BillScreen(
    ledgerDao: LedgerDao,
    billDao: BillDao,
    presetCustomerId: String?,
    onBack: () -> Unit,
    onSaved: (billId: String) -> Unit,
    editBillId: String? = null,
) {
    var editing by remember { mutableStateOf<Bill?>(null) }
    var customerId by rememberSaveable { mutableStateOf(presetCustomerId) }
    val customer by remember(customerId) {
        customerId?.let { ledgerDao.customer(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val lines = remember { mutableStateListOf<DraftLine>() }
    var editingIndex by remember { mutableStateOf<Int?>(null) } // -1 = new line
    var showPicker by remember { mutableStateOf(false) }
    var payChoice by rememberSaveable { mutableStateOf(PayChoice.FULL) }
    var partPaid by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Ship to: same as buyer unless the shopkeeper unticks it and types another place.
    var shipSame by rememberSaveable { mutableStateOf(true) }
    var shipName by rememberSaveable { mutableStateOf("") }
    var shipAddress by rememberSaveable { mutableStateOf("") }
    var shipPhone by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var confirmLeave by remember { mutableStateOf(false) }
    // Back with items on the bill asks first, so a slip of the thumb never throws away a long bill.
    val leave: () -> Unit = { if (lines.isNotEmpty() && !saving) { confirmLeave = true } else onBack() }
    BackHandler(enabled = lines.isNotEmpty() && !saving) { confirmLeave = true }

    // Edit mode: load the bill's customer, lines (with their GST links) and payment once.
    LaunchedEffect(editBillId) {
        val bill = editBillId?.let { billDao.bill(it).first() } ?: return@LaunchedEffect
        editing = bill
        customerId = bill.customerId
        lines.clear()
        billDao.itemsOnce(bill.id).forEach { item ->
            val product = billDao.item(itemKey(item.name))
            val link = product?.gstLink()?.takeIf { it.choice.hsnCode == item.hsnCode }
                ?: item.hsnCode?.let { GstLink(GstChoice("", "", it, "", item.cgstMilli), item.cgstMilli, item.priceIncludesTax) }
            lines.add(DraftLine(item.name, item.quantity, item.unit, item.pricePaise, link))
        }
        payChoice = when {
            bill.paidPaise >= bill.totalPaise -> PayChoice.FULL
            bill.paidPaise == 0L -> PayChoice.CREDIT
            else -> PayChoice.PART
        }
        if (payChoice == PayChoice.PART) partPaid = (bill.paidPaise / 100).toString()
        mode = bill.paymentMode
        shipSame = !bill.shipsElsewhere
        shipName = bill.shipName.orEmpty()
        shipAddress = bill.shipAddress.orEmpty()
        shipPhone = bill.shipPhone.orEmpty()
    }

    // A customer with a saved shipping address gets it filled in on new bills (the shopkeeper can still change or untick it).
    var autoShipFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(customer?.id) {
        val c = customer
        if (editBillId != null || c?.id == autoShipFor) return@LaunchedEffect
        if (c != null && c.shipAddress != null) {
            shipSame = false
            shipName = c.name
            shipAddress = c.shipAddress
            shipPhone = c.phone.orEmpty()
            autoShipFor = c.id
        } else if (autoShipFor != null) {
            // Switched from a customer with a saved address to one without: back to "same as buyer".
            shipSame = true
            shipName = ""
            shipAddress = ""
            shipPhone = ""
            autoShipFor = null
        }
    }

    val walkIn = customerId == null
    // GST is only charged by a regular GST shop; composition and unregistered bills stay at the plain price.
    // An edited bill keeps the registration it was made under.
    val shopMode = remember { ShopSettings(context).gstMode }
    val gstMode = editing?.gstMode ?: shopMode
    val splits = lines.map { lineSplit(it, gstMode) }
    val gstTotal = splits.fold(GstSplit.ZERO) { a, b -> a + b }
    val subtotal = gstTotal.totalPaise
    val (total, roundOff) = roundToRupee(subtotal)
    val paid = when {
        walkIn || payChoice == PayChoice.FULL -> total
        payChoice == PayChoice.CREDIT -> 0L
        else -> ((partPaid.toLongOrNull() ?: 0L) * 100).coerceAtMost(total)
    }

    val save: () -> Unit = {
        if (lines.isEmpty()) {
            errorRes = R.string.no_items
        } else if (!shipSame && shipName.isBlank()) {
            errorRes = R.string.ship_name_needed
        } else if (!saving) {
            saving = true
            scope.launch {
                val old = editing
                val bill = Bill(
                    id = old?.id ?: java.util.UUID.randomUUID().toString(),
                    billNo = old?.billNo ?: billDao.nextBillNo(),
                    billDate = old?.billDate ?: java.time.LocalDate.now().toEpochDay(),
                    createdAt = old?.createdAt ?: System.currentTimeMillis(),
                    customerId = customerId,
                    partyName = customer?.name,
                    partyPhone = customer?.phone,
                    partyAddress = customer?.address,
                    partyGstin = customer?.gstin,
                    shipName = if (shipSame) null else shipName.trim(),
                    shipAddress = if (shipSame) null else shipAddress.trim().ifBlank { null },
                    shipPhone = if (shipSame) null else shipPhone.trim().ifBlank { null },
                    subtotalPaise = subtotal,
                    roundOffPaise = roundOff,
                    totalPaise = total,
                    paidPaise = paid,
                    paymentMode = mode,
                    gstMode = gstMode,
                )
                val billItems = lines.mapIndexed { i, line ->
                        val split = splits[i]
                        val charged = split.taxPaise > 0
                        BillItem(
                            billId = bill.id, position = i, name = line.name, quantity = line.quantity, unit = line.unit,
                            pricePaise = line.pricePaise,
                            hsnCode = line.gst?.choice?.hsnCode,
                            cgstMilli = if (charged) line.gst!!.cgstMilli else 0,
                            priceIncludesTax = false,
                            taxablePaise = if (charged) split.taxablePaise else 0,
                            cgstPaise = split.cgstPaise,
                            sgstPaise = split.sgstPaise,
                        )
                    }
                if (old != null) {
                    billDao.updateBill(bill, billItems, lines.map { it.gst })
                } else {
                    billDao.saveBill(bill, billItems, lines.map { it.gst })
                }
                onSaved(bill.id)
            }
        }
    }

    Scaffold(
        bottomBar = {
            // Always on screen, however long the bill: the total, "Add item" and "Make bill".
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
                if (lines.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.total_items, lines.size), fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Text("₹" + formatRupees(total), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { editingIndex = -1; errorRes = null },
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_item), fontSize = 17.sp, maxLines = 1)
                    }
                    BigButton(
                        text = stringResource(if (editBillId != null) R.string.save else R.string.make_bill),
                        container = GotGreenBg,
                        content = GotGreen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        onClick = save,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = stringResource(if (editBillId != null) R.string.edit_bill else R.string.new_bill), onBack = leave)

            // Customer
            OutlinedCard(Modifier.fillMaxWidth().clickable { showPicker = true }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.customer_label), fontSize = 12.sp)
                        Text(customer?.name ?: stringResource(R.string.walk_in), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(stringResource(R.string.choose_customer), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Ship to
            Row(
                Modifier.fillMaxWidth().clickable { shipSame = !shipSame; errorRes = null },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = shipSame, onCheckedChange = { shipSame = it; errorRes = null })
                Text(stringResource(R.string.ship_same_as_buyer), fontSize = 15.sp)
            }
            if (!shipSame) {
                OutlinedTextField(
                    value = shipName, onValueChange = { shipName = it.take(60) },
                    label = { Text(stringResource(R.string.ship_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shipAddress, onValueChange = { shipAddress = it.take(150) },
                    label = { Text(stringResource(R.string.ship_address)) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shipPhone, onValueChange = { shipPhone = it.filter(Char::isDigit).take(10) },
                    label = { Text(stringResource(R.string.ship_phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
            }

            // Items
            lines.forEachIndexed { index, line ->
                Row(
                    Modifier.fillMaxWidth().clickable { editingIndex = index }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, fontSize = 17.sp)
                        Text("${formatQuantity(line.quantity)} ${line.unit} × ₹${formatRupees(line.pricePaise)}", fontSize = 13.sp)
                        line.gst?.let { HsnBadge(it.choice.hsnCode, it.cgstMilli) }
                    }
                    Text("₹" + formatRupees(splits[index].taxablePaise), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { lines.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                    }
                }
                HorizontalDivider()
            }

            // Totals
            if (lines.isNotEmpty()) {
                if (gstTotal.taxPaise > 0) {
                    TotalRow(stringResource(R.string.taxable_value), gstTotal.taxablePaise)
                    TotalRow(stringResource(R.string.cgst), gstTotal.cgstPaise)
                    TotalRow(stringResource(R.string.sgst), gstTotal.sgstPaise)
                } else {
                    TotalRow(stringResource(R.string.subtotal), subtotal)
                }
                if (roundOff != 0L) TotalRow(stringResource(R.string.round_off), roundOff, signed = true)
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.total), fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("₹" + formatRupees(total), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Payment
            Text(stringResource(R.string.payment_label), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayChip(stringResource(R.string.full_paid), payChoice == PayChoice.FULL || walkIn) { payChoice = PayChoice.FULL }
                PayChip(stringResource(R.string.on_credit), payChoice == PayChoice.CREDIT && !walkIn) {
                    if (walkIn) errorRes = R.string.walk_in_must_pay else payChoice = PayChoice.CREDIT
                }
                PayChip(stringResource(R.string.part_paid), payChoice == PayChoice.PART && !walkIn) {
                    if (walkIn) errorRes = R.string.walk_in_must_pay else payChoice = PayChoice.PART
                }
            }
            if (payChoice == PayChoice.PART && !walkIn) {
                OutlinedTextField(
                    value = partPaid,
                    onValueChange = { partPaid = it.filter(Char::isDigit).trimStart('0').take(7) },
                    label = { Text(stringResource(R.string.paid_amount)) },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (paid > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayChip(stringResource(R.string.cash), mode == PaymentMode.CASH) { mode = PaymentMode.CASH }
                    PayChip(stringResource(R.string.online), mode == PaymentMode.ONLINE) { mode = PaymentMode.ONLINE }
                }
            }
            if (lines.isNotEmpty() && paid < total) {
                TotalRow(stringResource(R.string.balance_due), total - paid, color = DueRed)
            }
            Spacer(Modifier.padding(8.dp))
        }
    }

    editingIndex?.let { index ->
        ItemEditorDialog(
            billDao = billDao,
            initial = lines.getOrNull(index),
            onDismiss = { editingIndex = null },
            onDone = { line ->
                val added = index !in lines.indices
                if (added) lines.add(line) else lines[index] = line
                editingIndex = null
                // Show the line just added (the list may be longer than the screen).
                if (added) scope.launch { delay(150); scroll.animateScrollTo(scroll.maxValue) }
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_bill_title)) },
            text = { Text(stringResource(R.string.leave_bill_text), fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.leave), color = DueRed) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.stay)) } },
        )
    }
    if (showPicker) {
        CustomerPickerDialog(
            dao = ledgerDao,
            onDismiss = { showPicker = false },
            onPicked = { id ->
                customerId = id
                errorRes = null
                showPicker = false
            },
        )
    }
}

@Composable
private fun PayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 15.sp) })
}

@Composable
private fun TotalRow(label: String, paise: Long, signed: Boolean = false, color: Color = Color.Unspecified) {
    val sign = if (signed && paise > 0) "+" else if (signed && paise < 0) "−" else ""
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 16.sp, color = color, modifier = Modifier.weight(1f))
        Text("$sign₹" + formatRupees(paise), fontSize = 16.sp, color = color)
    }
}
