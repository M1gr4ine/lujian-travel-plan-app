package com.lujian.travelplan.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlanEntity::class,
        PlanDayEntity::class,
        PlanItemEntity::class,
        DestinationEntity::class,
        PlanDestinationCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LujianDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
}
