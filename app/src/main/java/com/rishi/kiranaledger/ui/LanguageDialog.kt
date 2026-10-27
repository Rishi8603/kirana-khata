package com.rishi.kiranaledger.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.AppLanguage
import com.rishi.kiranaledger.R

/** Hinglish / English / हिंदी picker. Choosing one restarts the screen in that language. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.choose_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                language.apply()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current.equals(language.tag, ignoreCase = true), onClick = null)
                        Text(language.label, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
    )
}
