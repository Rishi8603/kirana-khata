package com.rishi.kiranaledger.bill

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.rishi.kiranaledger.R
import com.rishi.kiranaledger.ShopSettings
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillItem
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.GstSplit
import com.rishi.kiranaledger.formatPlainRupees
import com.rishi.kiranaledger.rupeesInWords
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A4 GST invoice in the familiar kirana-software layout: shop header box, buyer + invoice details,
 * a gridded item table (HSN, qty, unit, rate, GST%, amount), GST class table, totals,
 * amount in words, terms, bank / UPI box and signatures. Long bills continue onto more pages.
 *
 * Regular GST shows "GST INVOICE" with HSN, GST% and the tax table; Composition shows "BILL OF SUPPLY"
 * without tax columns; an unregistered shop gets a plain "INVOICE".
 */
object InvoicePdf {
    private const val W = 595f
    private const val H = 842f
    private const val LEFT = 20f
    private const val RIGHT = W - 20f
    private const val ROW = 14f
    private const val TABLE_TOP = 300f
    private const val FOOTER_H = 268f // totals + words + terms/bank + declaration + signatures
    private val TABLE_BOTTOM = H - 20f - FOOTER_H

    private class Col(val title: Int, val left: Float, val right: Float, val alignRight: Boolean)

    fun create(context: Context, bill: Bill, items: List<BillItem>): File {
        val shop = ShopSettings(context)
        val regular = bill.gstMode == GstMode.REGULAR
        val s = { id: Int -> context.getString(id) }

        // Tax columns only on a regular GST invoice; otherwise the description gets the space.
        val cols = if (regular) {
            listOf(
                Col(R.string.inv_sr, LEFT, LEFT + 22, false),
                Col(R.string.inv_description, LEFT + 22, 300f, false),
                Col(R.string.inv_hsn, 300f, 358f, false),
                Col(R.string.inv_qty, 358f, 416f, true),
                Col(R.string.inv_unit, 416f, 448f, false),
                Col(R.string.inv_rate, 448f, 500f, true),
                Col(R.string.inv_gst, 500f, 530f, true),
                Col(R.string.inv_amount, 530f, RIGHT, true),
            )
        } else {
            listOf(
                Col(R.string.inv_sr, LEFT, LEFT + 22, false),
                Col(R.string.inv_description, LEFT + 22, 358f, false),
                Col(R.string.inv_qty, 358f, 416f, true),
                Col(R.string.inv_unit, 416f, 448f, false),
                Col(R.string.inv_rate, 448f, 510f, true),
                Col(R.string.inv_amount, 510f, RIGHT, true),
            )
        }
        val rowsPerPage = ((TABLE_BOTTOM - (TABLE_TOP + 20f) - 4f) / ROW).toInt()
        val pages = items.chunked(rowsPerPage).ifEmpty { listOf(emptyList()) }

        val doc = PdfDocument()
        pages.forEachIndexed { pageIndex, pageItems ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageIndex + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            header(c, s, shop, bill, regular)
            table(c, s, cols, pageItems, pageIndex * rowsPerPage, regular)
            if (pageIndex == pages.lastIndex) {
                footer(c, s, shop, bill, items, regular)
            } else {
                text(c, "Continued on next page… (${pageIndex + 1}/${pages.size})", W / 2, TABLE_BOTTOM + 20, 9f, align = Paint.Align.CENTER)
            }
            c.drawRect(LEFT, 36f, RIGHT, H - 30f, stroke(1f)) // outer border
            doc.finishPage(page)
        }
        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "Invoice-%04d.pdf".format(bill.billNo))
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ---- sections ----

    private fun header(c: Canvas, s: (Int) -> String, shop: ShopSettings, bill: Bill, regular: Boolean) {
        val title = when (bill.gstMode) {
            GstMode.REGULAR -> s(R.string.inv_gst_invoice)
            GstMode.COMPOSITION -> s(R.string.inv_bill_of_supply)
            GstMode.UNREGISTERED -> s(R.string.inv_invoice)
        }
        text(c, title, W / 2, 30f, 11f, bold = true, serif = true, align = Paint.Align.CENTER)
        text(c, s(R.string.inv_original), RIGHT, 28f, 8f, align = Paint.Align.RIGHT)

        // Shop box
        var y = 66f
        text(c, shop.name.ifBlank { s(R.string.app_name) }.uppercase(), W / 2, y, 24f, bold = true, serif = true, align = Paint.Align.CENTER)
        y += 15f
        if (shop.address.isNotBlank()) {
            text(c, shop.address.uppercase(), W / 2, y, 10f, serif = true, align = Paint.Align.CENTER)
            y += 13f
        }
        if (shop.phone.isNotBlank()) {
            text(c, s(R.string.inv_phone) + " : " + shop.phone, W / 2, y, 10f, align = Paint.Align.CENTER)
            y += 13f
        }
        if (shop.email.isNotBlank()) {
            text(c, s(R.string.inv_email) + " : " + shop.email.uppercase(), W / 2, y, 10f, serif = true, align = Paint.Align.CENTER)
        }
        // GSTIN (registered shops) and FSSAI licence share the band under the shop box.
        val band = listOfNotNull(
            (s(R.string.gstin) + " : " + shop.gstin).takeIf { bill.gstMode != GstMode.UNREGISTERED && shop.gstin.isNotBlank() },
            (s(R.string.inv_fssai) + " : " + shop.fssai).takeIf { shop.fssai.isNotBlank() },
        ).joinToString("      |      ")
        c.drawLine(LEFT, 122f, RIGHT, 122f, stroke(0.8f))
        if (band.isNotEmpty()) text(c, band, W / 2, 134f, 9.5f, bold = true, serif = true, align = Paint.Align.CENTER)
        c.drawLine(LEFT, 140f, RIGHT, 140f, stroke(0.8f))

        // Buyer (left) and invoice details (right)
        val mid = 330f
        c.drawLine(mid, 140f, mid, TABLE_TOP, stroke(0.8f))
        c.drawLine(LEFT, 218f, mid, 218f, stroke(0.8f))
        val buyerName = if (bill.isWalkIn) s(R.string.walk_in) else bill.partyName.orEmpty()
        val gstinLine = if (regular || bill.gstMode == GstMode.COMPOSITION) bill.partyGstin.orEmpty() else null
        party(c, s(R.string.inv_buyer), buyerName, bill.partyAddress, shop.state.takeIf { it.isNotBlank() }, bill.partyPhone, gstinLine, 152f)
        if (bill.shipsElsewhere) {
            party(c, s(R.string.inv_shipping), bill.shipName.orEmpty(), bill.shipAddress, null, bill.shipPhone, null, 230f)
        } else {
            party(c, s(R.string.inv_shipping), buyerName, bill.partyAddress, null, bill.partyPhone, gstinLine, 230f)
        }

        val date = LocalDate.ofEpochDay(bill.billDate).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        listOf(
            Triple(s(R.string.inv_no), bill.billNo.toString(), true),
            Triple(s(R.string.inv_date), date, true),
            Triple(s(R.string.inv_transport), "", false),
            Triple(s(R.string.inv_vehicle), "", false),
        ).forEachIndexed { i, (label, value, bold) ->
            val top = 140f + i * 24f
            text(c, "$label : ", mid + 6, top + 16, 9f, bold = bold)
            text(c, value, mid + 6 + measure("$label : ", 9f, bold), top + 16, 9.5f, bold = true)
            c.drawLine(mid, top + 24, RIGHT, top + 24, stroke(0.5f))
        }
    }

    private fun party(c: Canvas, label: String, name: String, address: String?, state: String?, phone: String?, gstin: String?, top: Float) {
        var y = top
        text(c, label, LEFT + 6, y, 9.5f)
        y += 12f
        text(c, name, LEFT + 12, y, 9.5f, bold = true)
        y += 11f
        wrap(address.orEmpty(), 8.5f, 290f).take(2).forEach {
            text(c, it, LEFT + 12, y, 8.5f)
            y += 10f
        }
        if (state != null) {
            text(c, "State : $state", LEFT + 12, y, 8.5f)
            y += 10f
        }
        text(c, "PHONE NO. : " + phone.orEmpty(), LEFT + 12, y, 8.5f)
        y += 10f
        if (gstin != null) text(c, "GSTIN : $gstin", LEFT + 12, y, 8.5f)
    }

    private fun table(c: Canvas, s: (Int) -> String, cols: List<Col>, items: List<BillItem>, firstIndex: Int, regular: Boolean) {
        c.drawLine(LEFT, TABLE_TOP, RIGHT, TABLE_TOP, stroke(0.8f))
        c.drawLine(LEFT, TABLE_TOP + 20, RIGHT, TABLE_TOP + 20, stroke(0.8f))
        cols.forEach { col ->
            if (col.left > LEFT) c.drawLine(col.left, TABLE_TOP, col.left, TABLE_BOTTOM, stroke(0.5f))
            val x = if (col.alignRight) col.right - 3 else col.left + 3
            text(c, s(col.title), x, TABLE_TOP + 14, 8f, bold = true, serif = true, align = if (col.alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
        }
        var y = TABLE_TOP + 20 + ROW
        items.forEachIndexed { i, item ->
            val values = buildList {
                add((firstIndex + i + 1).toString())
                add(item.name.uppercase())
                if (regular) add(item.hsnCode.orEmpty())
                add(quantity(item.quantity))
                add(item.unit.uppercase())
                add(formatPlainRupees(item.pricePaise))
                if (regular) add(formatPct(2L * item.cgstMilli))
                add(formatPlainRupees(item.gst.taxablePaise)) // before GST: the AMOUNT column adds up to GROSS AMOUNT
            }
            cols.zip(values).forEach { (col, v) ->
                val width = col.right - col.left - 6
                val fitted = ellipsize(v, 8.5f, width)
                val x = if (col.alignRight) col.right - 3 else col.left + 3
                text(c, fitted, x, y, 8.5f, align = if (col.alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
            }
            y += ROW
        }
        c.drawLine(LEFT, TABLE_BOTTOM, RIGHT, TABLE_BOTTOM, stroke(0.8f))
    }

    private fun footer(c: Canvas, s: (Int) -> String, shop: ShopSettings, bill: Bill, items: List<BillItem>, regular: Boolean) {
        val split = items.fold(GstSplit.ZERO) { a, i -> a + i.gst }
        val totalQty = items.fold(BigDecimal.ZERO) { a, i -> a + BigDecimal.valueOf(i.quantity) }
        val right = 360f
        var y = TABLE_BOTTOM

        // Total quantity | gross amount
        text(c, s(R.string.inv_total_qty) + "  " + totalQty.stripTrailingZeros().toPlainString(), right - 6, y + 13, 9f, align = Paint.Align.RIGHT)
        c.drawLine(right, y, right, y + 120, stroke(0.8f))
        val gross = if (regular) split.taxablePaise else bill.subtotalPaise
        val rightRows = buildList {
            add(Triple(s(R.string.inv_gross), formatPlainRupees(gross), false))
            add(Triple(s(R.string.inv_discount), "0.00", false))
            if (regular) {
                add(Triple(s(R.string.inv_sgst_value), formatPlainRupees(split.sgstPaise), false))
                add(Triple(s(R.string.inv_cgst_value), formatPlainRupees(split.cgstPaise), false))
            }
            add(Triple(s(R.string.inv_round_off), formatPlainRupees(bill.roundOffPaise), false))
            if (bill.duePaise > 0) add(Triple(s(R.string.inv_balance), formatPlainRupees(bill.duePaise), false))
        }
        rightRows.forEachIndexed { i, (label, value, _) ->
            text(c, label, right + 6, y + 13 + i * 17f, 10f)
            text(c, value, RIGHT - 6, y + 13 + i * 17f, 10f, align = Paint.Align.RIGHT)
        }
        c.drawLine(LEFT, y + 18, RIGHT, y + 18, stroke(0.5f))

        // GST class table (regular) or composition note
        val tableTop = y + 18
        if (regular) {
            val x1 = LEFT + 60
            val x2 = x1 + 90
            val x3 = x2 + 75
            text(c, s(R.string.inv_class), LEFT + 4, tableTop + 11, 9f)
            text(c, s(R.string.inv_gst_pct), LEFT + 4, tableTop + 21, 7.5f)
            text(c, s(R.string.sgst), (x1 + x3) / 2, tableTop + 11, 9f, align = Paint.Align.CENTER)
            text(c, s(R.string.inv_taxable), (x1 + x2) / 2, tableTop + 21, 7.5f, align = Paint.Align.CENTER)
            text(c, s(R.string.inv_tax), (x2 + x3) / 2, tableTop + 21, 7.5f, align = Paint.Align.CENTER)
            text(c, s(R.string.cgst), (x3 + right) / 2, tableTop + 11, 9f, align = Paint.Align.CENTER)
            // The class table fits exactly between the totals line and the amount-in-words line (84pt).
            listOf(x1, x2, x3).forEach { c.drawLine(it, tableTop + if (it == x2) 13 else 0, it, tableTop + 66, stroke(0.5f)) }
            val byRate = items.filter { it.cgstMilli > 0 }.groupBy { it.cgstMilli }.toSortedMap()
            val rates = (byRate.keys + listOf(2500, 9000)).distinct().sorted().take(3)
            rates.forEachIndexed { i, cgst ->
                val rowY = tableTop + 24 + i * 14f
                c.drawLine(LEFT, rowY, x3, rowY, stroke(0.5f))
                text(c, formatPct(2L * cgst) + "%", LEFT + 4, rowY + 11, 9f)
                byRate[cgst]?.fold(GstSplit.ZERO) { a, line -> a + line.gst }?.let { r ->
                    text(c, formatPlainRupees(r.taxablePaise), (x1 + x2) / 2, rowY + 11, 8.5f, align = Paint.Align.CENTER)
                    text(c, formatPlainRupees(r.sgstPaise), (x2 + x3) / 2, rowY + 11, 8.5f, align = Paint.Align.CENTER)
                    text(c, formatPlainRupees(r.cgstPaise), (x3 + right) / 2, rowY + 11, 8.5f, align = Paint.Align.CENTER)
                }
            }
            val exempt = items.filter { it.cgstMilli == 0 }.sumOf { it.totalPaise }
            c.drawLine(LEFT, tableTop + 66, right, tableTop + 66, stroke(0.5f))
            text(c, s(R.string.inv_exempted) + "  " + formatPlainRupees(exempt), LEFT + 4, tableTop + 79, 9f)
        } else if (bill.gstMode == GstMode.COMPOSITION) {
            wrap(s(R.string.composition_note), 9f, right - LEFT - 12).forEachIndexed { i, line ->
                text(c, line, LEFT + 6, tableTop + 18 + i * 12f, 9f, bold = true)
            }
        }

        // Words | grand total
        y += 102
        c.drawLine(LEFT, y, RIGHT, y, stroke(0.8f))
        text(c, rupeesInWords(bill.totalPaise), LEFT + 4, y + 13, 9f)
        text(c, s(R.string.inv_grand_total), right + 6, y + 13, 11f, bold = true)
        text(c, formatPlainRupees(bill.totalPaise), RIGHT - 6, y + 13, 11f, bold = true, align = Paint.Align.RIGHT)
        y += 18
        c.drawLine(LEFT, y, RIGHT, y, stroke(0.8f))

        // Terms (left) and bank / UPI box (right)
        text(c, s(R.string.inv_remark), LEFT + 6, y + 12, 9.5f)
        text(c, s(R.string.inv_terms), LEFT + 6, y + 25, 10f, underline = true)
        val terms = shop.terms.ifBlank { s(R.string.inv_default_terms) }.lines().filter { it.isNotBlank() }.take(4)
        terms.forEachIndexed { i, t -> text(c, ellipsize(t, 8.5f, 340f), LEFT + 6, y + 37 + i * 11f, 8.5f) }
        val bank = listOf(
            s(R.string.inv_ac_name) to shop.accountName,
            s(R.string.inv_bank) to shop.bankName,
            s(R.string.inv_ac_no) to shop.accountNo,
            s(R.string.inv_ifsc) to shop.ifsc,
        ).filter { it.second.isNotBlank() }
        if (shop.upiId.isNotBlank() || bank.isNotEmpty()) {
            val bx = 380f
            var by = y + 8
            c.drawRect(bx, by, RIGHT - 6, by + 18 + bank.size * 12f, stroke(0.8f))
            if (shop.upiId.isNotBlank()) text(c, s(R.string.inv_payee) + " " + shop.upiId.uppercase(), bx + 4, by + 13, 10f, bold = true, underline = true)
            by += 18
            bank.forEach { (label, value) ->
                c.drawLine(bx, by, RIGHT - 6, by, stroke(0.5f))
                text(c, label, bx + 4, by + 9, 7.5f, bold = true)
                text(c, value.uppercase(), RIGHT - 10, by + 9, 7.5f, bold = true, align = Paint.Align.RIGHT)
                by += 12f
            }
        }

        // Declaration and signatures (kept inside the page border)
        y += 70
        c.drawLine(LEFT + 6, y, 360f, y, Paint().apply { color = Color.DKGRAY; strokeWidth = 0.6f; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f) })
        wrap(s(R.string.inv_declaration), 8.5f, 330f).forEachIndexed { i, line -> text(c, line, LEFT + 6, y + 12 + i * 11f, 8.5f) }
        val forShop = s(R.string.inv_for).format(shop.name.ifBlank { s(R.string.app_name) }.uppercase())
        text(c, forShop, RIGHT - 6, y + 38, 10f, bold = true, align = Paint.Align.RIGHT)
        text(c, s(R.string.inv_receiver), LEFT + 30, y + 64, 9f, bold = true)
        text(c, s(R.string.inv_signatory), RIGHT - 30, y + 64, 10f, bold = true, align = Paint.Align.RIGHT)
    }

    // ---- drawing helpers ----

    private fun paint(size: Float, bold: Boolean, serif: Boolean, align: Paint.Align, underline: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.BLACK
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
            isUnderlineText = underline
        }

    private fun text(
        c: Canvas, value: String, x: Float, y: Float, size: Float,
        bold: Boolean = false, serif: Boolean = false, align: Paint.Align = Paint.Align.LEFT, underline: Boolean = false,
    ) = c.drawText(value, x, y, paint(size, bold, serif, align, underline))

    private fun measure(value: String, size: Float, bold: Boolean) = paint(size, bold, false, Paint.Align.LEFT).measureText(value)

    private fun stroke(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = width
        style = Paint.Style.STROKE
    }

    private fun ellipsize(value: String, size: Float, width: Float): String {
        val p = paint(size, false, false, Paint.Align.LEFT)
        if (p.measureText(value) <= width) return value
        var end = value.length
        while (end > 0 && p.measureText(value.substring(0, end) + "…") > width) end--
        return value.substring(0, end) + "…"
    }

    private fun wrap(value: String, size: Float, width: Float): List<String> {
        if (value.isBlank()) return emptyList()
        val p = paint(size, false, false, Paint.Align.LEFT)
        val lines = mutableListOf<String>()
        var line = ""
        for (word in value.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) > width && line.isNotEmpty()) {
                lines += line
                line = word
            } else {
                line = next
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun quantity(q: Double): String =
        if (q % 1.0 == 0.0) q.toLong().toString() else BigDecimal.valueOf(q).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString()

    /** 5000 -> "5.00", 250 -> "0.25", like the GST% column of most invoices. */
    private fun formatPct(totalMilli: Long): String = BigDecimal.valueOf(totalMilli, 3).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}
