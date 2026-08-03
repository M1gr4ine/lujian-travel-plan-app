package com.lujian.travelplan.export

import com.lujian.travelplan.model.ParsedPlan
import org.json.JSONArray
import org.json.JSONObject

object MobileHtmlGenerator {
    fun generate(plan: ParsedPlan): String {
        val primaryDestination = plan.destinations.firstOrNull()
        val destinationMeta = primaryDestination?.let { destination ->
            buildString {
                append("<meta name=\"lujian:destination\" content=\"")
                append(escape(destination.name))
                append("\">")
                destination.countryCode?.let { append("<meta name=\"lujian:country-code\" content=\"${escape(it)}\">") }
                destination.latitude?.let { append("<meta name=\"lujian:latitude\" content=\"$it\">") }
                destination.longitude?.let { append("<meta name=\"lujian:longitude\" content=\"$it\">") }
            }
        }.orEmpty()
        val sourceMetadata = plan.sourcePayloadJson
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: JSONObject()
        val sourceDestinations = sourceMetadata.objectsBy("destinations", "name")
        val sourceDays = sourceMetadata.objectsBy("days", "id")
        val sourceItems = sourceDays.values
            .flatMap { day -> day.optJSONArray("items").objectsBy("id").entries }
            .associate { it.toPair() }
        val sourcePlaces = sourceMetadata.objectsBy("places", "id")
        val metadata = sourceMetadata.copyObject().apply {
            put("schemaVersion", 1)
            put("title", plan.title)
            put("destination", primaryDestination?.name.orEmpty())
            put("dateRange", plan.dateRange.orEmpty())
            put("travelers", plan.travelers.orEmpty())
            put("style", plan.style.orEmpty())
            put("baseArea", plan.baseArea.orEmpty())
            put("budget", plan.budget.orEmpty())
            put("assumptions", JSONArray(plan.assumptions))
            put("destinations", JSONArray().apply {
                plan.destinations.forEach { destination ->
                    put(sourceDestinations[destination.name].copyOrNew().apply {
                        put("name", destination.name)
                        put("countryCode", destination.countryCode.orEmpty())
                        destination.latitude?.let { put("latitude", it) }
                        destination.longitude?.let { put("longitude", it) }
                    })
                }
            })
            put("days", JSONArray().apply {
                plan.days.forEach { day ->
                    put(sourceDays[day.id].copyOrNew().apply {
                        put("id", day.id)
                        put("label", day.label)
                        put("title", day.title)
                        put("summary", day.summary.orEmpty())
                        put("budget", day.budget.orEmpty())
                        put("backup", day.backup.orEmpty())
                        put("items", JSONArray().apply {
                            day.items.forEach { item ->
                                put(sourceItems[item.id].copyOrNew().apply {
                                    put("id", item.id)
                                    put("time", item.time?.takeIf { it.isNotBlank() } ?: "时间待定")
                                    put("title", item.title)
                                    put("category", item.category?.takeIf { it.isNotBlank() } ?: "other")
                                    put("cost", item.cost.orEmpty())
                                    put("notes", item.notes.orEmpty())
                                    put("placeId", item.placeId.orEmpty())
                                    put("transport", item.transport.orEmpty())
                                    put("mapLinks", optJSONObject("mapLinks").copyOrNew().apply {
                                        put("amap", item.mapLinks.amap.orEmpty())
                                        put("baidu", item.mapLinks.baidu.orEmpty())
                                    })
                                })
                            }
                        })
                    })
                }
            })
            put("sections", JSONArray().apply {
                plan.sections.forEach { section ->
                    put(JSONObject().apply {
                        put("title", section.title)
                        put("content", section.content)
                    })
                }
            })
            put("places", JSONArray().apply {
                plan.places.forEach { place ->
                    put(sourcePlaces[place.id].copyOrNew().apply {
                        put("id", place.id)
                        put("name", place.name)
                        put("address", place.address.orEmpty())
                        place.latitude?.let { put("latitude", it) }
                        place.longitude?.let { put("longitude", it) }
                        put("mapLinks", optJSONObject("mapLinks").copyOrNew().apply {
                            put("amap", place.mapLinks.amap.orEmpty())
                            put("baidu", place.mapLinks.baidu.orEmpty())
                        })
                    })
                }
            })
            put("trip", optJSONObject("trip").copyOrNew().apply {
                put("title", plan.title)
                put("destination", primaryDestination?.name.orEmpty())
                put("accommodationBudget", plan.accommodationBudget.orEmpty())
            })
        }

        val daySections = plan.days.joinToString("\n") { day ->
            val cards = day.items.joinToString("\n") { item ->
                """
                <article class="card">
                  <div class="time">${escape(item.time.orEmpty())}</div>
                  <div><span class="tag">${escape(item.category.orEmpty())}</span><h3>${escape(item.title)}</h3>
                  <p>${escape(item.notes.orEmpty())}</p><b>${escape(item.cost.orEmpty())}</b></div>
                </article>
                """.trimIndent()
            }
            """
            <section>
              <header><small>${escape(day.label)}</small><h2>${escape(day.title)}</h2></header>
              $cards
            </section>
            """.trimIndent()
        }
        val extraSections = plan.sections.joinToString("\n") { section ->
            "<section><header><h2>${escape(section.title)}</h2></header><p>${escape(section.content)}</p></section>"
        }

        return """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">$destinationMeta
<title>${escape(plan.title)}</title>
<style>
:root{--paper:#FAF6EF;--ink:#2A2520;--coral:#FF6B4A;--gold:#F2B43A}*{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font-family:serif;padding:28px 18px 60px}main{max-width:760px;margin:auto}h1{font-size:42px;line-height:1;margin:12px 0 32px}section{margin:0 0 36px}section>header{border-bottom:4px solid var(--ink);margin-bottom:14px}.card{display:grid;grid-template-columns:70px 1fr;gap:14px;border:3px solid var(--ink);border-radius:18px;padding:16px;margin:12px 0;background:#fff;box-shadow:5px 5px 0 var(--gold)}.time{font-weight:900;color:var(--coral)}.tag{font:700 12px sans-serif;background:var(--gold);padding:4px 8px;border:2px solid var(--ink);border-radius:999px}.card h3{margin:8px 0}.card p{white-space:pre-wrap}</style>
<script id="lujian-plan" type="application/json">${metadata}</script></head>
<body><main><p>旅笺 · TRAVEL NOTE</p><h1>${escape(plan.title)}</h1>$daySections$extraSections</main></body></html>"""
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> char
                },
            )
        }
    }

    private fun JSONObject.copyObject(): JSONObject = JSONObject(toString())

    private fun JSONObject?.copyOrNew(): JSONObject = this?.copyObject() ?: JSONObject()

    private fun JSONObject.objectsBy(arrayName: String, key: String): Map<String, JSONObject> =
        optJSONArray(arrayName).objectsBy(key)

    private fun JSONArray?.objectsBy(key: String): Map<String, JSONObject> = buildMap {
        val array = this@objectsBy ?: return@buildMap
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            item.optString(key).takeIf { it.isNotBlank() }?.let { put(it, item) }
        }
    }
}
