package com.rishi.kiranaledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.ShopSettings
import com.rishi.kiranaledger.data.GSTIN_PATTERN
import com.rishi.kiranaledger.data.GstMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Alignment

/** Shop details for the bill header, and the language choice. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val shop = ShopSettings(LocalContext.current)
    var name by rememberSaveable { mutableStateOf(shop.name) }
    var address by rememberSaveable { mutableStateOf(shop.address) }
    var phone by rememberSaveable { mutableStateOf(shop.phone) }
    var gstin by rememberSaveable { mutableStateOf(shop.gstin) }
    var gstMode by rememberSaveable { mutableStateOf(shop.gstMode) }
    var gstinError by remember { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf(shop.email) }
    var state by rememberSaveable { mutableStateOf(shop.state) }
    var upiId by rememberSaveable { mutableStateOf(shop.upiId) }
    var bankName by rememberSaveable { mutableStateOf(shop.bankName) }
    var accountName by rememberSaveable { mutableStateOf(shop.accountName) }
    var accountNo by rememberSaveable { mutableStateOf(shop.accountNo) }
    var ifsc by rememberSaveable { mutableStateOf(shop.ifsc) }
    var terms by rememberSaveable { mutableStateOf(shop.terms) }
    var a4 by rememberSaveable { mutableStateOf(shop.a4Invoice) }
    var fssai by rememberSaveable { mutableStateOf(shop.fssai) }
    var fssaiError by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            BigButton(
                text = stringResource(R.string.save),
                icon = Icons.Filled.Check,
                container = GotGreenBg,
                content = GotGreen,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                onClick = {
                    val cleanGstin = gstin.trim().uppercase()
                    if (cleanGstin.isNotEmpty() && !GSTIN_PATTERN.matches(cleanGstin)) {
                        gstinError = true
                    } else if (fssai.isNotEmpty() && fssai.length != 14) {
                        fssaiError = true
                    } else {
                        shop.fssai = fssai
                        shop.name = name
                        shop.address = address
                        shop.phone = phone
                        shop.gstin = cleanGstin
                        shop.gstMode = gstMode
                        shop.email = email
                        shop.state = state
                        shop.upiId = upiId
                        shop.bankName = bankName
                        shop.accountName = accountName
                        shop.accountNo = accountNo
                        shop.ifsc = ifsc
                        shop.terms = terms
                        shop.a4Invoice = a4
                        onBack()
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = stringResource(R.string.settings), onBack = onBack)
            Text(stringResource(R.string.shop_details), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.shop_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it.take(80) }, label = { Text(stringResource(R.string.shop_address)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone,
                { phone = it.take(15) },
                label = { Text(stringResource(R.string.shop_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SimpleField(email, { email = it.take(60) }, R.string.shop_email, KeyboardType.Email)
            OutlinedTextField(
                fssai,
                {
                    fssai = it.filter(Char::isDigit).take(14)
                    fssaiError = false
                },
                label = { Text(stringResource(R.string.fssai)) },
                isError = fssaiError,
                supportingText = if (fssaiError) ({ Text(stringResource(R.string.fssai_invalid)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SimpleField(state, { state = it.take(30) }, R.string.shop_state)

            Text(stringResource(R.string.bill_format), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(a4, { a4 = true }, label = { Text(stringResource(R.string.format_a4)) })
                FilterChip(!a4, { a4 = false }, label = { Text(stringResource(R.string.format_receipt)) })
            }

            Text(stringResource(R.string.gst_registration), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            listOf(
                GstMode.REGULAR to R.string.gst_regular,
                GstMode.COMPOSITION to R.string.gst_composition,
                GstMode.UNREGISTERED to R.string.gst_unregistered,
            ).forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { gstMode = mode }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = gstMode == mode, onClick = { gstMode = mode })
                    Text(stringResource(label), fontSize = 16.sp)
                }
            }
            if (gstMode != GstMode.UNREGISTERED) {
                OutlinedTextField(
                    gstin,
                    {
                        gstin = it.filter(Char::isLetterOrDigit).uppercase().take(15)
                        gstinError = false
                    },
                    label = { Text(stringResource(R.string.gstin)) },
                    isError = gstinError,
                    supportingText = if (gstinError) ({ Text(stringResource(R.string.gstin_invalid)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(R.string.bank_details), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            SimpleField(upiId, { upiId = it.take(50) }, R.string.upi_id)
            SimpleField(accountName, { accountName = it.take(50) }, R.string.account_name)
            SimpleField(bankName, { bankName = it.take(50) }, R.string.bank_name)
            SimpleField(accountNo, { accountNo = it.filter(Char::isLetterOrDigit).take(20) }, R.string.account_no, KeyboardType.Number)
            SimpleField(ifsc, { ifsc = it.filter(Char::isLetterOrDigit).uppercase().take(11) }, R.string.ifsc)
            OutlinedTextField(
                terms,
                { terms = it.take(400) },
                label = { Text(stringResource(R.string.terms_label)) },
                placeholder = { Text(stringResource(R.string.inv_default_terms)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { showLanguage = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Translate, contentDescription = null)
                Text("  " + stringResource(R.string.language), fontSize = 17.sp)
            }
        }
    }
    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
}

@Composable
private fun SimpleField(value: String, onChange: (String) -> Unit, label: Int, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
