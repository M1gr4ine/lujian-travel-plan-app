package com.lujian.travelplan.importing

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.data.CURRENT_PLAN_DATA_REVISION
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.model.ParsedPlan
import com.lujian.travelplan.parser.CompositePlanParser
import com.lujian.travelplan.parser.LujianJsonParser
import com.lujian.travelplan.parser.ParseRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlanReindexService(
    private val context: Context,
    private val repository: PlanRepository,
) {
    private companion object {
        const val TAG = "PlanReindexService"
    }

    private val structuredParser = LujianJsonParser()
    private val destinationParser = CompositePlanParser()
    private val locationResolver = LocationResolver(context)

    suspend fun reindexMissingDestinations() = withContext(Dispatchers.IO) {
        var plans = repository.getPlansOnce()
        plans
            .filter(PlanReindexPolicy::shouldRefreshStructuredData)
            .forEach { plan ->
                runCatching {
                    val rawFile = File(context.filesDir, plan.rawPath)
                    if (!rawFile.isFile || rawFile.length() > HtmlFileValidator.MAX_BYTES) {
                        Log.w(TAG, "跳过计划 ${plan.id}：原始文件不存在或超出大小限制")
                        return@runCatching
                    }
                    val html = EncodingDetector.decode(rawFile.readBytes()).text
                    val parsed = structuredParser.parse(ParseRequest(plan.sourceFileName, "text/html", html))
                    if (parsed == null) {
                        Log.w(TAG, "跳过计划 ${plan.id}：未识别到兼容的结构化数据")
                        return@runCatching
                    }
                    repository.refreshParsed(
                        plan.id,
                        PlanReindexPolicy.mergeForPlan(plan, parsed),
                    )
                    Log.i(TAG, "计划 ${plan.id} 已刷新到数据版本 $CURRENT_PLAN_DATA_REVISION")
                }.onFailure { error ->
                    Log.e(TAG, "刷新计划 ${plan.id} 失败", error)
                }
            }
        plans = repository.getPlansOnce()
        plans
            .filter { it.parsed.destinations.isEmpty() }
            .forEach { plan ->
                runCatching {
                    val rawFile = File(context.filesDir, plan.rawPath)
                    if (!rawFile.isFile || rawFile.length() > HtmlFileValidator.MAX_BYTES) return@runCatching
                    val html = EncodingDetector.decode(rawFile.readBytes()).text
                    val parsed = destinationParser.parse(ParseRequest(plan.sourceFileName, "text/html", html))
                        ?: return@runCatching
                    val destinations = parsed.destinations.map { destination ->
                        if (destination.latitude != null && destination.longitude != null) destination
                        else locationResolver.resolve(destination.name).firstOrNull()?.let { candidate ->
                            destination.copy(
                                countryCode = candidate.countryCode ?: destination.countryCode,
                                latitude = candidate.latitude,
                                longitude = candidate.longitude,
                            )
                        } ?: destination
                    }
                    if (destinations.isNotEmpty()) repository.replaceDestinations(plan.id, destinations)
                }
            }
        plans
            .filter { it.thumbnailPath?.endsWith(ThumbnailWorker.OUTPUT_FILE_NAME) != true }
            .forEach { plan ->
                val input = Data.Builder()
                    .putLong("planId", plan.id)
                    .putString("title", plan.parsed.title)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "content-thumbnail-v7-${plan.id}",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ThumbnailWorker>().setInputData(input).build(),
                )
            }
    }
}

object PlanReindexPolicy {
    fun shouldRefreshStructuredData(plan: StoredPlan): Boolean =
        plan.parsed.capability == PlanCapability.ENHANCED &&
            plan.dataRevision < CURRENT_PLAN_DATA_REVISION

    fun mergeForPlan(plan: StoredPlan, refreshed: ParsedPlan): ParsedPlan {
        val resolved = mergePreservingResolvedDestinations(plan.parsed, refreshed)
        if (plan.generatedPath == null) return resolved

        val sourceDays = resolved.days.associateBy { it.id }
        val mergedDays = plan.parsed.days.map { currentDay ->
            val sourceDay = sourceDays[currentDay.id] ?: return@map currentDay
            val sourceItems = sourceDay.items.associateBy { it.id }
            currentDay.copy(
                items = currentDay.items.map { currentItem ->
                    val sourceItem = sourceItems[currentItem.id] ?: return@map currentItem
                    currentItem.copy(
                        placeId = currentItem.placeId ?: sourceItem.placeId,
                        transport = currentItem.transport ?: sourceItem.transport,
                        mapLinks = currentItem.mapLinks.takeIf { it.amap != null || it.baidu != null }
                            ?: sourceItem.mapLinks,
                    )
                },
                summary = currentDay.summary ?: sourceDay.summary,
                budget = currentDay.budget ?: sourceDay.budget,
                backup = currentDay.backup ?: sourceDay.backup,
            )
        }.ifEmpty { resolved.days }
        val currentPlaces = plan.parsed.places.associateBy { it.id }
        val mergedPlaces = resolved.places.map { sourcePlace ->
            val currentPlace = currentPlaces[sourcePlace.id] ?: return@map sourcePlace
            sourcePlace.copy(
                name = currentPlace.name.ifBlank { sourcePlace.name },
                address = currentPlace.address ?: sourcePlace.address,
                latitude = currentPlace.latitude ?: sourcePlace.latitude,
                longitude = currentPlace.longitude ?: sourcePlace.longitude,
                mapLinks = currentPlace.mapLinks.takeIf { it.amap != null || it.baidu != null }
                    ?: sourcePlace.mapLinks,
            )
        }
        return plan.parsed.copy(
            title = resolved.title,
            destinations = resolved.destinations,
            days = mergedDays,
            dateRange = plan.parsed.dateRange ?: resolved.dateRange,
            travelers = plan.parsed.travelers ?: resolved.travelers,
            style = plan.parsed.style ?: resolved.style,
            baseArea = plan.parsed.baseArea ?: resolved.baseArea,
            budget = plan.parsed.budget ?: resolved.budget,
            accommodationBudget = plan.parsed.accommodationBudget ?: resolved.accommodationBudget,
            assumptions = plan.parsed.assumptions.ifEmpty { resolved.assumptions },
            places = mergedPlaces,
            sourcePayloadJson = resolved.sourcePayloadJson,
        )
    }

    fun mergePreservingResolvedDestinations(current: ParsedPlan, refreshed: ParsedPlan): ParsedPlan {
        val currentByName = current.destinations.associateBy { it.name }
        return refreshed.copy(
            destinations = refreshed.destinations.mapIndexed { index, destination ->
                val resolved = currentByName[destination.name] ?: current.destinations.getOrNull(index)
                if (resolved?.latitude != null && resolved.longitude != null) {
                    destination.copy(
                        countryCode = resolved.countryCode ?: destination.countryCode,
                        latitude = resolved.latitude,
                        longitude = resolved.longitude,
                    )
                } else destination
            },
        )
    }
}
