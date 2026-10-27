package com.rishi.kiranaledger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * A manual galla entry for cash that no bill, khata entry or expense covers
 * (e.g. money taken home, change added). Positive = cash in, negative = cash out. Added in DB version 5.
 */
@Entity(tableName = "cash_entries", indices = [Index("entry_date")])
data class CashEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "entry_date") val entryDate: Long = LocalDate.now().toEpochDay(),
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "payment_mode") val paymentMode: PaymentMode = PaymentMode.CASH,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** Everything that happened in a date range, gathered from bills, khata entries, expenses and manual galla entries. */
data class PeriodSummary(
    val salesPaise: Long,
    val billCount: Int,
    val billPaidPaise: Long,
    val creditGivenPaise: Long,
    val paymentsReceivedPaise: Long,
    val purchasesPaise: Long,
    val supplierPaymentsPaise: Long,
    val expensesPaise: Long,
    val manualInPaise: Long,
    val manualOutPaise: Long,
) {
    /** Money that came into the galla. */
    val cashInPaise: Long get() = billPaidPaise + paymentsReceivedPaise + manualInPaise

    /** Money that went out of the galla. */
    val cashOutPaise: Long get() = supplierPaymentsPaise + expensesPaise + manualOutPaise
    val netPaise: Long get() = cashInPaise - cashOutPaise
}

@Dao
interface ReportDao {

    // Khata entries made by a bill (bill_id set) are counted through the bill itself,
    // and opening balances copied from the bahi are not activity in this period.
    @Query(
        "SELECT " +
            "(SELECT COALESCE(SUM(total_paise), 0) FROM bills WHERE deleted = 0 AND bill_date BETWEEN :from AND :to) AS salesPaise, " +
            "(SELECT COUNT(*) FROM bills WHERE deleted = 0 AND bill_date BETWEEN :from AND :to) AS billCount, " +
            "(SELECT COALESCE(SUM(paid_paise), 0) FROM bills WHERE deleted = 0 AND bill_date BETWEEN :from AND :to) AS billPaidPaise, " +
            "(SELECT COALESCE(-SUM(e.amount_paise), 0) FROM entries e JOIN customers c ON c.id = e.customer_id " +
            "  WHERE e.deleted = 0 AND e.from_bahi = 0 AND c.party_type = 'CUSTOMER' AND e.amount_paise < 0 " +
            "  AND e.entry_date BETWEEN :from AND :to) AS creditGivenPaise, " +
            "(SELECT COALESCE(SUM(e.amount_paise), 0) FROM entries e JOIN customers c ON c.id = e.customer_id " +
            "  WHERE e.deleted = 0 AND e.from_bahi = 0 AND e.bill_id IS NULL AND c.party_type = 'CUSTOMER' AND e.amount_paise > 0 " +
            "  AND e.entry_date BETWEEN :from AND :to) AS paymentsReceivedPaise, " +
            "(SELECT COALESCE(SUM(e.amount_paise), 0) FROM entries e JOIN customers c ON c.id = e.customer_id " +
            "  WHERE e.deleted = 0 AND e.from_bahi = 0 AND c.party_type = 'SUPPLIER' AND e.amount_paise > 0 " +
            "  AND e.entry_date BETWEEN :from AND :to) AS purchasesPaise, " +
            "(SELECT COALESCE(-SUM(e.amount_paise), 0) FROM entries e JOIN customers c ON c.id = e.customer_id " +
            "  WHERE e.deleted = 0 AND e.from_bahi = 0 AND c.party_type = 'SUPPLIER' AND e.amount_paise < 0 " +
            "  AND e.entry_date BETWEEN :from AND :to) AS supplierPaymentsPaise, " +
            "(SELECT COALESCE(SUM(amount_paise), 0) FROM expenses WHERE deleted = 0 AND expense_date BETWEEN :from AND :to) AS expensesPaise, " +
            "(SELECT COALESCE(SUM(amount_paise), 0) FROM cash_entries WHERE deleted = 0 AND amount_paise > 0 AND entry_date BETWEEN :from AND :to) AS manualInPaise, " +
            "(SELECT COALESCE(-SUM(amount_paise), 0) FROM cash_entries WHERE deleted = 0 AND amount_paise < 0 AND entry_date BETWEEN :from AND :to) AS manualOutPaise",
    )
    fun summary(from: Long, to: Long): Flow<PeriodSummary>

    /** Customers who owe the most, for the report. */
    @Query("SELECT * FROM customers WHERE deleted = 0 AND party_type = 'CUSTOMER' AND balance_paise < 0 ORDER BY balance_paise ASC LIMIT 5")
    fun topDues(): Flow<List<Customer>>

    @Query("SELECT * FROM cash_entries WHERE deleted = 0 AND entry_date = :day ORDER BY created_at DESC")
    fun cashEntries(day: Long): Flow<List<CashEntry>>

    @Insert
    suspend fun insertCash(entry: CashEntry)

    @Query("UPDATE cash_entries SET deleted = 1 WHERE id = :id")
    suspend fun deleteCash(id: String)
}
