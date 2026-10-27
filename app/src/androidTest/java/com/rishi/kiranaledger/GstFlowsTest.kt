package com.rishi.kiranaledger

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rishi.kiranaledger.bill.BillPdf
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillItem
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.GstCatalog
import com.rishi.kiranaledger.data.GstChoice
import com.rishi.kiranaledger.data.GstLink
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.Item
import com.rishi.kiranaledger.data.LedgerDatabase
import com.rishi.kiranaledger.data.MIGRATION_5_6
import com.rishi.kiranaledger.data.MIGRATION_6_7
import com.rishi.kiranaledger.data.MIGRATION_7_8
import com.rishi.kiranaledger.data.MIGRATION_8_9
import com.rishi.kiranaledger.data.gstSplit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** v1.1 GST & HSN: migration, search, the bundled CBIC catalog, and a GST bill end to end. */
@RunWith(AndroidJUnit4::class)
class GstFlowsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LedgerDatabase

    @get:Rule
    val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LedgerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before fun open() {
        db = LedgerDatabase.inMemoryForTests(context)
    }

    @After fun close() = db.close()

    @Test fun migration5to6KeepsEveryRow() {
        val name = "migration-5-6.db"
        migrations.createDatabase(name, 5).use { v5 ->
            v5.execSQL(
                "INSERT INTO items (name_key, name, unit, last_price_paise, use_count, updated_at, track_stock, stock_qty, min_stock, purchase_price_paise) " +
                    "VALUES ('atta', 'Atta', 'kg', 5000, 3, 0, 1, 12.5, 5, 4200)",
            )
            v5.execSQL(
                "INSERT INTO bills (id, bill_no, customer_id, party_name, party_phone, bill_date, subtotal_paise, round_off_paise, total_paise, paid_paise, payment_mode, created_at, updated_at, deleted) " +
                    "VALUES ('b1', 1, NULL, NULL, NULL, 0, 31000, 0, 31000, 31000, 'CASH', 0, 0, 0)",
            )
            v5.execSQL("INSERT INTO bill_items (id, bill_id, position, name, quantity, unit, price_paise) VALUES ('i1', 'b1', 0, 'Atta', 2, 'kg', 15500)")
        }
        migrations.runMigrationsAndValidate(name, 9, true, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).use { v6 ->
            v6.query("SELECT stock_qty, purchase_price_paise, hsn_code, cgst_milli, price_includes_tax FROM items WHERE name_key = 'atta'").use {
                assertTrue(it.moveToFirst())
                assertEquals(12.5, it.getDouble(0), 0.0001)
                assertEquals(4200L, it.getLong(1))
                assertTrue(it.isNull(2)) // no GST until the shopkeeper sets it
                assertEquals(0, it.getInt(3))
                assertEquals(1, it.getInt(4))
            }
            v6.query("SELECT gst_mode, total_paise FROM bills WHERE id = 'b1'").use {
                it.moveToFirst()
                assertEquals("UNREGISTERED", it.getString(0)) // old bills keep printing as receipts
                assertEquals(31000L, it.getLong(1))
            }
            v6.query("SELECT cgst_milli, taxable_paise, price_paise FROM bill_items WHERE id = 'i1'").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertEquals(0L, it.getLong(1))
                assertEquals(15500L, it.getLong(2))
            }
        }
    }

    private fun item(key: String, name: String, hsn: String?, nature: String? = null) = Item(
        nameKey = key, name = name, unit = "pcs", lastPricePaise = 1000, useCount = 1,
        hsnCode = hsn, productNature = nature, cgstMilli = if (hsn == null) 0 else 2500,
    )

    @Test fun typingAnHsnCodeListsEveryProductUnderIt(): Unit = runBlocking {
        val stock = db.stockDao()
        stock.upsert(item("parle g", "Parle G", "1905", "Pastry, cakes, biscuits"))
        stock.upsert(item("good day", "Good Day", "190590", "Pastry, cakes, biscuits"))
        stock.upsert(item("atta", "Atta", "1101", "Wheat or meslin flour"))
        stock.upsert(item("pen", "Pen", null))

        val by1905 = stock.items("1905").first()
        assertEquals(listOf("1905", "190590"), by1905.take(2).map { it.hsnCode })
        assertEquals(2, by1905.count { it.hsnCode?.startsWith("1905") == true })
        assertEquals(setOf("Parle G", "Good Day"), db.billDao().suggestItems("1905").take(2).map { it.name }.toSet())
        assertEquals(listOf("Atta"), stock.items("1101").first().filter { it.hsnCode != null }.map { it.name })
        // Product nature and name still search as usual.
        assertEquals(2, stock.items("biscuits").first().size)
        assertEquals(listOf("Pen"), stock.items("Pen").first().map { it.name })
    }

    @Test fun bundledCatalogLoadsAndSearches() {
        val all = GstCatalog.entries(context)
        assertEquals(1367, all.size)
        val honey = GstCatalog.search(all, null, "0409").map { it.id }
        assertTrue("I-10" in honey && "EXEMPT-19" in honey) // packaged 5%, loose exempt
        assertTrue(GstCatalog.search(all, "I", "honey").any { it.id == "I-10" })
        assertTrue(GstCatalog.search(all, null, "19053100").any { it.id == "I-123" }) // longer code finds its heading
        assertEquals(2500, all.first { it.id == "I-10" }.cgstMilli)
        assertTrue(all.all { e -> e.hsnCodes.all { it.matches(Regex("\\d{2,8}")) } })
    }

    @Test fun gstBillSyncsProductAndRendersTaxInvoice(): Unit = runBlocking {
        val dao = db.ledgerDao()
        val vikas = Customer(name = "Vikas")
        dao.insertCustomer(vikas)
        val oil = GstLink(GstChoice("I-84", "I", "1507", "Soya-bean oil", 2500), 2500, priceIncludesTax = true)
        val soap = GstLink(GstChoice("II-66", "II", "3401", "Soap", 9000), 9000, priceIncludesTax = false)
        val oilSplit = gstSplit(2 * 14000, 2500, true)
        val soapSplit = gstSplit(4000, 9000, false)
        val subtotal = oilSplit.totalPaise + soapSplit.totalPaise + 2500
        val bill = Bill(billNo = 7, customerId = vikas.id, partyName = "Vikas", subtotalPaise = subtotal, roundOffPaise = 0, totalPaise = subtotal, paidPaise = subtotal, gstMode = GstMode.REGULAR)
        val items = listOf(
            BillItem(billId = bill.id, position = 0, name = "Fortune Soya Oil 1L", quantity = 2.0, unit = "pcs", pricePaise = 14000, hsnCode = "1507", cgstMilli = 2500, taxablePaise = oilSplit.taxablePaise, cgstPaise = oilSplit.cgstPaise, sgstPaise = oilSplit.sgstPaise),
            BillItem(billId = bill.id, position = 1, name = "Detergent Soap", quantity = 1.0, unit = "pcs", pricePaise = 4000, hsnCode = "3401", cgstMilli = 9000, priceIncludesTax = false, taxablePaise = soapSplit.taxablePaise, cgstPaise = soapSplit.cgstPaise, sgstPaise = soapSplit.sgstPaise),
            BillItem(billId = bill.id, position = 2, name = "Salt", quantity = 1.0, unit = "pcs", pricePaise = 2500, hsnCode = "2501"),
        )
        db.billDao().saveBill(bill, items, listOf(oil, soap, null))

        // The products are now linked to their HSN rows; Salt had no GST link and stays without one.
        val savedOil = db.stockDao().itemOnce("fortune soya oil 1l")!!
        assertEquals("1507", savedOil.hsnCode)
        assertEquals("I-84", savedOil.gstEntryId)
        assertEquals(2500, savedOil.cgstMilli)
        assertNull(db.stockDao().itemOnce("salt")!!.hsnCode)
        assertEquals(28000L, items[0].totalPaise) // inclusive: customer pays the printed price
        assertEquals(4720L, items[1].totalPaise) // exclusive: ₹40 + 18%
        assertEquals(0L, (bill.totalPaise - bill.paidPaise))
        assertEquals(-0L, dao.customerOnce(vikas.id)!!.balancePaise)

        val pdf = BillPdf.create(context, bill, db.billDao().items(bill.id).first())
        val out = File(context.filesDir, "test-render").apply { mkdirs() }
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            r.openPage(0).use { page ->
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(out, "gst_bill.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
