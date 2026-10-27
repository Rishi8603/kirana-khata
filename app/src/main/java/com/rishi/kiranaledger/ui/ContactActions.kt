package com.rishi.kiranaledger.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a WhatsApp chat with the message typed in; the shopkeeper still presses send. Returns false if it can't. */
fun openWhatsApp(context: Context, phone: String, message: String): Boolean {
    val digits = phone.filter(Char::isDigit).let { if (it.length == 10) "91$it" else it }
    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=" + Uri.encode(message))
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
        true
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri)) // WhatsApp Business or browser
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

/** Opens the phone dialer with the number filled in (no call permission needed). */
fun dial(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
}
