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
import com.lujian.travelplan.model.PlanMapLinks
import com.lujian.travelplan.model.PlanPlaceDraft
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
    val dataRevision: Int = CURRENT_PLAN_DATA_REVISION,
    val updatedAt: Long,
)

const val CURRENT_PLAN_DATA_REVISION = 2

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
                    sectionsJson = parsed.toExtrasJson(),
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
                    sectionsJson = parsed.toExtrasJson(),
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
                sectionsJson = parsed.toExtrasJson(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun refreshParsed(planId: Long, parsed: ParsedPlan) = database.withTransaction {
        val stored = dao.findById(planId) ?: return@withTransaction
        clearGraph(planId)
        insertGraph(planId, parsed)
        dao.updatePlan(
            stored.plan.copy(
                title = parsed.title,
                capability = parsed.capability.name,
                sectionsJson = parsed.toExtrasJson(),
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

private fun PlanWithDetails.toStoredPlan(): StoredPlan {
    val extras = plan.sectionsJson.toExtras()
    return StoredPlan(
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
                    val itemExtras = extras.items[item.sourceId]
                    PlanItemDraft(
                        id = item.sourceId,
                        time = item.time,
                        title = item.title,
                        category = item.category,
                        cost = item.cost,
                        notes = item.notes,
                        placeId = itemExtras?.placeId,
                        transport = itemExtras?.transport,
                        mapLinks = itemExtras?.mapLinks ?: PlanMapLinks(),
                    )
                },
                summary = extras.days[relation.day.sourceId]?.summary,
                budget = extras.days[relation.day.sourceId]?.budget,
                backup = extras.days[relation.day.sourceId]?.backup,
            )
        },
        sections = extras.sections,
        dateRange = extras.dateRange,
        travelers = extras.travelers,
        style = extras.style,
        baseArea = extras.baseArea,
        budget = extras.budget,
        accommodationBudget = extras.accommodationBudget,
        assumptions = extras.assumptions,
        places = extras.places,
        sourcePayloadJson = extras.sourcePayloadJson,
    ),
    sourceFileName = plan.sourceFileName,
    rawPath = plan.rawPath,
    generatedPath = plan.generatedPath,
    thumbnailPath = plan.thumbnailPath,
    compatibilityMode = plan.compatibilityMode,
    dataRevision = extras.dataRevision,
    updatedAt = plan.updatedAt,
)
}

private fun ParsedPlan.toExtrasJson(): String = JSONObject().apply {
    put("dataRevision", CURRENT_PLAN_DATA_REVISION)
    put("sections", sections.toJsonArray())
    put("dateRange", dateRange)
    put("travelers", travelers)
    put("style", style)
    put("baseArea", baseArea)
    put("budget", budget)
    put("accommodationBudget", accommodationBudget)
    put("assumptions", JSONArray(assumptions))
    put("sourcePayloadJson", sourcePayloadJson)
    put("places", JSONArray().apply {
        places.forEach { place ->
            put(JSONObject().apply {
                put("id", place.id)
                put("name", place.name)
                put("address", place.address)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("mapLinks", place.mapLinks.toJson())
            })
        }
    })
    put("days", JSONArray().apply {
        days.forEach { day ->
            put(JSONObject().apply {
                put("id", day.id)
                put("summary", day.summary)
                put("budget", day.budget)
                put("backup", day.backup)
            })
        }
    })
    put("items", JSONArray().apply {
        days.flatMap { it.items }.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("placeId", item.placeId)
                put("transport", item.transport)
                put("mapLinks", item.mapLinks.toJson())
            })
        }
    })
}.toString()

private fun List<PlanSectionDraft>.toJsonArray(): JSONArray = JSONArray().apply {
    this@toJsonArray.forEach { section ->
        put(JSONObject().put("title", section.title).put("content", section.content))
    }
}

private fun PlanMapLinks.toJson(): JSONObject = JSONObject().apply {
    put("amap", amap)
    put("baidu", baidu)
}

private data class DayExtras(val summary: String?, val budget: String?, val backup: String?)

private data class ItemExtras(val placeId: String?, val transport: String?, val mapLinks: PlanMapLinks)

private data class PlanExtras(
    val dataRevision: Int = 0,
    val sections: List<PlanSectionDraft> = emptyList(),
    val dateRange: String? = null,
    val travelers: String? = null,
    val style: String? = null,
    val baseArea: String? = null,
    val budget: String? = null,
    val accommodationBudget: String? = null,
    val assumptions: List<String> = emptyList(),
    val places: List<PlanPlaceDraft> = emptyList(),
    val sourcePayloadJson: String? = null,
    val days: Map<String, DayExtras> = emptyMap(),
    val items: Map<String, ItemExtras> = emptyMap(),
)

private fun String.toExtras(): PlanExtras = runCatching {
    val trimmed = trimStart()
    if (trimmed.startsWith("[")) {
        return@runCatching PlanExtras(sections = JSONArray(this).toSections())
    }
    val root = JSONObject(this)
    val places = root.optJSONArray("places")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index) }.map { place ->
            PlanPlaceDraft(
                id = place.optString("id"),
                name = place.optString("name"),
                address = place.optNullableString("address"),
                latitude = place.optNullableDouble("latitude"),
                longitude = place.optNullableDouble("longitude"),
                mapLinks = place.optJSONObject("mapLinks").toMapLinks(),
            )
        }
    }.orEmpty()
    val days = root.optJSONArray("days")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index) }.associate { day ->
            day.optString("id") to DayExtras(
                summary = day.optNullableString("summary"),
                budget = day.optNullableString("budget"),
                backup = day.optNullableString("backup"),
            )
        }
    }.orEmpty()
    val items = root.optJSONArray("items")?.let { array ->
        List(array.length()) { index -> array.getJSONObject(index) }.associate { item ->
            item.optString("id") to ItemExtras(
                placeId = item.optNullableString("placeId"),
                transport = item.optNullableString("transport"),
                mapLinks = item.optJSONObject("mapLinks").toMapLinks(),
            )
        }
    }.orEmpty()
    PlanExtras(
        dataRevision = root.optInt("dataRevision", 0),
        sections = root.optJSONArray("sections")?.toSections().orEmpty(),
        dateRange = root.optNullableString("dateRange"),
        travelers = root.optNullableString("travelers"),
        style = root.optNullableString("style"),
        baseArea = root.optNullableString("baseArea"),
        budget = root.optNullableString("budget"),
        accommodationBudget = root.optNullableString("accommodationBudget"),
        assumptions = root.optJSONArray("assumptions")?.let { array ->
            List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
        }.orEmpty(),
        places = places,
        sourcePayloadJson = root.optNullableString("sourcePayloadJson"),
        days = days,
        items = items,
    )
}.getOrDefault(PlanExtras())

private fun JSONArray.toSections(): List<PlanSectionDraft> =
    List(length()) { index ->
        val section = getJSONObject(index)
        PlanSectionDraft(section.optString("title"), section.optString("content"))
    }

private fun JSONObject?.toMapLinks(): PlanMapLinks = PlanMapLinks(
    amap = this?.optNullableString("amap"),
    baidu = this?.optNullableString("baidu"),
)

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).ifBlank { null } else null

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null
