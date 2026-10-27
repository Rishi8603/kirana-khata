package com.rishi.kiranaledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT COALESCE(MAX(bill_no), 0) + 1 FROM bills")
    suspend fun nextBillNo(): Int

    @Query("SELECT * FROM bills WHERE deleted = 0 ORDER BY bill_no DESC")
    fun bills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    fun bill(id: String): Flow<Bill?>

    @Query("SELECT * FROM bill_items WHERE bill_id = :billId ORDER BY position")
    fun items(billId: String): Flow<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE bill_id = :billId ORDER BY position")
    suspend fun itemsOnce(billId: String): List<BillItem>

    /**
     * Remembered items for the bill's item box: name starts with (or has a word starting with) [text],
     * product nature contains it, or — when an HSN code is typed — every product under that HSN.
     */
    @Query(
        "SELECT * FROM items WHERE name LIKE :text || '%' OR name LIKE '% ' || :text || '%' " +
            "OR product_nature LIKE '%' || :text || '%' OR hsn_code LIKE :text || '%' " +
            "ORDER BY (hsn_code LIKE :text || '%') DESC, use_count DESC, updated_at DESC LIMIT 12",
    )
    suspend fun suggestItems(text: String): List<Item>

    @Insert
    suspend fun insertBill(bill: Bill)

    @Insert
    suspend fun insertBillItems(items: List<BillItem>)

    @Insert
    suspend fun insertEntry(entry: Entry)

    @Query("SELECT * FROM items WHERE name_key = :key")
    suspend fun item(key: String): Item?

    @Upsert
    suspend fun upsertItem(item: Item)

    /**
     * Saves the bill, remembers its items for next time, and — for a khata customer —
     * adds a Credit entry for the total and a Payment entry for what was paid now.
     * Everything succeeds or fails together.
     */
    @Transaction
    suspend fun saveBill(bill: Bill, items: List<BillItem>, gstLinks: List<GstLink?>) {
        insertBill(bill)
        applyBill(bill, items, gstLinks, countUse = true)
    }

    /**
     * Replaces a bill's contents (same bill number), e.g. to fix a mistake within a day.
     * The old version's khata entries and stock are undone first, then the new version is applied —
     * all in one transaction, so balances and stock can never end up half-changed.
     */
    @Transaction
    suspend fun updateBill(bill: Bill, items: List<BillItem>, gstLinks: List<GstLink?>, now: Long = System.currentTimeMillis()) {
        for (line in itemsOnce(bill.id)) {
            val key = itemKey(line.name)
            if (item(key)?.trackStock == true) {
                changeQty(key, line.quantity)
                insertMove(StockMove(itemKey = key, quantity = line.quantity, reason = StockReason.BILL_EDITED, billId = bill.id))
            }
        }
        markBillEntriesDeleted(bill.id, now)
        deleteBillItems(bill.id)
        updateBillRow(bill.copy(updatedAt = now))
        applyBill(bill, items, gstLinks, countUse = false)
    }

    @Update
    suspend fun updateBillRow(bill: Bill)

    @Query("DELETE FROM bill_items WHERE bill_id = :billId")
    suspend fun deleteBillItems(billId: String)

    /** Saves the lines, remembers products (and their GST link), takes stock out, and adds the khata entries. */
    suspend fun applyBill(bill: Bill, items: List<BillItem>, gstLinks: List<GstLink?>, countUse: Boolean) {
        insertBillItems(items)
        for ((i, line) in items.withIndex()) {
            val key = itemKey(line.name)
            val known = item(key)
            // copy() keeps the stock fields of a known item; only price, unit and usage change.
            val uses = (known?.useCount ?: 0) + if (countUse) 1 else 0
            val base = known?.copy(unit = line.unit, lastPricePaise = line.pricePaise, useCount = uses, updatedAt = System.currentTimeMillis())
                ?: Item(nameKey = key, name = line.name.trim(), unit = line.unit, lastPricePaise = line.pricePaise, useCount = 1)
            // A GST link chosen on the bill is saved to the product; a line without one never wipes an existing link.
            val gst = gstLinks.getOrNull(i)
            upsertItem(
                if (gst == null) base else base.copy(
                    hsnCode = gst.choice.hsnCode,
                    gstSchedule = gst.choice.schedule,
                    gstEntryId = gst.choice.entryId,
                    productNature = gst.choice.nature,
                    cgstMilli = gst.cgstMilli,
                    priceIncludesTax = false,
                ),
            )
            if (known?.trackStock == true) {
                changeQty(key, -line.quantity)
                insertMove(StockMove(itemKey = key, quantity = -line.quantity, reason = StockReason.SALE, billId = bill.id))
            }
        }
        val customerId = bill.customerId ?: return
        val note = "Bill ${bill.numberLabel}"
        insertEntry(
            Entry(customerId = customerId, amountPaise = -bill.totalPaise, note = note, entryDate = bill.billDate, billId = bill.id),
        )
        if (bill.paidPaise > 0) {
            insertEntry(
                Entry(
                    customerId = customerId,
                    amountPaise = bill.paidPaise,
                    note = note,
                    entryDate = bill.billDate,
                    billId = bill.id,
                    // One millisecond later so the payment lists above the credit.
                    createdAt = System.currentTimeMillis() + 1,
                ),
            )
        }
    }

    /** Soft-deletes the bill and its khata entries; the balance triggers fix the customer's balance. */
    @Transaction
    suspend fun deleteBill(id: String, now: Long = System.currentTimeMillis()) {
        markBillDeleted(id, now)
        markBillEntriesDeleted(id, now)
        // Put back the stock this bill took out.
        for (line in itemsOnce(id)) {
            val key = itemKey(line.name)
            if (item(key)?.trackStock == true) {
                changeQty(key, line.quantity)
                insertMove(StockMove(itemKey = key, quantity = line.quantity, reason = StockReason.BILL_DELETED, billId = id))
            }
        }
    }

    @Query("UPDATE items SET stock_qty = stock_qty + :delta WHERE name_key = :key")
    suspend fun changeQty(key: String, delta: Double)

    @Insert
    suspend fun insertMove(move: StockMove)

    @Query("UPDATE bills SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun markBillDeleted(id: String, now: Long)

    @Query("UPDATE entries SET deleted = 1, updated_at = :now WHERE bill_id = :billId")
    suspend fun markBillEntriesDeleted(billId: String, now: Long)
}
