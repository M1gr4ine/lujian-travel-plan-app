#!/usr/bin/env python3
"""Create a self-contained 旅笺-compatible travel-plan HTML file."""

from __future__ import annotations

import argparse
import html
import json
import math
from datetime import date, timedelta
from pathlib import Path
from typing import Any

from generate_map_links import ensure_place_links
from validate_itinerary import validate


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def first(*values: Any, default: Any = "") -> Any:
    return next((value for value in values if value not in (None, "")), default)


def note_text(value: Any) -> str:
    if isinstance(value, list):
        return "\n".join(str(item) for item in value if item not in (None, ""))
    return str(value) if value not in (None, "") else ""


def valid_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def coordinates_from(value: Any) -> tuple[float, float] | None:
    if not isinstance(value, dict):
        return None
    latitude = first(value.get("latitude"), value.get("lat"), default=None)
    longitude = first(value.get("longitude"), value.get("lng"), value.get("lon"), default=None)
    if valid_number(latitude) and valid_number(longitude):
        return float(latitude), float(longitude)
    return None


def build_destinations(data: dict[str, Any]) -> list[dict[str, Any]]:
    trip = data.get("trip") if isinstance(data.get("trip"), dict) else {}
    places = [place for place in as_list(data.get("places")) if isinstance(place, dict)]
    explicit = as_list(trip.get("destinations"))
    destinations: list[dict[str, Any]] = []

    for index, raw in enumerate(explicit):
        if isinstance(raw, str):
            raw = {"name": raw}
        if not isinstance(raw, dict) or not raw.get("name"):
            continue
        item: dict[str, Any] = {
            "id": str(first(raw.get("id"), f"destination-{index + 1}")),
            "name": str(raw["name"]),
        }
        country_code = first(raw.get("countryCode"), raw.get("country_code"))
        if country_code:
            item["countryCode"] = str(country_code).upper()
        coordinates = coordinates_from(raw.get("coordinates")) or coordinates_from(raw)
        if coordinates:
            item["latitude"], item["longitude"] = coordinates
        destinations.append(item)

    if destinations:
        return destinations

    cities: list[str] = []
    for place in places:
        city = str(place.get("city") or "").strip()
        if city and city not in cities:
            cities.append(city)
    if not cities:
        destination = str(trip.get("destination") or "").strip()
        if destination:
            cities.append(destination)

    trip_coordinates = coordinates_from(trip.get("coordinates")) or coordinates_from(
        trip.get("destinationCoordinates")
    )
    for index, city in enumerate(cities):
        item = {"id": f"destination-{index + 1}", "name": city}
        country_code = trip.get("countryCode")
        if country_code:
            item["countryCode"] = str(country_code).upper()
        coordinates = trip_coordinates if len(cities) == 1 else None
        if coordinates is None:
            for place in places:
                if str(place.get("city") or "").strip() != city:
                    continue
                coordinates = coordinates_from(place.get("coordinates"))
                if coordinates:
                    break
        if coordinates:
            item["latitude"], item["longitude"] = coordinates
        destinations.append(item)
    return destinations


def display_range(value: Any) -> str:
    if isinstance(value, dict):
        return " - ".join(str(value.get(key)) for key in ("start", "end") if value.get(key))
    return str(value) if value not in (None, "") else ""


def day_label(raw_day: dict[str, Any], day_number: int, trip: dict[str, Any]) -> str:
    explicit = first(raw_day.get("label"), raw_day.get("date"), raw_day.get("dayDate"))
    if explicit:
        return str(explicit)
    date_range = trip.get("dateRange")
    if isinstance(date_range, dict) and date_range.get("start"):
        try:
            current = date.fromisoformat(str(date_range["start"])) + timedelta(days=day_number - 1)
            return f"{current.month}月{current.day}日"
        except ValueError:
            pass
    return f"第 {day_number} 天"


def build_headline(trip: dict[str, Any], destination: str, day_count: int) -> str:
    explicit = first(trip.get("headline"), trip.get("displayHeadline"))
    if explicit:
        return str(explicit)
    chinese_days = {
        1: "一天",
        2: "两天",
        3: "三天",
        4: "四天",
        5: "五天",
        6: "六天",
        7: "七天",
        8: "八天",
        9: "九天",
        10: "十天",
    }.get(day_count, f"{day_count}天")
    if "美食" in str(trip.get("style") or ""):
        return f"{chinese_days}说走就走，把{destination}吃个痛快。"
    return f"{chinese_days}慢慢走，把{destination}写进旅笺。"


def build_lujian_payload(data: dict[str, Any]) -> dict[str, Any]:
    trip = data.get("trip") if isinstance(data.get("trip"), dict) else {}
    places = [place for place in as_list(data.get("places")) if isinstance(place, dict)]
    place_map = {str(place.get("id")): place for place in places if place.get("id")}
    days: list[dict[str, Any]] = []

    for index, raw_day in enumerate(as_list(data.get("days"))):
        if not isinstance(raw_day, dict):
            continue
        day_number = raw_day.get("dayNumber") if isinstance(raw_day.get("dayNumber"), int) else index + 1
        day_id = str(first(raw_day.get("id"), f"day-{day_number}"))
        items: list[dict[str, Any]] = []
        for item_index, raw_item in enumerate(as_list(raw_day.get("items"))):
            if not isinstance(raw_item, dict):
                continue
            place = place_map.get(str(raw_item.get("placeId")))
            source_ids = list(dict.fromkeys([
                *as_list(raw_item.get("sourceIds")),
                *as_list(place.get("sourceIds") if place else []),
            ]))
            item = {
                "id": str(first(raw_item.get("id"), f"{day_id}-item-{item_index + 1}")),
                "time": str(first(raw_item.get("time"), raw_item.get("timeBlock"))),
                "title": str(raw_item.get("title") or ""),
                "category": str(first(raw_item.get("category"), place.get("category") if place else None, "other")),
                "cost": str(first(raw_item.get("cost"), place.get("cost") if place else None)),
                "notes": note_text(first(raw_item.get("notes"), place.get("reason") if place else None)),
                "placeId": raw_item.get("placeId"),
                "duration": str(raw_item.get("duration") or ""),
                "transport": str(first(raw_item.get("transportToNext"), raw_item.get("transport"))),
                "mapLinks": raw_item.get("mapLinks") or (place.get("mapLinks") if place else {}) or {},
                "sourceIds": source_ids,
                "warningIds": as_list(raw_item.get("warningIds")),
            }
            items.append(item)
        days.append({
            "id": day_id,
            "dayNumber": day_number,
            "label": day_label(raw_day, day_number, trip),
            "title": str(raw_day.get("title") or f"第 {day_number} 天"),
            "summary": str(raw_day.get("summary") or ""),
            "budget": str(first(raw_day.get("budgetEstimate"), raw_day.get("budget"))),
            "distanceEstimate": str(first(raw_day.get("distanceEstimate"), raw_day.get("distance"))),
            "durationEstimate": str(first(raw_day.get("durationEstimate"), raw_day.get("travelDuration"), raw_day.get("duration"))),
            "backup": str(raw_day.get("backup") or ""),
            "mapStops": as_list(raw_day.get("mapStops")),
            "mapLegs": as_list(raw_day.get("mapLegs")),
            "items": items,
        })

    day_count = trip.get("dayCount") if isinstance(trip.get("dayCount"), int) else len(days)
    destination = str(trip.get("destination") or "旅行")
    nights = first(trip.get("nightCount"), trip.get("nights"), max(day_count - 1, 0))
    title = str(first(trip.get("title"), f"{destination} {day_count}天{nights}晚旅行计划"))
    return {
        "schemaVersion": 1,
        "title": title,
        "headline": build_headline(trip, destination, day_count),
        "destination": destination,
        "dateRange": display_range(trip.get("dateRange")),
        "travelers": trip.get("travelers") or "",
        "style": trip.get("style") or "",
        "baseArea": trip.get("baseArea") or "",
        "budget": first(trip.get("budgetEstimate"), trip.get("budget")),
        "destinations": build_destinations(data),
        "days": days,
        "places": places,
        "warnings": as_list(data.get("warnings")),
        "sources": as_list(data.get("sources")),
        "assumptions": as_list(trip.get("assumptions")),
        "trip": trip,
    }


def build_static_headline_html(plan: dict[str, Any]) -> str:
    """生成禁用 JavaScript 时仍可见的主标题，并保持与运行时渲染一致。"""
    headline = str(plan.get("headline") or plan.get("title") or f"{plan.get('destination') or '旅行'}计划")
    comma_index = headline.find("，")
    lines = [headline[:comma_index + 1], headline[comma_index + 1:]] if comma_index >= 0 else [headline]
    accent_text = "吃个痛快。" if "美食" in str(plan.get("style") or "") else "写进旅笺。"
    rendered: list[str] = []
    for line in lines:
        if line.endswith(accent_text):
            prefix = html.escape(line[:-len(accent_text)], quote=False)
            accent = html.escape(accent_text, quote=False)
            rendered.append(f'{prefix}<span class="headline-accent">{accent}</span>')
        else:
            rendered.append(html.escape(line, quote=False))
    return "<br>".join(rendered)


def build_html(data: dict[str, Any]) -> str:
    plan = build_lujian_payload(data)
    payload = json.dumps(plan, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c")
    title = html.escape(plan["title"], quote=True)
    headline_markup = build_static_headline_html(plan)
    template = r'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta name="theme-color" content="#FAF6EF">
  <meta name="lujian-schema-version" content="1">
  <title>__TITLE__</title>
  <link rel="stylesheet" href="https://unpkg.com/maplibre-gl@5.14.0/dist/maplibre-gl.css">
  <script src="https://unpkg.com/maplibre-gl@5.14.0/dist/maplibre-gl.js"></script>
  <style>
    :root {
      --bg:#FAF6EF; --paper:#FFFDF8; --ink:#2A2520; --muted:#6B6354;
      --faint:#8A8070; --line:#D9CFBC; --coral:#FF6B4A; --gold:#F2B43A;
      --green:#6F8F58; --blue:#4F78A4; --violet:#8B6FA7;
    }
    *,*::before,*::after{box-sizing:border-box}
    html{background:#E8E0D2;overflow-x:hidden}
    body{margin:0;color:var(--ink);background:var(--bg);font-family:"Noto Serif SC","Songti SC","STSong",serif;min-height:100vh;overflow-x:hidden}
    button,a{font:inherit}
    button{color:inherit}
    .mobile-app{display:block}.desktop-app{display:none}
    [data-itinerary-view][hidden]{display:none!important}.map-mode .budget-trigger{display:none!important}
    .app-shell{width:min(100%,720px);min-height:100vh;margin:0 auto;background:var(--bg);box-shadow:0 0 36px rgba(42,37,32,.12);overflow:hidden}
    .paper{background-image:radial-gradient(rgba(217,207,188,.7) .8px,transparent .8px);background-size:20px 20px}
    .hero{position:relative;padding:calc(18px + env(safe-area-inset-top)) 20px 22px;border-bottom:2px solid var(--ink);isolation:isolate;overflow:hidden}
    .hero::after{content:"";position:absolute;width:160px;height:160px;border:24px solid rgba(242,180,58,.2);border-radius:50%;right:-72px;bottom:-96px;z-index:-1}
    .hero-top{display:flex;align-items:center;justify-content:space-between;gap:12px}
    .brand{display:flex;align-items:center;gap:10px;font-family:ui-serif,Georgia,"Noto Serif SC",serif;font-size:18px;font-weight:900}
    .brand-mark{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:var(--ink);color:var(--bg);font:900 20px/1 ui-serif,Georgia,"Noto Serif SC","Songti SC",serif;transform:rotate(-5deg);box-shadow:3px 3px 0 var(--gold)}
    .save-btn,.ink-btn{border:2px solid var(--ink);border-radius:999px;background:var(--ink);color:var(--bg);padding:9px 14px;font:700 12px/1 system-ui,"PingFang SC",sans-serif;cursor:pointer;box-shadow:2px 2px 0 var(--gold)}
    .save-btn:active,.ink-btn:active{transform:translate(1px,1px);box-shadow:1px 1px 0 var(--gold)}
    .eyebrow{margin:22px 0 7px;color:var(--coral);font:800 11px/1.4 system-ui,"PingFang SC",sans-serif;letter-spacing:.14em;text-transform:uppercase}
    h1{margin:0;max-width:580px;font-family:ui-serif,Georgia,"Noto Serif SC",serif;font-size:clamp(42px,13vw,58px);line-height:1.06;letter-spacing:-.035em}
    .headline-accent{position:relative;display:inline-block}.headline-accent::after{content:"";position:absolute;left:0;right:0;bottom:-10px;height:12px;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='96' height='12' viewBox='0 0 96 12'%3E%3Cpath d='M0 6 C8 1 16 1 24 6 S40 11 48 6 S64 1 72 6 S88 11 96 6' fill='none' stroke='%23F2B43A' stroke-width='3.5' stroke-linecap='round'/%3E%3C/svg%3E");background-position:left bottom;background-repeat:repeat-x;background-size:96px 12px}
    .hero-meta{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
    .meta-chip{border:1px solid var(--line);border-radius:999px;background:rgba(255,253,248,.72);padding:6px 10px;color:var(--muted);font:700 11px/1 system-ui,"PingFang SC",sans-serif}
    .page-tabs{display:flex;gap:7px;border-bottom:1px solid var(--line);padding:9px 14px;background:var(--bg)}.page-tab{min-width:74px;border:1px solid var(--line);border-radius:999px;background:var(--paper);padding:8px 14px;color:var(--muted);font:800 12px/1 system-ui,"PingFang SC",sans-serif;cursor:pointer}.page-tab.active{border-color:var(--ink);background:var(--ink);color:var(--bg);box-shadow:2px 2px 0 var(--gold)}
    .date-axis-wrap{position:sticky;top:0;z-index:20;border-bottom:2px solid var(--ink);background:rgba(250,246,239,.96);backdrop-filter:blur(10px)}
    .date-axis{display:flex;gap:8px;overflow-x:auto;padding:10px 14px;scrollbar-width:none;scroll-snap-type:x proximity}
    .date-axis::-webkit-scrollbar{display:none}
    .day-tab{flex:0 0 auto;min-width:88px;max-width:132px;border:1.5px solid var(--line);border-radius:14px;background:var(--paper);padding:8px 11px;text-align:left;cursor:pointer;scroll-snap-align:center}
    .day-tab strong{display:block;font:900 13px/1.2 ui-serif,Georgia,serif}
    .day-tab span{display:block;margin-top:3px;color:var(--faint);font:700 10px/1.2 system-ui,"PingFang SC",sans-serif;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .day-tab.active{border-color:var(--ink);background:var(--ink);color:var(--bg);box-shadow:3px 3px 0 var(--gold)}
    .day-tab.active span{color:rgba(250,246,239,.68)}
    main{padding:18px 14px 90px}
    .day-panel{animation:day-in .2s ease both;transform-origin:center top}
    .day-panel[data-direction="-1"]{--slide:-12px}.day-panel[data-direction="1"]{--slide:12px}
    @keyframes day-in{from{opacity:0;transform:translateX(var(--slide,0))}to{opacity:1;transform:none}}
    .day-heading{padding:4px 4px 16px}
    .day-kicker{color:var(--coral);font:800 11px/1.3 system-ui,"PingFang SC",sans-serif;letter-spacing:.12em;text-transform:uppercase}
    .day-heading h2{margin:5px 0 0;font:900 clamp(23px,7vw,34px)/1.15 ui-serif,Georgia,"Noto Serif SC",serif}
    .day-summary{margin:8px 0 0;color:var(--muted);font-size:13px;line-height:1.7}
    .route-strip{margin:0 0 16px;border:1px dashed var(--line);border-radius:14px;background:rgba(255,253,248,.72);padding:10px 12px;color:var(--muted);font:700 11px/1.55 system-ui,"PingFang SC",sans-serif;overflow-wrap:anywhere}
    .timeline{position:relative;display:grid;gap:12px}
    .timeline::before{content:"";position:absolute;left:18px;top:18px;bottom:18px;border-left:2px dotted var(--line)}
    .stop-card{position:relative;margin-left:38px;border:2px solid rgba(42,37,32,.18);border-radius:19px;background:var(--card-bg,#FFFDF8);box-shadow:3px 3px 0 rgba(42,37,32,.1);overflow:visible}
    .stop-card::before{content:"";position:absolute;left:-35px;top:14px;display:grid;place-items:center;width:28px;height:28px;border:2px solid var(--ink);border-radius:50% 50% 50% 0;background:var(--coral);color:#fff;font:900 11px/1 system-ui,sans-serif;transform:rotate(-45deg);z-index:2}
    .stop-card::after{content:attr(data-index);position:absolute;left:-35px;top:14px;display:grid;place-items:center;width:28px;height:28px;color:#fff;font:900 11px/1 system-ui,sans-serif;z-index:3}
    .card-main{width:100%;border:0;background:transparent;padding:14px;text-align:left;cursor:pointer}
    .card-top{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}
    .time{color:var(--faint);font:800 11px/1.3 system-ui,"PingFang SC",sans-serif;white-space:nowrap}
    .category{border:1px solid currentColor;border-radius:7px;padding:3px 7px;font:800 10px/1 system-ui,"PingFang SC",sans-serif}
    .card-title{margin:9px 0 0;font-size:16px;line-height:1.4}
    .card-meta{display:flex;flex-wrap:wrap;gap:6px 10px;margin-top:7px;color:var(--muted);font:700 11px/1.4 system-ui,"PingFang SC",sans-serif}
    .card-note{margin:9px 0 0;color:var(--muted);font-size:12px;line-height:1.6;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
    .expand-row{display:flex;align-items:center;justify-content:space-between;margin-top:10px;color:var(--coral);font:800 11px/1 system-ui,"PingFang SC",sans-serif}
    .chevron{width:8px;height:8px;border-right:2px solid currentColor;border-bottom:2px solid currentColor;transform:rotate(45deg);transition:transform .18s}
    .card-main[aria-expanded="true"] .chevron{transform:rotate(225deg)}
    .card-detail{border-top:1px solid rgba(42,37,32,.12);padding:13px 14px 15px;background:rgba(255,255,255,.3)}
    .detail-line{margin:0 0 7px;color:var(--muted);font-size:12px;line-height:1.6;white-space:pre-line}
    .warning-inline{margin:9px 0;border-left:4px solid var(--coral);border-radius:0 10px 10px 0;background:#FFE6DE;padding:9px 10px;color:#7E3324;font-size:11px;line-height:1.55}
    .link-row{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}
    .map-link,.source-link{display:inline-flex;align-items:center;min-height:34px;border:1.5px solid var(--ink);border-radius:10px;background:var(--paper);padding:7px 10px;color:var(--ink);font:800 11px/1.2 system-ui,"PingFang SC",sans-serif;text-decoration:none}
    .map-link.primary{background:var(--ink);color:var(--bg)}
    .empty-link{color:var(--faint);font-size:11px}
    .source-card{margin-top:9px;border:1px solid var(--line);border-radius:11px;background:rgba(255,253,248,.8);padding:9px;color:var(--muted);font-size:10px;line-height:1.55}
    .source-card strong{display:block;color:var(--ink);font-size:11px}
    .source-stats{color:var(--coral);font:800 10px/1.4 system-ui,"PingFang SC",sans-serif}
    .info-card{margin-top:16px;border:2px solid var(--ink);border-radius:18px;background:var(--paper);padding:14px;box-shadow:4px 4px 0 var(--gold)}
    .info-card h3{margin:0 0 8px;font:900 16px/1.3 ui-serif,Georgia,"Noto Serif SC",serif}
    .info-card p{margin:5px 0;color:var(--muted);font-size:12px;line-height:1.6}
    .budget-trigger{position:fixed;left:50%;bottom:max(12px,env(safe-area-inset-bottom));z-index:25;width:min(calc(100% - 28px),692px);transform:translateX(-50%);border:2px solid var(--ink);border-radius:18px;background:var(--ink);color:var(--bg);padding:11px 13px;box-shadow:4px 4px 0 var(--gold);display:flex;align-items:center;justify-content:space-between;gap:12px;cursor:pointer}
    .budget-trigger small{display:block;color:rgba(250,246,239,.58);font:700 10px/1.3 system-ui,"PingFang SC",sans-serif}
    .budget-trigger strong{display:block;margin-top:2px;color:var(--gold);font:900 18px/1.1 ui-serif,Georgia,serif}
    .budget-trigger span{font:800 11px/1.2 system-ui,"PingFang SC",sans-serif}
    .sheet-overlay[hidden]{display:none}.sheet-overlay{position:fixed;inset:0;z-index:40;display:flex;align-items:flex-end;justify-content:center;background:rgba(42,37,32,.46);padding-top:40px}
    .sheet{width:min(100%,720px);max-height:84vh;overflow:auto;border:2px solid var(--ink);border-bottom:0;border-radius:24px 24px 0 0;background:var(--bg);padding:18px 16px calc(20px + env(safe-area-inset-bottom));box-shadow:0 -12px 36px rgba(42,37,32,.2)}
    .sheet-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.sheet h2{margin:0;font:900 22px/1.2 ui-serif,Georgia,serif}.close-btn{width:36px;height:36px;border:2px solid var(--ink);border-radius:50%;background:var(--paper);font-size:20px;cursor:pointer}
    .budget-row{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid var(--line);padding:11px 2px;color:var(--muted);font-size:12px}.budget-row strong{color:var(--ink);white-space:nowrap}
    .footer-note{padding:16px 16px 100px;text-align:center;color:var(--faint);font:700 10px/1.5 system-ui,"PingFang SC",sans-serif}
    .desktop-header{position:sticky;top:0;z-index:35;border-bottom:2px solid var(--ink);background:rgba(250,246,239,.96);backdrop-filter:blur(10px)}
    .desktop-header-inner{max-width:1520px;margin:0 auto;padding:13px 28px;display:flex;align-items:center;justify-content:space-between;gap:18px}
    .desktop-brand-block{display:flex;align-items:center;gap:11px;min-width:0}.desktop-brand-copy{min-width:0}.desktop-brand-title{font:900 20px/1 ui-serif,Georgia,"Noto Serif SC",serif;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.desktop-brand-sub{margin-top:4px;color:var(--faint);font:700 10px/1 system-ui,"PingFang SC",sans-serif;letter-spacing:.13em;text-transform:uppercase}
    .desktop-header-meta{margin-left:auto;color:var(--muted);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .desktop-save{border:0;border-radius:999px;background:var(--ink);color:var(--bg);padding:10px 18px;font:800 12px/1 system-ui,"PingFang SC",sans-serif;cursor:pointer;white-space:nowrap}.desktop-save:hover{background:var(--coral);color:var(--ink)}
    .desktop-hero{max-width:1520px;margin:0 auto;padding:34px 28px 22px}.desktop-hero h1{max-width:780px;font-size:clamp(44px,4.5vw,68px)}.desktop-hero p{max-width:650px;margin:15px 0 0;color:var(--muted);font-size:14px;line-height:1.75}
    .desktop-controls{max-width:1520px;margin:0 auto;padding:0 28px 20px;display:flex;align-items:center;gap:16px}.desktop-control-label{color:var(--faint);font:800 10px/1 system-ui,"PingFang SC",sans-serif;letter-spacing:.15em;text-transform:uppercase}.desktop-filter-list{display:flex;gap:7px;flex-wrap:wrap}.desktop-filter{border:0;border-radius:999px;background:transparent;padding:7px 14px;color:var(--muted);font:800 12px/1 system-ui,"PingFang SC",sans-serif;cursor:pointer}.desktop-filter:hover{background:#EFE7D8}.desktop-filter.active{background:var(--ink);color:var(--bg)}
    .desktop-main{max-width:1520px;margin:0 auto;padding:0 28px 32px;display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:28px;align-items:start}
    .desktop-schedule{min-width:0;overflow-x:hidden;border:2px solid var(--ink);border-radius:28px;background-color:var(--paper);background-image:radial-gradient(rgba(217,207,188,.72) .8px,transparent .8px);background-size:21px 21px;padding:20px}
    .desktop-schedule.is-scrollable{overflow-x:auto;scrollbar-gutter:stable}
    .desktop-days{display:grid;min-width:0;gap:16px;align-items:start}.desktop-day-col{min-width:0;display:flex;flex-direction:column}.desktop-day-header{padding:0 2px 12px}.desktop-day-name{font:800 17px/1.2 ui-serif,Georgia,serif}.desktop-day-date{margin-top:2px;color:var(--faint);font:700 11px/1.25 system-ui,"PingFang SC",sans-serif}.desktop-day-count{margin-top:7px;color:var(--faint);font:800 10px/1 system-ui,"PingFang SC",sans-serif;letter-spacing:.1em;text-transform:uppercase}.desktop-day-summary{display:inline-block;margin-top:6px;border-radius:8px;background:#F0EBE0;padding:4px 8px;color:var(--faint);font-size:10px;line-height:1.45}
    .desktop-stops{display:flex;flex-direction:column;gap:10px}.desktop-stop-card{width:100%;min-width:0;border:2px solid rgba(42,37,32,.13);border-radius:18px;background:var(--card-bg,var(--paper));transition:border-color .16s,box-shadow .16s,transform .16s;overflow:hidden}.desktop-stop-card:hover{border-color:var(--ink);box-shadow:4px 4px 0 rgba(42,37,32,.12);transform:translateY(-2px)}.desktop-stop-card.expanded{border-color:var(--ink);box-shadow:4px 4px 0 var(--ink)}.desktop-stop-card[hidden]{display:none}.desktop-stop-card:not(.expanded){min-height:172px}
    .desktop-card-toggle{display:block;width:100%;min-height:168px;border:0;background:transparent;padding:14px;text-align:left;cursor:pointer}.desktop-card-top{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:8px}.desktop-cat-icon{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:var(--cat-color,var(--coral));font-size:16px}.desktop-card-time{color:var(--faint);font:800 11px/1.3 system-ui,"PingFang SC",sans-serif;text-align:right}.desktop-card-title{min-height:2.6em;margin:0 0 5px;font-size:13.5px;line-height:1.3;overflow-wrap:anywhere;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden}.desktop-card-cost{color:var(--muted);font-size:11.5px}.desktop-cat-badge{display:inline-block;margin-top:6px;border:1px solid var(--cat-color,var(--coral));border-radius:6px;padding:3px 8px;color:var(--cat-color,var(--coral));font:800 10px/1 system-ui,"PingFang SC",sans-serif}.desktop-expand{display:flex;align-items:center;gap:6px;margin-top:9px;color:#2563EB;font:800 10px/1 system-ui,"PingFang SC",sans-serif}.desktop-expand .chevron{border-color:#2563EB}.desktop-card-toggle[aria-expanded="true"] .chevron{transform:rotate(225deg)}
    .desktop-card-detail{border-top:1px solid rgba(42,37,32,.1);background:rgba(255,255,255,.22);padding:10px 14px 12px;font-family:"Noto Serif SC","Songti SC","STSong",serif;font-size:11px;line-height:1.5}.desktop-detail-note{margin:0 0 4px;color:var(--muted);font-size:11px;line-height:1.5}.desktop-detail-transport{margin-top:6px;border-radius:8px;background:rgba(42,37,32,.04);padding:5px 8px;color:var(--faint);font-size:11px;line-height:1.45}.desktop-detail-map-links{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.desktop-detail-map-link{display:inline-flex;align-items:center;border:1px solid #BFDBFE;border-radius:8px;background:transparent;padding:3px 8px;color:#2563EB;font-family:"Noto Serif SC","Songti SC","STSong",serif;font-size:11px;font-weight:500;line-height:1.35;text-decoration:none}.desktop-detail-map-link:hover{background:#EFF6FF}
    .desktop-budget{position:sticky;top:82px;border:2px solid var(--ink);border-radius:28px;background:var(--ink);color:var(--bg);padding:24px;box-shadow:6px 6px 0 var(--gold)}.desktop-budget-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.desktop-budget-title{font:800 22px/1.2 ui-serif,Georgia,serif}.desktop-budget-tag{border-radius:999px;background:rgba(250,246,239,.12);padding:5px 11px;font:700 10px/1 system-ui,"PingFang SC",sans-serif}.desktop-budget-sub{margin:7px 0 17px;color:rgba(250,246,239,.5);font-size:11px;line-height:1.5}.desktop-budget-section{margin:14px 0 8px;color:rgba(250,246,239,.45);font:800 10px/1 system-ui,"PingFang SC",sans-serif;letter-spacing:.13em;text-transform:uppercase}.desktop-budget-row{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:6px;border-radius:12px;background:rgba(250,246,239,.06);padding:8px 10px;font-size:11px}.desktop-budget-row strong{color:var(--gold);white-space:nowrap}.desktop-budget-total{display:flex;align-items:baseline;justify-content:space-between;gap:12px;margin-top:16px;border-top:1px solid rgba(250,246,239,.14);padding-top:15px;font:800 17px/1.2 ui-serif,Georgia,serif}.desktop-budget-total strong{color:var(--gold);font-size:22px}.desktop-budget-note{margin-top:10px;color:rgba(250,246,239,.42);font-size:10px;line-height:1.55}.desktop-budget .desktop-save{width:100%;margin-top:16px;background:var(--coral);color:var(--ink);padding:12px}.desktop-budget .desktop-save:hover{background:var(--gold)}
    .desktop-info-grid{max-width:1520px;margin:0 auto;padding:0 28px 60px;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px;align-items:start}.desktop-info-card{overflow:hidden;border:2px solid var(--ink);border-radius:18px;background:var(--paper)}.desktop-info-card summary{display:flex;align-items:center;justify-content:space-between;gap:14px;min-height:56px;padding:14px 20px;cursor:pointer;font:900 16px/1.3 ui-serif,Georgia,"Noto Serif SC",serif;list-style:none}.desktop-info-card summary::-webkit-details-marker{display:none}.desktop-info-card summary::after{content:"▼";color:var(--faint);font:700 12px/1 system-ui,sans-serif;transition:transform .18s}.desktop-info-card[open] summary{border-bottom:1px solid var(--line)}.desktop-info-card[open] summary::after{transform:rotate(180deg)}.desktop-info-body{padding:14px 20px 18px;color:var(--muted);font-size:12px;line-height:1.6}.desktop-info-body>p{margin:6px 0}.transport-leg{margin-bottom:10px;border-radius:12px;background:#F0EBE0;padding:12px 14px}.transport-leg:last-of-type{margin-bottom:0}.transport-leg h4{margin:0 0 4px;color:var(--ink);font-size:13px}.transport-leg p{margin:2px 0;font-size:12px}.price-badge{display:inline-block;margin-top:6px;border-radius:999px;background:#fff;padding:3px 10px;color:#059669;font-size:11px;font-weight:800}.warning-text{margin-top:4px!important;color:#D97706!important;font-size:11px!important}.info-action-link{display:inline-block;margin-top:6px;color:#2563EB;font-size:11px;text-decoration:none}.hotel-item{margin-bottom:10px;border-left:3px solid var(--gold);border-radius:0 10px 10px 0;background:#F0EBE0;padding:10px 12px}.hotel-item.muted{border-left-color:#D9CFBC}.hotel-item:last-of-type{margin-bottom:0}.hotel-rank{color:var(--faint);font-size:10px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}.hotel-item h4{margin:2px 0 0;color:var(--ink);font-size:13px}.hotel-price{margin:3px 0;color:#059669;font-size:12px;font-weight:700}.hotel-note{margin:3px 0!important;color:var(--muted)!important;font-size:11px!important}.hotel-risk{margin:3px 0 0!important;color:#D97706!important;font-size:11px!important}.amap-link{display:inline-block;margin-top:5px;border:1px solid #BFDBFE;border-radius:7px;padding:3px 8px;color:#2563EB;font-size:11px;text-decoration:none}.desktop-info-note{margin-top:8px!important;color:var(--faint)!important;font-size:11px!important}.desktop-footer{border-top:1px solid var(--line);padding:20px 28px;text-align:center;color:var(--faint);font:700 10px/1.5 system-ui,"PingFang SC",sans-serif}
    .desktop-page-tabs{max-width:1520px;margin:0 auto;padding:0 28px 18px;border:0}.map-view[hidden]{display:none}.map-view{max-width:1520px;margin:0 auto;padding:22px 28px 64px}.map-view-head{display:flex;align-items:end;justify-content:space-between;gap:18px;margin-bottom:13px}.map-eyebrow{color:var(--faint);font:800 10px/1 system-ui,"PingFang SC",sans-serif;letter-spacing:.14em;text-transform:uppercase}.map-view h2{margin:5px 0 0;font:900 clamp(25px,3vw,38px)/1.15 ui-serif,Georgia,"Noto Serif SC",serif}.map-day-tabs{display:flex;gap:7px;overflow-x:auto;scrollbar-width:none}.map-day-tabs::-webkit-scrollbar{display:none}.map-day-tab{flex:0 0 auto;min-width:69px;border:1px solid var(--line);border-radius:999px;background:var(--paper);padding:8px 12px;color:var(--muted);font:800 11px/1 system-ui,"PingFang SC",sans-serif;cursor:pointer}.map-day-tab.active{border-color:var(--ink);background:var(--ink);color:var(--bg)}.map-status{margin-bottom:12px;border:1px solid #9ED8BE;border-radius:12px;background:#E3FCEF;padding:10px 13px;color:#216E4E;font-size:12px;line-height:1.5}.map-stats{display:flex;gap:9px;margin-bottom:14px}.map-stat{flex:1;border:1px solid var(--line);border-radius:13px;background:var(--paper);padding:9px 12px}.map-stat strong,.map-stat small{display:block}.map-stat strong{font:900 18px/1.2 ui-serif,Georgia,serif}.map-stat small{margin-top:2px;color:var(--faint);font:700 10px/1.3 system-ui,"PingFang SC",sans-serif}.map-layout{display:grid;grid-template-columns:minmax(0,1fr) 340px;gap:18px;align-items:stretch}.map-panel{position:relative;height:clamp(460px,58vw,680px);overflow:hidden;border:2px solid var(--ink);border-radius:24px;background:#F0EBE0}.travel-map,.map-static{position:absolute;inset:0}.map-static{z-index:2;overflow:hidden;background:#F0EBE0}.map-static::before{content:"";position:absolute;inset:-12%;background:repeating-linear-gradient(12deg,transparent 0 42px,rgba(67,62,55,.13) 43px 44px,transparent 45px 84px),repeating-linear-gradient(93deg,transparent 0 58px,rgba(67,62,55,.11) 59px 60px,transparent 61px 118px);transform:rotate(-6deg)}.map-panel.map-live .map-static{display:none}.map-title-card{position:absolute;top:15px;left:15px;z-index:5;max-width:min(62%,360px);border:1px solid rgba(42,37,32,.18);border-radius:11px;background:rgba(250,246,239,.94);padding:10px 12px;pointer-events:none}.map-title-card strong{display:block;font:900 16px/1.25 ui-serif,Georgia,serif}.map-title-card span{display:block;margin-top:3px;color:var(--faint);font-size:10px;line-height:1.4}.map-route-panel{min-height:0;overflow:hidden;border:2px solid var(--ink);border-radius:20px;background:var(--paper)}.map-legend{display:flex;gap:12px;border-bottom:1px solid var(--line);padding:11px 13px;font:800 10px/1 system-ui,"PingFang SC",sans-serif}.map-legend span{display:inline-flex;align-items:center;gap:5px}.map-legend span::before{content:"";width:8px;height:8px;border-radius:50%;background:currentColor}.map-legend .walk{color:#4A8F63}.map-legend .ride{color:#2D73C8}.map-legend .drive{color:#D45D3D}.map-route-list{max-height:calc(clamp(460px,58vw,680px) - 42px);overflow:auto;padding:9px}.map-route-card{display:block;width:100%;border:0;border-radius:11px;background:transparent;padding:9px 10px;text-align:left;color:var(--ink)}.map-route-card+.map-route-card{margin-top:3px}.map-route-card:hover{background:#F0EBE0}.map-route-card strong{display:block;font-size:12px;line-height:1.35}.map-route-card span{display:block;margin-top:3px;color:var(--faint);font-size:10px;line-height:1.4}.static-relations{position:absolute;inset:0;width:100%;height:100%;overflow:visible;pointer-events:none}.static-relations line{stroke-width:.38;stroke-dasharray:1.45 1.15;opacity:.92}.static-relations .walk{stroke:#4A8F63}.static-relations .ride{stroke:#2D73C8}.static-relations .drive{stroke:#D45D3D}.static-marker{position:absolute;z-index:3;width:0;height:0;transform:translate(-50%,-50%);pointer-events:none}.pin-dot{position:absolute;left:-7px;top:-7px;width:14px;height:14px;border:2px solid #fff;border-radius:50% 50% 50% 0;background:var(--coral);box-shadow:0 0 0 1px rgba(42,37,32,.35);transform:rotate(-45deg)}.pin-dot::after{content:attr(data-n);position:absolute;inset:0;display:grid;place-items:center;color:#fff;font:900 8px/1 system-ui,"PingFang SC",sans-serif;transform:rotate(45deg)}.static-marker.hotel .pin-dot{background:#9B6BB3}.static-marker.attraction .pin-dot{background:#7BA05B}.static-marker.other .pin-dot{background:#2D73C8}.pin-line{position:absolute;top:0;left:8px;width:21px;height:1px;background:var(--ink);opacity:.45}.static-marker.left .pin-line{right:8px;left:auto}.pin-card{position:absolute;top:calc(-18px + var(--label-y,0px));left:29px;min-width:76px;max-width:150px;border:1px solid rgba(42,37,32,.14);border-radius:6px;background:rgba(250,246,239,.95);padding:5px 7px;color:var(--ink);font-size:11px;font-weight:700;line-height:1.15;white-space:nowrap}.static-marker.left .pin-card{right:29px;left:auto;text-align:right}.pin-card small{display:block;margin-top:3px;color:var(--faint);font:700 9px/1.2 system-ui,"PingFang SC",sans-serif;letter-spacing:.04em}.map-empty{position:absolute;inset:0;z-index:4;display:grid;place-items:center;padding:28px;text-align:center;color:var(--muted);font-size:12px;line-height:1.7}.maplibregl-ctrl-bottom-left,.maplibregl-ctrl-bottom-right{display:none!important}
    .map-controls{position:absolute;top:16px;right:16px;z-index:6;display:flex;gap:5px;border:1px solid rgba(42,37,32,.18);border-radius:10px;background:rgba(250,246,239,.94);padding:5px}.map-ctrl{min-width:32px;height:32px;border:1px solid var(--ink);border-radius:0;background:transparent;color:var(--ink);font:700 12px/1 system-ui,"PingFang SC",sans-serif;letter-spacing:.06em;cursor:pointer}.map-ctrl.drag{min-width:58px}.map-ctrl.active{border-color:var(--coral);background:var(--coral);color:#fff}.maplibregl-canvas-container,.maplibregl-canvas{cursor:default!important}.map-panel.drag-enabled .maplibregl-canvas-container,.map-panel.drag-enabled .maplibregl-canvas{cursor:grab!important}.map-route-card{position:relative;min-height:44px;padding:10px 10px 10px 44px;cursor:pointer}.map-pin{position:absolute;left:10px;top:10px;display:grid!important;place-items:center;width:22px;height:22px;margin:0!important;border-radius:50% 50% 50% 0;background:var(--coral);color:#fff!important;font:800 10px/1 system-ui,"PingFang SC",sans-serif!important;transform:rotate(-45deg)}.map-pin>span{display:block!important;margin:0!important;color:#fff!important;transform:rotate(45deg)}.map-pin.optional{background:#7BA05B}
    :focus-visible{outline:3px solid var(--gold);outline-offset:3px}
    @media (min-width:600px) and (max-width:959px){main{padding-left:24px;padding-right:24px}.hero{padding-left:28px;padding-right:28px}.timeline::before{left:24px}.stop-card{margin-left:48px}.stop-card::before,.stop-card::after{left:-42px}}
    @media (max-width:959px){.map-mode .app-shell{min-height:0}.map-view{padding:18px 14px 110px}.map-view-head{align-items:flex-start;flex-direction:column}.map-day-tabs{width:100%}.map-layout{grid-template-columns:1fr}.map-panel{height:470px}.map-route-panel{max-height:370px}.map-route-list{max-height:328px}.map-title-card{max-width:67%}}
    @media (max-width:720px){.map-panel .pin-card,.map-panel .pin-line{display:none}}
    @media (min-width:960px){html,body{overflow-x:auto}.mobile-app,#budget-trigger,#budget-sheet{display:none!important}.desktop-app{display:block}}
    @media (prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;transition-duration:.01ms!important;scroll-behavior:auto!important}}
  </style>
</head>
<body>
  <div class="mobile-app app-shell paper">
    <header class="hero">
      <div class="hero-top">
        <div class="brand"><span class="brand-mark">笺</span><span>旅笺</span></div>
        <button id="save-page" class="save-btn js-save" type="button">保存页面</button>
      </div>
      <p class="eyebrow">Travel journal · 行程手账</p>
      <h1 id="plan-title" data-lujian-cover>__HEADLINE__</h1>
      <div id="hero-meta" class="hero-meta"></div>
    </header>
    <nav class="page-tabs mobile-page-tabs" aria-label="手机页面视图"><button id="mobile-itinerary-tab" class="page-tab js-page-tab active" type="button" data-view="itinerary" aria-selected="true">行程</button><button id="mobile-map-tab" class="page-tab js-page-tab" type="button" data-view="map" aria-selected="false">地图</button></nav>
    <div class="mobile-itinerary-view" data-itinerary-view>
      <div class="date-axis-wrap"><nav id="date-axis" class="date-axis" aria-label="切换日期"></nav></div>
      <main><div id="day-root" aria-live="polite"></div></main>
      <p class="footer-note">来源可追溯 · 路线按实际顺序整理 · 旅笺增强模板</p>
    </div>
  </div>

  <div class="desktop-app paper">
    <header class="desktop-header">
      <div class="desktop-header-inner">
        <div class="desktop-brand-block">
          <span class="brand-mark">笺</span>
          <div class="desktop-brand-copy"><div id="desktop-header-title" class="desktop-brand-title"></div><div id="desktop-header-sub" class="desktop-brand-sub"></div></div>
        </div>
        <div id="desktop-header-meta" class="desktop-header-meta"></div>
        <button class="desktop-save js-save" type="button">保存页面</button>
      </div>
    </header>
    <section class="desktop-hero">
      <p class="eyebrow">Travel journal · 桌面行程总览</p>
      <h1 id="desktop-hero-title">__HEADLINE__</h1>
      <p id="desktop-hero-copy"></p>
    </section>
    <nav class="page-tabs desktop-page-tabs" aria-label="电脑页面视图"><button id="desktop-itinerary-tab" class="page-tab js-page-tab active" type="button" data-view="itinerary" aria-selected="true">行程</button><button id="desktop-map-tab" class="page-tab js-page-tab" type="button" data-view="map" aria-selected="false">地图</button></nav>
    <div class="desktop-itinerary-view" data-itinerary-view>
      <section class="desktop-controls" aria-label="按类型筛选行程">
      <span class="desktop-control-label">按类型</span>
      <div class="desktop-filter-list">
        <button class="desktop-filter active" type="button" data-filter="all">全部</button>
        <button class="desktop-filter" type="button" data-filter="restaurant">🍜 美食</button>
        <button class="desktop-filter" type="button" data-filter="attraction">🏖 景点</button>
        <button class="desktop-filter" type="button" data-filter="hotel">🏨 住宿</button>
        <button class="desktop-filter" type="button" data-filter="other">🍸 其他</button>
      </div>
      </section>
      <div class="desktop-main">
        <section class="desktop-schedule" aria-label="全部日期行程"><div id="desktop-days" class="desktop-days"></div></section>
        <aside id="desktop-budget" class="desktop-budget"></aside>
      </div>
      <section id="desktop-info-grid" class="desktop-info-grid"></section>
    </div>
    <footer class="desktop-footer" data-itinerary-view>来源可追溯 · 路线按实际顺序整理 · 旅笺增强模板</footer>
  </div>

  <section id="shared-map-view" class="map-view paper" hidden aria-labelledby="map-view-title">
    <div class="map-view-head"><div><div class="map-eyebrow">MapLibre route map · 静态路线兜底</div><h2 id="map-view-title">每天大概怎么走</h2></div><div id="map-day-tabs" class="map-day-tabs" role="tablist" aria-label="按天查看路线"></div></div>
    <div id="map-status" class="map-status" aria-live="polite">地图与计划顺序已载入。</div>
    <div class="map-stats"><div class="map-stat"><strong id="map-stop-count">—</strong><small>地点</small></div><div class="map-stat"><strong id="map-distance">—</strong><small>预计里程</small></div><div class="map-stat"><strong id="map-duration">—</strong><small>移动时间</small></div></div>
    <div class="map-layout"><div class="map-panel"><div class="map-title-card"><strong id="map-title">Day 1</strong><span>点位按行程顺序连接；底图不可用时仍显示静态路线。</span></div><div class="map-controls" aria-label="地图控制"><button class="map-ctrl" type="button" data-map-ctrl="zoom-in" aria-label="放大地图">+</button><button class="map-ctrl" type="button" data-map-ctrl="zoom-out" aria-label="缩小地图">−</button><button class="map-ctrl drag" type="button" data-map-ctrl="drag" aria-label="允许拖动地图" aria-pressed="false">DRAG</button></div><div id="travel-map" class="travel-map" aria-label="旅行路线地图"></div><div id="map-static" class="map-static" aria-hidden="true"></div></div><aside class="map-route-panel"><div class="map-legend"><span class="walk">步行</span><span class="ride">骑行 / 公交</span><span class="drive">打车</span></div><div id="map-route-list" class="map-route-list"></div></aside></div>
  </section>

  <button id="budget-trigger" class="budget-trigger" type="button" data-itinerary-view>
    <span><small>行程总预算</small><strong id="budget-total">待估算</strong></span><span>查看明细 →</span>
  </button>
  <div id="budget-sheet" class="sheet-overlay" hidden>
    <section class="sheet" role="dialog" aria-modal="true" aria-labelledby="budget-title">
      <div class="sheet-head"><h2 id="budget-title">预算与住宿</h2><button id="close-budget" class="close-btn" type="button" aria-label="关闭">×</button></div>
      <div id="budget-rows"></div>
    </section>
  </div>

  <script id="lujian-plan" type="application/json">__PAYLOAD__</script>
  <script>
    const plan = JSON.parse(document.getElementById('lujian-plan').textContent);
    const $ = id => document.getElementById(id);
    const places = new Map((plan.places || []).map(place => [String(place.id), place]));
    const sources = new Map((plan.sources || []).map(source => [String(source.id), source]));
    const warnings = new Map((plan.warnings || []).map(warning => [String(warning.id), warning]));
    let activeIndex = 0;
    let touchStartX = null;
    const mapState = {activeIndex:0,map:null,ready:false,markers:[],points:[],legs:[],drag:false};
    const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
    const uniq = values => [...new Set((values || []).filter(value => value !== null && value !== undefined && value !== ''))];
    const list = value => Array.isArray(value) ? value : [];
    const first = (...values) => values.find(value => value !== undefined && value !== null && value !== '') ?? '';

    function headlineHtml() {
      const headline = String(plan.headline || plan.title || (plan.destination || '旅行') + '计划');
      const commaIndex = headline.indexOf('，');
      const lineBroken = commaIndex >= 0
        ? esc(headline.slice(0,commaIndex + 1)) + '<br>' + esc(headline.slice(commaIndex + 1))
        : esc(headline);
      const accentText = String(plan.style || '').includes('美食') ? '吃个痛快。' : '写进旅笺。';
      const escapedAccent = esc(accentText);
      return lineBroken.endsWith(escapedAccent)
        ? lineBroken.slice(0,-escapedAccent.length) + '<span class="headline-accent">' + escapedAccent + '</span>'
        : lineBroken;
    }

    function renderHeadline(element) {
      element.innerHTML = headlineHtml();
    }

    function categoryMeta(category) {
      const map = {
        restaurant:['美食','#FFE9E2','#FF6B4A','🍜'], cafe:['咖啡','#F3E6D2','#B9824B','☕'], attraction:['景点','#E9F1E0','#7BA05B','🏖'],
        hotel:['住宿','#E7EDF6','#6F82A4','🏨'], transport:['交通','#E8EEF5','#4F78A4','🚄'], shop:['逛店','#F1E7F7','#8B6FA7','🛍'],
        neighborhood:['街区','#E9F1E0','#6F8F58','🏙'], other:['其他','#F0EBE0','#6B6354','✦']
      };
      return map[category] || [category || '其他','#F0EBE0','#6B6354','✦'];
    }

    function renderHero() {
      renderHeadline($('plan-title'));
      const meta = uniq([plan.dateRange, plan.travelers, plan.baseArea && `住 ${plan.baseArea}`, plan.style]);
      $('hero-meta').innerHTML = meta.map(item => `<span class="meta-chip">${esc(item)}</span>`).join('');
      $('budget-total').textContent = plan.budget || '待估算';
    }

    function renderDateAxis() {
      $('date-axis').innerHTML = (plan.days || []).map((day,index) => `
        <button class="day-tab ${index === activeIndex ? 'active' : ''}" type="button" data-index="${index}" aria-current="${index === activeIndex ? 'date' : 'false'}">
          <strong>Day ${esc(day.dayNumber || index + 1)}</strong><span>${esc(day.label || day.title)}</span>
        </button>`).join('');
      $('date-axis').querySelectorAll('.day-tab').forEach(button => button.addEventListener('click', () => changeDay(Number(button.dataset.index))));
      requestAnimationFrame(() => $('date-axis').querySelector('.day-tab.active')?.scrollIntoView({behavior:'smooth',inline:'center',block:'nearest'}));
    }

    function sourceHtml(sourceIds) {
      const related = uniq(sourceIds).map(id => sources.get(String(id))).filter(Boolean);
      if (!related.length) return '<p class="empty-link">暂无可打开来源，此项按候选信息处理。</p>';
      return related.map(source => {
        const mobile = source.mobileShareUrl || source.shareUrl;
        const web = source.desktopUrl || source.url;
        const stats = [source.likes != null && `赞 ${source.likes}`, source.collects != null && `收藏 ${source.collects}`, source.comments != null && `评论 ${source.comments}`].filter(Boolean).join(' · ');
        return `<article class="source-card"><strong>${esc(source.title || source.id)}</strong><span>${esc([source.author,source.publishedDate].filter(Boolean).join(' · ') || '来源信息待补')}</span>${stats ? `<div class="source-stats">${esc(stats)}</div>` : ''}<div class="link-row">${mobile ? `<a class="source-link" href="${esc(mobile)}" target="_blank" rel="noreferrer">手机打开小红书</a>` : ''}${web && web !== mobile ? `<a class="source-link" href="${esc(web)}" target="_blank" rel="noreferrer">${mobile ? '网页备用' : '网页打开（手机可能不可用）'}</a>` : ''}</div></article>`;
      }).join('');
    }

    function warningHtml(ids) {
      return uniq(ids).map(id => warnings.get(String(id))).filter(Boolean).map(warning => `<div class="warning-inline"><strong>避坑：</strong>${esc(warning.title)}${warning.detail ? ` · ${esc(warning.detail)}` : ''}${warning.mitigation ? `<br>建议：${esc(warning.mitigation)}` : ''}</div>`).join('');
    }

    function itemHtml(item,index) {
      const place = item.placeId ? places.get(String(item.placeId)) : null;
      const [category,bg] = categoryMeta(first(item.category,place?.category));
      const links = item.mapLinks || place?.mapLinks || {};
      const sourceIds = uniq([...list(item.sourceIds),...list(place?.sourceIds)]);
      const note = first(item.notes,place?.reason);
      const confidence = place?.confidence;
      const confidenceText = confidence === 'candidate' ? '候选 · 待核实' : confidence === 'assumption' ? '规划假设' : confidence === 'avoid' ? '不建议' : '';
      return `<article class="stop-card" style="--card-bg:${bg}" data-index="${index + 1}">
        <button class="card-main" type="button" aria-expanded="false">
          <div class="card-top"><span class="time">${esc(item.time || '时间待定')}</span><span class="category">${esc(category)}</span></div>
          <h3 class="card-title">${esc(item.title)}</h3>
          <div class="card-meta">${item.duration ? `<span>${esc(item.duration)}</span>` : ''}${item.cost ? `<span>${esc(item.cost)}</span>` : ''}${confidenceText ? `<span>${esc(confidenceText)}</span>` : ''}</div>
          ${note ? `<p class="card-note">${esc(note)}</p>` : ''}
          <div class="expand-row"><span>查看详情</span><i class="chevron" aria-hidden="true"></i></div>
        </button>
        <div class="card-detail" hidden>
          ${note ? `<p class="detail-line">${esc(note)}</p>` : '<p class="detail-line">补充说明待完善。</p>'}
          ${item.transport ? `<p class="detail-line"><strong>下一程：</strong>${esc(item.transport)}</p>` : ''}
          ${warningHtml(item.warningIds)}
          <div class="link-row">${links.amap ? `<a class="map-link primary" href="${esc(links.amap)}" target="_blank" rel="noreferrer">高德地图</a>` : ''}${links.baidu ? `<a class="map-link" href="${esc(links.baidu)}" target="_blank" rel="noreferrer">百度备用</a>` : ''}${!links.amap && !links.baidu ? '<span class="empty-link">地图信息待补</span>' : ''}</div>
          ${sourceHtml(sourceIds)}
        </div>
      </article>`;
    }

    function desktopFilterBucket(category) {
      if (category === 'restaurant' || category === 'cafe') return 'restaurant';
      if (category === 'attraction' || category === 'neighborhood') return 'attraction';
      if (category === 'hotel') return 'hotel';
      return 'other';
    }

    function desktopItemHtml(item,index) {
      const place = item.placeId ? places.get(String(item.placeId)) : null;
      const rawCategory = first(item.category,place?.category,'other');
      const [category,bg,accent,emoji] = categoryMeta(rawCategory);
      const links = item.mapLinks || place?.mapLinks || {};
      const note = first(item.notes,place?.reason);
      const confidence = place?.confidence;
      const confidenceText = confidence === 'candidate' ? '候选 · 待核实' : confidence === 'assumption' ? '规划假设' : confidence === 'avoid' ? '不建议' : '';
      return `<article class="desktop-stop-card" data-category="${desktopFilterBucket(rawCategory)}" style="--card-bg:${bg};--cat-color:${accent}">
        <button class="desktop-card-toggle" type="button" aria-expanded="false">
          <div class="desktop-card-top"><span class="desktop-cat-icon" aria-hidden="true">${emoji}</span><span class="desktop-card-time">${esc(item.time || '时间待定')}</span></div>
          <h3 class="desktop-card-title">${esc(item.title)}</h3>
          <div class="desktop-card-cost">${item.cost ? `<strong>${esc(item.cost)}</strong>` : '<span>费用待估</span>'}${item.duration ? ` · ${esc(item.duration)}` : ''}</div>
          <span class="desktop-cat-badge">${esc(confidenceText || category)}</span>
          <div class="desktop-expand"><span>展开详情</span><i class="chevron" aria-hidden="true"></i></div>
        </button>
        <div class="desktop-card-detail" hidden>
          <p class="desktop-detail-note">${esc(note || '补充说明待完善。')}</p>
          ${item.transport ? `<div class="desktop-detail-transport">→ ${esc(item.transport)}</div>` : ''}
          <div class="desktop-detail-map-links">${links.amap ? `<a class="desktop-detail-map-link" href="${esc(links.amap)}" target="_blank" rel="noreferrer">📍 高德地图</a>` : links.baidu ? `<a class="desktop-detail-map-link" href="${esc(links.baidu)}" target="_blank" rel="noreferrer">百度地图</a>` : '<span class="empty-link">地图信息待补</span>'}</div>
        </div>
      </article>`;
    }

    function desktopDayHtml(day,index) {
      const items = day.items || [];
      return `<section class="desktop-day-col">
        <header class="desktop-day-header">
          <div class="desktop-day-name">Day ${esc(day.dayNumber || index + 1)}</div>
          <div class="desktop-day-date">${esc(day.label || day.title || '')}</div>
          <div class="desktop-day-count">${items.length} stops</div>
          ${day.summary ? `<span class="desktop-day-summary">${esc(day.summary)}</span>` : ''}
        </header>
        <div class="desktop-stops">${items.map(desktopItemHtml).join('') || '<div class="desktop-empty">今日留白，尚未安排具体行程。</div>'}</div>
      </section>`;
    }

    function configureDesktopDayGrid(days) {
      const count = Math.max(days.length,1);
      const scrollable = days.length > 5;
      const schedule = document.querySelector('.desktop-schedule');
      const daysElement = $('desktop-days');
      schedule.classList.toggle('is-scrollable',scrollable);
      daysElement.style.gridTemplateColumns = 'repeat(' + count + ',minmax(' + (scrollable ? '190px' : '0') + ',1fr))';
      daysElement.style.minWidth = scrollable
        ? String(count * 190 + Math.max(count - 1,0) * 16) + 'px'
        : '0';
    }

    function transportPlanHtml(item) {
      return `<article class="transport-leg">
        <h4>${esc(item.title || '交通方案')}</h4>
        ${item.summary ? `<p>${esc(item.summary)}</p>` : ''}
        ${item.price ? `<div class="price-badge">${esc(item.price)}</div>` : ''}
        ${item.warning ? `<p class="warning-text">${esc(item.warning)}</p>` : ''}
        ${item.url ? `<a class="info-action-link" href="${esc(item.url)}" target="_blank" rel="noreferrer">${esc(item.linkLabel || '打开页面核对 →')}</a>` : ''}
      </article>`;
    }

    function accommodationPlanHtml(item,index) {
      const links=item.mapLinks || {};
      const href=links.amap || links.baidu || '';
      const label=links.amap ? '📍 高德核对' : links.baidu ? '百度核对' : '';
      return `<article class="hotel-item ${item.tone === 'muted' || index > 0 ? 'muted' : ''}">
        ${item.rank ? `<div class="hotel-rank">${esc(item.rank)}</div>` : ''}
        <h4>${esc(item.name || '住宿方案')}</h4>
        ${item.price ? `<div class="hotel-price">${esc(item.price)}</div>` : ''}
        ${item.note ? `<p class="hotel-note">${esc(item.note)}</p>` : ''}
        ${item.risk ? `<p class="hotel-risk">${esc(item.risk)}</p>` : ''}
        ${href ? `<a class="amap-link" href="${esc(href)}" target="_blank" rel="noreferrer">${label}</a>` : ''}
      </article>`;
    }

    function renderDesktopInfo(days,trip) {
      const transportPlans=list(trip.transportPlans);
      const accommodationPlans=list(trip.accommodationPlans);
      const transports=uniq(days.flatMap(day => (day.items || []).map(item => item.transport))).slice(0,4);
      const transportFallback=first(trip.transportSummary,trip.roundTripTransport);
      const accommodationBudget=first(trip.accommodationBudget,trip.hotelBudget,'待估算');
      const transportBody=transportPlans.length
        ? transportPlans.map(transportPlanHtml).join('')
        : `<article class="transport-leg"><h4>${esc(transportFallback || '往返交通待补充')}</h4>${transports.map(item => `<p>→ ${esc(item)}</p>`).join('')}</article>`;
      const accommodationBody=accommodationPlans.length
        ? accommodationPlans.map(accommodationPlanHtml).join('')
        : `<article class="hotel-item"><div class="hotel-rank">住宿基点</div><h4>${esc(plan.baseArea || '住宿区域待定')}</h4><div class="hotel-price">${esc(accommodationBudget)}</div><p class="hotel-note">入住与退订条件以预订页面为准。</p></article>`;
      $('desktop-info-grid').innerHTML =
        '<details class="desktop-info-card"><summary><span>🚄 往返交通方案</span></summary><div class="desktop-info-body">' +
        transportBody +
        (trip.transportNote ? '<p class="desktop-info-note">' + esc(trip.transportNote) + '</p>' : '') +
        '</div></details>' +
        '<details class="desktop-info-card"><summary><span>🏨 ' + esc(trip.accommodationLabel || '住宿方案') + '</span></summary><div class="desktop-info-body">' +
        accommodationBody +
        (trip.accommodationNote ? '<p class="desktop-info-note">' + esc(trip.accommodationNote) + '</p>' : '') +
        '</div></details>';
    }

    function renderDesktop() {
      const days = plan.days || [];
      const trip = plan.trip || {};
      const destination = plan.destination || '目的地';
      $('desktop-header-title').textContent = plan.title || `${destination}旅行计划`;
      $('desktop-header-sub').textContent = `${days.length}天${Math.max(days.length - 1,0)}晚 · 旅笺`;
      $('desktop-header-meta').textContent = uniq([plan.dateRange,plan.travelers,plan.baseArea]).join(' · ');
      renderHeadline($('desktop-hero-title'));
      $('desktop-hero-copy').textContent = '点击任意行程卡片展开详情与地图来源；右侧预算面板随时对账，全部日期一次看清。';
      $('desktop-days').innerHTML = days.map(desktopDayHtml).join('') || '<div class="desktop-empty">暂无行程，请先补充每日安排。</div>';
      configureDesktopDayGrid(days);
      $('desktop-days').querySelectorAll('.desktop-card-toggle').forEach(button => button.addEventListener('click',() => {
        const detail = button.nextElementSibling;
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded',String(open));
        button.closest('.desktop-stop-card').classList.toggle('expanded',open);
        detail.hidden = !open;
        button.querySelector('.desktop-expand span').textContent = open ? '收起详情' : '展开详情';
      }));
      document.querySelectorAll('.desktop-filter').forEach(button => button.addEventListener('click',() => {
        document.querySelectorAll('.desktop-filter').forEach(item => item.classList.toggle('active',item === button));
        const filter = button.dataset.filter;
        document.querySelectorAll('.desktop-stop-card').forEach(card => { card.hidden = filter !== 'all' && card.dataset.category !== filter; });
      }));

      const budgetRows = days.map(day => `<div class="desktop-budget-row"><span>Day ${esc(day.dayNumber)} · ${esc(day.title)}</span><strong>${esc(day.budget || '待估算')}</strong></div>`).join('');
      $('desktop-budget').innerHTML = `<div class="desktop-budget-head"><div class="desktop-budget-title">预算清单</div><span class="desktop-budget-tag">${days.length} DAYS</span></div><p class="desktop-budget-sub">${esc(uniq([plan.dateRange,plan.travelers]).join(' · '))}</p>
        <div class="desktop-budget-section">固定信息</div><div class="desktop-budget-row"><span>住宿区域</span><strong>${esc(plan.baseArea || '待确定')}</strong></div><div class="desktop-budget-row"><span>住宿预算</span><strong>${esc(first(trip.accommodationBudget,trip.hotelBudget,'待估算'))}</strong></div><div class="desktop-budget-row"><span>行程风格</span><strong>${esc(plan.style || '自在探索')}</strong></div>
        <div class="desktop-budget-section">每日游玩支出</div>${budgetRows || '<p>暂无每日预算。</p>'}
        <div class="desktop-budget-total"><span>合计预算</span><strong>${esc(plan.budget || '待估算')}</strong></div>
        ${(plan.assumptions || []).length ? `<div class="desktop-budget-note"><strong>补充说明</strong>${plan.assumptions.map(item => `<p>${esc(item)}</p>`).join('')}</div>` : ''}<button class="desktop-save js-save" type="button">保存完整计划</button>`;

      renderDesktopInfo(days,trip);
    }

    function coordinatesFor(place) {
      const raw = place?.coordinates || {};
      const latitudeValue=first(place?.latitude,raw.latitude,raw.lat), longitudeValue=first(place?.longitude,raw.longitude,raw.lng,raw.lon);
      if (latitudeValue === '' || longitudeValue === '') return null;
      const latitude = Number(latitudeValue);
      const longitude = Number(longitudeValue);
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
      return [longitude,latitude];
    }

    function destinationCenter() {
      for (const destination of plan.destinations || []) {
        const coordinates = coordinatesFor(destination);
        if (coordinates) return coordinates;
      }
      return null;
    }

    function mapDayData(index=mapState.activeIndex) {
      const day = (plan.days || [])[index] || {};
      const configuredStops=list(day.mapStops);
      const stops = configuredStops.length
        ? configuredStops.map((stop,stopIndex)=>({id:String(first(stop.id,`${day.id}-map-${stopIndex}`)),title:first(stop.title,stop.name,'地点'),time:first(stop.time,stop.meta),transport:stop.transport,category:first(stop.category,stop.kind,'other'),side:stop.side,labelY:Number(stop.labelY || 0),coordinates:coordinatesFor(stop)}))
        : (day.items || []).map((item,itemIndex) => {
            const place = item.placeId ? places.get(String(item.placeId)) : null;
            return {id:String(item.id || `${day.id}-${itemIndex}`),title:item.title,time:item.time,transport:item.transport,category:first(item.category,place?.category,'other'),side:item.side,labelY:Number(item.labelY || 0),coordinates:coordinatesFor(place)};
          });
      const legs=mapLegData(stops,day.mapLegs);
      return {day,stops,points:stops.filter(stop => stop.coordinates),legs};
    }

    function routeMode(transport) {
      const value=String(transport || '');
      if (/步行|徒步/.test(value)) return 'walk';
      if (/打车|出租|网约|驾车|接送/.test(value)) return 'drive';
      return 'ride';
    }

    function pinKind(point) {
      if (point.category === 'optional') return 'attraction';
      return ['restaurant','attraction','hotel','other'].includes(point.category) ? point.category : 'other';
    }

    function pinSide(point,index,points) {
      if (point.side === 'left') return ' left';
      const longitudes=points.map(item=>item.coordinates?.[0]).filter(Number.isFinite);
      const min=Math.min(...longitudes),max=Math.max(...longitudes);
      return index%3===2 || (Number.isFinite(min) && max>min && point.coordinates[0]>min+(max-min)*.72) ? ' left' : '';
    }

    function pinHtml(point,index) {
      return '<span class="pin-dot" data-n="'+String(index+1)+'"></span><span class="pin-line"></span><span class="pin-card">'+esc(point.title)+'<small>'+esc(point.time || '')+'</small></span>';
    }

    function mapLegData(stops,configuredLegs) {
      const byId=new Map(stops.map(stop=>[String(stop.id),stop]));
      const explicit=list(configuredLegs).map((leg,index)=>{
        const fromStop=byId.get(String(leg.from)),toStop=byId.get(String(leg.to));
        if (!fromStop || !toStop) return null;
        const summary=first(leg.summary,leg.transport,'交通方式待补');
        return {id:String(first(leg.id,`leg-${index+1}`)),from:fromStop.id,to:toStop.id,fromStop,toStop,mode:first(leg.mode,routeMode(summary)),summary};
      }).filter(Boolean);
      if (explicit.length) return explicit;
      return stops.slice(1).map((stop,index)=>{const previous=stops[index];return {id:`leg-${index+1}`,from:previous.id,to:stop.id,fromStop:previous,toStop:stop,mode:routeMode(previous.transport),summary:previous.transport || '交通方式待补'};});
    }

    function haversineKm(from,to) {
      const radians=value=>value*Math.PI/180;
      const earthRadius=6371;
      const deltaLatitude=radians(to[1]-from[1]),deltaLongitude=radians(to[0]-from[0]);
      const latitude1=radians(from[1]),latitude2=radians(to[1]);
      const value=Math.sin(deltaLatitude/2)**2+Math.cos(latitude1)*Math.cos(latitude2)*Math.sin(deltaLongitude/2)**2;
      return earthRadius*2*Math.atan2(Math.sqrt(value),Math.sqrt(1-value));
    }

    function estimateRoute(points,legs) {
      const usableLegs=(legs || []).filter(leg=>leg.fromStop.coordinates && leg.toStop.coordinates);
      if (!usableLegs.length) return {distance:'待补',duration:'待补'};
      let distance=0,minutes=0;
      usableLegs.forEach(leg=>{
        const mode=leg.mode;
        const segment=haversineKm(leg.fromStop.coordinates,leg.toStop.coordinates)*1.22;
        const speed=mode==='walk'?4.5:mode==='drive'?28:22;
        distance+=segment;
        minutes+=segment/speed*60+(mode==='walk'?0:3);
      });
      const roundedMinutes=Math.max(1,Math.round(minutes/5)*5);
      const duration=roundedMinutes>=60
        ? `约 ${Math.floor(roundedMinutes/60)} 小时${roundedMinutes%60 ? ` ${roundedMinutes%60} 分钟` : ''}`
        : `约 ${roundedMinutes} 分钟`;
      return {distance:`约 ${distance.toFixed(1)} km`,duration};
    }

    function renderMapTabs() {
      $('map-day-tabs').innerHTML = (plan.days || []).map((day,index) => `<button class="map-day-tab ${index === mapState.activeIndex ? 'active' : ''}" type="button" data-index="${index}" role="tab" aria-selected="${index === mapState.activeIndex}">Day ${esc(day.dayNumber || index + 1)}</button>`).join('');
      $('map-day-tabs').querySelectorAll('.map-day-tab').forEach(button => button.addEventListener('click',() => { mapState.activeIndex=Number(button.dataset.index); renderMapTabs(); renderMapDay(); }));
    }

    function renderMapRoutes(stops,legs) {
      if (!legs.length) { $('map-route-list').innerHTML='<div class="map-route-card"><strong>暂无路线</strong><span>请先补充当天行程。</span></div>'; return; }
      $('map-route-list').innerHTML = legs.map((leg,index) => `<button class="map-route-card" type="button" data-index="${index}"><span class="map-pin${leg.toStop.category === 'optional' ? ' optional' : ''}"><span>${index + 1}</span></span><strong>${String(index + 1).padStart(2,'0')} · ${esc(leg.fromStop.title)} → ${esc(leg.toStop.title)}</strong><span>${esc(leg.summary)}</span></button>`).join('');
      $('map-route-list').querySelectorAll('.map-route-card').forEach(button => button.addEventListener('click',() => focusMapStop(legs[Number(button.dataset.index)]?.toStop)));
    }

    function renderStaticMap(points,legs) {
      if (!points.length) { $('map-static').innerHTML='<div class="map-empty">当天地点缺少有效坐标，文字路线与地图链接仍可使用；请补充坐标后再显示点位。</div>'; return; }
      const longitudes=points.map(point=>point.coordinates[0]), latitudes=points.map(point=>point.coordinates[1]);
      const minLng=Math.min(...longitudes),maxLng=Math.max(...longitudes),minLat=Math.min(...latitudes),maxLat=Math.max(...latitudes);
      const lngRange=Math.max(maxLng-minLng,.012),latRange=Math.max(maxLat-minLat,.012);
      const projected=points.map((point,index)=>({point,index,x:12+76*(point.coordinates[0]-minLng)/lngRange,y:88-76*(point.coordinates[1]-minLat)/latRange}));
      const projectedById=new Map(projected.map(position=>[String(position.point.id),position]));
      const lines=legs.map(leg=>{const from=projectedById.get(String(leg.from)),to=projectedById.get(String(leg.to));return from&&to?`<line class="${leg.mode}" x1="${from.x}" y1="${from.y}" x2="${to.x}" y2="${to.y}"></line>`:'';}).join('');
      const markers=projected.map(position=>{const side=position.point.side==='left'||position.index%3===2||position.x>72?' left':'';return `<div class="static-marker ${pinKind(position.point)}${side}" style="left:${position.x}%;top:${position.y}%;--label-y:${position.point.labelY||0}px">${pinHtml(position.point,position.index)}</div>`;}).join('');
      $('map-static').innerHTML=`<svg class="static-relations" viewBox="0 0 100 100" preserveAspectRatio="none">${lines}</svg>${markers}`;
    }

    function clearMapRoute() {
      const map=mapState.map;
      const empty={type:'FeatureCollection',features:[]};
      if (map?.getSource('travel-route')) map.getSource('travel-route').setData(empty);
      mapState.markers.forEach(marker=>marker.remove());
      mapState.markers=[];
    }

    function setMapDrag(enabled) {
      mapState.drag=Boolean(enabled);
      const panel=document.querySelector('.map-panel');
      const button=panel?.querySelector('[data-map-ctrl="drag"]');
      panel?.classList.toggle('drag-enabled',mapState.drag);
      if (button) {
        button.classList.toggle('active',mapState.drag);
        button.textContent=mapState.drag ? 'DRAG ON' : 'DRAG';
        button.setAttribute('aria-pressed',String(mapState.drag));
      }
      if (mapState.map) {
        if (mapState.drag) mapState.map.dragPan.enable();
        else mapState.map.dragPan.disable();
      }
    }

    function bindMapControls() {
      const panel=document.querySelector('.map-panel');
      if (!panel || panel.dataset.controlsBound === 'true') return;
      panel.dataset.controlsBound='true';
      panel.querySelector('[data-map-ctrl="zoom-in"]')?.addEventListener('click',event=>{event.stopPropagation();mapState.map?.zoomIn();});
      panel.querySelector('[data-map-ctrl="zoom-out"]')?.addEventListener('click',event=>{event.stopPropagation();mapState.map?.zoomOut();});
      panel.querySelector('[data-map-ctrl="drag"]')?.addEventListener('click',event=>{event.stopPropagation();setMapDrag(!mapState.drag);});
      setMapDrag(false);
    }

    function mountMap(points,legs) {
      const panel=document.querySelector('.map-panel');
      const center=points[0]?.coordinates || destinationCenter();
      if (!center) { panel.classList.remove('map-live'); return; }
      if (!window.maplibregl) { panel.classList.remove('map-live'); $('map-status').textContent='MapLibre 未加载，已显示文件内静态点位与路线。'; return; }
      if (mapState.map) { if (mapState.ready) syncMap(points,legs); return; }
      try {
        const map=new maplibregl.Map({container:'travel-map',style:{version:8,sources:{base:{type:'raster',tiles:['https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'],tileSize:256,attribution:'地图数据 © 高德地图'}},layers:[{id:'base',type:'raster',source:'base',paint:{'raster-saturation':-.62,'raster-opacity':.82}}]},center,zoom:points.length ? 12 : 10.5,interactive:true,attributionControl:false});
        map.scrollZoom.disable(); map.boxZoom.disable(); map.doubleClickZoom.disable(); map.dragPan.disable();
        mapState.map=map;
        map.on('load',()=>{
          mapState.ready=true;
          panel.classList.add('map-live');
          syncMap(mapState.points,mapState.legs);
          $('map-status').textContent=mapState.points.length
            ? '在线底图与当天路线已加载。'
            : '目的地底图已加载；当天地点缺少坐标，文字路线与地图链接仍可使用。';
        });
        map.on('error',()=>{
          if (!mapState.ready) $('map-status').textContent='在线底图加载中或暂不可用，已保留静态点位与文字路线。';
        });
      } catch { panel.classList.remove('map-live'); $('map-status').textContent='MapLibre 初始化失败，已保留静态点位与路线。'; }
    }

    function syncMap(points,legs) {
      const map=mapState.map;
      if (!map || !mapState.ready) return;
      document.querySelector('.map-panel').classList.add('map-live');
      clearMapRoute();
      if (!points.length) {
        const center=destinationCenter();
        if (center) map.easeTo({center,zoom:10.5,duration:0});
        map.resize();
        return;
      }
      const data={type:'FeatureCollection',features:legs.filter(leg=>leg.fromStop.coordinates&&leg.toStop.coordinates).map(leg=>({type:'Feature',properties:{mode:leg.mode},geometry:{type:'LineString',coordinates:[leg.fromStop.coordinates,leg.toStop.coordinates]}}))};
      if (map.getSource('travel-route')) map.getSource('travel-route').setData(data);
      else {
        map.addSource('travel-route',{type:'geojson',data});
        [['route-walk','#4A8F63','walk'],['route-ride','#2D73C8','ride'],['route-drive','#D45D3D','drive']].forEach(([id,color,mode])=>map.addLayer({id,type:'line',source:'travel-route',filter:['==',['get','mode'],mode],paint:{'line-color':color,'line-opacity':.88,'line-width':3,'line-dasharray':[2,2]}}));
      }
      mapState.markers=points.map((point,index)=>{const element=document.createElement('div');element.className='static-marker '+pinKind(point)+pinSide(point,index,points);element.style.setProperty('--label-y',(point.labelY||0)+'px');element.innerHTML=pinHtml(point,index);element.title=point.title;return new maplibregl.Marker({element,anchor:'center'}).setLngLat(point.coordinates).addTo(map);});
      if (points.length===1) map.easeTo({center:points[0].coordinates,zoom:14,duration:0});
      else { const bounds=points.reduce((value,point)=>value.extend(point.coordinates),new maplibregl.LngLatBounds(points[0].coordinates,points[0].coordinates)); map.fitBounds(bounds,{padding:{top:90,right:90,bottom:55,left:65},maxZoom:14,duration:0}); }
      map.resize();
    }

    function focusMapStop(stop) {
      if (stop?.coordinates && mapState.map && mapState.ready) mapState.map.easeTo({center:stop.coordinates,zoom:Math.max(mapState.map.getZoom(),14),duration:420});
    }

    function renderMapDay() {
      const {day,stops,points,legs}=mapDayData();
      mapState.points=points;
      mapState.legs=legs;
      const estimate=estimateRoute(points,legs);
      $('map-title').textContent=`Day ${day.dayNumber || mapState.activeIndex + 1} · ${day.title || ''}`;
      $('map-stop-count').textContent=stops.length;
      $('map-distance').textContent=day.distanceEstimate || estimate.distance;
      $('map-duration').textContent=day.durationEstimate || estimate.duration;
      $('map-status').textContent=points.length
        ? '已载入 ' + points.length + '/' + stops.length + ' 个有效坐标；正在加载在线底图。'
        : destinationCenter()
          ? '正在加载目的地底图；当天地点缺少坐标，文字路线与地图链接仍可使用。'
          : '当天地点与目的地均缺少有效坐标，已保留文字路线与地图链接。';
      renderMapRoutes(stops,legs); renderStaticMap(points,legs); mountMap(points,legs);
    }

    function setPageView(view) {
      const showMap=view==='map';
      document.body.classList.toggle('map-mode',showMap);
      document.querySelectorAll('[data-itinerary-view]').forEach(section=>{section.hidden=showMap;});
      $('budget-sheet').hidden=true;
      $('shared-map-view').hidden=!showMap;
      document.querySelectorAll('.js-page-tab').forEach(button=>{const active=button.dataset.view===view;button.classList.toggle('active',active);button.setAttribute('aria-selected',String(active));});
      if(showMap){renderMapTabs();renderMapDay();requestAnimationFrame(()=>mapState.map?.resize());}
      window.scrollTo({top:0,behavior:'instant'});
    }

    function dayWarnings(day) {
      return (plan.warnings || []).filter(item => !item.dayId || String(item.dayId) === String(day.id));
    }

    function renderDay(direction=0) {
      const day = (plan.days || [])[activeIndex];
      if (!day) {
        $('day-root').innerHTML = '<section class="info-card"><h3>暂无行程</h3><p>请先补充每日安排。</p></section>';
        return;
      }
      const route = (day.items || []).map(item => item.title).filter(Boolean).join(' → ');
      const reminders = dayWarnings(day);
      $('day-root').innerHTML = `<section class="day-panel" data-direction="${direction}">
        <header class="day-heading"><div class="day-kicker">Day ${esc(day.dayNumber || activeIndex + 1)} · ${esc(day.label || '')}</div><h2>${esc(day.title)}</h2>${day.summary ? `<p class="day-summary">${esc(day.summary)}</p>` : ''}</header>
        <div class="route-strip">${esc(route || '今日路线待补充')}</div>
        <div class="timeline">${(day.items || []).map(itemHtml).join('') || '<section class="info-card"><h3>今日留白</h3><p>尚未安排具体行程。</p></section>'}</div>
        ${day.backup ? `<aside class="info-card"><h3>天气 / 排队备选</h3><p>${esc(day.backup)}</p></aside>` : ''}
        ${reminders.length ? `<aside class="info-card"><h3>今天先避这些坑</h3>${reminders.map(item => `<p>⚠ ${esc(item.title)}${item.detail ? `：${esc(item.detail)}` : ''}${item.mitigation ? `；${esc(item.mitigation)}` : ''}</p>`).join('')}</aside>` : ''}
      </section>`;
      $('day-root').querySelectorAll('.card-main').forEach(button => button.addEventListener('click', () => {
        const detail = button.nextElementSibling;
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        detail.hidden = !open;
        button.querySelector('.expand-row span').textContent = open ? '收起详情' : '查看详情';
      }));
    }

    function changeDay(nextIndex) {
      if (nextIndex < 0 || nextIndex >= (plan.days || []).length || nextIndex === activeIndex) return;
      const direction = nextIndex > activeIndex ? 1 : -1;
      activeIndex = nextIndex;
      renderDateAxis();
      renderDay(direction);
      window.scrollTo({top:Math.max(document.querySelector('.date-axis-wrap').offsetTop,0),behavior:'smooth'});
    }

    function renderBudget() {
      const trip = plan.trip || {};
      const rows = [
        ...((plan.days || []).map(day => [`Day ${day.dayNumber} · ${day.title}`,day.budget || '待估算'])),
        [plan.baseArea ? `住宿 · ${plan.baseArea}` : '住宿', first(trip.accommodationBudget,trip.hotelBudget,'待估算')],
        ['总预算',plan.budget || '待估算']
      ];
      $('budget-rows').innerHTML = rows.map(([label,value]) => `<div class="budget-row"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`).join('') + ((plan.assumptions || []).length ? `<div class="info-card"><h3>补充说明</h3>${plan.assumptions.map(item => `<p>${esc(item)}</p>`).join('')}</div>` : '');
    }

    function downloadPage() {
      const content = '<!DOCTYPE html>\n' + document.documentElement.outerHTML;
      const url = URL.createObjectURL(new Blob([content],{type:'text/html;charset=utf-8'}));
      const link = document.createElement('a');
      link.href = url;
      link.download = 'lujian-travel-plan.html';
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    }

    $('day-root').addEventListener('touchstart',event => { touchStartX = event.changedTouches[0]?.clientX ?? null; },{passive:true});
    $('day-root').addEventListener('touchend',event => {
      if (touchStartX === null) return;
      const delta = (event.changedTouches[0]?.clientX ?? touchStartX) - touchStartX;
      touchStartX = null;
      if (Math.abs(delta) < 56) return;
      changeDay(activeIndex + (delta < 0 ? 1 : -1));
    },{passive:true});
    $('budget-trigger').addEventListener('click',() => $('budget-sheet').hidden = false);
    $('close-budget').addEventListener('click',() => $('budget-sheet').hidden = true);
    $('budget-sheet').addEventListener('click',event => { if (event.target === $('budget-sheet')) $('budget-sheet').hidden = true; });
    document.querySelectorAll('.js-page-tab').forEach(button => button.addEventListener('click',() => setPageView(button.dataset.view)));

    renderHero();
    bindMapControls();
    renderDateAxis();
    renderDay();
    renderBudget();
    renderDesktop();
    document.querySelectorAll('.js-save').forEach(button => button.addEventListener('click',downloadPage));
  </script>
</body>
</html>
'''
    return template.replace("__TITLE__", title).replace("__HEADLINE__", headline_markup).replace("__PAYLOAD__", payload)


def main() -> int:
    parser = argparse.ArgumentParser(description="Create a 旅笺-compatible single-file travel plan HTML.")
    parser.add_argument("itinerary", type=Path)
    parser.add_argument("output", type=Path, nargs="?", default=Path("lujian-travel-plan.html"))
    parser.add_argument("--skip-validation", action="store_true")
    args = parser.parse_args()

    data = json.loads(args.itinerary.read_text(encoding="utf-8"))
    data = ensure_place_links(data)
    errors, warnings = validate(data)
    for warning in warnings:
        print(f"WARNING: {warning}")
    if errors and not args.skip_validation:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(build_html(data))
    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
