package com.rishi.kiranaledger.data

/**
 * GST rates are whole numbers in units of 0.001% ("milli-percent"): 2.5% = 2500, 0.125% = 125.
 * Together with money in whole paise, no GST calculation ever uses floating point.
 * Intra-state kirana sales: CGST and SGST are each half of the total GST.
 */
const val PERCENT_100_MILLI = 100_000L

/** The shop's GST registration; decides what a bill shows. */
enum class GstMode {
    /** Charges GST: "Tax Invoice" with GSTIN, HSN and CGST/SGST. */
    REGULAR,

    /** Can't collect tax: "Bill of Supply" with GSTIN, no tax lines. */
    COMPOSITION,

    /** Plain receipt, as in v1.0. Also the mode stored on bills made before v1.1. */
    UNREGISTERED,
}

/** One line's (or one bill's) GST: taxable value plus the two halves of the tax, all in paise. */
data class GstSplit(val taxablePaise: Long, val cgstPaise: Long, val sgstPaise: Long) {
    val taxPaise: Long get() = cgstPaise + sgstPaise
    val totalPaise: Long get() = taxablePaise + taxPaise

    operator fun plus(o: GstSplit) = GstSplit(taxablePaise + o.taxablePaise, cgstPaise + o.cgstPaise, sgstPaise + o.sgstPaise)

    companion object {
        val ZERO = GstSplit(0, 0, 0)
    }
}

/** a / b rounded half up, for non-negative values. */
private fun roundDiv(a: Long, b: Long): Long = (2 * a + b) / (2 * b)

/**
 * Splits [amountPaise] at a CGST rate of [cgstMilli] (SGST is the same).
 * Price excluding GST (what new bills use): the tax is added on top of Rate × Qty.
 * Price including GST (older bills only): the tax is taken out of the amount.
 * CGST and SGST are each worked out on the taxable value at their own rate, so they are always exactly equal,
 * as on a proper GST invoice; any leftover paisa goes into the bill's round off.
 */
fun gstSplit(amountPaise: Long, cgstMilli: Int, priceIncludesTax: Boolean): GstSplit {
    if (cgstMilli <= 0 || amountPaise <= 0) return GstSplit(amountPaise, 0, 0)
    val totalMilli = 2L * cgstMilli
    val taxable = if (priceIncludesTax) roundDiv(amountPaise * PERCENT_100_MILLI, PERCENT_100_MILLI + totalMilli) else amountPaise
    val half = roundDiv(taxable * cgstMilli, PERCENT_100_MILLI)
    return GstSplit(taxable, half, half)
}

/** "5", "0.25", "18" — a total GST percentage for display. */
fun gstPercentLabel(totalMilli: Long): String =
    java.math.BigDecimal.valueOf(totalMilli, 3).stripTrailingZeros().toPlainString()

/** GSTIN: 2-digit state code, PAN, entity number, Z, check character. */
val GSTIN_PATTERN = Regex("^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
