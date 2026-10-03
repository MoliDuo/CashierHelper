package pro.xiangyu.cashierhelper.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {
    @Test
    fun `known currencies use their symbol`() {
        assertEquals("¥35.00", AmountFormat.format("35.00", "CNY"))
        assertEquals("US$9.90", AmountFormat.format("9.90", "USD"))
        assertEquals("€12.50", AmountFormat.format("12.50", "eur"))
        assertEquals("JP¥1,500", AmountFormat.format("1500", "JPY"))
    }

    @Test
    fun `the integer part is grouped and the decimals are left exactly as sent`() {
        assertEquals("¥1,234,567.890", AmountFormat.format("1234567.890", "CNY"))
        assertEquals("¥0.10", AmountFormat.format("0.10", "CNY"))
        assertEquals("¥999", AmountFormat.format("999", "CNY"))
    }

    @Test
    fun `negative amounts keep the sign in front`() {
        assertEquals("-¥12.00", AmountFormat.format("-12.00", "CNY"))
        assertEquals("THB -1,200.00", AmountFormat.format("-1200.00", "THB"))
    }

    @Test
    fun `other currencies show their code and a missing one shows only the number`() {
        assertEquals("THB 120.00", AmountFormat.format("120.00", "THB"))
        assertEquals("120.00", AmountFormat.format("120.00", null))
        assertEquals("120.00", AmountFormat.format("120.00", " "))
    }

    @Test
    fun `text that is not a decimal is shown as it came`() {
        assertEquals("CNY about 30", AmountFormat.format("about 30", "CNY"))
        assertEquals("1e3", AmountFormat.format("1e3", null))
    }
}
