package com.lujian.travelplan.parser

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup

sealed interface LujianHtmlDetection {
    data object Absent : LujianHtmlDetection

    data class Compatible(val payload: JSONObject) : LujianHtmlDetection

    data class Incompatible(val reasons: List<String>) : LujianHtmlDetection
}

/** 只校验旅笺可解析的数据契约；页面布局与依赖由生成技能自行校验。 */
object LujianHtmlContract {
    const val SCHEMA_VERSION = 1
    const val MAPLIBRE_SCRIPT = "https://unpkg.com/maplibre-gl@5.14.0/dist/maplibre-gl.js"
    const val MAPLIBRE_STYLESHEET = "https://unpkg.com/maplibre-gl@5.14.0/dist/maplibre-gl.css"

    fun inspect(html: String): LujianHtmlDetection {
        val document = Jsoup.parse(html)
        val blocks = document.select("script#lujian-plan")
        if (blocks.isEmpty()) return LujianHtmlDetection.Absent

        val reasons = mutableListOf<String>()
        if (blocks.size != 1) reasons += "必须且只能包含一个 lujian-plan 元数据块"
        val block = blocks.first()
        if (block?.attr("type") != "application/json") {
            reasons += "lujian-plan 元数据块必须使用 application/json"
        }

        val payload = block?.let { element ->
            runCatching {
                JSONObject(element.data().ifBlank { element.html() })
            }.getOrElse { error ->
                val detail = if (error is JSONException) error.message else null
                reasons += "lujian-plan JSON 无效${detail?.let { "：$it" }.orEmpty()}"
                null
            }
        }
        if (payload != null) reasons += validatePayload(payload)

        return if (reasons.isEmpty() && payload != null) {
            LujianHtmlDetection.Compatible(payload)
        } else {
            LujianHtmlDetection.Incompatible(reasons.distinct())
        }
    }

    private fun validatePayload(payload: JSONObject): List<String> {
        val reasons = mutableListOf<String>()
        val schemaVersion = payload.opt("schemaVersion")
        if (schemaVersion !is Number || schemaVersion.toDouble() != SCHEMA_VERSION.toDouble()) {
            reasons += "schemaVersion 必须为数字 1"
        }
        if (payload.opt("title") !is String || payload.optString("title").isBlank()) {
            reasons += "title 不能为空"
        }
        val destinations = payload.opt("destinations") as? JSONArray
        if (destinations == null || destinations.length() == 0) reasons += "destinations 必须是非空数组"
        val days = payload.opt("days") as? JSONArray
        if (days == null || days.length() == 0) {
            reasons += "days 必须是非空数组"
            return reasons
        }

        repeat(days.length()) { dayIndex ->
            val day = days.opt(dayIndex) as? JSONObject
            if (day == null) {
                reasons += "days[$dayIndex] 必须是对象"
                return@repeat
            }
            listOf("id", "label", "title").forEach { field ->
                if (day.opt(field) !is String || day.optString(field).isBlank()) {
                    reasons += "days[$dayIndex].$field 不能为空"
                }
            }
            val items = day.opt("items") as? JSONArray
            if (items == null) {
                reasons += "days[$dayIndex].items 必须是数组"
                return@repeat
            }
            repeat(items.length()) { itemIndex ->
                val item = items.opt(itemIndex) as? JSONObject
                if (item == null) {
                    reasons += "days[$dayIndex].items[$itemIndex] 必须是对象"
                    return@repeat
                }
                listOf("id", "time", "title", "category").forEach { field ->
                    if (item.opt(field) !is String || item.optString(field).isBlank()) {
                        reasons += "days[$dayIndex].items[$itemIndex].$field 不能为空"
                    }
                }
                if (item.opt("notes") !is String) {
                    reasons += "days[$dayIndex].items[$itemIndex].notes 必须是字符串"
                }
            }
        }
        return reasons
    }
}
