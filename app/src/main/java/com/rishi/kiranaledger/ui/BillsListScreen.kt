package com.rishi.kiranaledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillDao
import com.rishi.kiranaledger.formatRupees

/** Bills tab: all bills, newest first, with their payment status. Search by name, phone or bill number. */
@Composable
fun BillsTab(billDao: BillDao, onOpen: (String) -> Unit) {
    val bills by billDao.bills().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var dueOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(bills, query, dueOnly) { bills.filter { billMatches(it, query) && (!dueOnly || it.duePaise > 0) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
    ) {
        if (bills.isEmpty()) {
            item { Text(stringResource(R.string.no_bills_yet), fontSize = 17.sp, modifier = Modifier.padding(top = 16.dp)) }
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                placeholder = { Text(stringResource(R.string.search_bills)) },
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
        item {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!dueOnly, { dueOnly = false }, label = { Text(stringResource(R.string.filter_all)) })
                FilterChip(dueOnly, { dueOnly = true }, label = { Text(stringResource(R.string.filter_due)) })
            }
        }
        if (shown.isEmpty()) {
            item { Text(stringResource(R.string.no_match), fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp)) }
        }
        // One lazy row per bill (not one big card), so a shop with thousands of bills still scrolls smoothly.
        itemsIndexed(shown, key = { _, b -> b.id }) { i, bill ->
            val shape = when {
                shown.size == 1 -> RoundedCornerShape(12.dp)
                i == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                i == shown.lastIndex -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, shape)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(bill.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(bill.partyName ?: "#")
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            bill.partyName ?: stringResource(R.string.walk_in),
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${bill.numberLabel} · ${formatEntryDate(bill.billDate)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("₹" + formatRupees(bill.totalPaise), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        BillStatusLabel(bill.status)
                    }
                }
                if (i < shown.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp))
            }
        }
    }
}

/** Matches the customer name, phone or bill number ("12", "#0012" and "0012" all find bill 12). */
fun billMatches(bill: Bill, query: String): Boolean {
    val q = query.trim().removePrefix("#")
    if (q.isEmpty()) return true
    if (bill.partyName?.contains(q, ignoreCase = true) == true) return true
    if (bill.partyPhone?.contains(q) == true) return true
    val number = q.toIntOrNull()
    return number != null && number == bill.billNo
}
