package com.kuswa.clashtimer.ui

import org.junit.Assert.*
import org.junit.Test

class SettingValueTest {
    @Test fun validatesWholeNumbersAndBounds() {
        assertEquals(1, settingValue("1", 1..47))
        assertEquals(47, settingValue(" 47 ", 1..47))
        assertEquals(12, settingValue("12", 1..24))
        for (input in listOf("", " ", "0", "48", "-1", "1.5", "text", "9999999999999")) {
            assertNull("Invalid: $input", settingValue(input, 1..47))
        }
    }
}
