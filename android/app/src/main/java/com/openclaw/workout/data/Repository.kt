package com.openclaw.workout.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
fun norm(s: String) = s.trim().lowercase().replace('ё','е')

@Serializable private data class SeedExercise(val id: String, val name: String, val muscle_group: String = "", val aliases: List<String> = emptyList())
@Serializable private data class SeedLog(val date: String, val exercise: String, val weight: Double, val reps: Int, val notes: String? = null, val source: String? = null)

@Serializable data class TemplateExerciseItem(
    val exerciseId: String, val variantId: String? = null,
    val weight: Double = 0.0, val reps: Int = 0, val sets: Int = 3, val restSeconds: Int = 60
)

class WorkoutRepository(private val context: Context, val dao: WorkoutDao = AppDatabase.get(context).dao()) {
    suspend fun seedIfEmpty() {
        if (dao.allExercisesOnce().isNotEmpty()) return
        val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        val seeds = json.decodeFromString<List<SeedExercise>>(text)
        seeds.forEach { s ->
            val e = ExerciseEntity(id = s.id, name = s.name, muscleGroup = s.muscle_group, weightStrategy = inferStrategy(s.name))
            dao.upsertExercise(e)
            if (dao.countVariantsByName(e.id, "Базовый") == 0) {
                val v = ExerciseVariantEntity(id = "${e.id}_Базовый", exerciseId = e.id, name = "Базовый", isDefault = true)
                dao.upsertVariant(v)
            }
            s.aliases.forEach { dao.upsertAlias(ExerciseAliasEntity(exerciseId = e.id, alias = it, normalizedAlias = norm(it))) }
        }
    }

    private fun inferStrategy(name: String) = when {
        name.contains("гантел", true) -> "гантели (отдельно)"
        name.contains("кроссовер", true) || name.contains("руки", true) -> "на руку"
        name.contains("штанг", true) || name.contains("жим лёжа", true) || name.contains("жим лежа", true) -> "общий вес"
        else -> ""
    }

    suspend fun startSession(date: String = LocalDate.now().toString()) = WorkoutSessionEntity(date = date).also { dao.upsertSession(it) }
    suspend fun addExercise(sessionId: String, exerciseId: String, variantId: String?, order: Int, restSeconds: Int = 60) =
        WorkoutExerciseEntity(sessionId = sessionId, exerciseId = exerciseId, variantId = variantId, orderIndex = order, restSeconds = restSeconds).also { dao.upsertWorkoutExercise(it) }
    // PATCH-12: set no longer stores weight/reps; main segment does
    suspend fun addSet(workoutExerciseId: String, index: Int, weight: Double, reps: Int, notes: String = "", warmup: Boolean = false) =
        WorkoutSetEntity(workoutExerciseId = workoutExerciseId, setIndex = index, notes = notes, isWarmup = warmup).also { dao.upsertSet(it); dao.upsertSegment(WorkoutSetSegmentEntity(workoutSetId = it.id, segmentIndex = 0, type = SegmentType.main, weight = weight, reps = reps)) }
    suspend fun addDropSegment(setId: String, segmentIndex: Int, weight: Double, reps: Int, type: SegmentType = SegmentType.drop, notes: String = "") = dao.upsertSegment(WorkoutSetSegmentEntity(workoutSetId = setId, segmentIndex = segmentIndex, weight = weight, reps = reps, type = type, notes = notes))

    suspend fun createPlan(date: String, exercises: List<Triple<String, String?, TemplateExerciseItem>>): String {
        val session = startSession(date)
        exercises.forEachIndexed { idx, (exerciseId, variantId, item) ->
            val we = addExercise(session.id, exerciseId, variantId, idx, item.restSeconds)
            for (i in 0 until item.sets) {
                addSet(we.id, i, item.weight, item.reps)
            }
        }
        return session.id
    }

    suspend fun saveTemplate(name: String, exercises: List<TemplateExerciseItem>) {
        val jsonStr = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TemplateExerciseItem.serializer()), exercises)
        dao.upsertTemplate(WorkoutTemplateEntity(name = name, exercisesJson = jsonStr))
    }

    suspend fun createFromTemplate(templateId: String, date: String): String? {
        val template = dao.templateById(templateId) ?: return null
        val items = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(TemplateExerciseItem.serializer()), template.exercisesJson)
        val session = startSession(date)
        items.forEachIndexed { idx, item ->
            val we = addExercise(session.id, item.exerciseId, item.variantId, idx, item.restSeconds)
            for (i in 0 until item.sets) {
                addSet(we.id, i, item.weight, item.reps)
            }
        }
        return session.id
    }

    suspend fun loadTemplateAsPlan(templateId: String): List<TemplateExerciseItem>? {
        val template = dao.templateById(templateId) ?: return null
        return json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(TemplateExerciseItem.serializer()), template.exercisesJson)
    }

    suspend fun getLastSetForVariant(exerciseId: String, variantId: String?): WorkoutSetWithSessionDate? {
        return dao.lastSetForExerciseVariant(exerciseId, variantId)
    }

    suspend fun getLastWorkoutExerciseForVariant(exerciseId: String, variantId: String?): WorkoutExerciseEntity? {
        return dao.lastWorkoutExerciseForVariant(exerciseId, variantId)
    }

    suspend fun getSetting(key: String): String? = dao.getSetting(key)?.value
    suspend fun setSetting(key: String, value: String) = dao.upsertSetting(AppSettingEntity(key = key, value = value))

    suspend fun canDeleteVariant(variantId: String): Boolean = dao.countSetsForVariant(variantId) == 0

    suspend fun exportJson(): String = json.encodeToString(ExportBundle.serializer(), ExportBundle(
        exercises = dao.allExercisesOnce(), variants = dao.allVariantsOnce(), aliases = dao.allAliasesOnce(), related = dao.allRelatedOnce(), sessions = dao.allSessionsOnce(), blocks = dao.allBlocksOnce(), workoutExercises = dao.allWorkoutExercisesOnce(), sets = dao.allSetsOnce(), segments = dao.allSegmentsOnce(), media = dao.allMediaOnce(), templates = dao.allTemplatesOnce(), settings = dao.allSettingsOnce()))

    suspend fun importJson(text: String) {
        val b = json.decodeFromString(ExportBundle.serializer(), text)
        b.exercises.forEach { dao.upsertExercise(it.copy(dirty = true, syncStatus = SyncStatus.DIRTY, updatedAt = now())) }
        b.variants.forEach { dao.upsertVariant(it.copy(dirty = true, syncStatus = SyncStatus.DIRTY, updatedAt = now())) }
        b.aliases.forEach { dao.upsertAlias(it) }; b.related.forEach { dao.upsertRelated(it) }; b.sessions.forEach { dao.upsertSession(it.copy(dirty = true, syncStatus = SyncStatus.DIRTY, updatedAt = now())) }
        b.blocks.forEach { dao.upsertBlock(it) }; b.workoutExercises.forEach { dao.upsertWorkoutExercise(it.copy(dirty = true, syncStatus = SyncStatus.DIRTY, updatedAt = now())) }; b.sets.forEach { dao.upsertSet(it.copy(dirty = true, syncStatus = SyncStatus.DIRTY, updatedAt = now())); it.segmentsForImport()?.let { segs -> segs.forEach { seg -> dao.upsertSegment(seg) } } }; b.segments.forEach { dao.upsertSegment(it) }; b.media.forEach { dao.upsertMedia(it) }
        b.templates.forEach { dao.upsertTemplate(it) }
        b.settings.forEach { dao.upsertSetting(it) }
    }
}