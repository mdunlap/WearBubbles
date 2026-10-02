package com.wearbubbles.ui.messages

/**
 * Builds the 1:1 iMessage chat guid for a typed phone number or email.
 * Phone numbers are normalized to E.164 ("(555) 123-4567" -> "+15551234567"); bare
 * 10-digit numbers are assumed to be US/Canada, matching how contacts are matched.
 */
fun recipientChatGuid(input: String): String {
    val trimmed = input.trim()
    val address = if ('@' in trimmed) {
        trimmed.lowercase()
    } else {
        val digits = trimmed.filter { it.isDigit() }
        when {
            digits.isEmpty() -> trimmed
            trimmed.startsWith("+") -> "+$digits"
            digits.length == 10 -> "+1$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            else -> digits
        }
    }
    return "iMessage;-;$address"
}
