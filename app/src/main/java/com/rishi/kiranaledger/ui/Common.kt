package com.rishi.kiranaledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.rishi.kiranaledger.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Two colours carry meaning everywhere: red = Diya / baaki, green = Liya / settled.
val DueRed = Color(0xFFB3261E)
val DueRedBg = Color(0xFFFCEBEB)
val GotGreen = Color(0xFF1B6E3A)
val GotGreenBg = Color(0xFFE3F2E6)
val PartlyAmber = Color(0xFFB06E00)
private val BahiTagBg = Color(0xFFFAEEDA)
private val BahiTagText = Color(0xFF854F0B)

/** The big, thumb-sized buttons used for every main action. */
@Composable
fun BigButton(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = contentPadding,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                }
                // One line only, so a long label never breaks the button in half.
                Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (sub != null) Text(sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Delete X?" with Cancel / Delete — every delete in the app asks first. */
@Composable
fun ConfirmDeleteDialog(title: String, text: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it, fontSize = 16.sp) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = DueRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Title row with an optional back arrow and optional icons on the right. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    titleColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = titleColor, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Round avatar with the first letter of the name, like contact lists. */
@Composable
fun Avatar(name: String, size: Int = 44) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(BrandSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase(), color = Brand, fontSize = (size / 2.4).sp, fontWeight = FontWeight.Medium)
    }
}

/** Icon in a soft circle with a label underneath — the customer page's quick actions. */
@Composable
fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(BrandSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Brand) }
        // Up to two lines, so a long label ("Yaad dilayein") wraps instead of being cut off.
        Text(label, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Small "Bahi se" label for customers and entries copied from the paper bahi. */
@Composable
fun BahiTag() {
    Text(
        stringResource(R.string.from_bahi),
        fontSize = 11.sp,
        color = BahiTagText,
        modifier = Modifier
            .background(BahiTagBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Large number pad, so the shopkeeper never needs the phone keyboard for amounts. */
@Composable
fun NumberPad(onKey: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", BACKSPACE))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { if (key == BACKSPACE) onBackspace() else onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == BACKSPACE) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                        } else {
                            Text(key, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

private const val BACKSPACE = "<"

/** "21 Oct" style date. Devanagari month names only when the app is in Hindi. */
@Composable
fun formatEntryDate(epochDay: Long): String {
    val appLocale = LocalConfiguration.current.locales[0]
    val locale = if (appLocale.language == "hi" && appLocale.script != "Latn") appLocale else Locale.ENGLISH
    return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM", locale))
}
