package com.rishi.kiranaledger

import com.rishi.kiranaledger.data.BILL_EDIT_WINDOW_MS
import com.rishi.kiranaledger.data.Bill
import com.rishi.kiranaledger.data.BillStatus
import com.rishi.kiranaledger.data.canEdit
import com.rishi.kiranaledger.data.Item
import com.rishi.kiranaledger.data.PartyType
import com.rishi.kiranaledger.data.redSign
import com.rishi.kiranaledger.data.lineAmountPaise
import com.rishi.kiranaledger.data.roundToRupee
import com.rishi.kiranaledger.ui.billMatches
import com.rishi.kiranaledger.ui.phoneOk
import org.junit.Assert.assertEquals
import org.junit.Test

class BillMathTest {

    @Test fun roundsToNearestRupee() {
        assertEquals(57500L to 50L, roundToRupee(57450)) // ₹574.50 -> ₹575 (+0.50)
        assertEquals(57400L to -49L, roundToRupee(57449)) // ₹574.49 -> ₹574 (−0.49)
        assertEquals(57500L to 0L, roundToRupee(57500)) // already whole
    }

    @Test fun lineAmountsHandleHalfQuantities() {
        assertEquals(3000L, lineAmountPaise(0.5, 6000)) // 0.5 kg × ₹60
        assertEquals(28000L, lineAmountPaise(2.0, 14000)) // 2 L × ₹140
        assertEquals(5000L, lineAmountPaise(1.5, 3333)) // 4999.5 paise rounds to the nearest paisa
    }

    @Test fun statusFollowsPayment() {
        fun bill(customer: String?, total: Long, paid: Long) =
            Bill(billNo = 1, customerId = customer, subtotalPaise = total, roundOffPaise = 0, totalPaise = total, paidPaise = paid)
        assertEquals(BillStatus.CASH_SALE, bill(null, 57500, 57500).status)
        assertEquals(BillStatus.PAID, bill("c", 57500, 57500).status)
        assertEquals(BillStatus.UNPAID, bill("c", 57500, 0).status)
        assertEquals(BillStatus.PARTLY_PAID, bill("c", 57500, 30000).status)
        assertEquals(27500L, bill("c", 57500, 30000).duePaise)
    }

    @Test fun redButtonSignPerPartyType() {
        // Customer Credit: they owe us -> negative. Supplier Purchase on credit: we owe them -> positive.
        assertEquals(-1L, PartyType.CUSTOMER.redSign())
        assertEquals(1L, PartyType.SUPPLIER.redSign())
    }

    @Test fun lowStockOnlyForTrackedItems() {
        val atta = Item(nameKey = "atta", name = "Atta", unit = "kg", lastPricePaise = 6000, useCount = 1, trackStock = true, stockQty = 2.0, minStock = 5.0)
        assertEquals(true, atta.isLow) // 2 kg left, warn at 5
        assertEquals(false, atta.copy(stockQty = 10.0).isLow)
        assertEquals(false, atta.copy(trackStock = false).isLow) // untracked items never warn
    }

    @Test fun billsCanBeEditedFor24Hours() {
        val made = 1_000_000_000_000L
        val bill = Bill(billNo = 1, subtotalPaise = 0, roundOffPaise = 0, totalPaise = 0, paidPaise = 0, createdAt = made)
        assertEquals(true, bill.canEdit(now = made + 60_000)) // a minute later
        assertEquals(true, bill.canEdit(now = made + BILL_EDIT_WINDOW_MS - 1))
        assertEquals(false, bill.canEdit(now = made + BILL_EDIT_WINDOW_MS)) // a full day later
        assertEquals(false, bill.copy(deleted = true).canEdit(now = made + 60_000))
    }

    @Test fun phoneNumbersMustBeTenDigits() {
        assertEquals(true, phoneOk("")) // optional
        assertEquals(true, phoneOk("98765 43210"))
        assertEquals(true, phoneOk("+91 9876543210"))
        assertEquals(true, phoneOk("09876543210"))
        assertEquals(false, phoneOk("98765"))
        assertEquals(false, phoneOk("98765432101"))
    }

    @Test fun billSearchMatchesNamePhoneAndNumber() {
        val bill = Bill(billNo = 12, partyName = "Ramesh Kumar", partyPhone = "9876543210", subtotalPaise = 0, roundOffPaise = 0, totalPaise = 0, paidPaise = 0)
        assertEquals(true, billMatches(bill, "ramesh"))
        assertEquals(true, billMatches(bill, "98765"))
        assertEquals(true, billMatches(bill, "#0012"))
        assertEquals(true, billMatches(bill, "12"))
        assertEquals(false, billMatches(bill, "13"))
        assertEquals(false, billMatches(bill, "Suresh"))
        assertEquals(true, billMatches(bill, ""))
    }

    @Test fun billNumberLabel() {
        assertEquals("#0012", Bill(billNo = 12, subtotalPaise = 0, roundOffPaise = 0, totalPaise = 0, paidPaise = 0).numberLabel)
    }

    @Test fun moneyParsingAndFormatting() {
        assertEquals(1250L, parseRupeesToPaise("12.50"))
        assertEquals(1250L, parseRupeesToPaise("12.5"))
        assertEquals(null, parseRupeesToPaise("abc"))
        assertEquals("12,400", formatRupees(1240000))
        assertEquals("450.50", formatRupees(-45050))
        assertEquals("0.5", formatQuantity(0.5))
        assertEquals("2", formatQuantity(2.0))
    }
}
