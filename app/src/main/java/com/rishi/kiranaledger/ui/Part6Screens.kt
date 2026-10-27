package com.rishi.kiranaledger.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.backup.BackupFile
import kotlin.system.exitProcess

/** Covers the whole app until the shopkeeper unlocks with fingerprint or the phone's PIN. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() } // ask straight away
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {}, // swallow taps so nothing underneath is touchable
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Brand, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.locked), fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp))
        BigButton(stringResource(R.string.unlock), container = BrandSoft, content = Brand, onClick = onUnlock)
    }
}

/** Backup and restore: Android's automatic Drive backup, plus a backup file to share and restore. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val restoredText = stringResource(R.string.restore_done)
    val failedText = stringResource(R.string.restore_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title = stringResource(R.string.backup), onBack = onBack)
            InfoCard(Icons.Filled.CloudDone, stringResource(R.string.auto_backup_title), stringResource(R.string.auto_backup_body))
            InfoCard(Icons.Filled.Share, stringResource(R.string.backup_file_title), stringResource(R.string.backup_file_body))
            BigButton(
                stringResource(R.string.make_backup_file),
                icon = Icons.Filled.Share,
                container = GotGreenBg,
                content = GotGreen,
                modifier = Modifier.fillMaxWidth(),
            ) { BackupFile.share(context, BackupFile.create(context)) }
            OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text("  " + stringResource(R.string.restore_from_file), fontSize = 16.sp)
            }
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            text = { Text(stringResource(R.string.restore_warning), fontSize = 16.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    if (BackupFile.restore(context, uri)) {
                        Toast.makeText(context, restoredText, Toast.LENGTH_LONG).show()
                        // Restart so every screen reads the restored data.
                        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        }
                        exitProcess(0)
                    } else {
                        Toast.makeText(context, failedText, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.restore_from_file), color = DueRed) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Brand)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full-screen view of a bahi page photo. */
@Composable
fun PhotoScreen(path: String, onBack: () -> Unit) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(title = stringResource(R.string.bahi_photo), onBack = onBack)
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            } else {
                Text("—", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
