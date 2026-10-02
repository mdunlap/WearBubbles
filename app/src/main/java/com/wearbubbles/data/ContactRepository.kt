package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.BlueBubblesApi
import com.wearbubbles.db.ContactDao
import com.wearbubbles.db.entities.ContactEntity
import java.util.concurrent.ConcurrentHashMap

class ContactRepository(
    private val contactDao: ContactDao
) {
    companion object {
        private const val TAG = "ContactRepository"
    }

    // In-memory cache for fast lookups; shared app-wide, so read from UI and IO threads
    private val contactMap = ConcurrentHashMap<String, String>()
    @Volatile private var loaded = false

    suspend fun loadFromCache() {
        if (loaded) return
        val entities = contactDao.getAllContacts()
        for (entity in entities) {
            contactMap[entity.normalizedAddress] = entity.displayName
        }
        loaded = true
        Log.d(TAG, "Loaded ${contactMap.size} contacts from Room cache")
    }

    suspend fun loadContacts(api: BlueBubblesApi, password: String) {
        // Load Room cache first for instant lookups
        loadFromCache()

        try {
            val response = api.getContacts(password)
            val entities = mutableListOf<ContactEntity>()

            for (contact in response.data) {
                val name = contact.displayName
                    ?: listOfNotNull(contact.firstName, contact.lastName).joinToString(" ")
                if (name.isBlank()) continue

                val addresses = contact.phoneNumbers.orEmpty().mapNotNull { it.address } +
                    contact.emails.orEmpty().mapNotNull { it.address }
                for (addr in addresses) {
                    entities.add(ContactEntity(normalizeAddress(addr), name))
                }
            }

            // Merge rather than clear-and-refill so lookups never see an empty map mid-load
            entities.forEach { contactMap[it.normalizedAddress] = it.displayName }

            // Upsert only — no delete-all, preserves data during brief API failures
            if (entities.isNotEmpty()) {
                contactDao.upsertContacts(entities)
            }
            Log.d(TAG, "Loaded ${entities.size} contact addresses from API")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts from API, using cache", e)
        }
    }

    /** Drops the in-memory cache (the Room table is cleared separately on sign-out). */
    fun clear() {
        contactMap.clear()
        loaded = false
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
