package com.rishi.kiranaledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Expense
import com.rishi.kiranaledger.data.ExpenseDao
import com.rishi.kiranaledger.data.PaymentMode
import com.rishi.kiranaledger.formatRupees
import com.rishi.kiranaledger.parseRupeesToPaise
import kotlinx.coroutines.launch

/** Add (or with [editId], edit or delete) one expense. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(expenseDao: ExpenseDao, editId: String?, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<Expense?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var customCategory by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
    var date by rememberSaveable { mutableStateOf(today()) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var usedCategories by remember { mutableStateOf(emptyList<String>()) }
    val presets = stringArrayResource(R.array.expense_categories).toList()
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(editId) {
        usedCategories = expenseDao.usedCategories()
        val e = editId?.let { expenseDao.expense(it) } ?: return@LaunchedEffect
        editing = e
        if (!prefilled) {
            amount = formatRupees(e.amountPaise).replace(",", "")
            category = e.category
            customCategory = e.category !in presets && e.category !in usedCategories
            note = e.note.orEmpty()
            mode = e.paymentMode
            date = e.expenseDate
            prefilled = true
        }
    }
    // Preset categories first, then any others the shopkeeper has typed before.
    val categories = (presets + usedCategories).distinct()

    Scaffold(
        bottomBar = {
            BigButton(
                text = stringResource(R.string.save),
                icon = Icons.Filled.Check,
                container = BrandSoft,
                content = Brand,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                onClick = {
                    val paise = parseRupeesToPaise(amount)?.takeIf { it > 0 }
                    when {
                        paise == null -> errorRes = R.string.amount_required
                        category.isBlank() -> errorRes = R.string.category_required
                        else -> scope.launch {
                            val old = editing
                            if (old != null) {
                                expenseDao.update(
                                    old.copy(
                                        amountPaise = paise,
                                        category = category.trim(),
                                        note = note.trim().ifBlank { null },
                                        paymentMode = mode,
                                        expenseDate = date,
                                        updatedAt = System.currentTimeMillis(),
                                    ),
                                )
                            } else {
                                expenseDao.insert(
                                    Expense(amountPaise = paise, category = category.trim(), note = note.trim().ifBlank { null }, paymentMode = mode, expenseDate = date),
                                )
                            }
                            onDone()
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(title = stringResource(if (editing != null) R.string.edit_expense else R.string.add_expense), onBack = onDone)

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filter { c -> c.isDigit() || c == '.' }.take(10)
                    errorRes = null
                },
                label = { Text(stringResource(R.string.amount)) },
                prefix = { Text("₹ ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.expense_category), fontSize = 15.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                categories.forEach { option ->
                    FilterChip(
                        selected = !customCategory && category == option,
                        onClick = {
                            category = option
                            customCategory = false
                            errorRes = null
                        },
                        label = { Text(option) },
                    )
                }
                FilterChip(
                    selected = customCategory,
                    onClick = {
                        customCategory = true
                        category = ""
                    },
                    label = { Text(stringResource(R.string.other_category)) },
                )
            }
            if (customCategory) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {
                        category = it.take(30)
                        errorRes = null
                    },
                    label = { Text(stringResource(R.string.expense_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == PaymentMode.CASH, { mode = PaymentMode.CASH }, label = { Text(stringResource(R.string.cash)) })
                FilterChip(mode == PaymentMode.ONLINE, { mode = PaymentMode.ONLINE }, label = { Text(stringResource(R.string.online)) })
            }

            OutlinedButton(onClick = { pickingDate = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (date == today()) stringResource(R.string.today) + ", " + formatEntryDate(date) else formatEntryDate(date))
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(100) },
                label = { Text(stringResource(R.string.add_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }

            editing?.let {
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = DueRed) }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_expense_title),
            text = null,
            onConfirm = {
                confirmDelete = false
                editing?.let { e -> scope.launch { expenseDao.delete(e.id); onDone() } }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (pickingDate) {
        DayPickerDialog(
            initialDay = date,
            pastOnly = true,
            onDismiss = { pickingDate = false },
            onPicked = {
                date = it
                pickingDate = false
            },
        )
    }
}
