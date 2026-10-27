package com.rishi.kiranaledger.ui

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.bill.BillPdf
import com.rishi.kiranaledger.data.BillDao
import com.rishi.kiranaledger.data.BillStatus
import com.rishi.kiranaledger.data.canEdit
import androidx.compose.material.icons.filled.Edit
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.GstSplit
import com.rishi.kiranaledger.formatQuantity
import com.rishi.kiranaledger.formatRupees
import kotlinx.coroutines.launch

/**
 * A saved bill: total first, then the lines. Share (WhatsApp), Download and — for 24 hours — Edit
 * stay pinned at the bottom, so they are on screen however many items the bill has.
 */
@Composable
fun BillDetailScreen(billDao: BillDao, billId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val bill by billDao.bill(billId).collectAsState(initial = null)
    val items by billDao.items(billId).collectAsState(initial = emptyList())
    val b = bill ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadedText = stringResource(R.string.downloaded)
    val deletedText = stringResource(R.string.bill_deleted)
    var confirmDelete by remember { mutableStateOf(false) }
    val editable = b.canEdit()

    Scaffold(
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(
                    text = stringResource(R.string.share_bill),
                    icon = Icons.Filled.Share,
                    container = GotGreenBg,
                    content = GotGreen,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { BillPdf.share(context, BillPdf.create(context, b, items)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editable) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.edit), fontSize = 17.sp, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val saved = BillPdf.download(context, BillPdf.create(context, b, items))
                            if (saved) {
                                Toast.makeText(context, downloadedText, Toast.LENGTH_SHORT).show()
                            } else {
                                BillPdf.share(context, BillPdf.create(context, b, items)) // Android 9 and older
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.download_bill), fontSize = 17.sp, maxLines = 1)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(title = "Bill ${b.numberLabel}", onBack = onBack) {
                if (editable) {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_bill)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.partyName ?: stringResource(R.string.walk_in), fontSize = 18.sp, modifier = Modifier.weight(1f))
                BillStatusLabel(b.status)
            }
            Text(formatEntryDate(b.billDate), fontSize = 14.sp)
            // Total up top, so it is visible without scrolling past a long list of items.
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.total_items, items.size), fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("₹" + formatRupees(b.totalPaise), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (b.duePaise > 0) {
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.balance_due), color = DueRed, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("₹" + formatRupees(b.duePaise), color = DueRed, fontSize = 16.sp)
                }
            }
            if (editable) {
                Text(stringResource(R.string.edit_bill_note), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!b.deleted) {
                Text(stringResource(R.string.edit_closed), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (b.shipsElsewhere) {
                Text(
                    stringResource(R.string.ship_to) + ": " + listOfNotNull(b.shipName, b.shipAddress, b.shipPhone).joinToString(", "),
                    fontSize = 14.sp,
                )
            }
            HorizontalDivider()
            items.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontSize = 16.sp)
                        Text("${formatQuantity(item.quantity)} ${item.unit} × ₹${formatRupees(item.pricePaise)}", fontSize = 13.sp)
                        if (b.gstMode == GstMode.REGULAR && item.hsnCode != null) HsnBadge(item.hsnCode, item.cgstMilli)
                    }
                    Text("₹" + formatRupees(item.gst.taxablePaise), fontSize = 16.sp)
                }
            }
            HorizontalDivider()
            val gst = items.fold(GstSplit.ZERO) { a, i -> a + i.gst }
            if (b.gstMode == GstMode.REGULAR && gst.taxPaise > 0) {
                listOf(R.string.taxable_value to gst.taxablePaise, R.string.cgst to gst.cgstPaise, R.string.sgst to gst.sgstPaise).forEach { (label, paise) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(label), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("₹" + formatRupees(paise), fontSize = 14.sp)
                    }
                }
            }
            // Gross + CGST + SGST + round off = total, like the printed invoice.
            if (b.roundOffPaise != 0L) {
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.round_off), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text((if (b.roundOffPaise > 0) "+" else "−") + "₹" + formatRupees(b.roundOffPaise), fontSize = 14.sp)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.total), fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("₹" + formatRupees(b.totalPaise), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            // Deleting asks first: one stray tap must never remove a bill.
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_bill), color = DueRed) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_bill_title)) },
            text = { Text(stringResource(R.string.delete_bill_text), fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        billDao.deleteBill(b.id)
                        Toast.makeText(context, deletedText, Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete), color = DueRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Small coloured label: PAID / PARTLY PAID / UNPAID / CASH SALE. */
@Composable
fun BillStatusLabel(status: BillStatus) {
    val (res, color) = when (status) {
        BillStatus.PAID -> R.string.status_paid to GotGreen
        BillStatus.CASH_SALE -> R.string.status_cash_sale to GotGreen
        BillStatus.PARTLY_PAID -> R.string.status_partly_paid to PartlyAmber
        BillStatus.UNPAID -> R.string.status_unpaid to DueRed
    }
    Text(stringResource(res), color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
