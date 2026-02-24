package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.BlueBubblesApi

class ContactRepository(
    private val api: BlueBubblesApi,
    private val password: String
) {
    companion object {
        private const val TAG = "ContactRepository"
    }

    // address -> display name
    private val contactMap = mutableMapOf<String, String>()

    suspend fun loadContacts() {
        try {
            val response = api.getContacts(password)
            contactMap.clear()
            for (contact in response.data) {
                val name = contact.displayName
                    ?: listOfNotNull(contact.firstName, contact.lastName).joinToString(" ")
                if (name.isBlank()) continue

                contact.phoneNumbers?.forEach { phone ->
                    phone.address?.let { addr ->
                        contactMap[normalizeAddress(addr)] = name
                    }
                }
                contact.emails?.forEach { email ->
                    email.address?.let { addr ->
                        contactMap[normalizeAddress(addr)] = name
                    }
                }
            }
            Log.d(TAG, "Loaded ${contactMap.size} contact addresses")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts", e)
        }
    }

    fun getDisplayName(address: String): String {
        return contactMap[normalizeAddress(address)] ?: formatAddress(address)
    }

    fun getDisplayNameForAddresses(addresses: List<String>): String {
        return addresses.joinToString(", ") { getDisplayName(it) }
    }

    private fun normalizeAddress(address: String): String {
        // Strip everything except digits and @ for emails
        return if (address.contains("@")) {
            address.lowercase().trim()
        } else {
            address.replace(Regex("[^0-9+]"), "").let { cleaned ->
                // Normalize to last 10 digits for US numbers
                if (cleaned.length >= 10) {
                    cleaned.takeLast(10)
                } else {
                    cleaned
                }
            }
        }
    }

    private fun formatAddress(address: String): String {
        // If it looks like a phone number, format it; otherwise return as-is
        val digits = address.replace(Regex("[^0-9]"), "")
        return if (digits.length == 10) {
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        } else {
            address
        }
    }
}
