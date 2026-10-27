package com.rishi.kiranaledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Formats paise as Indian-style rupees without the sign: 1240000 -> "12,400", 45050 -> "450.50". */
fun formatRupees(paise: Long): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = if (abs(paise) % 100 == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return format.format(abs(paise) / 100.0)
}

/** "12.50" -> 1250 paise; null if it isn't a number. */
fun parseRupeesToPaise(text: String): Long? =
    text.trim().toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

/** 966.00-style amount without grouping, for invoice columns: 96600 -> "966.00". */
fun formatPlainRupees(paise: Long): String {
    val p = abs(paise)
    return (if (paise < 0) "-" else "") + "${p / 100}." + (p % 100).toString().padStart(2, '0')
}

private val ONES = arrayOf(
    "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
    "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
)
private val TENS = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

private fun below100(n: Int): String = if (n < 20) ONES[n] else TENS[n / 10] + if (n % 10 > 0) " " + ONES[n % 10] else ""

private fun below1000(n: Int): String {
    val rest = n % 100
    if (n < 100) return below100(n)
    return ONES[n / 100] + " Hundred" + if (rest > 0) " and " + below100(rest) else ""
}

/** Indian-system words: 1,23,45,678 -> "One Crore Twenty Three Lakh Forty Five Thousand Six Hundred and Seventy Eight". */
fun numberInWords(n: Long): String {
    if (n == 0L) return "Zero"
    val parts = mutableListOf<String>()
    val crore = n / 1_00_00_000
    if (crore > 0) parts += numberInWords(crore) + " Crore"
    val lakh = ((n / 1_00_000) % 100).toInt()
    if (lakh > 0) parts += below100(lakh) + " Lakh"
    val thousand = ((n / 1000) % 100).toInt()
    if (thousand > 0) parts += below100(thousand) + " Thousand"
    val rest = (n % 1000).toInt()
    if (rest > 0) parts += below1000(rest)
    return parts.joinToString(" ")
}

/** "Rs. Eight Thousand Five Hundred and Seventy only", with paise when there are any. */
fun rupeesInWords(paise: Long): String {
    val p = abs(paise)
    val words = "Rs. " + numberInWords(p / 100)
    return words + (if (p % 100 > 0) " and " + below100((p % 100).toInt()) + " Paise" else "") + " only"
}

/** 2.0 -> "2", 0.5 -> "0.5". */
fun formatQuantity(quantity: Double): String = BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()
