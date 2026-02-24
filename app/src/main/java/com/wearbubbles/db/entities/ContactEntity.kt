package com.wearbubbles.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val normalizedAddress: String,
    val displayName: String
)
