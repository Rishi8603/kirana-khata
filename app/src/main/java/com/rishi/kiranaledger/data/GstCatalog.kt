package com.rishi.kiranaledger.data

import android.content.Context
import org.json.JSONArray

/** One row of the CBIC rate notification: a description of goods with its HSN codes and CGST rate. */
data class GstEntry(
    val id: String, // e.g. "I-10" = Schedule I, S. No. 10
    val schedule: String, // I..VII or EXEMPT
    val hsnCodes: List<String>,
    val nature: String,
    val cgstMilli: Int,
) {
    val totalMilli: Long get() = 2L * cgstMilli
}

/** A product's chosen GST row and the specific HSN code from it. */
data class GstChoice(val entryId: String, val schedule: String, val hsnCode: String, val nature: String, val cgstMilli: Int)

/** GST settings for one product: its HSN row, the rate the shopkeeper uses (normally the row's rate), and whether prices include GST. */
data class GstLink(val choice: GstChoice, val cgstMilli: Int, val priceIncludesTax: Boolean)

/** The GST link saved on an item, if it has one. */
fun Item.gstLink(): GstLink? =
    if (hsnCode == null) null else GstLink(
        GstChoice(gstEntryId.orEmpty(), gstSchedule.orEmpty(), hsnCode, productNature.orEmpty(), cgstMilli),
        cgstMilli,
        priceIncludesTax,
    )

/**
 * The bundled GST rate list (CBIC, rates as on 22.09.2025), parsed from the official PDF by tools/parse_gst.py.
 * Reference data only: it lives in assets, never in the shop's database, so rate updates ship with app updates.
 */
object GstCatalog {
    const val ASSET = "gst_rates_2025-09-22.json"

    /** Schedules in the order shown to the shopkeeper, with their CGST rate. */
    val SCHEDULES = listOf("EXEMPT" to 0, "I" to 2500, "II" to 9000, "VII" to 14000, "III" to 20000, "IV" to 1500, "VI" to 750, "V" to 125)

    @Volatile private var cache: List<GstEntry>? = null

    fun entries(context: Context): List<GstEntry> = cache ?: synchronized(this) {
        cache ?: parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }).also { cache = it }
    }

    fun parse(json: String): List<GstEntry> {
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val codes = o.getJSONArray("h")
            GstEntry(o.getString("id"), o.getString("s"), List(codes.length()) { codes.getString(it) }, o.getString("d"), o.getInt("r"))
        }
    }

    /**
     * Rows matching [query] within [schedule] (null = all). Digits search HSN codes in both directions:
     * "1905" finds 1905 and 19059010; "19053100" finds the row listed as 1905. Words search the description.
     */
    fun search(entries: List<GstEntry>, schedule: String?, query: String): List<GstEntry> {
        val q = query.trim()
        val inSchedule = if (schedule == null) entries else entries.filter { it.schedule == schedule }
        if (q.isEmpty()) return inSchedule
        val digits = q.filter { !it.isWhitespace() }
        return if (digits.all(Char::isDigit)) {
            inSchedule.filter { e -> e.hsnCodes.any { it.startsWith(digits) || digits.startsWith(it) } }
        } else {
            val words = q.lowercase().split(Regex("\\s+"))
            inSchedule.filter { e -> words.all { e.nature.lowercase().contains(it) } }
        }
    }
}
