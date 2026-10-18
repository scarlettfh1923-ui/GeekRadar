package com.scarlett.geekradar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert suspend fun insert(t: TaskEntity): Long
    @Update suspend fun update(t: TaskEntity)
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks WHERE id = :id") fun observe(id: Long): Flow<TaskEntity?>
    @Query("SELECT * FROM tasks WHERE uuid = :uuid") suspend fun byUuid(uuid: String): TaskEntity?
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC") fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks ORDER BY createdAt ASC") suspend fun all(): List<TaskEntity>
    @Query(
        """SELECT * FROM tasks WHERE pageTitle LIKE '%' || :q || '%' OR finalTitle LIKE '%' || :q || '%'
           OR originalUrl LIKE '%' || :q || '%' OR rawInput LIKE '%' || :q || '%' OR finalBody LIKE '%' || :q || '%'
           OR sourceText LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR tagsJson LIKE '%' || :q || '%'
           OR id IN (SELECT taskId FROM links WHERE rawUrl LIKE '%' || :q || '%')
           ORDER BY updatedAt DESC"""
    )
    fun search(q: String): Flow<List<TaskEntity>>
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tasks") suspend fun deleteAll()
    @Query("UPDATE tasks SET genStatus = 'INTERRUPTED', genError = '上次生成被中断（应用被关闭或回收），可重新生成' WHERE genStatus = 'RUNNING'")
    suspend fun markInterrupted()
    @Query("SELECT COUNT(*) FROM tasks") fun count(): Flow<Int>
}

@Dao
interface LinkDao {
    @Insert suspend fun insertAll(l: List<LinkEntity>)
    @Update suspend fun update(l: LinkEntity)
    @Update suspend fun updateAll(l: List<LinkEntity>)
    @Query("SELECT * FROM links WHERE taskId = :taskId ORDER BY position") suspend fun forTask(taskId: Long): List<LinkEntity>
    @Query("SELECT * FROM links WHERE taskId = :taskId ORDER BY position") fun observeForTask(taskId: Long): Flow<List<LinkEntity>>
    @Query("DELETE FROM links WHERE taskId = :taskId") suspend fun deleteForTask(taskId: Long)
    @Query("SELECT * FROM links") suspend fun all(): List<LinkEntity>
    @Query("SELECT * FROM links WHERE userConfirmed = 0") suspend fun unconfirmed(): List<LinkEntity>

    @Transaction
    suspend fun replaceForTask(taskId: Long, links: List<LinkEntity>) { deleteForTask(taskId); insertAll(links) }
}

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(p: PromptSnapshotEntity): Long
    @Query("SELECT * FROM prompt_snapshots ORDER BY version DESC, id DESC") fun observeAll(): Flow<List<PromptSnapshotEntity>>
    @Query("SELECT * FROM prompt_snapshots ORDER BY version DESC, id DESC") suspend fun all(): List<PromptSnapshotEntity>
    @Query("SELECT * FROM prompt_snapshots WHERE version = :v AND content = :c LIMIT 1") suspend fun find(v: Int, c: String): PromptSnapshotEntity?
}
