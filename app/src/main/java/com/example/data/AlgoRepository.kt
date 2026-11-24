package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AlgoRepository(private val dao: AlgoFileDao) {
    val files: Flow<List<AlgoFileEntity>> = dao.observeAllFiles()
    val exerciseProgress: Flow<List<ExerciseProgressEntity>> = dao.observeExerciseProgress()

    suspend fun ensureSeeded() {
        val current = dao.observeAllFiles().first()
        if (current.isEmpty()) {
            dao.insertFile(
                AlgoFileEntity(
                    title = "Moyenne_deux_reels",
                    code = CourseContent.chapters[1].examples.first().code
                )
            )
            dao.insertFile(
                AlgoFileEntity(
                    title = "Etat_Eau",
                    code = CourseContent.chapters[2].examples.first().code
                )
            )
            dao.insertFile(
                AlgoFileEntity(
                    title = "Exemple_pointeur",
                    code = CourseContent.chapters[7].examples.first().code
                )
            )
        }
    }

    suspend fun saveFile(id: Long?, title: String, code: String): Long {
        val now = System.currentTimeMillis()
        return if (id != null && id > 0) {
            dao.updateFile(AlgoFileEntity(id = id, title = title, code = code, updatedAt = now))
            id
        } else {
            dao.insertFile(AlgoFileEntity(title = title, code = code, updatedAt = now))
        }
    }

    suspend fun deleteFile(id: Long) {
        dao.deleteFile(id)
    }

    suspend fun saveExerciseProgress(exerciseId: String, solved: Boolean, code: String) {
        dao.upsertProgress(
            ExerciseProgressEntity(
                exerciseId = exerciseId,
                solved = solved,
                lastCode = code
            )
        )
    }
}
