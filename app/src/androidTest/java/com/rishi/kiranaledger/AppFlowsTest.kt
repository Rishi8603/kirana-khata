package com.rishi.kiranaledger

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rishi.kiranaledger.backup.BackupFile
import com.rishi.kiranaledger.bill.BillPdf
import com.rishi.kiranaledger.bill.StatementPdf
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillItem
import com.rishi.kiranaledger.data.BillStatus
import com.rishi.kiranaledger.data.CashEntry
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.CustomerFilter
import com.rishi.kiranaledger.data.CustomerSort
import com.rishi.kiranaledger.data.Entry
import com.rishi.kiranaledger.data.Expense
import com.rishi.kiranaledger.data.Item
import com.rishi.kiranaledger.data.LedgerDatabase
import com.rishi.kiranaledger.data.MIGRATION_5_6
import com.rishi.kiranaledger.data.MIGRATION_6_7
import com.rishi.kiranaledger.data.MIGRATION_7_8
import com.rishi.kiranaledger.data.MIGRATION_8_9
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.data.StockReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * End-to-end checks of the app's money logic on a real phone. Every test uses a fresh in-memory
 * database, so the shop's real data is never read or changed (except the backup test, which only reads it).
 */
@RunWith(AndroidJUnit4::class)
class AppFlowsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LedgerDatabase
    private val today = LocalDate.now().toEpochDay()

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

    private suspend fun balance(id: String) = db.ledgerDao().customerOnce(id)!!.balancePaise

    @Test fun khataBalanceFollowsAddEditDeleteUndo() = runBlocking {
        val dao = db.ledgerDao()
        val ramesh = Customer(name = "Ramesh")
        dao.insertCustomerWithOpeningBalance(ramesh, 50000) // ₹500 from bahi
        assertEquals(-50000L, balance(ramesh.id))
        val credit = Entry(customerId = ramesh.id, amountPaise = -25000)
        dao.insertEntry(credit)
        dao.insertEntry(Entry(customerId = ramesh.id, amountPaise = 30000))
        assertEquals(-45000L, balance(ramesh.id))
        dao.updateEntry(credit.id, -35000, "edited", today - 3) // edit ₹250 -> ₹350, back-dated
        assertEquals(-55000L, balance(ramesh.id))
        dao.deleteEntry(credit.id)
        assertEquals(-20000L, balance(ramesh.id))
        dao.undoDeleteEntry(credit.id)
        assertEquals(-55000L, balance(ramesh.id))
        assertEquals(today - 3, dao.entry(credit.id)!!.entryDate)
    }

    @Test fun supplierMoneyRunsTheOtherWay() = runBlocking {
        val dao = db.ledgerDao()
        val wholesaler = Customer(name = "Gupta Wholesale", partyType = PartyType.SUPPLIER)
        dao.insertCustomerWithOpeningBalance(wholesaler, 100000) // we owe ₹1,000
        assertEquals(100000L, balance(wholesaler.id))
        dao.insertEntry(Entry(customerId = wholesaler.id, amountPaise = -40000)) // paid ₹400
        assertEquals(60000L, balance(wholesaler.id))
        val summary = dao.dueSummary(PartyType.SUPPLIER).first()
        assertEquals(60000L, summary.totalAdvancePaise) // "You'll give"
        assertEquals(1, summary.partiesWeOwe)
        // Suppliers never appear in the customer list, the bill picker or collections.
        assertTrue(dao.customerList(PartyType.CUSTOMER, "", 0, 0).first().isEmpty())
        assertTrue(dao.customers().first().isEmpty())
        assertTrue(dao.customersToCollect().first().isEmpty())
    }

    @Test fun billUpdatesKhataAndStock_andDeleteRestoresBoth() = runBlocking {
        val dao = db.ledgerDao()
        val bills = db.billDao()
        val stock = db.stockDao()
        val vikas = Customer(name = "Vikas")
        dao.insertCustomer(vikas)
        stock.upsert(Item(nameKey = "atta", name = "Atta", unit = "kg", lastPricePaise = 5000, useCount = 0, trackStock = true, stockQty = 10.0, minStock = 9.0))

        val bill = Bill(
            billNo = bills.nextBillNo(), customerId = vikas.id, partyName = vikas.name,
            subtotalPaise = 57500, roundOffPaise = 0, totalPaise = 57500, paidPaise = 30000,
        )
        bills.saveBill(
            bill,
            listOf(
                BillItem(billId = bill.id, position = 0, name = "Atta", quantity = 2.0, unit = "kg", pricePaise = 5000),
                BillItem(billId = bill.id, position = 1, name = "Tel", quantity = 2.0, unit = "L", pricePaise = 23750),
            ),
            emptyList(),
        )
        assertEquals(BillStatus.PARTLY_PAID, bill.status)
        assertEquals(-27500L, balance(vikas.id)) // ₹575 credit − ₹300 paid
        assertEquals(2, dao.entries(vikas.id).first().size)
        val atta = stock.itemOnce("atta")!!
        assertEquals(8.0, atta.stockQty, 0.0001) // 10 − 2
        assertTrue(atta.isLow) // 8 <= 9
        assertEquals(1, atta.useCount)
        assertEquals("kg", atta.unit)
        assertTrue(atta.trackStock) // saving a bill must not wipe stock settings
        assertEquals(StockReason.SALE, stock.moves("atta").first().first().reason)
        assertFalse(stock.itemOnce("tel")!!.trackStock) // new items start untracked
        assertEquals(2, bills.nextBillNo())

        bills.deleteBill(bill.id)
        assertEquals(0L, balance(vikas.id))
        assertEquals(10.0, stock.itemOnce("atta")!!.stockQty, 0.0001)
        assertTrue(bills.bills().first().isEmpty())
    }

    /** Editing a bill undoes the old khata entries and stock, then applies the new version — balances stay exact. */
    @Test fun editingABillKeepsKhataAndStockRight(): Unit = runBlocking {
        val dao = db.ledgerDao()
        val bills = db.billDao()
        val stock = db.stockDao()
        val vikas = Customer(name = "Vikas")
        dao.insertCustomer(vikas)
        stock.upsert(Item(nameKey = "atta", name = "Atta", unit = "kg", lastPricePaise = 5000, useCount = 0, trackStock = true, stockQty = 10.0))

        val bill = Bill(billNo = 1, customerId = vikas.id, partyName = "Vikas", subtotalPaise = 15000, roundOffPaise = 0, totalPaise = 15000, paidPaise = 0)
        bills.saveBill(bill, listOf(BillItem(billId = bill.id, position = 0, name = "Atta", quantity = 3.0, unit = "kg", pricePaise = 5000)), emptyList())
        assertEquals(-15000L, balance(vikas.id))
        assertEquals(7.0, stock.itemOnce("atta")!!.stockQty, 0.0001)

        // The shopkeeper typed 3 kg but it was 2 kg, and the customer paid ₹40 after all.
        val edited = bill.copy(subtotalPaise = 10000, totalPaise = 10000, paidPaise = 4000)
        bills.updateBill(edited, listOf(BillItem(billId = bill.id, position = 0, name = "Atta", quantity = 2.0, unit = "kg", pricePaise = 5000)), emptyList())
        assertEquals(-6000L, balance(vikas.id)) // ₹100 credit − ₹40 paid
        assertEquals(8.0, stock.itemOnce("atta")!!.stockQty, 0.0001)
        assertEquals(1, bills.bills().first().size) // still one bill, same number
        assertEquals(1, bills.bills().first().single().billNo)
        assertEquals(1, bills.itemsOnce(bill.id).size)
        assertEquals(2, dao.entries(vikas.id).first().size) // only the new credit + payment are live
        assertEquals(null, bills.bills().first().single().shipName) // same as buyer by default

        // Ship to another place, then back to "same as buyer".
        bills.updateBill(edited.copy(shipName = "Godown", shipAddress = "Station Road", shipPhone = "9222222222"),
            listOf(BillItem(billId = bill.id, position = 0, name = "Atta", quantity = 2.0, unit = "kg", pricePaise = 5000)), emptyList())
        val shipped = bills.bills().first().single()
        assertEquals(listOf("Godown", "Station Road", "9222222222"), listOf(shipped.shipName, shipped.shipAddress, shipped.shipPhone))
        bills.updateBill(edited, listOf(BillItem(billId = bill.id, position = 0, name = "Atta", quantity = 2.0, unit = "kg", pricePaise = 5000)), emptyList())
        assertEquals(false, bills.bills().first().single().shipsElsewhere)
        assertEquals(-6000L, balance(vikas.id))
        assertEquals(8.0, stock.itemOnce("atta")!!.stockQty, 0.0001)
    }

    @Test fun walkInBillIsCashSaleAndLeavesKhataAlone() = runBlocking {
        val bills = db.billDao()
        val bill = Bill(billNo = 1, subtotalPaise = 31000, roundOffPaise = 0, totalPaise = 31000, paidPaise = 31000)
        bills.saveBill(bill, listOf(BillItem(billId = bill.id, position = 0, name = "Mirch", quantity = 1.0, unit = "pcs", pricePaise = 31000)), emptyList())
        assertEquals(BillStatus.CASH_SALE, bill.status)
        assertEquals(1, bills.bills().first().size)
    }

    @Test fun collectionsSplitByDueDate() = runBlocking {
        val dao = db.ledgerDao()
        val names = listOf("A" to today - 2, "B" to today, "C" to today + 5)
        for ((name, due) in names) {
            val c = Customer(name = name)
            dao.insertCustomerWithOpeningBalance(c, 10000)
            dao.setDueDate(c.id, due)
        }
        val s = dao.collectionSummary(today).first()
        assertEquals(10000L, s.overduePaise)
        assertEquals(10000L, s.todayPaise)
        assertEquals(10000L, s.upcomingPaise)
        assertEquals(listOf("A", "B", "C"), dao.customersToCollect().first().map { it.name })
    }

    @Test fun customerListFiltersAndSorts() = runBlocking {
        val dao = db.ledgerDao()
        dao.insertCustomerWithOpeningBalance(Customer(name = "zeta"), 10000)
        dao.insertCustomerWithOpeningBalance(Customer(name = "Alpha"), 90000)
        dao.insertCustomer(Customer(name = "Settled Sam"))
        val highest = dao.customerList(PartyType.CUSTOMER, "", CustomerFilter.DUE.ordinal, CustomerSort.HIGHEST_DUE.ordinal).first()
        assertEquals(listOf("Alpha", "zeta"), highest.map { it.name })
        val byName = dao.customerList(PartyType.CUSTOMER, "", CustomerFilter.ALL.ordinal, CustomerSort.NAME.ordinal).first()
        assertEquals(listOf("Alpha", "Settled Sam", "zeta"), byName.map { it.name })
        val settled = dao.customerList(PartyType.CUSTOMER, "", CustomerFilter.SETTLED.ordinal, 0).first()
        assertEquals(listOf("Settled Sam"), settled.map { it.name })
        assertEquals(listOf("zeta"), dao.customerList(PartyType.CUSTOMER, "ZET", 0, 0).first().map { it.name })
    }

    @Test fun reportAndGallaAddUp() = runBlocking {
        val dao = db.ledgerDao()
        val vikas = Customer(name = "Vikas")
        dao.insertCustomerWithOpeningBalance(vikas, 100000) // bahi opening: not counted as today's activity
        val supplier = Customer(name = "Gupta", partyType = PartyType.SUPPLIER)
        dao.insertCustomer(supplier)
        val bill = Bill(billNo = 1, customerId = vikas.id, partyName = "Vikas", subtotalPaise = 50000, roundOffPaise = 0, totalPaise = 50000, paidPaise = 20000)
        db.billDao().saveBill(bill, listOf(BillItem(billId = bill.id, position = 0, name = "Rice", quantity = 1.0, unit = "kg", pricePaise = 50000)), emptyList())
        dao.insertEntry(Entry(customerId = vikas.id, amountPaise = 15000)) // khata payment ₹150
        dao.insertEntry(Entry(customerId = supplier.id, amountPaise = 80000)) // purchase on credit ₹800
        dao.insertEntry(Entry(customerId = supplier.id, amountPaise = -30000)) // paid supplier ₹300
        db.expenseDao().insert(Expense(category = "Bijli", amountPaise = 12000))
        db.reportDao().insertCash(CashEntry(amountPaise = -5000, note = "ghar"))

        val s = db.reportDao().summary(today, today).first()
        assertEquals(50000L, s.salesPaise)
        assertEquals(1, s.billCount)
        assertEquals(50000L, s.creditGivenPaise) // the bill's credit; bahi opening excluded
        assertEquals(15000L, s.paymentsReceivedPaise) // bill payment is counted via the bill, not twice
        assertEquals(80000L, s.purchasesPaise)
        assertEquals(30000L, s.supplierPaymentsPaise)
        assertEquals(12000L, s.expensesPaise)
        assertEquals(20000L + 15000L, s.cashInPaise)
        assertEquals(30000L + 12000L + 5000L, s.cashOutPaise)
        assertEquals(35000L - 47000L, s.netPaise)
    }

    /** The real phone's database went 1 → 2 → 3 → 4 → 5; data and balance triggers must survive. */
    @Test fun upgradesFromVersion1KeepDataAndTriggers() {
        val name = "migration-test.db"
        migrations.createDatabase(name, 1).use { v1 ->
            LedgerDatabase.triggerSql.forEach(v1::execSQL)
            v1.execSQL("INSERT INTO customers (id, name, balance_paise, from_bahi, created_at, updated_at, deleted) VALUES ('c1', 'Vikas', 0, 0, 0, 0, 0)")
            v1.execSQL("INSERT INTO entries (id, customer_id, amount_paise, entry_date, from_bahi, created_at, updated_at, deleted) VALUES ('e1', 'c1', -121800, 0, 0, 0, 0, 0)")
        }
        migrations.runMigrationsAndValidate(name, 9, true, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).use { v5 ->
            v5.query("SELECT balance_paise, party_type, due_date FROM customers WHERE id = 'c1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(-121800L, it.getLong(0))
                assertEquals("CUSTOMER", it.getString(1))
                assertTrue(it.isNull(2))
            }
            v5.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
            v5.execSQL("INSERT INTO entries (id, customer_id, amount_paise, entry_date, from_bahi, created_at, updated_at, deleted) VALUES ('e2', 'c1', 21800, 0, 0, 0, 0, 0)")
            v5.query("SELECT balance_paise FROM customers WHERE id = 'c1'").use {
                it.moveToFirst()
                assertEquals(-100000L, it.getLong(0)) // trigger still keeps the balance
            }
        }
    }

    /** Draws the bill and statement PDFs and saves page 1 as PNG, so they can be looked at. */
    @Test fun pdfsRender() = runBlocking {
        val dao = db.ledgerDao()
        val vikas = Customer(name = "Vikas", phone = "9876543210")
        dao.insertCustomerWithOpeningBalance(vikas, 100000)
        val bill = Bill(billNo = 12, customerId = vikas.id, partyName = "Vikas", partyPhone = "9876543210", subtotalPaise = 57450, roundOffPaise = 50, totalPaise = 57500, paidPaise = 30000)
        val items = listOf(
            BillItem(billId = bill.id, position = 0, name = "Aashirvaad Atta 5kg", quantity = 1.0, unit = "pcs", pricePaise = 25000),
            BillItem(billId = bill.id, position = 1, name = "Fortune Tel", quantity = 2.0, unit = "L", pricePaise = 14000),
            BillItem(billId = bill.id, position = 2, name = "Cheeni", quantity = 0.5, unit = "kg", pricePaise = 4900),
        )
        db.billDao().saveBill(bill, items, emptyList())
        dao.insertEntry(Entry(customerId = vikas.id, amountPaise = 50000, note = "UPI"))

        val out = File(context.filesDir, "test-render").apply { mkdirs() }
        render(BillPdf.create(context, bill, items), File(out, "bill.png"))
        val customer = dao.customerOnce(vikas.id)!!
        render(StatementPdf.create(context, customer, dao.entries(vikas.id).first()), File(out, "statement.png"))
    }

    private fun render(pdf: File, png: File) {
        assertTrue(pdf.length() > 1000)
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            assertTrue(r.pageCount >= 1)
            r.openPage(0).use { page ->
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                png.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** The backup file must contain the database; a wrong file must be refused without touching data. */
    @Test fun backupFileContainsDatabase_andRejectsWrongFiles() {
        val zip = BackupFile.create(context)
        ZipFile(zip).use { assertTrue(it.getEntry("ledger.db") != null) }
        val notABackup = File(context.cacheDir, "not-a-backup.zip")
        ZipOutputStream(notABackup.outputStream()).use { it.putNextEntry(java.util.zip.ZipEntry("hello.txt")); it.write(1); it.closeEntry() }
        assertFalse(BackupFile.restore(context, android.net.Uri.fromFile(notABackup)))
    }
}
