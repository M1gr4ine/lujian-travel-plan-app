package com.lujian.travelplan.importing

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

object HtmlTitleCoverExtractor {
    private val selectors = listOf(
        "[data-lujian-cover]",
        ".hero h1",
        "main h1",
        "h1",
        ".logo-title",
    )

    fun extract(html: String): String? {
        val document = Jsoup.parse(html)
        val selected = selectors.asSequence()
            .flatMap { selector -> document.select(selector).asSequence() }
            .firstOrNull { element -> element.text().isNotBlank() }
            ?: return null
        val cover = selected.clone().apply {
            if (hasAttr("data-lujian-cover")) {
                select("button, script, [data-lujian-cover-exclude], p:not(.brand-sub)").remove()
            }
        }
        val parent = selected.parent()
        val parentClass = parent?.className().orEmpty()
        val parentId = parent?.id().orEmpty()
        val parentAttributes = buildString {
            if (parentClass.isNotBlank()) append(" class=\"").append(Entities.escape(parentClass)).append('"')
            if (parentId.isNotBlank()) append(" id=\"").append(Entities.escape(parentId)).append('"')
        }
        val styles = document.select("style").joinToString("\n") { it.outerHtml() }

        return """<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  $styles
  <style>
    html,body{width:100%;height:100%;margin:0!important;overflow:hidden!important;background:#FAF6EF!important}
    body{display:grid!important;place-items:start stretch!important;padding:0!important;color:#2A2520!important}
    .lujian-cover-root{width:100%;box-sizing:border-box}
    .lujian-cover-root>div{width:100%;max-width:none!important;margin:0!important;padding:30px 34px!important;box-sizing:border-box}
    .lujian-cover-root h1{margin:0!important;max-width:none!important}
  </style>
</head>
<body><div class="lujian-cover-root"><div$parentAttributes>${cover.outerHtml()}</div></div></body>
</html>"""
    }
}
