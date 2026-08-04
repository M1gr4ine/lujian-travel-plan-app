#!/usr/bin/env python3
"""Validate the generated 旅笺 HTML contract with only the Python standard library."""

from __future__ import annotations

import argparse
import json
import re
from html.parser import HTMLParser
from pathlib import Path
from typing import Any


class LujianParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=False)
        self.capture = False
        self.blocks: list[str] = []
        self.current: list[str] = []
        self.external_scripts: list[str] = []
        self.external_stylesheets: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "script" and attributes.get("src"):
            self.external_scripts.append(str(attributes["src"]))
        if tag == "link" and "stylesheet" in str(attributes.get("rel", "")).lower():
            self.external_stylesheets.append(str(attributes.get("href", "")))
        if tag == "script" and attributes.get("id") == "lujian-plan":
            if attributes.get("type") != "application/json":
                raise ValueError('script#lujian-plan must use type="application/json"')
            self.capture = True
            self.current = []

    def handle_endtag(self, tag: str) -> None:
        if tag == "script" and self.capture:
            self.blocks.append("".join(self.current))
            self.capture = False
            self.current = []

    def handle_data(self, data: str) -> None:
        if self.capture:
            self.current.append(data)


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def validate_payload(payload: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if payload.get("schemaVersion") != 1:
        errors.append("schemaVersion must equal 1")
    if not payload.get("title"):
        errors.append("title is required")
    if not payload.get("headline"):
        errors.append("headline is required")
    if not isinstance(payload.get("destinations"), list) or not payload.get("destinations"):
        errors.append("destinations must be a non-empty array")
    if not isinstance(payload.get("days"), list) or not payload.get("days"):
        errors.append("days must be a non-empty array")

    for day_index, day in enumerate(as_list(payload.get("days"))):
        prefix = f"days[{day_index}]"
        if not isinstance(day, dict):
            errors.append(f"{prefix} must be an object")
            continue
        for field in ("id", "label", "title"):
            if not day.get(field):
                errors.append(f"{prefix}.{field} is required")
        if not isinstance(day.get("items"), list):
            errors.append(f"{prefix}.items must be an array")
            continue
        for item_index, item in enumerate(day["items"]):
            item_prefix = f"{prefix}.items[{item_index}]"
            if not isinstance(item, dict):
                errors.append(f"{item_prefix} must be an object")
                continue
            for field in ("id", "time", "title", "category"):
                if not item.get(field):
                    errors.append(f"{item_prefix}.{field} is required")
            if not isinstance(item.get("notes"), str):
                errors.append(f"{item_prefix}.notes must be a string")
    return errors


def compare_itinerary(payload: dict[str, Any], itinerary: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    expected_days = as_list(itinerary.get("days"))
    if len(as_list(payload.get("days"))) != len(expected_days):
        errors.append("embedded day count differs from itinerary")
    expected_sources = {
        str(source.get("id"))
        for source in as_list(itinerary.get("sources"))
        if isinstance(source, dict) and source.get("id")
    }
    actual_sources = {
        str(source.get("id"))
        for source in as_list(payload.get("sources"))
        if isinstance(source, dict) and source.get("id")
    }
    if actual_sources != expected_sources:
        errors.append("embedded source IDs differ from itinerary")
    expected_places = {
        str(place.get("id"))
        for place in as_list(itinerary.get("places"))
        if isinstance(place, dict) and place.get("id")
    }
    actual_places = {
        str(place.get("id"))
        for place in as_list(payload.get("places"))
        if isinstance(place, dict) and place.get("id")
    }
    if actual_places != expected_places:
        errors.append("embedded place IDs differ from itinerary")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate a 旅笺-compatible travel plan HTML file.")
    parser.add_argument("html", type=Path)
    parser.add_argument("--itinerary", type=Path)
    args = parser.parse_args()

    raw = args.html.read_bytes()
    errors: list[str] = []
    if raw.startswith(b"\xef\xbb\xbf"):
        errors.append("HTML must be UTF-8 without BOM")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        print(f"ERROR: HTML is not strict UTF-8: {exc}")
        return 1

    html_parser = LujianParser()
    try:
        html_parser.feed(text)
    except ValueError as exc:
        errors.append(str(exc))
    if len(html_parser.blocks) != 1:
        errors.append(f"expected exactly one script#lujian-plan, found {len(html_parser.blocks)}")
        payload: dict[str, Any] = {}
    else:
        try:
            payload = json.loads(html_parser.blocks[0])
            if not isinstance(payload, dict):
                raise ValueError("payload must be an object")
        except (json.JSONDecodeError, ValueError) as exc:
            errors.append(f"invalid lujian-plan JSON: {exc}")
            payload = {}

    errors.extend(validate_payload(payload))
    allowed_scripts = {"https://unpkg.com/maplibre-gl@5.14.0/dist/maplibre-gl.js"}
    allowed_stylesheets = {"https://unpkg.com/maplibre-gl@5.14.0/dist/maplibre-gl.css"}
    unexpected_scripts = sorted(set(html_parser.external_scripts) - allowed_scripts)
    unexpected_stylesheets = sorted(set(html_parser.external_stylesheets) - allowed_stylesheets)
    if unexpected_scripts:
        errors.append(f"only pinned MapLibre script is allowed: {unexpected_scripts}")
    if unexpected_stylesheets:
        errors.append(f"only pinned MapLibre stylesheet is allowed: {unexpected_stylesheets}")
    if set(html_parser.external_scripts) != allowed_scripts:
        errors.append("pinned MapLibre script dependency is required")
    if set(html_parser.external_stylesheets) != allowed_stylesheets:
        errors.append("pinned MapLibre stylesheet dependency is required")

    required_patterns = {
        "mobile app": r'class="mobile-app app-shell paper"',
        "mobile sticky date axis": r'class="date-axis-wrap"',
        "mobile single day renderer": r'class="day-panel"',
        "static Android cover headline": r'<h1 id="plan-title" data-lujian-cover>.+?</h1>',
        "date tab switch": r"function changeDay\(",
        "swipe start": r"touchstart",
        "swipe end": r"touchend",
        "mobile itinerary/map tabs": r'id="mobile-map-tab"',
        "desktop app": r'class="desktop-app paper"',
        "desktop multi-day renderer": r"function desktopDayHtml\(",
        "desktop day columns": r'class="desktop-day-col"',
        "shared mobile headline": r"renderHeadline\(\$\('plan-title'\)\)",
        "shared desktop headline": r"renderHeadline\(\$\('desktop-hero-title'\)\)",
        "bold shared brand mark": r"\.brand-mark\{[^}]*font:900",
        "desktop sticky budget": r"\.desktop-budget\{position:sticky",
        "desktop conditional overflow": r"function configureDesktopDayGrid\(",
        "overflow only after five days": r"days\.length > 5",
        "compact transport and hotel accordions": r'<details class="desktop-info-card">',
        "independent accordion heights": r"\.desktop-info-grid\{[^}]*align-items:start",
        "legacy desktop detail note": r'class="desktop-detail-note"',
        "legacy desktop detail transport": r'class="desktop-detail-transport"',
        "legacy desktop map action": r'class="desktop-detail-map-link"',
        "legacy transport cards": r'class="transport-leg"',
        "legacy accommodation cards": r'class="hotel-item',
        "structured transport renderer": r"function transportPlanHtml\(",
        "structured accommodation renderer": r"function accommodationPlanHtml\(",
        "responsive desktop breakpoint": r"@media \(min-width:960px\)",
        "shared desktop data renderer": r"function renderDesktop\(",
        "desktop itinerary/map tabs": r'id="desktop-map-tab"',
        "shared map view": r'id="shared-map-view"',
        "legacy itinerary/map view switch": r"function setPageView\(view\)",
        "legacy map pin dot": r'class="pin-dot"',
        "legacy map pin leader": r'class="pin-line"',
        "legacy map pin place card": r'class="pin-card"',
        "legacy route distance estimate": r'<small>预计里程</small>',
        "legacy route duration estimate": r'<small>移动时间</small>',
        "route estimate fallback": r"function estimateRoute\(points,legs\)",
        "independent map leg renderer": r"function mapLegData\(",
        "map route list uses legs": r"renderMapRoutes\(stops,legs\)",
        "legacy map controls": r'class="map-controls"[^>]*>.*data-map-ctrl="zoom-in".*data-map-ctrl="zoom-out".*data-map-ctrl="drag"',
        "default map drag lock": r"map\.dragPan\.disable\(\)",
        "legacy route-list pins": r'class="map-pin',
        "MapLibre renderer": r"new maplibregl\.Map\(",
        "static map fallback": r"function renderStaticMap\(",
        "destination map center fallback": r"function destinationCenter\(",
        "stale map route clearing": r"function clearMapRoute\(",
        "save action": r"function downloadPage\(",
        "旅笺 paper color": r"#FAF6EF",
        "旅笺 coral color": r"#FF6B4A",
    }
    for label, pattern in required_patterns.items():
        if not re.search(pattern, text):
            errors.append(f"missing {label}")
    if 'id="itinerary-data"' in text:
        errors.append("legacy itinerary-data block must not be present")
    if "static-marker-dot" in text:
        errors.append("simplified map pin must not replace the legacy dot/leader/place-card pin")
    if "function desktopItemHtml" in text and "function desktopDayHtml" in text:
        desktop_detail = text.split("function desktopItemHtml", 1)[1].split("function desktopDayHtml", 1)[0]
        if "sourceHtml(sourceIds)" in desktop_detail:
            errors.append("desktop legacy detail must not embed source cards")
        if "百度备用" in desktop_detail:
            errors.append("desktop legacy detail must not add the new Baidu secondary button")

    if args.itinerary:
        itinerary = json.loads(args.itinerary.read_text(encoding="utf-8"))
        if not isinstance(itinerary, dict):
            errors.append("itinerary must be an object")
        else:
            errors.extend(compare_itinerary(payload, itinerary))

    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        return 1
    print(
        f"OK: {args.html} is 旅笺 schemaVersion=1 compatible "
        f"({len(as_list(payload.get('days')))} day(s), {len(as_list(payload.get('sources')))} source(s))"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
