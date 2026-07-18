package com.openclaw.workout.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [ExerciseEntity::class, ExerciseVariantEntity::class, ExerciseAliasEntity::class, RelatedExerciseEntity::class, WorkoutSessionEntity::class, WorkoutBlockEntity::class, WorkoutExerciseEntity::class, WorkoutSetEntity::class, WorkoutSetSegmentEntity::class, ExerciseMediaEntity::class, WorkoutTemplateEntity::class, AppSettingEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): WorkoutDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "workout-offline.db")
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}

class Converters {
    @TypeConverter fun syncStatus(v: SyncStatus) = v.name
    @TypeConverter fun syncStatus(v: String) = SyncStatus.valueOf(v)
    @TypeConverter fun weightStrategy(v: WeightStrategy) = v.name
    @TypeConverter fun weightStrategy(v: String) = WeightStrategy.valueOf(v)
    @TypeConverter fun segmentType(v: SegmentType) = v.name
    @TypeConverter fun segmentType(v: String) = SegmentType.valueOf(v)
    @TypeConverter fun relationType(v: RelationType) = v.name
    @TypeConverter fun relationType(v: String) = RelationType.valueOf(v)
    @TypeConverter fun mediaType(v: MediaType) = v.name
    @TypeConverter fun mediaType(v: String) = MediaType.valueOf(v)
}