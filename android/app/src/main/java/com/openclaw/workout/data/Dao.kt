package com.openclaw.workout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface WorkoutDao {
    @Query("SELECT * FROM exercises WHERE isArchived=0 ORDER BY name") fun exercises(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM exercises ORDER BY name") suspend fun allExercisesOnce(): List<ExerciseEntity>
    @Query("SELECT * FROM exercises WHERE id=:id") suspend fun exerciseById(id: String): ExerciseEntity?
    @Query("SELECT * FROM exercise_variants WHERE exerciseId=:exerciseId ORDER BY isDefault DESC, name") fun variants(exerciseId: String): Flow<List<ExerciseVariantEntity>>
    @Query("SELECT COUNT(*) FROM exercise_variants WHERE exerciseId=:exerciseId AND name=:name") suspend fun countVariantsByName(exerciseId: String, name: String): Int
    @Query("SELECT * FROM exercise_variants WHERE id=:id") suspend fun variantById(id: String): ExerciseVariantEntity?
    @Query("SELECT * FROM exercise_variants") suspend fun allVariantsOnce(): List<ExerciseVariantEntity>
    @Query("SELECT * FROM exercise_aliases") suspend fun allAliasesOnce(): List<ExerciseAliasEntity>
    @Query("SELECT * FROM exercise_aliases WHERE normalizedAlias LIKE '%' || :q || '%' OR alias LIKE '%' || :q || '%' ") suspend fun aliasesSearch(q: String): List<ExerciseAliasEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY date DESC, startedAt DESC") fun sessions(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_sessions") suspend fun allSessionsOnce(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM workout_sessions WHERE id=:id") suspend fun session(id: String): WorkoutSessionEntity?
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY date ASC, startedAt DESC") fun plannedSessions(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL ORDER BY date DESC, startedAt DESC") fun completedSessions(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_exercises WHERE sessionId=:sessionId ORDER BY orderIndex") fun workoutExercises(sessionId: String): Flow<List<WorkoutExerciseEntity>>
    @Query("SELECT * FROM workout_exercises WHERE sessionId=:sessionId ORDER BY orderIndex") suspend fun workoutExercisesOnce(sessionId: String): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM workout_exercises") suspend fun allWorkoutExercisesOnce(): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM workout_exercises WHERE id=:id") suspend fun workoutExerciseById(id: String): WorkoutExerciseEntity?
    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId=:workoutExerciseId ORDER BY setIndex") fun sets(workoutExerciseId: String): Flow<List<WorkoutSetEntity>>
    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId=:workoutExerciseId ORDER BY setIndex") suspend fun setsOnce(workoutExerciseId: String): List<WorkoutSetEntity>
    @Query("SELECT * FROM workout_sets") suspend fun allSetsOnce(): List<WorkoutSetEntity>
    @Query("SELECT * FROM workout_sets WHERE id=:id") suspend fun setById(id: String): WorkoutSetEntity?
    @Query("SELECT * FROM workout_set_segments WHERE workoutSetId=:setId ORDER BY segmentIndex") fun segments(setId: String): Flow<List<WorkoutSetSegmentEntity>>
    @Query("SELECT * FROM workout_set_segments WHERE workoutSetId=:setId ORDER BY segmentIndex") suspend fun segmentsOnce(setId: String): List<WorkoutSetSegmentEntity>
    @Query("SELECT * FROM workout_set_segments") suspend fun allSegmentsOnce(): List<WorkoutSetSegmentEntity>
    @Query("SELECT * FROM workout_blocks") suspend fun allBlocksOnce(): List<WorkoutBlockEntity>
    @Query("SELECT * FROM related_exercises") suspend fun allRelatedOnce(): List<RelatedExerciseEntity>
    @Query("SELECT * FROM exercise_media") suspend fun allMediaOnce(): List<ExerciseMediaEntity>

    @Query("SELECT * FROM workout_exercises WHERE exerciseId=:exerciseId AND ((:variantId IS NULL AND variantId IS NULL) OR variantId=:variantId) AND id != :exclude ORDER BY createdAt DESC LIMIT 1") suspend fun lastExactWorkoutExercise(exerciseId: String, variantId: String?, exclude: String = ""): WorkoutExerciseEntity?
    @Query("""
        SELECT s.*, ws.date as sessionDate
        FROM workout_sets s
        INNER JOIN workout_exercises we ON we.id = s.workoutExerciseId
        INNER JOIN workout_sessions ws ON ws.id = we.sessionId
        WHERE we.exerciseId = :exerciseId AND ((:variantId IS NULL AND we.variantId IS NULL) OR we.variantId = :variantId)
        ORDER BY ws.date, s.createdAt
    """)
    fun exactVariantSets(exerciseId: String, variantId: String?): Flow<List<WorkoutSetWithSessionDate>>
    @Query("""
        SELECT s.*, ws.date as sessionDate
        FROM workout_sets s
        INNER JOIN workout_exercises we ON we.id = s.workoutExerciseId
        INNER JOIN workout_sessions ws ON ws.id = we.sessionId
        WHERE we.exerciseId = :exerciseId AND ((:variantId IS NULL AND we.variantId IS NULL) OR we.variantId = :variantId)
        ORDER BY ws.date DESC, s.createdAt DESC
        LIMIT 1
    """)
    suspend fun lastSetForExerciseVariant(exerciseId: String, variantId: String?): WorkoutSetWithSessionDate?
    @Query("SELECT we.* FROM workout_exercises we WHERE we.exerciseId=:exerciseId AND ((:variantId IS NULL AND we.variantId IS NULL) OR we.variantId=:variantId) ORDER BY we.createdAt DESC LIMIT 1") suspend fun lastWorkoutExerciseForVariant(exerciseId: String, variantId: String?): WorkoutExerciseEntity?
    @Query("SELECT COUNT(*) FROM workout_sets s INNER JOIN workout_exercises we ON we.id=s.workoutExerciseId WHERE we.exerciseId=:exerciseId AND ((:variantId IS NULL AND we.variantId IS NULL) OR we.variantId=:variantId)") suspend fun countSetsForExerciseVariant(exerciseId: String, variantId: String?): Int

    // Templates
    @Query("SELECT * FROM workout_templates ORDER BY createdAt DESC") fun templates(): Flow<List<WorkoutTemplateEntity>>
    @Query("SELECT * FROM workout_templates ORDER BY createdAt DESC") suspend fun allTemplatesOnce(): List<WorkoutTemplateEntity>
    @Query("SELECT * FROM workout_templates WHERE id=:id") suspend fun templateById(id: String): WorkoutTemplateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTemplate(t: WorkoutTemplateEntity)
    @Delete suspend fun deleteTemplate(t: WorkoutTemplateEntity)

    @Transaction
    suspend fun deleteExerciseWithData(exerciseId: String) {
        deleteSetsByExerciseId(exerciseId)
        deleteWorkoutExercisesByExerciseId(exerciseId)
        deleteVariantsByExerciseId(exerciseId)
        deleteExercise(exerciseId)
    }
    @Query("DELETE FROM workout_sets WHERE workoutExerciseId IN (SELECT id FROM workout_exercises WHERE exerciseId=:exerciseId)") suspend fun deleteSetsByExerciseId(exerciseId: String)
    @Query("DELETE FROM workout_exercises WHERE exerciseId=:exerciseId") suspend fun deleteWorkoutExercisesByExerciseId(exerciseId: String)
    @Query("DELETE FROM exercise_variants WHERE exerciseId=:exerciseId") suspend fun deleteVariantsByExerciseId(exerciseId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExercise(e: ExerciseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVariant(v: ExerciseVariantEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAlias(a: ExerciseAliasEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(s: WorkoutSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkoutExercise(e: WorkoutExerciseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSet(s: WorkoutSetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSegment(s: WorkoutSetSegmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMedia(m: ExerciseMediaEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRelated(r: RelatedExerciseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBlock(b: WorkoutBlockEntity)

    @Query("UPDATE workout_sessions SET finishedAt=:ts, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun finishSession(id: String, ts: Long = now())
    @Delete suspend fun deleteSet(s: WorkoutSetEntity)
    @Delete suspend fun deleteWorkoutExercise(e: WorkoutExerciseEntity)
    @Query("SELECT * FROM app_settings") suspend fun allSettingsOnce(): List<AppSettingEntity>

    @Delete suspend fun deleteSession(s: WorkoutSessionEntity)
    @Query("DELETE FROM workout_sessions WHERE id=:sessionId") suspend fun deleteSession(sessionId: String)
    @Query("DELETE FROM workout_sets WHERE workoutExerciseId IN (SELECT id FROM workout_exercises WHERE sessionId=:sessionId)") suspend fun deleteSetsBySessionId(sessionId: String)
    @Query("DELETE FROM workout_exercises WHERE sessionId=:sessionId") suspend fun deleteWorkoutExercisesBySessionId(sessionId: String)
    @Transaction
    suspend fun deleteSessionWithData(sessionId: String) {
        deleteSetsBySessionId(sessionId)
        deleteWorkoutExercisesBySessionId(sessionId)
        deleteSession(sessionId)
    }
    @Delete suspend fun deleteVariant(v: ExerciseVariantEntity)
    @Query("DELETE FROM exercise_variants WHERE id=:id") suspend fun deleteVariantById(id: String)
    @Query("SELECT COUNT(*) FROM workout_sets s INNER JOIN workout_exercises we ON we.id=s.workoutExerciseId WHERE we.variantId=:variantId") suspend fun countSetsForVariant(variantId: String): Int
    @Query("SELECT COUNT(*) FROM workout_sets s INNER JOIN workout_exercises we ON we.id=s.workoutExerciseId WHERE we.exerciseId=:exerciseId") suspend fun countSetsForExercise(exerciseId: String): Int
    @Query("DELETE FROM exercises WHERE id = :exerciseId") suspend fun deleteExercise(exerciseId: String)

    // PATCH-5: reopen completed session
    @Query("UPDATE workout_sessions SET finishedAt=NULL, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun reopenSession(id: String, ts: Long = now())

    @Query("UPDATE workout_sets SET weight=:weight, reps=:reps, updatedAt=:updated WHERE id=:setId")
    suspend fun updateSetWeightReps(setId: String, weight: Double, reps: Int, updated: Long = now())

    @Query("UPDATE workout_sets SET isCompleted=1, completedAt=:ts, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun completeSet(id: String, ts: Long = now())
    @Query("UPDATE exercises SET restSeconds=:seconds, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun updateExerciseRest(id: String, seconds: Int, ts: Long = now())
    @Query("UPDATE exercise_variants SET restSeconds=:seconds, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun updateVariantRest(id: String, seconds: Int, ts: Long = now())
    @Query("UPDATE workout_exercises SET restSeconds=:seconds, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun updateWorkoutExerciseRest(id: String, seconds: Int, ts: Long = now())

    @Query("UPDATE exercises SET name=:name, muscleGroup=:group, weightStrategy=:strategy, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun updateExercise(id: String, name: String, group: String, strategy: String, ts: Long = now())
    @Query("UPDATE exercise_variants SET name=:name, updatedAt=:ts, dirty=1, syncStatus='DIRTY' WHERE id=:id") suspend fun updateVariantName(id: String, name: String, ts: Long = now())

    // App settings
    @Query("SELECT * FROM app_settings WHERE key=:key LIMIT 1") suspend fun getSetting(key: String): AppSettingEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSetting(s: AppSettingEntity)
}