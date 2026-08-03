package com.lujian.travelplan.importing

import android.content.Context
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
                    if (!rawFile.isFile || rawFile.length() > HtmlFileValidator.MAX_BYTES) return@runCatching
                    val html = EncodingDetector.decode(rawFile.readBytes()).text
                    val parsed = structuredParser.parse(ParseRequest(plan.sourceFileName, "text/html", html))
                        ?: return@runCatching
                    repository.refreshParsed(
                        plan.id,
                        PlanReindexPolicy.mergePreservingResolvedDestinations(plan.parsed, parsed),
                    )
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
                    "content-thumbnail-v5-${plan.id}",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ThumbnailWorker>().setInputData(input).build(),
                )
            }
    }
}

object PlanReindexPolicy {
    fun shouldRefreshStructuredData(plan: StoredPlan): Boolean =
        plan.parsed.capability == PlanCapability.ENHANCED &&
            plan.generatedPath == null &&
            plan.dataRevision < CURRENT_PLAN_DATA_REVISION

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
