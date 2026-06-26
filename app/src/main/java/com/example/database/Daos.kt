package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE agent = :agent ORDER BY timestamp ASC")
    fun getMessagesForAgent(agent: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE agent = :agent")
    suspend fun clearHistory(agent: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueTime DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE agent = :agent ORDER BY dueTime DESC")
    fun getTasksForAgent(agent: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)
}

@Dao
interface SharedMemoryDao {
    @Query("SELECT * FROM shared_memory ORDER BY updatedAt DESC")
    fun getAllMemory(): Flow<List<SharedMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: SharedMemoryEntity)

    @Query("DELETE FROM shared_memory WHERE `key` = :key")
    suspend fun deleteMemory(key: String)
}
