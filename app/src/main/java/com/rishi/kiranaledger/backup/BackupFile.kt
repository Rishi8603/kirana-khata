package com.rishi.kiranaledger.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.rishi.kiranaledger.data.LedgerDatabase
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manual backup: one .zip with the database, shop details and bahi photos.
 * The shopkeeper can send it to himself on WhatsApp or save it to Drive, and restore it on any phone.
 */
object BackupFile {

    private const val DB_NAME = "ledger.db"
    private const val PREFS = "shared_prefs/shop.xml"

    fun create(context: Context): File {
        LedgerDatabase.checkpoint(context)
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val zip = File(dir, "KiranaKhata-backup-${LocalDate.now()}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            fun add(file: File, name: String) {
                if (!file.exists()) return
                out.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
            add(context.getDatabasePath(DB_NAME), DB_NAME)
            add(File(context.dataDir, PREFS), PREFS)
            File(context.filesDir, "photos").listFiles()?.forEach { add(it, "photos/${it.name}") }
        }
        return zip
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    }

    /**
     * Replaces all data with the backup at [uri]. Returns false (and changes nothing) if the file
     * isn't a Kirana Khata backup. The app must restart afterwards.
     */
    fun restore(context: Context, uri: Uri): Boolean {
        // Unpack into a temporary folder first, so a bad file can't damage the current data.
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val target = File(staging, entry.name)
                    // Refuse paths that try to escape the folder ("../").
                    if (!target.canonicalPath.startsWith(staging.canonicalPath)) return false
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        } ?: return false
        val db = File(staging, DB_NAME)
        if (!db.exists()) return false

        LedgerDatabase.close()
        val dbFile = context.getDatabasePath(DB_NAME)
        listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        db.copyTo(dbFile, overwrite = true)
        File(staging, PREFS).takeIf { it.exists() }?.copyTo(File(context.dataDir, PREFS), overwrite = true)
        File(staging, "photos").listFiles()?.forEach { it.copyTo(File(context.filesDir, "photos/${it.name}"), overwrite = true) }
        staging.deleteRecursively()
        return true
    }
}
