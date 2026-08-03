package com.lujian.travelplan.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lujian.travelplan.LujianApplication
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThumbnailWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val planId = inputData.getLong("planId", -1)
        if (planId < 0) return@withContext Result.failure()
        val title = inputData.getString("title").orEmpty()
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 246, 239))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 37, 32) }
        val coral = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 107, 74) }
        canvas.drawRect(0f, 0f, 640f, 32f, coral)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 10f
        canvas.drawRoundRect(40f, 52f, 600f, 588f, 30f, 30f, ink)
        ink.style = Paint.Style.FILL
        ink.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        ink.textSize = 62f
        val lines = title.chunked(7).take(3)
        lines.forEachIndexed { index, line -> canvas.drawText(line, 76f, 220f + index * 78f, ink) }
        ink.textSize = 28f
        canvas.drawText("旅笺 · TRAVEL NOTE", 76f, 510f, ink)
        val file = File(applicationContext.filesDir, "plans/$planId/thumbnail.png").apply {
            parentFile?.mkdirs()
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val database = (applicationContext as LujianApplication).graph.database
        database.planDao().updateThumbnail(planId, file.relativeTo(applicationContext.filesDir).invariantSeparatorsPath)
        Result.success()
    }
}
