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
        val metadata = JSONObject().apply {
            put("schemaVersion", 1)
            put("title", plan.title)
            put("destinations", JSONArray().apply {
                plan.destinations.forEach { destination ->
                    put(JSONObject().apply {
                        put("name", destination.name)
                        put("countryCode", destination.countryCode)
                        put("latitude", destination.latitude)
                        put("longitude", destination.longitude)
                    })
                }
            })
            put("days", JSONArray().apply {
                plan.days.forEach { day ->
                    put(JSONObject().apply {
                        put("id", day.id)
                        put("label", day.label)
                        put("title", day.title)
                        put("items", JSONArray().apply {
                            day.items.forEach { item ->
                                put(JSONObject().apply {
                                    put("id", item.id)
                                    put("time", item.time)
                                    put("title", item.title)
                                    put("category", item.category)
                                    put("cost", item.cost)
                                    put("notes", item.notes)
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
}
