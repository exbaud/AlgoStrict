package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlgoFileDao {
    @Query("SELECT * FROM algo_files ORDER BY updatedAt DESC")
    fun observeAllFiles(): Flow<List<AlgoFileEntity>>

    @Query("SELECT * FROM algo_files WHERE id = :id")
    suspend fun getFileById(id: Long): AlgoFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: AlgoFileEntity): Long

    @Update
    suspend fun updateFile(file: AlgoFileEntity)

    @Query("DELETE FROM algo_files WHERE id = :id")
    suspend fun deleteFile(id: Long)

    @Query("SELECT * FROM exercise_progress")
    fun observeExerciseProgress(): Flow<List<ExerciseProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ExerciseProgressEntity)
}
