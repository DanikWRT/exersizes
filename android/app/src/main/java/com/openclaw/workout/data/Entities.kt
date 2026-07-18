package com.openclaw.workout.data

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID

fun uuid(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()

enum class SyncStatus { SYNCED, DIRTY, DELETED }
enum class SegmentType { main, drop, burn, partial }
enum class RelationType { same_muscle, alternative, same_machine, different_grip, variation }
enum class MediaType { image, gif, video, external_url }

@Entity(tableName = "exercises")
@Serializable data class ExerciseEntity(
    @PrimaryKey val id: String = uuid(), val name: String, val muscleGroup: String = "",
    val description: String = "", val weightStrategy: String = "",
    val restSeconds: Int = 60,
    val isArchived: Boolean = false, val createdAt: Long = now(), val updatedAt: Long = now(),
    val syncStatus: SyncStatus = SyncStatus.DIRTY, val dirty: Boolean = true
)

@Entity(tableName = "exercise_variants", foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("exerciseId")])
@Serializable data class ExerciseVariantEntity(
    @PrimaryKey val id: String = uuid(), val exerciseId: String, val name: String = "default", val description: String = "",
    val isDefault: Boolean = false, val restSeconds: Int = 60,
    val createdAt: Long = now(), val updatedAt: Long = now(), val syncStatus: SyncStatus = SyncStatus.DIRTY, val dirty: Boolean = true
)

@Entity(tableName = "exercise_aliases", indices = [Index("exerciseId"), Index("normalizedAlias")])
@Serializable data class ExerciseAliasEntity(@PrimaryKey val id: String = uuid(), val exerciseId: String, val alias: String, val normalizedAlias: String)

@Entity(tableName = "related_exercises", indices = [Index("exerciseId"), Index("relatedExerciseId")])
@Serializable data class RelatedExerciseEntity(@PrimaryKey val id: String = uuid(), val exerciseId: String, val relatedExerciseId: String, val relationType: RelationType, val notes: String = "")

@Entity(tableName = "workout_sessions", indices = [Index("date")])
@Serializable data class WorkoutSessionEntity(
    @PrimaryKey val id: String = uuid(), val date: String, val startedAt: Long = now(), val finishedAt: Long? = null,
    val templateId: String? = null,
    val notes: String = "", val source: String = "android", val createdAt: Long = now(), val updatedAt: Long = now(), val syncStatus: SyncStatus = SyncStatus.DIRTY, val dirty: Boolean = true
)

@Entity(tableName = "workout_blocks", indices = [Index("sessionId")])
@Serializable data class WorkoutBlockEntity(@PrimaryKey val id: String = uuid(), val sessionId: String, val name: String, val orderIndex: Int = 0, val notes: String = "")

@Entity(tableName = "workout_exercises", indices = [Index("sessionId"), Index("blockId"), Index("exerciseId"), Index("variantId")])
@Serializable data class WorkoutExerciseEntity(
    @PrimaryKey val id: String = uuid(), val sessionId: String, val blockId: String? = null, val exerciseId: String, val variantId: String? = null,
    val orderIndex: Int = 0, val restSeconds: Int = 60,
    val notes: String = "", val setupNotes: String = "", val createdAt: Long = now(), val updatedAt: Long = now(), val syncStatus: SyncStatus = SyncStatus.DIRTY, val dirty: Boolean = true
)

@Entity(tableName = "workout_sets", indices = [Index("workoutExerciseId")])
@Serializable data class WorkoutSetEntity(
    @PrimaryKey val id: String = uuid(), val workoutExerciseId: String, val setIndex: Int,
    val notes: String = "", val isWarmup: Boolean = false, val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = now(), val updatedAt: Long = now(), val syncStatus: SyncStatus = SyncStatus.DIRTY, val dirty: Boolean = true,
    val weight: Double? = null, val reps: Int? = null
)

// PATCH-12: projection used by JOIN queries that need session date instead of createdAt
@Serializable data class WorkoutSetWithSessionDate(
    val id: String, val workoutExerciseId: String, val setIndex: Int,
    val notes: String = "", val isWarmup: Boolean = false, val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = now(), val updatedAt: Long = now(), val syncStatus: SyncStatus = SyncStatus.DIRTY, val dirty: Boolean = true,
    val sessionDate: String,
    val weight: Double? = null, val reps: Int? = null
)

@Entity(tableName = "workout_set_segments", indices = [Index("workoutSetId")])
@Serializable data class WorkoutSetSegmentEntity(
    @PrimaryKey val id: String = uuid(), val workoutSetId: String, val segmentIndex: Int = 0, val weight: Double = 0.0, val reps: Int = 0, val notes: String = "", val type: SegmentType = SegmentType.main, val isSupplemental: Boolean = false
)

// PATCH-12: migration helper to extract legacy weight/reps into main segment when importing old JSON
fun WorkoutSetEntity.segmentsForImport(): List<WorkoutSetSegmentEntity>? {
    if (weight == null || reps == null) return null
    return listOf(WorkoutSetSegmentEntity(workoutSetId = id, segmentIndex = 0, weight = weight, reps = reps, type = SegmentType.main, isSupplemental = false))
}

@Entity(tableName = "exercise_media", indices = [Index("exerciseId")])
@Serializable data class ExerciseMediaEntity(@PrimaryKey val id: String = uuid(), val exerciseId: String, val type: MediaType = MediaType.external_url, val uri: String, val thumbnailUri: String = "", val caption: String = "", val createdAt: Long = now())

@Entity(tableName = "workout_templates")
@Serializable data class WorkoutTemplateEntity(
    @PrimaryKey val id: String = uuid(), val name: String, val exercisesJson: String = "",
    val createdAt: Long = now()
)

@Entity(tableName = "app_settings", indices = [Index("key")])
@Serializable data class AppSettingEntity(
    @PrimaryKey val id: String = uuid(), val key: String, val value: String = ""
)

@Serializable data class ExportBundle(
    val exportedAt: Long = now(), val exercises: List<ExerciseEntity>, val variants: List<ExerciseVariantEntity>, val aliases: List<ExerciseAliasEntity>,
    val related: List<RelatedExerciseEntity>, val sessions: List<WorkoutSessionEntity>, val blocks: List<WorkoutBlockEntity>, val workoutExercises: List<WorkoutExerciseEntity>,
    val sets: List<WorkoutSetEntity>, val segments: List<WorkoutSetSegmentEntity>, val media: List<ExerciseMediaEntity>,
    val templates: List<WorkoutTemplateEntity> = emptyList(),
    val settings: List<AppSettingEntity> = emptyList()
)