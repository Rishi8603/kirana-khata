package com.rishi.kiranaledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * One ledger entry. The sign of [amountPaise] says which way money moved:
 *   negative = "Diya"  (goods given on credit, customer now owes more)
 *   positive = "Liya"  (customer paid)
 * Money is stored in whole paise (₹450 = 45000) so totals never drift.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
        ),
    ],
    indices = [Index("customer_id"), Index("bill_id")],
)
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "customer_id") val customerId: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val note: String? = null,
    /** The day the entry belongs to, as LocalDate.toEpochDay(). */
    @ColumnInfo(name = "entry_date") val entryDate: Long = LocalDate.now().toEpochDay(),
    /** The opening balance copied from the paper bahi. */
    @ColumnInfo(name = "from_bahi") val fromBahi: Boolean = false,
    /** Set when this entry was created by a bill, so deleting the bill removes it too. */
    @ColumnInfo(name = "bill_id") val billId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
