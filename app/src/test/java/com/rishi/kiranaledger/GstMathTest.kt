package com.rishi.kiranaledger

import com.rishi.kiranaledger.data.GSTIN_PATTERN
import com.rishi.kiranaledger.data.GstChoice
import com.rishi.kiranaledger.data.GstLink
import com.rishi.kiranaledger.data.GstMode
import com.rishi.kiranaledger.data.GstSplit
import com.rishi.kiranaledger.data.gstPercentLabel
import com.rishi.kiranaledger.data.gstSplit
import com.rishi.kiranaledger.data.roundToRupee
import com.rishi.kiranaledger.ui.DraftLine
import com.rishi.kiranaledger.ui.lineSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** GST maths must be exact to the paisa: rates in 0.001%, money in paise, never floating point. */
class GstMathTest {

    @Test fun billLineIsRateTimesQtyWithGstOnTop() {
        // Even a product saved earlier as "price includes GST" is billed as Rate × Qty, never reduced.
        val oldInclusive = GstLink(GstChoice("I-84", "I", "1507", "Soya-bean oil", 2500), 2500, priceIncludesTax = true)
        val lines = listOf(
            DraftLine("Mustard Oil", 5.0, "kg", 18000, oldInclusive), // 5 × ₹180, 5%
            DraftLine("Detergent", 2.0, "pcs", 9500, oldInclusive.copy(cgstMilli = 9000)), // 2 × ₹95, 18%
            DraftLine("Masoor Dal", 10.5, "kg", 9200), // no GST
        )
        val splits = lines.map { lineSplit(it, GstMode.REGULAR) }
        assertEquals(listOf(90000L, 19000L, 96600L), splits.map { it.taxablePaise }) // each = Rate × Qty
        val gross = splits.sumOf { it.taxablePaise }
        val tax = splits.sumOf { it.cgstPaise + it.sgstPaise }
        assertEquals(205600L, gross) // ₹2,056.00
        assertEquals(4500L + 3420L, tax) // 5% of 900 + 18% of 190
        val (grand, roundOff) = roundToRupee(gross + tax)
        assertEquals(grand, gross + tax + roundOff) // Gross + SGST + CGST + Round off = Grand total
        assertEquals(213500L, grand)
    }

    @Test fun inclusivePriceTakesTaxOut() {
        assertEquals(GstSplit(10000, 250, 250), gstSplit(10500, 2500, priceIncludesTax = true)) // ₹105 at 5%
        assertEquals(GstSplit(9524, 238, 238), gstSplit(10000, 2500, priceIncludesTax = true)) // ₹100 at 5%
        assertEquals(GstSplit(847, 76, 76), gstSplit(999, 9000, priceIncludesTax = true)) // ₹9.99 at 18%
    }

    @Test fun cgstAndSgstAreAlwaysEqual() {
        // ₹140.50 + 5%: 7.025 is split as CGST 3.51 + SGST 3.51 (never 3.52 / 3.51).
        assertEquals(GstSplit(14050, 351, 351), gstSplit(14050, 2500, priceIncludesTax = false))
        assertEquals(GstSplit(9533, 238, 238), gstSplit(10010, 2500, priceIncludesTax = true))
    }

    @Test fun exclusivePriceAddsTaxOnTop() {
        val s = gstSplit(10000, 9000, priceIncludesTax = false) // ₹100 + 18%
        assertEquals(GstSplit(10000, 900, 900), s)
        assertEquals(11800L, s.totalPaise)
    }

    @Test fun fractionalRatesStayExact() {
        // Schedule V: CGST 0.125% + SGST 0.125% = 0.25% on ₹1,000 inclusive.
        assertEquals(GstSplit(99751, 125, 125), gstSplit(100000, 125, priceIncludesTax = true))
    }

    @Test fun zeroRateMeansNoTax() {
        assertEquals(GstSplit(12345, 0, 0), gstSplit(12345, 0, priceIncludesTax = true))
        assertEquals(GstSplit(12345, 0, 0), gstSplit(12345, 0, priceIncludesTax = false))
    }

    @Test fun cgstEqualsSgstForEveryAmountAndRate() {
        val rates = listOf(125, 750, 1500, 2500, 9000, 14000, 20000)
        for (rate in rates) for (amount in (1L..5000L step 7) + listOf(99_99_999L)) {
            val exc = gstSplit(amount, rate, false)
            assertEquals(amount, exc.taxablePaise) // Rate × Qty is never reduced
            assertEquals(exc.cgstPaise, exc.sgstPaise)
            val inc = gstSplit(amount, rate, true)
            assertEquals(inc.cgstPaise, inc.sgstPaise)
            assertTrue(abs(inc.totalPaise - amount) <= 1) // at most a paisa, absorbed by round off
        }
    }

    @Test fun amountInWordsIndianSystem() {
        assertEquals("Rs. Eight Thousand Five Hundred and Seventy only", rupeesInWords(857000))
        assertEquals("Rs. One Crore Twenty Three Lakh Forty Five Thousand Six Hundred and Seventy Eight only", rupeesInWords(1234567800))
        assertEquals("Rs. Two Hundred and Seventeen and Fifty Paise only", rupeesInWords(21750))
        assertEquals("Rs. Zero only", rupeesInWords(0))
        assertEquals("966.00", formatPlainRupees(96600))
        assertEquals("0.10", formatPlainRupees(10))
    }

    @Test fun labelsAndGstin() {
        assertEquals("5", gstPercentLabel(5000))
        assertEquals("0.25", gstPercentLabel(250))
        assertEquals("18", gstPercentLabel(18000))
        assertTrue(GSTIN_PATTERN.matches("09ABCDE1234F1Z5"))
        assertFalse(GSTIN_PATTERN.matches("09ABCDE1234F1X5"))
        assertFalse(GSTIN_PATTERN.matches("09ABCDE1234F1Z"))
    }
}
