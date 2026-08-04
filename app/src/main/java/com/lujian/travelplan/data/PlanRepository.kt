package com.lujian.travelplan.data

import android.content.Context
import androidx.room.withTransaction
import com.lujian.travelplan.data.db.DestinationEntity
import com.lujian.travelplan.data.db.LujianDatabase
import com.lujian.travelplan.data.db.PlanDayEntity
import com.lujian.travelplan.data.db.PlanDestinationCrossRef
import com.lujian.travelplan.data.db.PlanEntity
import com.lujian.travelplan.data.db.PlanItemEntity
import com.lujian.travelplan.data.db.PlanWithDetails
import com.lujian.travelplan.export.MobileHtmlGenerator
import com.lujian.travelplan.importing.LocationCandidate
import com.lujian.travelplan.model.DestinationDraft
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.PlanDayDraft
import com.lujian.travelplan.model.PlanItemDraft
import com.lujian.travelplan.model.PlanSectionDraft
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class StoredPlan(
    val id: Long,
    val parsed: ParsedPlan,
    val sourceFileName: String,
    val rawPath: String,
    val generatedPath: String?,
    val thumbnailPath: String?,
    val compatibilityMode: Boolean,
    val updatedAt: Long,
)

data class ImportedPlanFiles(
    val sourceFileName: String,
    val sourceMimeType: String?,
    val charsetName: String,
    val sha256: String,
    val rawPath: String,
)

class PlanRepository(
    private val context: Context,
    private val database: LujianDatabase,
) {
    private val dao = database.planDao()

    fun observePlans(): Flow<List<StoredPlan>> = dao.observeAll().map { list ->
        list.map { it.toStoredPlan() }
    }

    suspend fun getPlan(id: Long): StoredPlan? = dao.findById(id)?.toStoredPlan()

    suspend fun getPlansOnce(): List<StoredPlan> = dao.getAll().map { it.toStoredPlan() }

    suspend fun findDuplicate(sha256: String): PlanEntity? = dao.findByHash(sha256)

    suspend fun insertImported(
        parsed: ParsedPlan,
        files: ImportedPlanFiles,
        replacePlanId: Long? = null,
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val existing = replacePlanId?.let { dao.findById(it)?.plan }
        val planId = if (existing != null) {
            clearGraph(existing.id)
            dao.updatePlan(
                existing.copy(
                    title = parsed.title,
                    capability = parsed.capability.name,
                    sourceFileName = files.sourceFileName,
                    sourceMimeType = files.sourceMimeType,
                    charsetName = files.charsetName,
                    sha256 = files.sha256,
                    rawPath = files.rawPath,
                    generatedPath = null,
                    thumbnailPath = null,
                    sectionsJson = parsed.sections.toJson(),
                    updatedAt = now,
                ),
            )
            existing.id
        } else {
            dao.insertPlan(
                PlanEntity(
                    title = parsed.title,
                    capability = parsed.capability.name,
                    sourceFileName = files.sourceFileName,
                    sourceMimeType = files.sourceMimeType,
                    charsetName = files.charsetName,
                    sha256 = files.sha256,
                    rawPath = files.rawPath,
                    generatedPath = null,
                    thumbnailPath = null,
                    compatibilityMode = false,
                    sectionsJson = parsed.sections.toJson(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        insertGraph(planId, parsed)
        planId
    }

    suspend fun saveEdits(planId: Long, parsed: ParsedPlan) = database.withTransaction {
        val stored = dao.findById(planId) ?: return@withTransaction
        clearGraph(planId)
        insertGraph(planId, parsed)
        val generated = File(context.filesDir, "plans/$planId/generated.html").apply {
            parentFile?.mkdirs()
            writeText(MobileHtmlGenerator.generate(parsed), Charsets.UTF_8)
        }
        dao.updatePlan(
            stored.plan.copy(
                title = parsed.title,
                capability = PlanCapability.ENHANCED.name,
                generatedPath = generated.relativeTo(context.filesDir).invariantSeparatorsPath,
                sectionsJson = parsed.sections.toJson(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setCompatibilityMode(planId: Long, enabled: Boolean) {
        val plan = dao.findById(planId)?.plan ?: return
        dao.updatePlan(plan.copy(compatibilityMode = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun confirmLocation(planId: Long, candidate: LocationCandidate) {
        val stored = getPlan(planId) ?: return
        replaceDestinations(
            planId,
            stored.parsed.destinations.map { destination ->
                if (destination.name == candidate.destinationName) {
                    destination.copy(
                        countryCode = candidate.countryCode,
                        latitude = candidate.latitude,
                        longitude = candidate.longitude,
                    )
                } else destination
            },
        )
    }

    suspend fun replaceDestinations(planId: Long, destinations: List<DestinationDraft>) =
        database.withTransaction {
            val destinationIds = dao.destinationIds(planId)
            dao.deleteCrossRefs(planId)
            if (destinationIds.isNotEmpty()) dao.deleteDestinations(destinationIds)
            insertDestinations(planId, destinations)
        }

    suspend fun delete(planId: Long) = deleteAll(setOf(planId))

    suspend fun deleteAll(planIds: Set<Long>) {
        val files = database.withTransaction {
            planIds.mapNotNull { planId -> dao.findById(planId) }
                .flatMap { stored ->
                    clearGraph(stored.plan.id)
                    dao.deletePlan(stored.plan)
                    listOfNotNull(
                        stored.plan.rawPath,
                        stored.plan.generatedPath,
                        stored.plan.thumbnailPath,
                    )
                }
        }
        files.map { File(context.filesDir, it) }.forEach { file -> file.delete() }
    }

    fun htmlFile(plan: StoredPlan, original: Boolean): File = File(
        context.filesDir,
        if (original || plan.generatedPath == null) plan.rawPath else plan.generatedPath,
    )

    private suspend fun insertGraph(planId: Long, parsed: ParsedPlan) {
        parsed.days.forEachIndexed { dayIndex, day ->
            val dayId = dao.insertDay(
                PlanDayEntity(
                    planId = planId,
                    sourceId = day.id,
                    position = dayIndex,
                    label = day.label,
                    title = day.title,
                ),
            )
            dao.insertItems(
                day.items.mapIndexed { itemIndex, item ->
                    PlanItemEntity(
                        dayId = dayId,
                        sourceId = item.id,
                        position = itemIndex,
                        time = item.time,
                        title = item.title,
                        category = item.category,
                        cost = item.cost,
                        notes = item.notes,
                    )
                },
            )
        }
        insertDestinations(planId, parsed.destinations)
    }

    private suspend fun insertDestinations(planId: Long, destinations: List<DestinationDraft>) {
        destinations.forEach { destination ->
            val destinationId = dao.insertDestination(
                DestinationEntity(
                    name = destination.name,
                    countryCode = destination.countryCode,
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                    confirmed = destination.latitude != null && destination.longitude != null,
                ),
            )
            dao.insertCrossRef(PlanDestinationCrossRef(planId, destinationId))
        }
    }

    private suspend fun clearGraph(planId: Long) {
        val destinationIds = dao.destinationIds(planId)
        dao.deleteDays(planId)
        dao.deleteCrossRefs(planId)
        if (destinationIds.isNotEmpty()) dao.deleteDestinations(destinationIds)
    }
}

private fun PlanWithDetails.toStoredPlan(): StoredPlan = StoredPlan(
    id = plan.id,
    parsed = ParsedPlan(
        title = plan.title,
        capability = PlanCapability.valueOf(plan.capability),
        destinations = destinations.map { destination ->
            DestinationDraft(
                destination.name,
                destination.countryCode,
                destination.latitude,
                destination.longitude,
            )
        },
        days = days.sortedBy { it.day.position }.map { relation ->
            PlanDayDraft(
                id = relation.day.sourceId,
                label = relation.day.label,
                title = relation.day.title,
                items = relation.items.sortedBy { it.position }.map { item ->
                    PlanItemDraft(
                        id = item.sourceId,
                        time = item.time,
                        title = item.title,
                        category = item.category,
                        cost = item.cost,
                        notes = item.notes,
                    )
                },
            )
        },
        sections = plan.sectionsJson.toSections(),
    ),
    sourceFileName = plan.sourceFileName,
    rawPath = plan.rawPath,
    generatedPath = plan.generatedPath,
    thumbnailPath = plan.thumbnailPath,
    compatibilityMode = plan.compatibilityMode,
    updatedAt = plan.updatedAt,
)

private fun List<PlanSectionDraft>.toJson(): String = JSONArray().apply {
    this@toJson.forEach { section ->
        put(JSONObject().put("title", section.title).put("content", section.content))
    }
}.toString()

private fun String.toSections(): List<PlanSectionDraft> = runCatching {
    val array = JSONArray(this)
    List(array.length()) { index ->
        val section = array.getJSONObject(index)
        PlanSectionDraft(section.optString("title"), section.optString("content"))
    }
}.getOrDefault(emptyList())
