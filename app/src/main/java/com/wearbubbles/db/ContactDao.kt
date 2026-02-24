package com.wearbubbles.db

import androidx.room.*
import com.wearbubbles.db.entities.ContactEntity

@Dao
interface ContactDao {

    @Query("SELECT displayName FROM contacts WHERE normalizedAddress = :address LIMIT 1")
    suspend fun getDisplayName(address: String): String?

    @Upsert
    suspend fun upsertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}
