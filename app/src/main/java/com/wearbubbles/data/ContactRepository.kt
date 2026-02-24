package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.BlueBubblesApi
import com.wearbubbles.db.ContactDao
import com.wearbubbles.db.entities.ContactEntity

class ContactRepository(
    private val api: BlueBubblesApi,
    private val password: String,
    private val contactDao: ContactDao
) {
    companion object {
        private const val TAG = "ContactRepository"
    }

    // In-memory cache for fast lookups
    private val contactMap = mutableMapOf<String, String>()
    private var loaded = false

    suspend fun loadFromCache() {
        if (loaded) return
        val entities = contactDao.getAllContacts()
        for (entity in entities) {
            contactMap[entity.normalizedAddress] = entity.displayName
        }
        loaded = true
        Log.d(TAG, "Loaded ${contactMap.size} contacts from Room cache")
    }

    suspend fun loadContacts() {
        // Load Room cache first for instant lookups
        if (!loaded) loadFromCache()

        try {
            val response = api.getContacts(password)
            val entities = mutableListOf<ContactEntity>()
            contactMap.clear()

            for (contact in response.data) {
                val name = contact.displayName
                    ?: listOfNotNull(contact.firstName, contact.lastName).joinToString(" ")
                if (name.isBlank()) continue

                contact.phoneNumbers?.forEach { phone ->
                    phone.address?.let { addr ->
                        val normalized = normalizeAddress(addr)
                        contactMap[normalized] = name
                        entities.add(ContactEntity(normalized, name))
                    }
                }
                contact.emails?.forEach { email ->
                    email.address?.let { addr ->
                        val normalized = normalizeAddress(addr)
                        contactMap[normalized] = name
                        entities.add(ContactEntity(normalized, name))
                    }
                }
            }

            // Upsert only — no delete-all, preserves data during brief API failures
            if (entities.isNotEmpty()) {
                contactDao.upsertContacts(entities)
            }
            Log.d(TAG, "Loaded ${contactMap.size} contact addresses from API")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts from API, using cache", e)
        }
    }

    suspend fun getDisplayName(address: String): String {
        val normalized = normalizeAddress(address)
        contactMap[normalized]?.let { return it }
        contactDao.getDisplayName(normalized)?.let { name ->
            contactMap[normalized] = name
            return name
        }
        return formatAddress(address)
    }

    suspend fun getDisplayNameForAddresses(addresses: List<String>): String {
        return addresses.map { getDisplayName(it) }.joinToString(", ")
    }

    // Synchronous version using only in-memory cache (no DB round-trips)
    fun getDisplayNameSync(address: String): String {
        return contactMap[normalizeAddress(address)] ?: formatAddress(address)
    }

    fun getDisplayNameForAddressesSync(addresses: List<String>): String {
        return addresses.joinToString(", ") { getDisplayNameSync(it) }
    }

    private fun normalizeAddress(address: String): String {
        return if (address.contains("@")) {
            address.lowercase().trim()
        } else {
            address.replace(Regex("[^0-9+]"), "").let { cleaned ->
                if (cleaned.length >= 10) {
                    cleaned.takeLast(10)
                } else {
                    cleaned
                }
            }
        }
    }

    private fun formatAddress(address: String): String {
        val digits = address.replace(Regex("[^0-9]"), "")
        return if (digits.length == 10) {
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        } else {
            address
        }
    }
}
