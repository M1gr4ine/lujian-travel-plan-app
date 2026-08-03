@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lujian.travelplan.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.lujian.travelplan.data.PlanRepository
import com.lujian.travelplan.data.StoredPlan
import com.lujian.travelplan.model.PlanCapability
import com.lujian.travelplan.web.HtmlSecurityPolicy
import java.io.File
import kotlinx.coroutines.launch

private const val ASSET_HOST = "appassets.androidplatform.net"

@Composable
fun WebPlanScreen(
    plan: StoredPlan,
    original: Boolean,
    repository: PlanRepository,
    onBack: () -> Unit,
) {
    var compatibilityMode by remember(plan.updatedAt) { mutableStateOf(plan.compatibilityMode) }
    var confirmCompatibility by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original) "原始 HTML" else "移动版 HTML") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (plan.parsed.capability == PlanCapability.VIEW_ONLY) {
                        IconButton(onClick = {
                            if (compatibilityMode) {
                                compatibilityMode = false
                                scope.launch { repository.setCompatibilityMode(plan.id, false) }
                            } else {
                                confirmCompatibility = true
                            }
                        }) {
                            Icon(Icons.Filled.Security, contentDescription = "兼容模式")
                        }
                    }
                },
            )
        },
    ) { padding ->
        SafeHtmlWebView(
            plan = plan,
            original = original,
            repository = repository,
            compatibilityMode = compatibilityMode,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }

    if (confirmCompatibility) {
        AlertDialog(
            onDismissRequest = { confirmCompatibility = false },
            title = { Text("为此计划启用脚本？") },
            text = { Text("只开启页面 JavaScript；本地文件、明文 HTTP、下载和原生桥仍然关闭。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCompatibility = false
                    compatibilityMode = true
                    scope.launch { repository.setCompatibilityMode(plan.id, true) }
                }) { Text("启用兼容模式") }
            },
            dismissButton = { TextButton(onClick = { confirmCompatibility = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SafeHtmlWebView(
    plan: StoredPlan,
    original: Boolean,
    repository: PlanRepository,
    compatibilityMode: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val htmlFile = repository.htmlFile(plan, original)
    val relativePath = htmlFile.relativeTo(context.filesDir).invariantSeparatorsPath
    val loader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/plan/", WebViewAssetLoader.InternalStoragePathHandler(context, context.filesDir))
            .build()
    }
    val webView = remember { WebView(context) }
    val security = HtmlSecurityPolicy.resolve(plan.parsed.capability, compatibilityMode)

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = {
            webView.apply {
                settings.javaScriptEnabled = security.javaScriptEnabled
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.domStorageEnabled = compatibilityMode
                setDownloadListener { _, _, _, _, _ -> }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.host == ASSET_HOST && uri.scheme == "https") return false
                        if (request.isForMainFrame && uri.scheme == "https") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                        return true
                    }
                }
                loadUrl("https://$ASSET_HOST/plan/${Uri.encode(relativePath, "/")}")
            }
        },
        update = { view ->
            view.settings.javaScriptEnabled = security.javaScriptEnabled
            view.settings.domStorageEnabled = security.javaScriptEnabled
        },
        modifier = modifier,
    )
}
