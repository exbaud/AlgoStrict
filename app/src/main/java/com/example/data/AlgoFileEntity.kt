package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "algo_files")
data class AlgoFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val code: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "exercise_progress")
data class ExerciseProgressEntity(
    @PrimaryKey val exerciseId: String,
    val solved: Boolean,
    val lastCode: String,
    val updatedAt: Long = System.currentTimeMillis()
)
