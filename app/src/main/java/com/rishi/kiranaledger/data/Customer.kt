package com.rishi.kiranaledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Who the khata is with. The sign of entries means the same for both (negative = they owe the shop),
 * but the buttons differ: a customer's Credit is negative, a supplier's Purchase on credit is positive.
 */
enum class PartyType { CUSTOMER, SUPPLIER }

/** Sign for the red ("owed more") button: customers' Credit makes them owe us; suppliers' Purchase makes us owe them. */
fun PartyType.redSign(): Long = if (this == PartyType.SUPPLIER) 1 else -1

/**
 * One customer (grahak) or supplier.
 *
 * [balancePaise] is never written by app code: SQL triggers in [LedgerDatabase]
 * keep it equal to the sum of this customer's non-deleted entries.
 * Negative = customer owes the shop (baaki); positive = customer paid in advance.
 */
@Entity(tableName = "customers", indices = [Index("name")])
data class Customer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String? = null,
    @ColumnInfo(name = "balance_paise") val balancePaise: Long = 0,
    /** Brought over from the paper bahi with an opening balance. */
    @ColumnInfo(name = "from_bahi") val fromBahi: Boolean = false,
    /** Photo of this customer's bahi page, stored in app-private storage. */
    @ColumnInfo(name = "bahi_photo_path") val bahiPhotoPath: String? = null,
    /** Day the customer promised to pay, as LocalDate.toEpochDay(); null if none. Added in DB version 2. */
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    /** Customer or supplier, kept in one table. Added in DB version 3. */
    @ColumnInfo(name = "party_type", defaultValue = "CUSTOMER") val partyType: PartyType = PartyType.CUSTOMER,
    /** Buyer details printed on the A4 invoice (v7). */
    val address: String? = null,
    val gstin: String? = null,
    /** Where this customer's goods usually go, if not their own address (v9). Fills "Ship to" on their bills. */
    @ColumnInfo(name = "ship_address") val shipAddress: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)
