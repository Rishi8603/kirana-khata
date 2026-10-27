package com.rishi.kiranaledger.bill

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.ShopSettings
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillItem
import com.rishi.kiranaledger.data.BillStatus
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.GstSplit
import com.rishi.kiranaledger.data.gstPercentLabel
import com.rishi.kiranaledger.formatQuantity
import com.rishi.kiranaledger.formatRupees
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws a receipt-style PDF for one bill, and shares or downloads it. Everything happens on the phone. */
object BillPdf {

    private const val PAGE_WIDTH = 300 // points (about 105 mm, a small receipt)
    private const val MARGIN = 18f
    private const val CONTENT = PAGE_WIDTH - 2 * MARGIN

    // Column right edges for qty, rate and amount; the item name gets the rest.
    private const val NAME_WIDTH = 112f
    private const val QTY_RIGHT = MARGIN + 160f
    private const val RATE_RIGHT = MARGIN + 210f
    private const val AMOUNT_RIGHT = PAGE_WIDTH - MARGIN

    /** Builds the PDF in the app's cache folder and returns the file. [context] must be the Activity, for the app language. */
    fun create(context: Context, bill: Bill, items: List<BillItem>): File {
        val shop = ShopSettings(context)
        // The A4 GST invoice is the default; the small receipt stays available in Shop details.
        if (shop.a4Invoice) return InvoicePdf.create(context, bill, items)
        // First pass measures the height, second pass draws on a page of exactly that height.
        val height = draw(null, context, shop, bill, items).toInt() + 10
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, height, 1).create())
        page.canvas.drawColor(Color.WHITE)
        draw(page.canvas, context, shop, bill, items)
        document.finishPage(page)

        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "Bill-%04d.pdf".format(bill.billNo))
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    /** Opens Android's share sheet (WhatsApp, etc.) with the PDF attached. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Copies the PDF into Downloads/Kirana Khata. Returns false on Android 9 and older (use Share there). */
    fun download(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Kirana Khata")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }

    /** Draws the whole receipt (or only measures it when [canvas] is null). Returns the final y position. */
    private fun draw(canvas: Canvas?, context: Context, shop: ShopSettings, bill: Bill, items: List<BillItem>): Float {
        val pen = Pen(canvas)
        val s = { id: Int -> context.getString(id) }

        // Shop header
        pen.center(shop.name.ifBlank { s(R.string.app_name) }, 17f, bold = true)
        if (shop.address.isNotBlank()) pen.center(shop.address, 10f)
        if (shop.phone.isNotBlank()) pen.center("Ph: " + shop.phone, 10f)
        if (shop.fssai.isNotBlank()) pen.center(context.getString(R.string.inv_fssai) + ": " + shop.fssai, 9f)
        // GST heading per the shop's registration when the bill was made; old bills stay plain receipts.
        val regular = bill.gstMode == GstMode.REGULAR
        if (bill.gstMode != GstMode.UNREGISTERED) {
            if (shop.gstin.isNotBlank()) pen.center(s(R.string.gstin) + ": " + shop.gstin, 10f, bold = true)
            pen.gap(2f)
            pen.center(s(if (regular) R.string.tax_invoice else R.string.bill_of_supply), 12f, bold = true)
        }
        pen.gap(6f)
        pen.rule()

        // Bill number, date, customer
        pen.leftRight("Bill ${bill.numberLabel}", formatDate(context, bill.billDate), 11f, bold = true)
        val party = when {
            bill.isWalkIn -> s(R.string.walk_in)
            bill.partyPhone != null -> "${bill.partyName} (${bill.partyPhone})"
            else -> bill.partyName.orEmpty()
        }
        pen.left(s(R.string.customer_label) + ": " + party, 11f)
        if (bill.shipsElsewhere) {
            pen.left(s(R.string.ship_to) + ": " + listOfNotNull(bill.shipName, bill.shipAddress, bill.shipPhone).joinToString(", "), 10f)
        }
        pen.gap(4f)
        pen.rule()

        // Item table
        pen.row(s(R.string.item_name), s(R.string.quantity), s(R.string.rate).removeSuffix(" ₹"), s(R.string.amount), 9.5f, bold = true)
        pen.rule(light = true)
        for (item in items) {
            pen.row(
                item.name,
                "${formatQuantity(item.quantity)} ${item.unit}",
                formatRupees(item.pricePaise),
                formatRupees(item.gst.taxablePaise), // before GST, so lines add up to the taxable total
                10.5f,
            )
            // Tax invoice: HSN and rate under each item (composition / unregistered bills don't show them).
            if (regular && item.hsnCode != null) {
                pen.left("   ${s(R.string.hsn)} ${item.hsnCode} · GST ${gstPercentLabel(2L * item.cgstMilli)}%", 8.5f)
            }
        }
        pen.rule()

        // Totals: a tax invoice breaks GST down by rate; other bills show the plain subtotal.
        val taxed = items.filter { it.cgstMilli > 0 }
        if (regular && taxed.isNotEmpty()) {
            val all = items.fold(GstSplit.ZERO) { a, i -> a + i.gst }
            pen.leftRight(s(R.string.taxable_value), "₹" + formatRupees(all.taxablePaise), 10.5f)
            taxed.groupBy { it.cgstMilli }.toSortedMap().forEach { (cgstMilli, group) ->
                val split = group.fold(GstSplit.ZERO) { a, i -> a + i.gst }
                val rate = gstPercentLabel(cgstMilli.toLong())
                pen.leftRight("${s(R.string.cgst)} @$rate%", "₹" + formatRupees(split.cgstPaise), 10f)
                pen.leftRight("${s(R.string.sgst)} @$rate%", "₹" + formatRupees(split.sgstPaise), 10f)
            }
        } else {
            pen.leftRight(s(R.string.subtotal), "₹" + formatRupees(bill.subtotalPaise), 10.5f)
        }
        if (bill.roundOffPaise != 0L) {
            val sign = if (bill.roundOffPaise > 0) "+" else "−"
            pen.leftRight(s(R.string.round_off), sign + "₹" + formatRupees(bill.roundOffPaise), 10.5f)
        }
        pen.leftRight(s(R.string.total), "₹" + formatRupees(bill.totalPaise), 14f, bold = true)
        if (!bill.isWalkIn) {
            pen.leftRight(s(R.string.paid), "₹" + formatRupees(bill.paidPaise), 10.5f)
            if (bill.duePaise > 0) pen.leftRight(s(R.string.balance_due), "₹" + formatRupees(bill.duePaise), 11f, bold = true)
        }

        // Status stamp
        pen.gap(10f)
        val (label, color) = when (bill.status) {
            BillStatus.PAID -> s(R.string.status_paid) to Color.rgb(27, 110, 58)
            BillStatus.CASH_SALE -> s(R.string.status_cash_sale) to Color.rgb(27, 110, 58)
            BillStatus.PARTLY_PAID -> s(R.string.status_partly_paid) to Color.rgb(176, 110, 0)
            BillStatus.UNPAID -> s(R.string.status_unpaid) to Color.rgb(179, 38, 30)
        }
        pen.stamp(label, color)

        pen.gap(10f)
        if (bill.gstMode == GstMode.COMPOSITION) pen.center(s(R.string.composition_note), 8.5f)
        pen.center(s(R.string.thank_you), 10f)
        return pen.y + MARGIN
    }

    private fun formatDate(context: Context, epochDay: Long): String {
        val appLocale = context.resources.configuration.locales[0]
        val locale = if (appLocale.language == "hi" && appLocale.script != "Latn") appLocale else Locale.ENGLISH
        return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }

    /** Tiny layout helper: keeps the current y and draws only when a canvas is given. */
    private class Pen(private val canvas: Canvas?) {
        var y = MARGIN

        private fun paint(size: Float, bold: Boolean, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun layout(text: String, p: TextPaint, width: Float, align: Layout.Alignment) =
            StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt()).setAlignment(align).build()

        private fun place(l: StaticLayout, x: Float) {
            canvas?.let { c ->
                c.save()
                c.translate(x, y)
                l.draw(c)
                c.restore()
            }
        }

        fun center(text: String, size: Float, bold: Boolean = false) {
            val l = layout(text, paint(size, bold), CONTENT, Layout.Alignment.ALIGN_CENTER)
            place(l, MARGIN)
            y += l.height + 2
        }

        fun left(text: String, size: Float, bold: Boolean = false) {
            val l = layout(text, paint(size, bold), CONTENT, Layout.Alignment.ALIGN_NORMAL)
            place(l, MARGIN)
            y += l.height + 2
        }

        fun leftRight(left: String, right: String, size: Float, bold: Boolean = false) {
            val p = paint(size, bold)
            val r = layout(right, p, CONTENT / 2, Layout.Alignment.ALIGN_OPPOSITE)
            val l = layout(left, p, CONTENT / 2, Layout.Alignment.ALIGN_NORMAL)
            place(l, MARGIN)
            place(r, MARGIN + CONTENT / 2)
            y += maxOf(l.height, r.height) + 3
        }

        /** One table row; the name wraps onto more lines if it is long. */
        fun row(name: String, qty: String, rate: String, amount: String, size: Float, bold: Boolean = false) {
            val p = paint(size, bold)
            val n = layout(name, p, NAME_WIDTH, Layout.Alignment.ALIGN_NORMAL)
            place(n, MARGIN)
            rightText(qty, QTY_RIGHT, p)
            rightText(rate, RATE_RIGHT, p)
            rightText(amount, AMOUNT_RIGHT, p)
            y += n.height + 4
        }

        private fun rightText(text: String, right: Float, p: TextPaint) {
            canvas?.let { c ->
                p.textAlign = Paint.Align.RIGHT
                c.drawText(text, right, y - p.ascent(), p)
                p.textAlign = Paint.Align.LEFT
            }
        }

        fun rule(light: Boolean = false) {
            y += 3
            canvas?.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply {
                color = if (light) Color.LTGRAY else Color.DKGRAY
                strokeWidth = if (light) 0.5f else 0.8f
            })
            y += 5
        }

        fun gap(space: Float) {
            y += space
        }

        /** A tilted, outlined stamp like a rubber stamp: PAID / PARTLY PAID / UNPAID / CASH SALE. */
        fun stamp(text: String, color: Int) {
            val p = paint(14f, bold = true, color = color).apply { textAlign = Paint.Align.CENTER }
            val width = p.measureText(text) + 24
            val height = 30f
            val cx = PAGE_WIDTH / 2f
            val box = RectF(cx - width / 2, y, cx + width / 2, y + height)
            canvas?.let { c ->
                c.save()
                c.rotate(-8f, cx, y + height / 2)
                c.drawRoundRect(box, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    this.color = color
                })
                c.drawText(text, cx, y + height / 2 - (p.ascent() + p.descent()) / 2, p)
                c.restore()
            }
            y += height + 6
        }
    }
}
