package com.lujian.travelplan.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "plans", indices = [Index("sha256")])
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val capability: String,
    val sourceFileName: String,
    val sourceMimeType: String?,
    val charsetName: String,
    val sha256: String,
    val rawPath: String,
    val generatedPath: String?,
    val thumbnailPath: String?,
    val compatibilityMode: Boolean,
    val sectionsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "plan_days",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val sourceId: String,
    val position: Int,
    val label: String,
    val title: String,
)

@Entity(
    tableName = "plan_items",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dayId")],
)
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val sourceId: String,
    val position: Int,
    val time: String?,
    val title: String,
    val category: String?,
    val cost: String?,
    val notes: String?,
)

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val confirmed: Boolean,
)

@Entity(
    tableName = "plan_destination_cross_ref",
    primaryKeys = ["planId", "destinationId"],
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DestinationEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId"), Index("destinationId")],
)
data class PlanDestinationCrossRef(
    val planId: Long,
    val destinationId: Long,
)

data class PlanDayWithItems(
    @Embedded val day: PlanDayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId")
    val items: List<PlanItemEntity>,
)

data class PlanWithDetails(
    @Embedded val plan: PlanEntity,
    @Relation(
        entity = PlanDayEntity::class,
        parentColumn = "id",
        entityColumn = "planId",
    )
    val days: List<PlanDayWithItems>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlanDestinationCrossRef::class,
            parentColumn = "planId",
            entityColumn = "destinationId",
        ),
    )
    val destinations: List<DestinationEntity>,
)
