package com.openclaw.workout.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExerciseEntity::class, ExerciseVariantEntity::class, ExerciseAliasEntity::class, RelatedExerciseEntity::class, WorkoutSessionEntity::class, WorkoutBlockEntity::class, WorkoutExerciseEntity::class, WorkoutSetEntity::class, WorkoutSetSegmentEntity::class, ExerciseMediaEntity::class, WorkoutTemplateEntity::class, AppSettingEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // PATCH-12: safe no-op migrations to avoid destructive migration
                db.execSQL("SELECT 1")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("SELECT 1")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("SELECT 1")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // PATCH-12: migrate to segments — copy weight/reps into main segment, remove columns
                db.execSQL("""
                    DELETE FROM exercise_variants
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM exercise_variants
                        GROUP BY exerciseId, name
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_variant_unique ON exercise_variants(exerciseId, name)")
                // Create segments table with isSupplemental
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workout_set_segments (
                        id TEXT PRIMARY KEY NOT NULL,
                        workoutSetId TEXT NOT NULL,
                        segmentIndex INTEGER NOT NULL DEFAULT 0,
                        weight REAL NOT NULL DEFAULT 0.0,
                        reps INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        type TEXT NOT NULL DEFAULT 'main',
                        isSupplemental INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    INSERT OR IGNORE INTO workout_set_segments(id, workoutSetId, segmentIndex, weight, reps, notes, type, isSupplemental)
                    SELECT id, id, 0, weight, reps, notes, 'main', 0 FROM workout_sets
                """)
                // SQLite does not support DROP COLUMN; recreate table without weight/reps
                db.execSQL("""
                    CREATE TABLE workout_sets_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        workoutExerciseId TEXT NOT NULL,
                        setIndex INTEGER NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        isWarmup INTEGER NOT NULL DEFAULT 0,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL DEFAULT 'DIRTY',
                        dirty INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("""
                    INSERT INTO workout_sets_new(id, workoutExerciseId, setIndex, notes, isWarmup, isCompleted, completedAt, createdAt, updatedAt, syncStatus, dirty)
                    SELECT id, workoutExerciseId, setIndex, notes, isWarmup, isCompleted, completedAt, createdAt, updatedAt, syncStatus, dirty FROM workout_sets
                """)
                db.execSQL("DROP TABLE workout_sets")
                db.execSQL("ALTER TABLE workout_sets_new RENAME TO workout_sets")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_sets_workoutExerciseId ON workout_sets(workoutExerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_workout_set_segments_workoutSetId ON workout_set_segments(workoutSetId)")
            }
        }
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "workout-offline.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { INSTANCE = it }
        }
    }
}

class Converters {
    @TypeConverter fun syncStatus(v: SyncStatus) = v.name
    @TypeConverter fun syncStatus(v: String) = SyncStatus.valueOf(v)
    @TypeConverter fun segmentType(v: SegmentType) = v.name
    @TypeConverter fun segmentType(v: String) = SegmentType.valueOf(v)
    @TypeConverter fun relationType(v: RelationType) = v.name
    @TypeConverter fun relationType(v: String) = RelationType.valueOf(v)
    @TypeConverter fun mediaType(v: MediaType) = v.name
    @TypeConverter fun mediaType(v: String) = MediaType.valueOf(v)
}