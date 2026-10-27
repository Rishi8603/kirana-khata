package com.rishi.kiranaledger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/** One shop expense (rent, bijli, transport...). Added in DB version 3. */
@Entity(tableName = "expenses", indices = [Index("expense_date")])
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "expense_date") val expenseDate: Long = LocalDate.now().toEpochDay(),
    val category: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "payment_mode") val paymentMode: PaymentMode = PaymentMode.CASH,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

data class CategoryTotal(val category: String, val totalPaise: Long)

@Dao
interface ExpenseDao {

    /** Expenses between two epoch days (inclusive), newest first. */
    @Query("SELECT * FROM expenses WHERE deleted = 0 AND expense_date BETWEEN :from AND :to ORDER BY expense_date DESC, created_at DESC")
    fun expenses(from: Long, to: Long): Flow<List<Expense>>

    @Query(
        "SELECT category, SUM(amount_paise) AS totalPaise FROM expenses " +
            "WHERE deleted = 0 AND expense_date BETWEEN :from AND :to GROUP BY category ORDER BY totalPaise DESC",
    )
    fun categoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    /** Categories the shopkeeper has used before, most used first, to offer them again. */
    @Query("SELECT category FROM expenses WHERE deleted = 0 GROUP BY category ORDER BY COUNT(*) DESC LIMIT 12")
    suspend fun usedCategories(): List<String>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: String): Expense?

    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Query("UPDATE expenses SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long = System.currentTimeMillis())
}
