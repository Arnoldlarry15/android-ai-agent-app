package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val agent: String,
    val sender: String, // "user" or "agent"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val agent: String, // e.g., "Assistant", "Business"
    val title: String,
    val details: String,
    val status: String = "Pending", // "Pending" or "Completed"
    val dueTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "shared_memory")
data class SharedMemoryEntity(
    @PrimaryKey val key: String,
    val agent: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
