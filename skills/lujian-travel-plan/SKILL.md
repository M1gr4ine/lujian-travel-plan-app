---
name: lujian-travel-plan
description: 当用户要求基于小红书/Xiaohongshu/RedNote 旅行笔记生成可导入“旅笺”Android App 的结构化单文件 HTML，或明确提到旅笺、lujian、手机单轴日期、电脑多日并排、响应式双视图、schemaVersion=1 行程文件时使用此技能。
---

# lujian-travel-plan

## Core Workflow

Use this skill to produce a source-backed travel planner from real 小红书 notes. The final output is one responsive UTF-8 file, `lujian-travel-plan.html`, that can be imported as an enhanced plan by the “旅笺” Android App, opened directly on phones or computers, or renamed to `index.html` for GitHub Pages.

Only the final artifact contract differs from `xhs-travel-planner`. Keep browser login, XHS collection, source thresholds, extraction, deduplication, route planning, itinerary JSON validation, and map-link generation behavior unchanged. If the user wants a generic mobile H5 without 旅笺 compatibility, use `xhs-travel-planner` instead.

1. Confirm the brief only when missing details block the itinerary: destination, dates or trip length, travelers, travel style, must-go places, budget, pace, and hotel/base area if known.
2. Before searching, open the user's regular Chrome/Edge browser profile to 小红书 so an existing login can be reused. Prefer `python scripts/open_xhs_login.py --keyword "<destination> 美食 避坑"` when a local browser is available. If the regular profile is not logged in, require the user to scan-code login in that browser session. Do not accept account passwords, Cookie strings, or verification codes. Use `--isolated-profile` only when the user explicitly wants a separate browser profile.
3. Gather real 小红书 notes only from the logged-in browser session. Open each retained note and record its traceable source metadata; do not use non-XHS web sources or unreadable search snippets as itinerary evidence.
4. Do not bypass CAPTCHA, rate limits, robots controls, paywalls, or platform access restrictions. If the site asks for verification, ask the user to complete it in the browser.
5. Keep source traceability. Every place, restaurant, route tip, and warning must link back to one or more source records whenever possible. For each retained XHS note, capture the platform-generated share link for mobile opening when the share UI exposes it; keep the browser page URL as desktop fallback.
6. Extract and deduplicate places, restaurants, shops, neighborhoods, transport tips, reservation notes, opening-hour risks, queue warnings, price warnings, and "avoid" advice.
7. Build a day-by-day route by clustering nearby items, checking map search/navigation links, and minimizing backtracking. Use 高德 links first and 百度 links as fallback.
8. Generate `lujian-travel-plan.html` with `scripts/create_static_html.py`.
9. Validate the itinerary with `scripts/validate_itinerary.py`, then validate the generated HTML with `scripts/validate_lujian_html.py` before presenting it.

## User Templates

When the user asks how to use the skill or wants a reusable workflow, provide:

- `assets/brief-template.md` as the fill-in user brief.
- `references/prompt-templates.md` as the copy-paste 攻略提示词 set.
- `assets/itinerary-template.json` as the skeleton for the structured itinerary.

## Research Rules

Read `references/xhs-research-workflow.md` before collecting notes. Use it for query patterns, credibility checks, and extraction rules.

Minimum source standard:

- Collect at least 8 opened notes for a one-day/narrow brief, 15 for a 2-4 day itinerary, and 20 for trips of 5 or more days. Only use fewer when the user supplied a fixed small source set or access is blocked; state that limitation.
- Run several intent searches (route, food, avoid-pit, logistics, and key neighborhoods/attractions). Do not build the itinerary from a single result page.
- Prioritize notes with visibly high engagement in the search results or opened note, especially likes and saves, while retaining useful lower-engagement notes for specific warnings or niche locations.
- Record `id`, `platform`, `url`, `mobileShareUrl`, `title`, `author`, `publishedDate`, `capturedAt`, and visible `likes`, `collects`, `comments` when available. `mobileShareUrl` must come from the note's share/copy-link UI, typically an `xhslink.com` link; never construct it from the note ID.
- Aim for multiple independent notes supporting important recommendations; a popular single note is not sufficient evidence by itself.
- Mark recommendations as `confirmed` only when the source is specific enough to identify the place and reason.
- Mark vague mentions, uncertain names, or unsourced AI inferences as `candidate`.
- Preserve negative advice as `warnings`; do not bury it inside attraction notes.

## Itinerary Data

Use `references/itinerary-schema.md` as the canonical JSON contract. The top-level object must include:

- `trip`: destination, dates or day count, travelers, style, base area, and assumptions.
- `sources[]`: source records for 小红书 notes opened or verified in the logged-in browser session.
- `places[]`: normalized places with category, area, address if known, source IDs, confidence, tags, and map links.
- `warnings[]`: avoid-pit advice with severity, affected place/day when known, and source IDs.
- `days[]`: ordered route items with time blocks, transport notes, map links, source IDs, and alternatives.

Generate map links with:

```bash
python scripts/generate_map_links.py itinerary.json --write
```

Validate with:

```bash
python scripts/validate_itinerary.py itinerary.json
```

## 旅笺 HTML 产物

Read `references/lujian-html-spec.md` before generating or reviewing the HTML output. Keep the research and itinerary JSON stages above unchanged.

Default single-file output:

```bash
python scripts/generate_map_links.py itinerary.json --write
python scripts/validate_itinerary.py itinerary.json
python scripts/create_static_html.py itinerary.json lujian-travel-plan.html
python scripts/validate_lujian_html.py lujian-travel-plan.html
python scripts/test_template_regressions.py
```

To deploy on GitHub Pages, copy or upload `lujian-travel-plan.html` as `index.html` in the target Pages directory or repository root.

The HTML output must:

- Embed exactly one `<script id="lujian-plan" type="application/json">` block with `schemaVersion: 1`, plan metadata, destinations, days, itinerary items, costs, places, warnings, and source records.
- Use the 旅笺 print-journal visual system: paper `#FAF6EF`, ink `#2A2520`, coral `#FF6B4A`, gold `#F2B43A`, serif headings, and thick outlined rounded cards. Keep all presentation code inline except for the pinned MapLibre CSS/JS dependency.
- Keep the generated visual language stable across runs. Content updates must not substantially change the paper palette, type scale, card geometry, header composition, or budget panel unless the user explicitly requests a redesign.
- In the same file, use two responsive presentations backed by the same embedded data: below `960px`, use a sticky horizontal date axis and show exactly one day at a time; at `960px` and above, use the desktop 大连 layout with all day columns side by side and a sticky right budget panel.
- Use one shared large `headline` on phones and computers. Prefer `trip.headline`; otherwise generate the screenshot-style sentence from day count, destination, and style. Keep the top brand label separate, and render the left icon as the same explicitly bold `笺` glyph on both viewports.
- Underline the final headline phrase with the legacy continuous gold wave rendered as an inline SVG/CSS decoration. Never simulate the wave with text characters or a dotted radial-gradient pattern.
- Keep the legacy `行程 / 地图` view switch on both phones and computers, with both entries sharing one view state and one map instance. Switching back to the itinerary must preserve its selected day.
- Keep the legacy map-pin treatment in both live and static maps: numbered `pin-dot`, short `pin-line`, and named `pin-card`. Keep a separate 22px orange numbered `map-pin` beside every route-list row. Do not replace either treatment with plain text or a simplified standalone dot. Restore the legacy top-right `+ / − / DRAG` controls; dragging is locked by default and enabled only while `DRAG ON` is active. The map tab must use pinned MapLibre CSS/JS, render the active day's ordered route, and retain an in-file static point/line fallback when the library, basemap, or coordinates are unavailable. When a reviewed plan returns to the fixed hotel, represent that closing segment explicitly with `days[].mapStops` plus `days[].mapLegs`; do not drop it merely because the hotel stop is already the first marker, and do not invent a return leg when the source data does not state one.
- Restore the legacy map summary as `地点 / 预计里程 / 移动时间`. Prefer reviewed `days[].distanceEstimate` and `days[].durationEstimate`; otherwise derive an explicitly approximate fallback from valid coordinates and transport modes. Do not show implementation counters such as “有效坐标 / 路线段” in the visual summary.
- Load a live basemap from verified destination coordinates even when the active day lacks point coordinates. When switching days, clear stale markers/routes before drawing the new day. Never invent a map center when neither destination nor place coordinates exist.
- On phones, support both date selection and horizontal swipe, keep the active date centered, and use a short slide-fade transition. On desktop, keep independent card expansion and category filters without hiding other days. Day selection in the map tab must not change the mobile itinerary's active day unless the user selects that day there.
- On desktop, 1-5 days must fit inside the itinerary board with no horizontal scrollbar; only plans longer than 5 days may enable horizontal scrolling inside that board. Keep transport and accommodation as two compact, collapsed detail rows below the board. Their open heights must remain independent; opening one must not stretch the closed sibling.
- Keep the legacy desktop place expansion: 11px serif note text, a compact next-leg strip, and one compact primary map action (高德 first, 百度 only as fallback). Do not add source cards or a second map button inside that desktop expansion, and do not inherit oversized card-title typography into the expanded body. Keep richer citations and warnings in the shared data/mobile presentation, plus daily and overall 避坑, transport, accommodation, notes, budget, and a real `保存页面` download action.
- Preserve reviewed rich logistics with structured `trip.transportPlans` and `trip.accommodationPlans`; do not collapse them into generic one-line estimates when those records are present.
- Keep uncertainty next to the affected place or warning. Do not add standalone source or data-boundary sections.
- Remain useful with missing coordinates, addresses, warnings, images, or source URLs; never invent missing coordinates.

## Quality Bar

Before final delivery:

- Confirm every non-obvious recommendation has a source ID or is labeled as an assumption/candidate.
- Confirm retained XHS sources include a platform-generated mobile share link when available; if not, clearly label the webpage link as a mobile-risk fallback.
- Confirm every displayed map button is generated from name plus city/area/address, or from coordinates when available.
- Prefer practical route order over "top ranked" order when the two conflict.
- Explain any unresolved uncertainty: unverified opening hours, possible seasonal closure, unclear branch, or missing exact address.
- Confirm `script#lujian-plan` parses as JSON and matches `schemaVersion: 1`.
- At `390px`, confirm only one mobile `.day-panel` is visible, date tabs/swipe both change the active day, and the legacy `行程 / 地图` switch works in both directions without resetting the selected itinerary day. Confirm its large headline and bold `笺` icon match the desktop.
- At `1440px`, run both a 5-day and a 6-day fixture: 5 days must show every `.desktop-day-col` without board overflow; 6 days must scroll only inside `.desktop-schedule`. Confirm filters, the legacy desktop card expansion, sticky budget, rich compact transport/accommodation accordions, and independent open/closed heights.
- With online access and verified destination coordinates, confirm MapLibre reaches `.map-live`, live markers contain `pin-dot + pin-line + pin-card`, every route row has its 22px `map-pin`, zoom buttons change the zoom, and drag is disabled until `DRAG` is toggled to `DRAG ON`. Then block external requests and confirm the static/文字 fallback remains usable with the same marker and route-pin structure. Switch from a coordinate-rich day to a coordinate-empty day and confirm stale markers are removed.
- Confirm every map day displays `地点 / 预计里程 / 移动时间`; explicit reviewed estimates must survive generation unchanged, while computed fallbacks must include `约` or `待补`. For every day with an explicit return-to-hotel `mapLeg`, confirm the final route-list row and live/static line both end at that hotel while the unique stop count remains unchanged.
- Confirm the only external stylesheet/script dependencies are the pinned MapLibre CSS and JS URLs; the file must remain readable and navigable when they fail to load.
- Run `python scripts/test_template_regressions.py` after any generator, specification, or validator change.
- Run `quick_validate.py` on the skill when editing this skill itself.
