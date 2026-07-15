package org.familytools.educationtracker.data

import kotlinx.coroutines.flow.Flow

class ChildRepository(private val dao: ChildDao) {
    fun observeAll(): Flow<List<Child>> = dao.observeAll()

    suspend fun getById(childId: Long): Child? = dao.getById(childId)

    suspend fun save(child: Child) {
        if (child.id == 0L) {
            dao.upsert(child)
        } else {
            dao.update(child)
        }
    }

    suspend fun delete(child: Child) = dao.delete(child)
}
