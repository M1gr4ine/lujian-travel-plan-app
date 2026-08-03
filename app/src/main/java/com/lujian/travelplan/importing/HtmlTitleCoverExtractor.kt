package com.lujian.travelplan.importing

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

object HtmlTitleCoverExtractor {
    data class CoverText(
        val brandTitle: String,
        val brandSub: String?,
        val headlineLines: List<String>,
    )

    private val selectors = listOf(
        "[data-lujian-cover]",
        ".hero h1",
        "main h1",
        "h1",
        ".logo-title",
    )

    fun extract(html: String): String? {
        val document = Jsoup.parse(html)
        val cover = legacyComposite(document) ?: selectedCover(document) ?: return null
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
    .lujian-cover-root>div{width:100%;max-width:none!important;margin:0!important;box-sizing:border-box}
    .lujian-cover-single{padding:30px 34px!important}
    .lujian-cover-composite>header{padding:20px 24px!important;box-sizing:border-box}
    .lujian-cover-composite>.hero{padding:42px 24px 20px!important;box-sizing:border-box}
    .lujian-cover-root h1{margin:0!important;max-width:none!important}
  </style>
</head>
<body><div class="lujian-cover-root">${cover.html}</div></body>
</html>"""
    }

    fun extractText(html: String): CoverText? {
        val document = Jsoup.parse(html)
        val brandTitle = sequenceOf(
            "[data-lujian-cover] .brand-title",
            "[data-lujian-cover] .logo-title",
            ".logo-title",
            "#mobile-brand-title",
        ).mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull(String::isNotBlank)
            ?: return null
        val brandSub = sequenceOf(
            "[data-lujian-cover] .brand-sub",
            "[data-lujian-cover] .logo-sub",
            ".logo-sub",
            "#mobile-brand-sub",
        ).mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull(String::isNotBlank)
        val headline = sequenceOf(
            "[data-lujian-cover] h1",
            ".hero h1",
            "main h1",
            "h1",
        ).mapNotNull { selector -> document.selectFirst(selector) }
            .firstOrNull { it.text().isNotBlank() }
            ?: return null
        val lines = headline.html()
            .split(Regex("(?i)<br\\s*/?>"))
            .map { fragment -> Jsoup.parseBodyFragment(fragment).text().trim() }
            .filter(String::isNotBlank)
            .ifEmpty { listOf(headline.text().trim()) }
        return CoverText(brandTitle, brandSub, lines)
    }

    private fun legacyComposite(document: org.jsoup.nodes.Document): CoverFragment? {
        if (document.selectFirst("[data-lujian-cover]") != null) return null
        val logo = document.selectFirst(".logo-block")?.takeIf { it.text().isNotBlank() } ?: return null
        val heroTitle = document.selectFirst(".hero h1")?.takeIf { it.text().isNotBlank() } ?: return null
        val header = document.selectFirst("header")
        val hero = heroTitle.parent()
        val headerAttributes = attributes(header?.className().orEmpty(), header?.id().orEmpty())
        val heroAttributes = attributes(hero?.className().orEmpty().ifBlank { "hero" }, hero?.id().orEmpty())
        return CoverFragment(
            """<div class="lujian-cover-composite"><header$headerAttributes>${logo.clone().outerHtml()}</header><div$heroAttributes>${heroTitle.clone().outerHtml()}</div></div>""",
        )
    }

    private fun selectedCover(document: org.jsoup.nodes.Document): CoverFragment? {
        val selected = selectors.asSequence()
            .flatMap { selector -> document.select(selector).asSequence() }
            .firstOrNull { element -> element.text().isNotBlank() }
            ?: return null
        val cloned = selected.clone().apply {
            if (hasAttr("data-lujian-cover")) {
                select("button, script, [data-lujian-cover-exclude], p:not(.brand-sub)").remove()
            }
        }
        val parent = selected.parent()
        return CoverFragment(
            """<div class="lujian-cover-single"${attributes(parent?.className().orEmpty(), parent?.id().orEmpty())}>${cloned.outerHtml()}</div>""",
        )
    }

    private fun attributes(className: String, id: String): String = buildString {
        if (className.isNotBlank()) append(" class=\"").append(Entities.escape(className)).append('"')
        if (id.isNotBlank()) append(" id=\"").append(Entities.escape(id)).append('"')
    }

    private data class CoverFragment(val html: String)
}
