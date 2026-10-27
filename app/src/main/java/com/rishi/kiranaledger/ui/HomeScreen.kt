package com.rishi.kiranaledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import com.rishi.kiranaledger.data.CollectionSummary
import com.rishi.kiranaledger.data.CustomerFilter
import com.rishi.kiranaledger.data.CustomerSort
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.DueSummary
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.formatRupees

/** Khata tab: Customers | Suppliers switch, Lena hai / Dena hai summary, search, and every party with their balance. */
@Composable
fun KhataTab(
    dao: LedgerDao,
    type: PartyType,
    onTypeChange: (PartyType) -> Unit,
    onOpenCustomer: (String) -> Unit,
    onOpenCollections: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CustomerFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(CustomerSort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }
    val summary by remember(type) { dao.dueSummary(type) }.collectAsState(initial = DueSummary(0, 0, 0))
    val collection by remember { dao.collectionSummary(today()) }.collectAsState(initial = null)
    val customers by remember(type, query, filter, sort) {
        dao.customerList(type, query.trim(), filter.ordinal, sort.ordinal)
    }.collectAsState(initial = emptyList())
    val supplier = type == PartyType.SUPPLIER

    LazyColumn(
        Modifier.fillMaxSize(),
        // Room at the bottom so the floating button never hides the last customer.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TabRow(
                selectedTabIndex = type.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(selected = !supplier, onClick = { onTypeChange(PartyType.CUSTOMER) }, text = { Text(stringResource(R.string.customers_tab), fontSize = 16.sp) })
                Tab(selected = supplier, onClick = { onTypeChange(PartyType.SUPPLIER) }, text = { Text(stringResource(R.string.suppliers_tab), fontSize = 16.sp) })
            }
        }
        item { SummaryCard(summary, supplier) }
        if (!supplier && summary.totalDuePaise > 0) {
            item { CollectionCard(collection, onOpenCollections) }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(if (supplier) R.string.search_suppliers else R.string.search_customers)) },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == CustomerFilter.ALL, { filter = CustomerFilter.ALL }, label = { Text(stringResource(R.string.filter_all)) })
                    FilterChip(filter == CustomerFilter.DUE, { filter = CustomerFilter.DUE }, label = { Text(stringResource(R.string.filter_due)) })
                    FilterChip(filter == CustomerFilter.SETTLED, { filter = CustomerFilter.SETTLED }, label = { Text(stringResource(R.string.filter_settled)) })
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(
                            CustomerSort.RECENT to R.string.sort_recent,
                            CustomerSort.HIGHEST_DUE to R.string.sort_highest_due,
                            CustomerSort.NAME to R.string.sort_name,
                            CustomerSort.OLDEST to R.string.sort_oldest,
                        ).forEach { (option, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label), fontWeight = if (option == sort) FontWeight.Medium else FontWeight.Normal) },
                                onClick = {
                                    sort = option
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
        if (customers.isEmpty() && query.isBlank() && filter == CustomerFilter.ALL) {
            item {
                Text(
                    stringResource(if (supplier) R.string.no_suppliers_yet else R.string.no_customers_yet),
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        if (customers.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    customers.forEachIndexed { i, customer ->
                        CustomerRow(customer, onClick = { onOpenCustomer(customer.id) })
                        if (i < customers.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DueSummary, supplier: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
            SummaryHalf(stringResource(R.string.you_will_get), summary.totalDuePaise, DueRed, Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 16.dp))
            SummaryHalf(stringResource(R.string.you_will_give), summary.totalAdvancePaise, GotGreen, Modifier.weight(1f))
        }
        Text(
            LocalContext.current.resources.let { res ->
                if (supplier) {
                    res.getQuantityString(R.plurals.to_n_suppliers, summary.partiesWeOwe, summary.partiesWeOwe)
                } else {
                    res.getQuantityString(R.plurals.from_n_customers, summary.customersWithDue, summary.customersWithDue)
                }
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp, horizontal = 16.dp),
        )
    }
}

/** Vasooli: overdue / today / upcoming amounts; tap for the full list. */
@Composable
private fun CollectionCard(c: CollectionSummary?, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Event, contentDescription = null, tint = Brand)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.collections), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            CollectionStat(stringResource(R.string.overdue), c?.overduePaise ?: 0, DueRed, Modifier.weight(1f))
            CollectionStat(stringResource(R.string.due_today), c?.todayPaise ?: 0, PartlyAmber, Modifier.weight(1f))
            CollectionStat(stringResource(R.string.upcoming), c?.upcomingPaise ?: 0, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CollectionStat(label: String, paise: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text("₹" + formatRupees(paise), color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun SummaryHalf(label: String, paise: Long, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text("₹" + formatRupees(paise), color = color, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomerRow(customer: Customer, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(customer.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(customer.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (customer.fromBahi) BahiTag()
        }
        Column(horizontalAlignment = Alignment.End) {
            BalanceText(customer.balancePaise, fontSizeSp = 17, supplier = customer.partyType == PartyType.SUPPLIER)
        }
    }
}

/**
 * Red amount when money is owed (the customer owes the shop, or the shop owes the supplier),
 * green "advance" amount when it's the other way round, green "Hisaab saaf" when settled.
 */
@Composable
fun BalanceText(balancePaise: Long, fontSizeSp: Int, supplier: Boolean = false) {
    val owed = if (supplier) balancePaise else -balancePaise
    val (text, color) = when {
        owed > 0 -> "₹" + formatRupees(owed) to DueRed
        owed < 0 -> "₹" + formatRupees(owed) + " " + stringResource(R.string.advance) to GotGreen
        else -> stringResource(R.string.settled) to GotGreen
    }
    Text(text, color = color, fontSize = fontSizeSp.sp, fontWeight = if (owed != 0L) FontWeight.Medium else FontWeight.Normal)
}
