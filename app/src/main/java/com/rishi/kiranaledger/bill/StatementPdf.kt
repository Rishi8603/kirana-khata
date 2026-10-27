package com.rishi.kiranaledger.bill

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.ShopSettings
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.Entry
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.formatRupees
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A customer's (or supplier's) full khata as an A4 PDF: every entry, oldest first,
 * with a running balance — what the shopkeeper shows when someone questions the amount.
 */
object StatementPdf {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 36f

    fun create(context: Context, customer: Customer, entriesNewestFirst: List<Entry>): File {
        val shop = ShopSettings(context)
        val supplier = customer.partyType == PartyType.SUPPLIER
        val entries = entriesNewestFirst.reversed()
        val s = { id: Int -> context.getString(id) }
        val appLocale = context.resources.configuration.locales[0]
        val dateLocale = if (appLocale.language == "hi" && appLocale.script != "Latn") appLocale else Locale.ENGLISH
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", dateLocale)

        fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                textAlign = align
            }
        val red = Color.rgb(179, 38, 30)
        val green = Color.rgb(27, 110, 58)
        // Columns: date | details | red column | green column | balance
        val cDate = M
        val cNote = M + 80
        val cRed = W - M - 200
        val cGreen = W - M - 110
        val cBal = W - M

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            val c = page!!.canvas
            y = M + 10
            c.drawText(shop.name.ifBlank { s(R.string.app_name) }, M, y, paint(18f, bold = true))
            if (shop.phone.isNotBlank()) c.drawText("Ph: " + shop.phone, W - M, y, paint(10f, align = Paint.Align.RIGHT))
            y += 22
            c.drawText(customer.name + (customer.phone?.let { "  ($it)" } ?: ""), M, y, paint(13f, bold = true))
            c.drawText(LocalDate.now().format(fmt), W - M, y, paint(10f, align = Paint.Align.RIGHT))
            y += 18
            val header = paint(9.5f, bold = true, color = Color.DKGRAY)
            c.drawText(s(R.string.col_date), cDate, y, header)
            c.drawText(s(R.string.col_details), cNote, y, header)
            c.drawText(s(if (supplier) R.string.purchase else R.string.gave), cRed, y, paint(9.5f, true, red, Paint.Align.RIGHT))
            c.drawText(s(if (supplier) R.string.pay_supplier else R.string.got), cGreen, y, paint(9.5f, true, green, Paint.Align.RIGHT))
            c.drawText(s(R.string.col_balance), cBal, y, paint(9.5f, true, Color.DKGRAY, Paint.Align.RIGHT))
            y += 6
            c.drawLine(M, y, W - M, y, Paint().apply { color = Color.GRAY; strokeWidth = 0.8f })
            y += 14
        }

        newPage()
        var running = 0L
        val body = paint(10f)
        for (e in entries) {
            if (y > H - M - 40) newPage()
            val c = page!!.canvas
            running += e.amountPaise
            val isRed = if (supplier) e.amountPaise > 0 else e.amountPaise < 0
            val note = when {
                e.fromBahi -> s(R.string.opening_balance)
                else -> e.note.orEmpty()
            }
            c.drawText(LocalDate.ofEpochDay(e.entryDate).format(fmt), cDate, y, body)
            c.drawText(note.take(38), cNote, y, body)
            val amount = "₹" + formatRupees(e.amountPaise)
            if (isRed) c.drawText(amount, cRed, y, paint(10f, color = red, align = Paint.Align.RIGHT))
            else c.drawText(amount, cGreen, y, paint(10f, color = green, align = Paint.Align.RIGHT))
            val owed = if (supplier) running else -running
            c.drawText("₹" + formatRupees(running), cBal, y, paint(10f, color = if (owed > 0) red else green, align = Paint.Align.RIGHT))
            y += 16
        }
        if (y > H - M - 40) newPage()
        val c = page!!.canvas
        c.drawLine(M, y - 6, W - M, y - 6, Paint().apply { color = Color.GRAY; strokeWidth = 0.8f })
        y += 10
        val owed = if (supplier) customer.balancePaise else -customer.balancePaise
        val label = when {
            owed > 0 -> s(if (supplier) R.string.to_pay else R.string.balance_due)
            owed < 0 -> s(R.string.advance)
            else -> s(R.string.settled)
        }
        c.drawText(label, M, y, paint(14f, bold = true))
        c.drawText("₹" + formatRupees(customer.balancePaise), W - M, y, paint(14f, true, if (owed > 0) red else green, Paint.Align.RIGHT))
        doc.finishPage(page)

        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "Statement-${customer.name.filter { it.isLetterOrDigit() }.ifEmpty { "khata" }}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
