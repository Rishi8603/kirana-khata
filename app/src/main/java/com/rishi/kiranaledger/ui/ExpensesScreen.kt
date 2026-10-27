package com.rishi.kiranaledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.ExpenseDao
import com.rishi.kiranaledger.data.PaymentMode
import com.rishi.kiranaledger.formatRupees
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One month of shop expenses: total, where the money went (by category), and every expense. */
@Composable
fun ExpensesScreen(expenseDao: ExpenseDao, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val from = month.atDay(1).toEpochDay()
    val to = month.atEndOfMonth().toEpochDay()
    val expenses by remember(from, to) { expenseDao.expenses(from, to) }.collectAsState(initial = emptyList())
    val byCategory by remember(from, to) { expenseDao.categoryTotals(from, to) }.collectAsState(initial = emptyList())
    val total = expenses.sumOf { it.amountPaise }

    val appLocale = LocalConfiguration.current.locales[0]
    val monthLocale = if (appLocale.language == "hi" && appLocale.script != "Latn") appLocale else Locale.ENGLISH
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", monthLocale))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Brand,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_expense), fontSize = 17.sp) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(title = stringResource(R.string.expenses), onBack = onBack) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(monthLabel, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹" + formatRupees(total), fontSize = 28.sp, fontWeight = FontWeight.Medium, color = DueRed)
                        }
                        IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.now()) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                    if (byCategory.isNotEmpty()) {
                        HorizontalDivider()
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCategory.forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(row.category, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text("₹" + formatRupees(row.totalPaise), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
            if (expenses.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_expenses),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        expenses.forEachIndexed { i, e ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onEdit(e.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(e.category)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(e.category, fontSize = 17.sp)
                                    val mode = stringResource(if (e.paymentMode == PaymentMode.CASH) R.string.cash else R.string.online)
                                    Text(
                                        listOfNotNull(formatEntryDate(e.expenseDate), mode, e.note).joinToString(" · "),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Text("₹" + formatRupees(e.amountPaise), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = DueRed)
                            }
                            if (i < expenses.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}
