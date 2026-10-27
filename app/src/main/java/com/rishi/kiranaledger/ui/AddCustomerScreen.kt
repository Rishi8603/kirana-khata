package com.rishi.kiranaledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.PhotoStore
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.LedgerDao
import com.rishi.kiranaledger.data.PartyType
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Add a customer, optionally with the balance they already owe in the paper bahi.
 * With [editCustomerId] it edits that customer's name and phone instead.
 */
@Composable
fun AddCustomerScreen(
    dao: LedgerDao,
    editCustomerId: String?,
    partyType: PartyType,
    onBack: () -> Unit,
    onSaved: (customerId: String) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(partyType) }
    val supplier = type == PartyType.SUPPLIER
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var phoneError by remember { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var gstin by rememberSaveable { mutableStateOf("") }
    var shipAddress by rememberSaveable { mutableStateOf("") }
    var openingRupees by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val editing = editCustomerId != null
    // The id is chosen up front so the bahi photo can be saved under it before the customer exists.
    val newId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var hasPhoto by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            PhotoStore.compress(PhotoStore.fileFor(context, newId))
            hasPhoto = true
        }
    }

    LaunchedEffect(editCustomerId) {
        if (editCustomerId != null && !prefilled) {
            dao.customerOnce(editCustomerId)?.let {
                name = it.name
                phone = it.phone.orEmpty()
                address = it.address.orEmpty()
                gstin = it.gstin.orEmpty()
                shipAddress = it.shipAddress.orEmpty()
                type = it.partyType
            }
            prefilled = true
        }
    }

    Scaffold(
        bottomBar = {
            BigButton(
                text = stringResource(if (editing || supplier) R.string.save else R.string.save_customer),
                icon = Icons.Filled.Check,
                container = GotGreenBg,
                content = GotGreen,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else if (!phoneOk(phone)) {
                        phoneError = true
                    } else if (!saving) {
                        saving = true
                        val cleanPhone = phone.filter { it.isDigit() || it == '+' }.ifBlank { null }
                        scope.launch {
                            val cleanAddress = address.trim().ifBlank { null }
                            val cleanGstin = gstin.trim().uppercase().ifBlank { null }
                            val cleanShip = shipAddress.trim().ifBlank { null }
                            if (editCustomerId != null) {
                                dao.updateCustomer(editCustomerId, name.trim(), cleanPhone, cleanAddress, cleanGstin, cleanShip)
                                onSaved(editCustomerId)
                            } else {
                                val openingPaise = (openingRupees.toLongOrNull() ?: 0L) * 100
                                val customer = Customer(
                                    id = newId,
                                    name = name.trim(),
                                    phone = cleanPhone,
                                    address = cleanAddress,
                                    gstin = cleanGstin,
                                    shipAddress = cleanShip,
                                    fromBahi = openingPaise > 0,
                                    partyType = type,
                                    bahiPhotoPath = if (hasPhoto) PhotoStore.fileFor(context, newId).path else null,
                                )
                                dao.insertCustomerWithOpeningBalance(customer, openingPaise)
                                onSaved(customer.id)
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val title = when {
                editing && supplier -> R.string.edit_supplier
                editing -> R.string.edit_customer
                supplier -> R.string.new_supplier
                else -> R.string.new_customer
            }
            ScreenHeader(title = stringResource(title), onBack = onBack)

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(40)
                    nameError = false
                },
                label = { Text(stringResource(R.string.name)) },
                isError = nameError,
                supportingText = if (nameError) ({ Text(stringResource(R.string.name_required)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it.take(15)
                    phoneError = false
                },
                label = { Text(stringResource(R.string.phone_optional)) },
                isError = phoneError,
                supportingText = { Text(stringResource(if (phoneError) R.string.phone_invalid else R.string.phone_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it.take(120) },
                label = { Text(stringResource(R.string.customer_address)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Suppliers don't get bills, so only customers have a shipping address.
            if (!supplier) {
                OutlinedTextField(
                    value = shipAddress,
                    onValueChange = { shipAddress = it.take(150) },
                    label = { Text(stringResource(R.string.customer_ship_address)) },
                    supportingText = { Text(stringResource(R.string.customer_ship_address_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = gstin,
                onValueChange = { gstin = it.filter(Char::isLetterOrDigit).uppercase().take(15) },
                label = { Text(stringResource(R.string.customer_gstin)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // The opening balance only makes sense once, when the customer comes over from the bahi.
            if (!editing) {
                OutlinedTextField(
                    value = openingRupees,
                    onValueChange = { openingRupees = it.filter(Char::isDigit).trimStart('0').take(7) },
                    label = { Text(stringResource(R.string.old_bahi_balance)) },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Opens the camera; the photo is saved privately under this customer's id.
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (hasPhoto) GotGreen else Color.Gray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { camera.launch(PhotoStore.cameraUri(context, newId)) },
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (hasPhoto) Icons.Filled.CheckCircle else Icons.Filled.CameraAlt, contentDescription = null, tint = if (hasPhoto) GotGreen else Color.Unspecified)
                        Text(stringResource(if (hasPhoto) R.string.photo_saved else R.string.take_bahi_photo), fontSize = 16.sp)
                        Text(stringResource(R.string.photo_for_proof), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Empty is fine (phone is optional); otherwise a 10-digit Indian mobile, with or without +91 / 0 in front. */
fun phoneOk(phone: String): Boolean {
    val digits = phone.filter(Char::isDigit)
    if (digits.isEmpty()) return true
    val local = when {
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }
    return local.length == 10
}
