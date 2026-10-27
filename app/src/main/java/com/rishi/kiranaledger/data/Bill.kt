package com.rishi.kiranaledger.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToLong

/**
 * One receipt. The customer's name and phone are copied in (a snapshot), so an old bill
 * never changes if the customer is renamed later. [customerId] is null for a walk-in cash sale.
 */
@Entity(tableName = "bills", indices = [Index(value = ["bill_no"], unique = true), Index("customer_id")])
data class Bill(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "bill_no") val billNo: Int,
    @ColumnInfo(name = "customer_id") val customerId: String? = null,
    @ColumnInfo(name = "party_name") val partyName: String? = null,
    @ColumnInfo(name = "party_phone") val partyPhone: String? = null,
    // Buyer address and GSTIN copied in when the bill is made (v7), like the name and phone.
    @ColumnInfo(name = "party_address") val partyAddress: String? = null,
    @ColumnInfo(name = "party_gstin") val partyGstin: String? = null,
    // Ship-to (v8): null [shipName] means "same as buyer".
    @ColumnInfo(name = "ship_name") val shipName: String? = null,
    @ColumnInfo(name = "ship_address") val shipAddress: String? = null,
    @ColumnInfo(name = "ship_phone") val shipPhone: String? = null,
    @ColumnInfo(name = "bill_date") val billDate: Long = LocalDate.now().toEpochDay(),
    @ColumnInfo(name = "subtotal_paise") val subtotalPaise: Long,
    @ColumnInfo(name = "round_off_paise") val roundOffPaise: Long,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "paid_paise") val paidPaise: Long,
    @ColumnInfo(name = "payment_mode") val paymentMode: PaymentMode = PaymentMode.CASH,
    /** The shop's GST registration when this bill was made (v6); old bills are plain receipts. */
    @ColumnInfo(name = "gst_mode", defaultValue = "UNREGISTERED") val gstMode: GstMode = GstMode.UNREGISTERED,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val isWalkIn: Boolean get() = customerId == null
    val duePaise: Long get() = totalPaise - paidPaise
    val status: BillStatus
        get() = when {
            isWalkIn -> BillStatus.CASH_SALE
            paidPaise >= totalPaise -> BillStatus.PAID
            paidPaise == 0L -> BillStatus.UNPAID
            else -> BillStatus.PARTLY_PAID
        }
    val numberLabel: String get() = "#%04d".format(billNo)
    val shipsElsewhere: Boolean get() = !shipName.isNullOrBlank()
}

enum class PaymentMode { CASH, ONLINE }

enum class BillStatus { PAID, PARTLY_PAID, UNPAID, CASH_SALE }

/** One line on a bill. Item name and price are copied in, so later price changes don't alter old bills. */
@Entity(
    tableName = "bill_items",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["bill_id"])],
    indices = [Index("bill_id")],
)
data class BillItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "bill_id") val billId: String,
    val position: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    @ColumnInfo(name = "price_paise") val pricePaise: Long,
    // GST snapshot (v6), so old bills never change if a rate changes. Zero rate = no GST on this line.
    @ColumnInfo(name = "hsn_code") val hsnCode: String? = null,
    @ColumnInfo(name = "cgst_milli", defaultValue = "0") val cgstMilli: Int = 0,
    @ColumnInfo(name = "price_includes_tax", defaultValue = "1") val priceIncludesTax: Boolean = true,
    @ColumnInfo(name = "taxable_paise", defaultValue = "0") val taxablePaise: Long = 0,
    @ColumnInfo(name = "cgst_paise", defaultValue = "0") val cgstPaise: Long = 0,
    @ColumnInfo(name = "sgst_paise", defaultValue = "0") val sgstPaise: Long = 0,
) {
    /** Quantity × price. */
    val amountPaise: Long get() = lineAmountPaise(quantity, pricePaise)

    /** What the customer pays for this line: amount, plus tax when GST was charged on top. */
    val totalPaise: Long get() = if (cgstMilli > 0) taxablePaise + cgstPaise + sgstPaise else amountPaise

    val gst: GstSplit get() = if (cgstMilli > 0) GstSplit(taxablePaise, cgstPaise, sgstPaise) else GstSplit(amountPaise, 0, 0)
}

fun lineAmountPaise(quantity: Double, pricePaise: Long): Long = (quantity * pricePaise).roundToLong()

/** Total rounded to the nearest rupee, as Khatabook's bills do. Returns (total, roundOff). */
fun roundToRupee(subtotalPaise: Long): Pair<Long, Long> {
    val total = ((subtotalPaise + 50) / 100) * 100
    return total to (total - subtotalPaise)
}

/**
 * Remembered items, used to suggest name, unit and last price on the next bill.
 * Keyed by the lower-cased name so "Atta" and "atta" are the same item.
 */
@Entity(tableName = "items", indices = [Index("hsn_code")])
data class Item(
    @PrimaryKey @ColumnInfo(name = "name_key") val nameKey: String,
    val name: String,
    val unit: String,
    @ColumnInfo(name = "last_price_paise") val lastPricePaise: Long,
    @ColumnInfo(name = "use_count") val useCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    // Stock fields, added in DB version 4.
    /** Only items with tracking on get their stock reduced by bills. */
    @ColumnInfo(name = "track_stock", defaultValue = "0") val trackStock: Boolean = false,
    @ColumnInfo(name = "stock_qty", defaultValue = "0") val stockQty: Double = 0.0,
    /** Warn when stock falls to this level or below. */
    @ColumnInfo(name = "min_stock", defaultValue = "0") val minStock: Double = 0.0,
    @ColumnInfo(name = "purchase_price_paise", defaultValue = "0") val purchasePricePaise: Long = 0,
    // GST link (v6): the CBIC row and HSN code this product belongs to. Many products can share one HSN.
    @ColumnInfo(name = "hsn_code") val hsnCode: String? = null,
    @ColumnInfo(name = "gst_schedule") val gstSchedule: String? = null,
    @ColumnInfo(name = "gst_entry_id") val gstEntryId: String? = null,
    @ColumnInfo(name = "product_nature") val productNature: String? = null,
    @ColumnInfo(name = "cgst_milli", defaultValue = "0") val cgstMilli: Int = 0,
    @ColumnInfo(name = "price_includes_tax", defaultValue = "1") val priceIncludesTax: Boolean = true,
) {
    val isLow: Boolean get() = trackStock && stockQty <= minStock
}

/** Why stock changed. */
enum class StockReason { SALE, PURCHASE, ADJUST, BILL_DELETED, BILL_EDITED }

/** A bill can be edited for this long after it was made. */
const val BILL_EDIT_WINDOW_MS = 24L * 60 * 60 * 1000

fun Bill.canEdit(now: Long = System.currentTimeMillis()): Boolean = !deleted && now - createdAt < BILL_EDIT_WINDOW_MS

/** One stock movement: + for stock in, − for stock out. */
@Entity(tableName = "stock_moves", indices = [Index("item_key")])
data class StockMove(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "item_key") val itemKey: String,
    val quantity: Double,
    val reason: StockReason,
    @ColumnInfo(name = "bill_id") val billId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

fun itemKey(name: String): String = name.trim().lowercase()

/** Units offered on the item form. */
val UNITS = listOf("pcs", "kg", "g", "L", "ml", "packet", "dozen")
