package com.rishi.kiranaledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.safeDrawingPadding
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.GstCatalog
import com.rishi.kiranaledger.data.GstChoice
import com.rishi.kiranaledger.data.GstEntry
import com.rishi.kiranaledger.data.GstLink
import com.rishi.kiranaledger.data.gstPercentLabel

/** Small "HSN 0409 · 5%" label used in lists. */
@Composable
fun HsnBadge(hsn: String, cgstMilli: Int) {
    Text(
        "${stringResource(R.string.hsn)} $hsn · ${gstPercentLabel(2L * cgstMilli)}%",
        fontSize = 11.sp,
        color = Brand,
        modifier = Modifier
            .background(BrandSoft, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Full-screen HSN chooser: step 1 pick a schedule (or search across all by HSN code / keyword),
 * step 2 tap the HSN row — its product nature and GST rate come with it.
 */
@Composable
fun GstPickerDialog(onDismiss: () -> Unit, onPicked: (GstChoice) -> Unit) {
    val context = LocalContext.current
    val all = remember { GstCatalog.entries(context) }
    var schedule by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // One list row per (row, HSN code), so the shopkeeper picks the exact code in a single tap.
    val rows = remember(schedule, query) {
        GstCatalog.search(all, schedule, query).flatMap { e ->
            val digits = query.filter(Char::isDigit)
            val codes = if (digits.isNotEmpty() && digits.length == query.trim().length) {
                e.hsnCodes.filter { it.startsWith(digits) || digits.startsWith(it) }
            } else {
                e.hsnCodes
            }
            (codes.ifEmpty { listOf("") }).map { code -> e to code }
        }
    }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } } // keyboard ready for the search
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                ScreenHeader(title = stringResource(R.string.choose_hsn), onBack = onDismiss)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    placeholder = { Text(stringResource(R.string.search_hsn)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(schedule == null, { schedule = null }, label = { Text(stringResource(R.string.all)) })
                    GstCatalog.SCHEDULES.forEach { (id, cgst) ->
                        FilterChip(
                            schedule == id,
                            { schedule = id },
                            label = { Text(if (id == "EXEMPT") stringResource(R.string.exempt) else "${gstPercentLabel(2L * cgst)}%") },
                        )
                    }
                }
                Text(stringResource(R.string.gst_source_note), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.no_hsn_results), fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { (e, code) -> e.id + "/" + code }) { (e, code) ->
                        GstRow(e, code) { onPicked(GstChoice(e.id, e.schedule, code, e.nature, e.cgstMilli)) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun GstRow(e: GstEntry, code: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(code.ifEmpty { "—" }, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                if (e.cgstMilli == 0) stringResource(R.string.exempt) else "GST ${gstPercentLabel(e.totalMilli)}%",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (e.cgstMilli == 0) GotGreen else Brand,
            )
        }
        Text(e.nature, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rates offered when the shopkeeper overrides a row's rate (total GST, in 0.001%). */
private val OVERRIDE_TOTALS = listOf(0L, 250L, 1500L, 3000L, 5000L, 18000L, 28000L, 40000L)

/**
 * The GST part of an item form: current HSN + product nature, rate (pre-filled from the row, can be changed),
 * "price includes GST", and change / remove.
 */
@Composable
fun GstSection(link: GstLink?, onChange: (GstLink?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.gst_hsn), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (link == null) {
            Text(stringResource(R.string.no_gst_set), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.choose_hsn)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HsnBadge(link.choice.hsnCode, link.cgstMilli)
                Spacer(Modifier.width(8.dp))
                Text(
                    "CGST ${gstPercentLabel(link.cgstMilli.toLong())}% + SGST ${gstPercentLabel(link.cgstMilli.toLong())}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(link.choice.nature, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.gst_rate), fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OVERRIDE_TOTALS.forEach { total ->
                    FilterChip(
                        selected = 2L * link.cgstMilli == total,
                        onClick = { onChange(link.copy(cgstMilli = (total / 2).toInt())) },
                        label = { Text("${gstPercentLabel(total)}%") },
                    )
                }
            }
            Text(stringResource(R.string.gst_added_on_top), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.change)) }
                TextButton(onClick = { onChange(null) }) { Text(stringResource(R.string.remove_gst), color = DueRed) }
            }
        }
    }
    if (picking) {
        GstPickerDialog(
            onDismiss = { picking = false },
            onPicked = { choice ->
                onChange(GstLink(choice, choice.cgstMilli, priceIncludesTax = false))
                picking = false
            },
        )
    }
}
