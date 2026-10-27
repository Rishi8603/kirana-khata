package com.rishi.kiranaledger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rishi.kiranaledger.bill.InvoicePdf
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillItem
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.GstSplit
import com.rishi.kiranaledger.data.gstSplit
import com.rishi.kiranaledger.data.lineAmountPaise
import com.rishi.kiranaledger.data.roundToRupee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Renders the A4 GST invoice (and a 2-page one) with sample data, then puts the real shop settings back. */
@RunWith(AndroidJUnit4::class)
class InvoiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withSampleShop(block: () -> Unit) {
        val prefs = context.getSharedPreferences("shop", Context.MODE_PRIVATE)
        val saved = prefs.all
        try {
            ShopSettings(context).apply {
                name = "Sample Kirana Store"
                address = "Main Road, Sample City-400001"
                phone = "9000000000"
                email = "sample@example.com"
                state = "27-Maharashtra"
                gstin = "27ABCDE1234F1Z5"
                upiId = "sample@upi"
                accountName = "Sample Kirana Store"
                bankName = "Sample Bank"
                accountNo = "000011112222"
                ifsc = "SBIN0000001"
                fssai = "12345678901234"
                terms = ""
                a4Invoice = true
            }
            block()
        } finally {
            // Restore exactly what was there before the test.
            prefs.edit().clear().apply {
                saved.forEach { (k, v) ->
                    when (v) {
                        is String -> putString(k, v)
                        is Boolean -> putBoolean(k, v)
                        is Int -> putInt(k, v)
                        is Long -> putLong(k, v)
                        is Float -> putFloat(k, v)
                    }
                }
            }.commit()
        }
    }

    private fun line(bill: Bill, i: Int, name: String, hsn: String, qty: Double, unit: String, rupees: Long, cgstMilli: Int): BillItem {
        val split = gstSplit(lineAmountPaise(qty, rupees * 100), cgstMilli, false) // GST added on top
        return BillItem(
            billId = bill.id, position = i, name = name, quantity = qty, unit = unit, pricePaise = rupees * 100,
            hsnCode = hsn, cgstMilli = cgstMilli, priceIncludesTax = false, taxablePaise = if (cgstMilli > 0) split.taxablePaise else 0,
            cgstPaise = split.cgstPaise, sgstPaise = split.sgstPaise,
        )
    }

    private fun sampleItems(bill: Bill) = listOf(
        line(bill, 0, "Masoor Dal", "071320", 10.5, "kg", 92, 0),
        line(bill, 1, "Chana Dal", "071320", 5.0, "kg", 96, 0),
        line(bill, 2, "Soyabeen", "07149090", 3.0, "kg", 120, 2500),
        line(bill, 3, "Mustard Oil", "15149120", 5.0, "kg", 180, 2500),
        line(bill, 4, "Haldi Powder", "0910", 0.75, "kg", 290, 2500),
        line(bill, 5, "Detergent", "3402", 2.0, "pcs", 95, 9000),
        line(bill, 6, "Machis", "3605", 4.0, "pcs", 2, 0),
    )

    private fun billFor(items: (Bill) -> List<BillItem>): Pair<Bill, List<BillItem>> {
        val draft = Bill(billNo = 325, partyName = "M/s N P S Traders", partyPhone = "9111111111", partyAddress = "Station Road, Sample City", partyGstin = "27AAAPL1234C1Z2", customerId = "c1", subtotalPaise = 0, roundOffPaise = 0, totalPaise = 0, paidPaise = 0, gstMode = GstMode.REGULAR)
        val lines = items(draft)
        val subtotal = lines.sumOf { it.totalPaise }
        val (total, roundOff) = roundToRupee(subtotal)
        return draft.copy(subtotalPaise = subtotal, roundOffPaise = roundOff, totalPaise = total, paidPaise = total) to lines
    }

    @Test fun a4InvoiceRendersAndAddsUp() = withSampleShop {
        val (bill, items) = billFor(::sampleItems)
        val split = items.fold(GstSplit.ZERO) { a, i -> a + i.gst }
        // The AMOUNT column (qty × rate, before GST) adds up to GROSS AMOUNT; GST goes on top of that.
        assertEquals(items.sumOf { it.amountPaise }, split.taxablePaise)
        assertEquals(bill.totalPaise, split.taxablePaise + split.cgstPaise + split.sgstPaise + bill.roundOffPaise)
        render(InvoicePdf.create(context, bill, items), "invoice_a4.png")
    }

    @Test fun shipToPrintsTheOtherAddress() = withSampleShop {
        val (bill, items) = billFor(::sampleItems)
        val shipped = bill.copy(shipName = "Ramesh Godown", shipAddress = "Market Road, Sample Town", shipPhone = "9222222222")
        assertTrue(shipped.shipsElsewhere)
        assertTrue(!bill.shipsElsewhere) // default: same as buyer
        render(InvoicePdf.create(context, shipped, items), "invoice_ship.png")
    }

    @Test fun longInvoiceContinuesOnSecondPage() = withSampleShop {
        val (bill, items) = billFor { b -> (0 until 25).map { line(b, it, "Item ${it + 1}", "1905", 1.0, "pcs", 10, 2500) } }
        val pdf = InvoicePdf.create(context, bill, items)
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(2, it.pageCount) }
    }

    private fun render(pdf: File, name: String) {
        val out = File(context.filesDir, "test-render").apply { mkdirs() }
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            assertTrue(r.pageCount >= 1)
            r.openPage(0).use { page ->
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
