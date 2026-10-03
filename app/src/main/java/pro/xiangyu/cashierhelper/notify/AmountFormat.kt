package pro.xiangyu.cashierhelper.notify

/**
 * Formats the decimal strings Cashier returns. Amounts are never converted to a
 * float, so what is shown is exactly what the server sent.
 */
object AmountFormat {
    private val decimal = Regex("^(-?)(\\d+)(\\.\\d+)?$")

    private val symbols = mapOf(
        "CNY" to "¥",
        "JPY" to "JP¥",
        "USD" to "US$",
        "EUR" to "€",
        "GBP" to "£",
        "HKD" to "HK$",
        "TWD" to "NT$",
        "KRW" to "₩",
    )

    fun format(amount: String, currency: String?): String {
        val match = decimal.matchEntire(amount.trim()) ?: return listOfNotNull(currency, amount.trim()).joinToString(" ")
        val (sign, integer, fraction) = match.destructured
        val grouped = integer.reversed().chunked(3).joinToString(",").reversed()
        val number = "$grouped$fraction"
        val symbol = currency?.let { symbols[it.uppercase()] }
        return when {
            symbol != null -> "$sign$symbol$number"
            currency.isNullOrBlank() -> "$sign$number"
            else -> "$currency $sign$number"
        }
    }
}
