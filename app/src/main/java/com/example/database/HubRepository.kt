package com.example.database

import kotlinx.coroutines.flow.Flow

class HubRepository(
    private val chatDao: ChatDao,
    private val taskDao: TaskDao,
    private val sharedMemoryDao: SharedMemoryDao
) {
    fun getMessagesForAgent(agent: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForAgent(agent)

    suspend fun insertMessage(message: ChatMessageEntity) {
        chatDao.insertMessage(message)
    }

    suspend fun clearHistory(agent: String) {
        chatDao.clearHistory(agent)
    }

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    fun getTasksForAgent(agent: String): Flow<List<TaskEntity>> =
        taskDao.getTasksForAgent(agent)

    suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTaskById(id: Int) {
        taskDao.deleteTaskById(id)
    }

    val allMemory: Flow<List<SharedMemoryEntity>> = sharedMemoryDao.getAllMemory()

    suspend fun insertMemory(memory: SharedMemoryEntity) {
        sharedMemoryDao.insertMemory(memory)
    }

    suspend fun deleteMemory(key: String) {
        sharedMemoryDao.deleteMemory(key)
    }
}
