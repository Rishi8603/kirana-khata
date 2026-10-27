package com.rishi.kiranaledger.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.formatRupees

/** Vasooli: everyone who owes money, grouped by due date, with a one-tap WhatsApp reminder. */
@Composable
fun CollectionsScreen(dao: LedgerDao, onBack: () -> Unit, onOpenCustomer: (String) -> Unit) {
    val customers by dao.customersToCollect().collectAsState(initial = emptyList())
    val day = today()
    val groups = listOf(
        Triple(R.string.overdue, DueRed, customers.filter { it.dueDate != null && it.dueDate < day }),
        Triple(R.string.due_today, PartlyAmber, customers.filter { it.dueDate == day }),
        Triple(R.string.upcoming, Brand, customers.filter { it.dueDate != null && it.dueDate > day }),
        Triple(R.string.no_due_date, Color.Gray, customers.filter { it.dueDate == null }),
    ).filter { it.third.isNotEmpty() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(title = stringResource(R.string.collections), onBack = onBack) }
            if (groups.isEmpty()) {
                item { Text(stringResource(R.string.settled), fontSize = 17.sp, color = GotGreen) }
            }
            groups.forEach { (title, color, list) ->
                item {
                    Text(
                        stringResource(title) + "  ·  ₹" + formatRupees(list.sumOf { it.balancePaise }),
                        color = color,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                        list.forEachIndexed { i, customer ->
                            CollectRow(customer, onClick = { onOpenCustomer(customer.id) })
                            if (i < list.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectRow(customer: Customer, onClick: () -> Unit) {
    val context = LocalContext.current
    val reminder = stringResource(R.string.reminder_message, customer.name, formatRupees(customer.balancePaise))
    val whatsappMissing = stringResource(R.string.whatsapp_missing)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(customer.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(customer.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            customer.dueDate?.let {
                Text(stringResource(R.string.due_on, formatEntryDate(it)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("₹" + formatRupees(customer.balancePaise), color = DueRed, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (customer.phone != null) {
            IconButton(onClick = {
                if (!openWhatsApp(context, customer.phone, reminder)) Toast.makeText(context, whatsappMissing, Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.remind), tint = GotGreen)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
