package com.rishi.kiranaledger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Bahi page photos, kept in the app's private storage (and included in backups). */
object PhotoStore {

    fun fileFor(context: Context, customerId: String): File =
        File(context.filesDir, "photos").apply { mkdirs() }.let { File(it, "bahi-$customerId.jpg") }

    /** Where the camera app should save the photo. */
    fun cameraUri(context: Context, customerId: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", fileFor(context, customerId))

    /** Shrinks a fresh camera photo (often 4–8 MB) to about 1600 px, so backups stay small. */
    fun compress(file: File) {
        if (!file.exists()) return
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
    }
}
