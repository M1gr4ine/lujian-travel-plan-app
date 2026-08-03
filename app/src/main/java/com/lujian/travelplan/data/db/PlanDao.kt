package com.lujian.travelplan.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM plans ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlanWithDetails>>

    @Transaction
    @Query("SELECT * FROM plans ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PlanWithDetails>

    @Transaction
    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun findById(id: Long): PlanWithDetails?

    @Query("SELECT * FROM plans WHERE sha256 = :sha256 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByHash(sha256: String): PlanEntity?

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Update
    suspend fun updatePlan(plan: PlanEntity)

    @Query("UPDATE plans SET thumbnailPath = :path WHERE id = :planId")
    suspend fun updateThumbnail(planId: Long, path: String)

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Insert
    suspend fun insertItems(items: List<PlanItemEntity>)

    @Insert
    suspend fun insertDestination(destination: DestinationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: PlanDestinationCrossRef)

    @Query("DELETE FROM plan_days WHERE planId = :planId")
    suspend fun deleteDays(planId: Long)

    @Query("SELECT destinationId FROM plan_destination_cross_ref WHERE planId = :planId")
    suspend fun destinationIds(planId: Long): List<Long>

    @Query("DELETE FROM plan_destination_cross_ref WHERE planId = :planId")
    suspend fun deleteCrossRefs(planId: Long)

    @Query("DELETE FROM destinations WHERE id IN (:ids)")
    suspend fun deleteDestinations(ids: List<Long>)

    @Delete
    suspend fun deletePlan(plan: PlanEntity)
}
