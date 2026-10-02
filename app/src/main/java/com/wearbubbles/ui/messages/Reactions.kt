package com.wearbubbles.ui.messages

import com.wearbubbles.db.entities.MessageEntity

// BlueBubbles reports tapbacks as Apple's numeric codes or, on newer servers, by name
private val LOVE_TYPES = setOf("2000", "love")
private val REMOVE_LOVE_TYPES = setOf("3000", "-love")

/**
 * Guids of messages that currently carry a love reaction. Each sender's most recent
 * love/un-love on a message wins, so removing a heart clears it.
 * associatedMessageGuid format: "p:0/ORIGINAL_GUID"
 */
fun lovedMessageGuids(messages: List<MessageEntity>): Set<String> =
    messages
        .filter { it.associatedMessageType in LOVE_TYPES || it.associatedMessageType in REMOVE_LOVE_TYPES }
        .groupBy { reaction ->
            val target = reaction.associatedMessageGuid?.substringAfterLast("/")
            val sender = if (reaction.isFromMe) "me" else reaction.handleAddress
            target to sender
        }
        .filter { (key, reactions) ->
            key.first != null && reactions.maxBy { it.dateCreated }.associatedMessageType in LOVE_TYPES
        }
        .mapNotNull { (key, _) -> key.first }
        .toSet()
