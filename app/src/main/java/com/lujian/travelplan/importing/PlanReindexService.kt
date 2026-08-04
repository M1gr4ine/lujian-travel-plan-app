package com.lujian.travelplan.importing

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.parser.CompositePlanParser
import com.lujian.travelplan.parser.ParseRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlanReindexService(
    private val context: Context,
    private val repository: PlanRepository,
) {
    private val parser = CompositePlanParser()
    private val locationResolver = LocationResolver(context)

    suspend fun reindexMissingDestinations() = withContext(Dispatchers.IO) {
        val plans = repository.getPlansOnce()
        plans
            .filter { it.parsed.destinations.isEmpty() }
            .forEach { plan ->
                runCatching {
                    val rawFile = File(context.filesDir, plan.rawPath)
                    if (!rawFile.isFile || rawFile.length() > HtmlFileValidator.MAX_BYTES) return@runCatching
                    val html = EncodingDetector.decode(rawFile.readBytes()).text
                    val parsed = parser.parse(ParseRequest(plan.sourceFileName, "text/html", html))
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
