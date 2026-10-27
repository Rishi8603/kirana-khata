package com.rishi.kiranaledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    /**
     * Items for the Stock tab. Typing an HSN code (e.g. "1905") lists every product saved under it, grouped by HSN;
     * otherwise name or GST product nature match, with low-stock items first.
     */
    @Query(
        "SELECT * FROM items WHERE (:text = '' OR name LIKE '%' || :text || '%' " +
            "OR product_nature LIKE '%' || :text || '%' OR hsn_code LIKE :text || '%') " +
            "ORDER BY (hsn_code LIKE :text || '%') DESC, " +
            "CASE WHEN hsn_code LIKE :text || '%' THEN hsn_code END, " +
            "(track_stock = 1 AND stock_qty <= min_stock) DESC, name COLLATE NOCASE",
    )
    fun items(text: String): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM items WHERE track_stock = 1 AND stock_qty <= min_stock")
    fun lowStockCount(): Flow<Int>

    @Query("SELECT * FROM items WHERE name_key = :key")
    fun item(key: String): Flow<Item?>

    @Query("SELECT * FROM items WHERE name_key = :key")
    suspend fun itemOnce(key: String): Item?

    @Query("SELECT * FROM stock_moves WHERE item_key = :key ORDER BY created_at DESC LIMIT 50")
    fun moves(key: String): Flow<List<StockMove>>

    @Upsert
    suspend fun upsert(item: Item)

    @Insert
    suspend fun insertMove(move: StockMove)

    @Query("UPDATE items SET stock_qty = stock_qty + :delta, updated_at = :now WHERE name_key = :key")
    suspend fun changeQty(key: String, delta: Double, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM items WHERE name_key = :key")
    suspend fun deleteItemRow(key: String)

    @Query("DELETE FROM stock_moves WHERE item_key = :key")
    suspend fun deleteMoves(key: String)

    @Query("UPDATE stock_moves SET item_key = :newKey WHERE item_key = :oldKey")
    suspend fun moveHistory(oldKey: String, newKey: String)

    /** Remove an item from the Stock list (and its stock history). Old bills keep their own copy of the name. */
    @Transaction
    suspend fun deleteItem(key: String) {
        deleteMoves(key)
        deleteItemRow(key)
    }

    /** Save an item under a corrected name: the item, its stock and its history move to the new name. */
    @Transaction
    suspend fun renameAndSave(oldKey: String, item: Item) {
        upsert(item)
        if (item.nameKey != oldKey) {
            moveHistory(oldKey, item.nameKey)
            deleteItemRow(oldKey)
        }
    }

    /** Stock in (+) or out (−) with a log entry, together. */
    @Transaction
    suspend fun move(key: String, delta: Double, reason: StockReason, billId: String? = null) {
        changeQty(key, delta)
        insertMove(StockMove(itemKey = key, quantity = delta, reason = reason, billId = billId))
    }
}
