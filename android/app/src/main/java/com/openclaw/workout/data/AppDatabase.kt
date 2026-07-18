package com.openclaw.workout.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExerciseEntity::class, ExerciseVariantEntity::class, ExerciseAliasEntity::class, RelatedExerciseEntity::class, WorkoutSessionEntity::class, WorkoutBlockEntity::class, WorkoutExerciseEntity::class, WorkoutSetEntity::class, WorkoutSetSegmentEntity::class, ExerciseMediaEntity::class, WorkoutTemplateEntity::class, AppSettingEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM exercise_variants
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM exercise_variants
                        GROUP BY exerciseId, name
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_variant_unique ON exercise_variants(exerciseId, name)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sets ADD COLUMN isSupplemental INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "workout-offline.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
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