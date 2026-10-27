package com.rishi.kiranaledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Entry
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.data.redSign
import com.rishi.kiranaledger.formatRupees
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_DIGITS = 7 // up to ₹99,99,999

/**
 * Type the amount for a Credit (gave = true) or Payment (gave = false) entry.
 * With [editEntryId] it edits that entry instead; customer and direction then come from the entry.
 */
@Composable
fun AmountScreen(dao: LedgerDao, customerId: String?, gave: Boolean, editEntryId: String?, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<Entry?>(null) }
    var digits by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var entryDate by rememberSaveable { mutableStateOf(today()) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var showError by rememberSaveable { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(editEntryId) {
        val entry = editEntryId?.let { dao.entry(it) } ?: return@LaunchedEffect
        editing = entry
        if (!prefilled) {
            digits = (abs(entry.amountPaise) / 100).toString()
            note = entry.note.orEmpty()
            showNote = entry.note != null
            entryDate = entry.entryDate
            prefilled = true
        }
    }

    val custId = editing?.customerId ?: customerId.orEmpty()
    val customer by dao.customer(custId).collectAsState(initial = null)
    val partyType = customer?.partyType ?: PartyType.CUSTOMER
    val supplier = partyType == PartyType.SUPPLIER
    // isGave = the red button: Credit to a customer, or Purchase from a supplier.
    val isGave = editing?.let { (it.amountPaise > 0) == (partyType.redSign() > 0) } ?: gave
    val color = if (isGave) DueRed else GotGreen
    val rupees = digits.toLongOrNull() ?: 0L

    Scaffold(
        bottomBar = {
            BigButton(
                text = stringResource(R.string.save),
                icon = Icons.Filled.Check,
                container = if (isGave) DueRedBg else GotGreenBg,
                content = color,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                onClick = {
                    if (rupees == 0L) {
                        showError = true
                    } else if (!saving && customer != null) {
                        saving = true
                        val amount = (if (isGave) partyType.redSign() else -partyType.redSign()) * rupees * 100
                        val cleanNote = note.trim().ifBlank { null }
                        scope.launch {
                            val existing = editing
                            if (existing != null) {
                                dao.updateEntry(existing.id, amount, cleanNote, entryDate)
                            } else {
                                dao.insertEntry(Entry(customerId = custId, amountPaise = amount, note = cleanNote, entryDate = entryDate))
                            }
                            onDone()
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val name = customer?.name.orEmpty()
            ScreenHeader(
                title = if (editing != null) {
                    stringResource(R.string.edit_entry)
                } else {
                    val title = when {
                        supplier && isGave -> R.string.purchase_from
                        supplier -> R.string.paid_to
                        isGave -> R.string.gave_to
                        else -> R.string.got_from
                    }
                    stringResource(title, name)
                },
                titleColor = color,
                onBack = onDone,
            ) {
                if (editing != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = DueRed)
                    }
                }
            }

            Text(
                "₹ " + formatRupees(rupees * 100),
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            HorizontalDivider()
            if (showError) {
                Text(stringResource(R.string.amount_required), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            if (showNote) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(100) },
                    label = { Text(stringResource(R.string.add_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { showNote = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("+ " + stringResource(R.string.add_note))
                }
            }

            // Tap to back-date, e.g. when copying old entries from the bahi.
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                val label = formatEntryDate(entryDate)
                Text(if (entryDate == today()) stringResource(R.string.today) + ", " + label else label, fontSize = 15.sp)
            }

            NumberPad(
                onKey = { key ->
                    showError = false
                    val next = (digits + key).trimStart('0')
                    if (next.length <= MAX_DIGITS) digits = next
                },
                onBackspace = { digits = digits.dropLast(1) },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_entry_title),
            text = null,
            onConfirm = {
                confirmDelete = false
                editing?.let { e -> scope.launch { dao.deleteEntry(e.id); onDone() } }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (pickingDate) {
        DayPickerDialog(
            initialDay = entryDate,
            pastOnly = true,
            onDismiss = { pickingDate = false },
            onPicked = {
                entryDate = it
                pickingDate = false
            },
        )
    }
}
