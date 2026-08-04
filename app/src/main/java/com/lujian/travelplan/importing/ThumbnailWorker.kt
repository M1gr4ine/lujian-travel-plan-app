package com.lujian.travelplan.importing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lujian.travelplan.LujianApplication
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ThumbnailWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val planId = inputData.getLong("planId", -1)
        if (planId < 0) return Result.failure()
        val title = inputData.getString("title").orEmpty()
        val database = (applicationContext as LujianApplication).graph.database
        val stored = database.planDao().findById(planId)?.plan ?: return Result.failure()
        val rawFile = File(applicationContext.filesDir, stored.rawPath)
        val html = withContext(Dispatchers.IO) {
            if (rawFile.isFile) EncodingDetector.decode(rawFile.readBytes()).text else ""
        }
        val customCover = withContext(Dispatchers.IO) { loadCustomCover() }
        val titleCoverHtml = if (html.isBlank()) null else HtmlTitleCoverExtractor.extract(html)
        val bitmap = customCover ?: withTimeoutOrNull(6_000) {
            titleCoverHtml?.let { renderHtmlFirstScreen(it) }
        } ?: fallbackThumbnail(title)

        return withContext(Dispatchers.IO) {
            val file = File(applicationContext.filesDir, "plans/$planId/content-thumbnail-v4.png").apply {
                parentFile?.mkdirs()
            }
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 92, it) }
            bitmap.recycle()
            val relativePath = file.relativeTo(applicationContext.filesDir).invariantSeparatorsPath
            database.planDao().updateThumbnail(planId, relativePath)
            stored.thumbnailPath
                ?.takeIf { it != relativePath }
                ?.let { File(applicationContext.filesDir, it).delete() }
            Result.success()
        }
    }

    private fun loadCustomCover(): Bitmap? {
        val relativePath = inputData.getString(INPUT_CUSTOM_COVER_PATH)?.takeIf { it.isNotBlank() } ?: return null
        val filesRoot = applicationContext.filesDir.canonicalFile
        val coverFile = File(filesRoot, relativePath).canonicalFile
        if (!coverFile.path.startsWith(filesRoot.path + File.separator) || !coverFile.isFile) return null
        return BitmapFactory.decodeFile(coverFile.absolutePath)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun renderHtmlFirstScreen(html: String): Bitmap = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(applicationContext).apply {
                setBackgroundColor(Color.rgb(250, 246, 239))
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    loadsImagesAutomatically = false
                    domStorageEnabled = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
            }
            var captured = false
            var destroyed = false
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                override fun onPageFinished(view: WebView, url: String?) {
                    if (captured) return
                    captured = true
                    view.postDelayed({
                        if (!continuation.isActive) {
                            if (!destroyed) {
                                destroyed = true
                                view.stopLoading()
                                view.destroy()
                            }
                            return@postDelayed
                        }
                        val width = 640
                        val height = 640
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, width, height)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).apply {
                            drawColor(Color.rgb(250, 246, 239))
                            view.draw(this)
                        }
                        destroyed = true
                        view.destroy()
                        continuation.resume(bitmap)
                    }, 120)
                }
            }
            continuation.invokeOnCancellation {
                webView.post {
                    if (!destroyed) {
                        destroyed = true
                        webView.stopLoading()
                        webView.destroy()
                    }
                }
            }
            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    private fun fallbackThumbnail(title: String): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 246, 239))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 37, 32) }
        val coral = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 107, 74) }
        canvas.drawRect(0f, 0f, 640f, 26f, coral)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 8f
        canvas.drawRoundRect(38f, 44f, 602f, 602f, 28f, 28f, ink)
        ink.style = Paint.Style.FILL
        ink.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        ink.textSize = 54f
        title.chunked(8).take(3).forEachIndexed { index, line ->
            canvas.drawText(line, 70f, 265f + index * 68f, ink)
        }
        return bitmap
    }

    companion object {
        const val INPUT_CUSTOM_COVER_PATH = "customCoverPath"
    }
}
