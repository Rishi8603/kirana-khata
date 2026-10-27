package com.rishi.kiranaledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import com.rishi.kiranaledger.data.Customer
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.LedgerDao

/**
 * Pick the bill's customer, or "walk-in" (null). A name that isn't in the khata yet can be added
 * right here, so the shopkeeper never has to leave a half-made bill to create the customer first.
 */
@Composable
fun CustomerPickerDialog(dao: LedgerDao, onDismiss: () -> Unit, onPicked: (customerId: String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val customers by remember(query) {
        if (query.isBlank()) dao.customers() else dao.searchCustomers(query.trim())
    }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val typed = query.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_customer)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_customers)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (typed.isNotEmpty() && customers.none { it.name.equals(typed, ignoreCase = true) }) {
                        item {
                            Text(
                                stringResource(R.string.quick_add_customer, typed),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Brand,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val customer = Customer(name = typed.take(40))
                                    scope.launch {
                                        dao.insertCustomer(customer)
                                        onPicked(customer.id)
                                    }
                                }.padding(vertical = 14.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.walk_in),
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(null) }.padding(vertical = 14.dp),
                        )
                        HorizontalDivider()
                    }
                    items(customers, key = { it.id }) { customer ->
                        Text(
                            customer.name,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(customer.id) }.padding(vertical = 14.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
