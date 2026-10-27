package com.rishi.kiranaledger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.rishi.kiranaledger.data.CashEntry
import com.rishi.kiranaledger.data.PeriodSummary
import com.rishi.kiranaledger.data.ReportDao
import com.rishi.kiranaledger.formatRupees
import com.rishi.kiranaledger.parseRupeesToPaise
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Galla: one day's cash. Cash in and out are gathered automatically from bills, khata payments,
 * supplier payments and expenses; manual entries cover anything else.
 */
@Composable
fun GallaScreen(reportDao: ReportDao, onBack: () -> Unit) {
    var day by rememberSaveable { mutableStateOf(today()) }
    val summary by remember(day) { reportDao.summary(day, day) }.collectAsState(initial = null)
    val manual by remember(day) { reportDao.cashEntries(day) }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf<Boolean?>(null) } // true = cash in, false = cash out
    var deletingCash by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(title = stringResource(R.string.galla), onBack = onBack) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { day -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (day == today()) stringResource(R.string.today) + ", " + formatEntryDate(day) else formatEntryDate(day),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val net = summary?.netPaise ?: 0
                            Text(
                                (if (net < 0) "−₹" else "₹") + formatRupees(net),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (net < 0) DueRed else GotGreen,
                            )
                            Text(stringResource(R.string.net), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { day += 1 }, enabled = day < today()) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
            summary?.let { s -> item { CashFlowCard(s) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(stringResource(R.string.add_cash_in), container = GotGreenBg, content = GotGreen, modifier = Modifier.weight(1f)) { adding = true }
                    BigButton(stringResource(R.string.add_cash_out), container = DueRedBg, content = DueRed, modifier = Modifier.weight(1f)) { adding = false }
                }
            }
            if (manual.isNotEmpty()) {
                item { Text(stringResource(R.string.manual_entries), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        manual.forEachIndexed { i, e ->
                            ManualRow(e, onLongPress = { deletingCash = e.id })
                            if (i < manual.lastIndex) HorizontalDivider()
                        }
                    }
                }
                item { Text(stringResource(R.string.long_press_to_delete), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    deletingCash?.let { id ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_cash_title),
            text = null,
            onConfirm = {
                deletingCash = null
                scope.launch { reportDao.deleteCash(id) }
            },
            onDismiss = { deletingCash = null },
        )
    }
    adding?.let { cashIn ->
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(stringResource(if (cashIn) R.string.cash_in else R.string.cash_out)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                        label = { Text(stringResource(R.string.amount)) },
                        prefix = { Text("₹ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(60) },
                        label = { Text(stringResource(R.string.add_note)) },
                        placeholder = { Text(stringResource(R.string.cash_note_hint)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val paise = parseRupeesToPaise(amount)?.takeIf { it > 0 }
                    if (paise != null) {
                        scope.launch {
                            reportDao.insertCash(
                                CashEntry(entryDate = day, amountPaise = if (cashIn) paise else -paise, note = note.trim().ifBlank { null }),
                            )
                        }
                    }
                    adding = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualRow(e: CashEntry, onLongPress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(e.note ?: stringResource(if (e.amountPaise > 0) R.string.other_cash_in else R.string.other_cash_out), fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            (if (e.amountPaise > 0) "+₹" else "−₹") + formatRupees(e.amountPaise),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (e.amountPaise > 0) GotGreen else DueRed,
        )
    }
}

/** Cash in (green) and cash out (red), line by line. */
@Composable
private fun CashFlowCard(s: PeriodSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LineRow(stringResource(R.string.cash_in), s.cashInPaise, GotGreen, bold = true)
            LineRow(stringResource(R.string.from_bills), s.billPaidPaise)
            LineRow(stringResource(R.string.khata_payments), s.paymentsReceivedPaise)
            if (s.manualInPaise > 0) LineRow(stringResource(R.string.other_cash_in), s.manualInPaise)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            LineRow(stringResource(R.string.cash_out), s.cashOutPaise, DueRed, bold = true)
            LineRow(stringResource(R.string.supplier_payments), s.supplierPaymentsPaise)
            LineRow(stringResource(R.string.expenses), s.expensesPaise)
            if (s.manualOutPaise > 0) LineRow(stringResource(R.string.other_cash_out), s.manualOutPaise)
        }
    }
}

@Composable
private fun LineRow(label: String, paise: Long, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, color = color, modifier = Modifier.weight(1f))
        Text(
            "₹" + formatRupees(paise),
            fontSize = if (bold) 16.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            color = color,
        )
    }
}

private enum class Period { TODAY, WEEK, MONTH, LAST_MONTH }

/** Reports: business totals and cash flow for a period, plus who owes the most. */
@Composable
fun ReportsScreen(reportDao: ReportDao, onBack: () -> Unit, onOpenCustomer: (String) -> Unit) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    val now = LocalDate.now()
    val (from, to) = when (period) {
        Period.TODAY -> now to now
        Period.WEEK -> now.with(DayOfWeek.MONDAY) to now
        Period.MONTH -> now.withDayOfMonth(1) to now
        Period.LAST_MONTH -> YearMonth.from(now).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
    }
    val summary by remember(period) { reportDao.summary(from.toEpochDay(), to.toEpochDay()) }.collectAsState(initial = null)
    val topDues by reportDao.topDues().collectAsState(initial = emptyList())

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(title = stringResource(R.string.reports), onBack = onBack) }
            item {
                // Scrolls sideways so long labels ("Last month", "Pichhle mahine") never get squeezed.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Period.TODAY to R.string.period_today,
                        Period.WEEK to R.string.period_week,
                        Period.MONTH to R.string.period_month,
                        Period.LAST_MONTH to R.string.period_last_month,
                    ).forEach { (p, label) ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(label), fontSize = 13.sp, maxLines = 1) })
                    }
                }
            }
            summary?.let { s ->
                item {
                    Text(stringResource(R.string.business), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LineRow(stringResource(R.string.sales), s.salesPaise, bold = true)
                            Row(Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.bills_count), fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(s.billCount.toString(), fontSize = 14.sp)
                            }
                            LineRow(stringResource(R.string.credit_given), s.creditGivenPaise, DueRed)
                            LineRow(stringResource(R.string.khata_payments), s.paymentsReceivedPaise, GotGreen)
                            LineRow(stringResource(R.string.purchases), s.purchasesPaise)
                            LineRow(stringResource(R.string.expenses), s.expensesPaise)
                        }
                    }
                }
                item { Text(stringResource(R.string.cash_flow), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                item { CashFlowCard(s) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.net), fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(
                                (if (s.netPaise < 0) "−₹" else "₹") + formatRupees(s.netPaise),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (s.netPaise < 0) DueRed else GotGreen,
                            )
                        }
                    }
                }
            }
            if (topDues.isNotEmpty()) {
                item { Text(stringResource(R.string.top_dues), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        topDues.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenCustomer(c.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(c.name, size = 36)
                                Text(c.name, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                Text("₹" + formatRupees(c.balancePaise), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DueRed)
                            }
                            if (i < topDues.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
