package com.rishi.kiranaledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.rishi.kiranaledger.data.GstLink
import com.rishi.kiranaledger.data.gstLink
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.BillDao
import com.rishi.kiranaledger.data.Item
import com.rishi.kiranaledger.data.UNITS
import com.rishi.kiranaledger.data.lineAmountPaise
import com.rishi.kiranaledger.formatQuantity
import com.rishi.kiranaledger.formatRupees
import com.rishi.kiranaledger.parseRupeesToPaise

/** One line being typed on a new bill. */
data class DraftLine(val name: String, val quantity: Double, val unit: String, val pricePaise: Long, val gst: GstLink? = null) {
    val amountPaise: Long get() = lineAmountPaise(quantity, pricePaise)
}

/**
 * Add or edit one bill line. As the shopkeeper types the name, items from earlier bills are suggested;
 * tapping one fills in its unit and last price (which he can still change).
 * Full screen, with Cancel / Done pinned just above the keyboard so they are never hidden behind it.
 */
@Composable
fun ItemEditorDialog(billDao: BillDao, initial: DraftLine?, onDismiss: () -> Unit, onDone: (DraftLine) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(initial?.quantity?.let(::formatQuantity) ?: "1") }
    var unit by rememberSaveable { mutableStateOf(initial?.unit ?: UNITS.first()) }
    var price by rememberSaveable { mutableStateOf(initial?.pricePaise?.let { formatRupees(it).replace(",", "") }.orEmpty()) }
    var suggestions by remember { mutableStateOf(emptyList<Item>()) }
    var gst by remember { mutableStateOf(initial?.gst) }
    var showErrors by remember { mutableStateOf(false) }

    LaunchedEffect(name) {
        suggestions = if (name.isBlank()) emptyList() else billDao.suggestItems(name.trim()).filter { it.name != name.trim() }
    }

    val qty = quantity.toDoubleOrNull()?.takeIf { it > 0 }
    val pricePaise = parseRupeesToPaise(price)?.takeIf { it > 0 }
    val valid = name.isNotBlank() && qty != null && pricePaise != null
    val submit: () -> Unit = { if (valid) onDone(DraftLine(name.trim(), qty!!, unit, pricePaise!!, gst)) else { showErrors = true } }
    val nameFocus = remember { FocusRequester() }
    // A new line starts with the keyboard open on the name, ready to type.
    LaunchedEffect(Unit) { if (initial == null) runCatching { nameFocus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                ScreenHeader(title = stringResource(if (initial == null) R.string.add_item else R.string.edit_item), onBack = onDismiss)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text(stringResource(R.string.item_name)) },
                        isError = showErrors && name.isBlank(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    )
                    if (suggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(suggestions, key = { it.nameKey }) { item ->
                                SuggestionChip(
                                    onClick = {
                                        name = item.name
                                        unit = item.unit
                                        price = formatRupees(item.lastPricePaise).replace(",", "")
                                        gst = item.gstLink() ?: gst
                                    },
                                    label = {
                                        Text("${item.name} ₹${formatRupees(item.lastPricePaise)}" + (item.hsnCode?.let { " · $it" } ?: ""))
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                        label = { Text(stringResource(R.string.quantity)) },
                        isError = showErrors && qty == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(UNITS) { u ->
                            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                        }
                    }
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                        label = { Text(stringResource(R.string.rate) + " / " + unit) },
                        isError = showErrors && pricePaise == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (qty != null && pricePaise != null) {
                        Text(
                            stringResource(R.string.amount) + ": ₹" + formatRupees(lineAmountPaise(qty, pricePaise)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    GstSection(gst) { gst = it }
                    Spacer(Modifier.padding(4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.cancel), fontSize = 18.sp) }
                    BigButton(
                        text = stringResource(R.string.done),
                        container = GotGreenBg,
                        content = GotGreen,
                        modifier = Modifier.weight(1f),
                        onClick = submit,
                    )
                }
            }
        }
    }
}
