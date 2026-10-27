package com.rishi.kiranaledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rishi.kiranaledger.data.Customer
import com.rishi.kiranaledger.data.Item
import com.rishi.kiranaledger.data.LedgerDatabase
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.data.StockReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Deleting customers and stock items, and renaming items — on a fresh in-memory database. */
@RunWith(AndroidJUnit4::class)
class DeleteFlowsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: LedgerDatabase

    @Before fun open() {
        db = LedgerDatabase.inMemoryForTests(context)
    }

    @After fun close() = db.close()

    @Test fun deletedCustomerLeavesListsAndTotals(): Unit = runBlocking {
        val dao = db.ledgerDao()
        val ramesh = Customer(name = "Ramesh")
        val suresh = Customer(name = "Suresh")
        dao.insertCustomerWithOpeningBalance(ramesh, 50000)
        dao.insertCustomerWithOpeningBalance(suresh, 20000)
        assertEquals(70000L, dao.dueSummary(PartyType.CUSTOMER).first().totalDuePaise)

        dao.deleteCustomer(ramesh.id)
        assertEquals(listOf("Suresh"), dao.customers().first().map { it.name })
        assertEquals(emptyList<String>(), dao.searchCustomers("Ram").first().map { it.name })
        assertEquals(20000L, dao.dueSummary(PartyType.CUSTOMER).first().totalDuePaise)
    }

    @Test fun deletingAnItemRemovesItAndItsHistory(): Unit = runBlocking {
        val stock = db.stockDao()
        stock.upsert(Item(nameKey = "atta", name = "Atta", unit = "kg", lastPricePaise = 4500, useCount = 0, trackStock = true))
        stock.move("atta", 10.0, StockReason.PURCHASE)
        stock.deleteItem("atta")
        assertNull(stock.itemOnce("atta"))
        assertEquals(0, stock.moves("atta").first().size)
    }

    @Test fun renamingAnItemKeepsStockAndHistory(): Unit = runBlocking {
        val stock = db.stockDao()
        stock.upsert(Item(nameKey = "aata", name = "Aata", unit = "kg", lastPricePaise = 4500, useCount = 0, trackStock = true))
        stock.move("aata", 10.0, StockReason.PURCHASE)
        val old = stock.itemOnce("aata")!!
        stock.renameAndSave("aata", old.copy(nameKey = "atta", name = "Atta"))
        assertNull(stock.itemOnce("aata"))
        assertEquals(10.0, stock.itemOnce("atta")!!.stockQty, 0.0001)
        assertEquals(1, stock.moves("atta").first().size)
    }

    @Test fun savedShippingAddressIsKeptAndEditable(): Unit = runBlocking {
        val dao = db.ledgerDao()
        val c = Customer(name = "Ramesh", shipAddress = "Godown, Station Road")
        dao.insertCustomer(c)
        assertEquals("Godown, Station Road", dao.customerOnce(c.id)!!.shipAddress)
        dao.updateCustomer(c.id, "Ramesh", null, null, null, null)
        assertNull(dao.customerOnce(c.id)!!.shipAddress)
    }
}
