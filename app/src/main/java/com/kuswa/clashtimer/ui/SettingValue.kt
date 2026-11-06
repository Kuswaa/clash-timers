package com.kuswa.clashtimer.ui

/** A dialog value is valid only when it is a whole number inside the setting's bounds. */
fun settingValue(input: String, range: IntRange): Int? =
    input.trim().toIntOrNull()?.takeIf { it in range }
