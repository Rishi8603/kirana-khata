package com.rishi.kiranaledger

import android.content.Context
import com.rishi.kiranaledger.data.GstMode

/** Shop name, address and phone printed at the top of every bill. Stored on the phone only. */
class ShopSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shop", Context.MODE_PRIVATE)

    var name: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) = prefs.edit().putString("name", value.trim()).apply()

    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(value) = prefs.edit().putString("address", value.trim()).apply()

    var phone: String
        get() = prefs.getString("phone", "").orEmpty()
        set(value) = prefs.edit().putString("phone", value.trim()).apply()

    private fun text(key: String, default: String = "") = prefs.getString(key, default) ?: default
    private fun put(key: String, value: String) = prefs.edit().putString(key, value.trim()).apply()

    // Printed on the A4 invoice.
    var email: String
        get() = text("email")
        set(value) = put("email", value)

    /** e.g. "27-Maharashtra" (GST state code + name). */
    var state: String
        get() = text("state")
        set(value) = put("state", value)

    /** FSSAI food licence number (14 digits); printed on every bill when set. */
    var fssai: String
        get() = text("fssai")
        set(value) = put("fssai", value)

    var upiId: String
        get() = text("upi_id")
        set(value) = put("upi_id", value)

    var bankName: String
        get() = text("bank_name")
        set(value) = put("bank_name", value)

    var accountName: String
        get() = text("account_name")
        set(value) = put("account_name", value)

    var accountNo: String
        get() = text("account_no")
        set(value) = put("account_no", value)

    var ifsc: String
        get() = text("ifsc")
        set(value) = put("ifsc", value.uppercase())

    /** One term per line. Empty = the default terms. */
    var terms: String
        get() = text("terms")
        set(value) = put("terms", value)

    /** A4 invoice (default) or the small receipt. */
    var a4Invoice: Boolean
        get() = prefs.getBoolean("a4_invoice", true)
        set(value) = prefs.edit().putBoolean("a4_invoice", value).apply()

    var gstin: String
        get() = prefs.getString("gstin", "").orEmpty()
        set(value) = prefs.edit().putString("gstin", value.trim().uppercase()).apply()

    /** Decides what bills show. Regular GST unless the shopkeeper picks otherwise. */
    var gstMode: GstMode
        get() = prefs.getString("gst_mode", null)?.let { runCatching { GstMode.valueOf(it) }.getOrNull() } ?: GstMode.REGULAR
        set(value) = prefs.edit().putString("gst_mode", value.name).apply()
}
