# Itinerary JSON Contract

Use this as the canonical data shape for the generated travel planner. The scripts validate the required parts of this contract without requiring a full JSON Schema dependency.

## Top-Level Shape

```json
{
  "trip": {},
  "sources": [],
  "places": [],
  "warnings": [],
  "days": []
}
```

## `trip`

Required:

- `destination`: city, region, or country.
- `dayCount`: integer greater than zero.

Recommended:

- `dateRange`: display string or `{ "start": "YYYY-MM-DD", "end": "YYYY-MM-DD" }`.
- `travelers`: traveler count and profile.
- `style`: food, family, citywalk, photography, museum, relaxed, budget, luxury, etc.
- `baseArea`: hotel or preferred area.
- `destinationEn` or `englishName`: short English/script subtitle for the mobile hero.
- `headline` or `displayHeadline`: optional shared large headline for phone and desktop; when omitted, the 旅笺 generator derives it from day count, destination, and style.
- `destinations[]`: destination records with `name` and, only when verified, `latitude`/`longitude`. These coordinates let the live map load a destination overview when daily places lack coordinates.
- `heroImage` or `coverImage`: remote image URL for the top hero.
- `weather` or `weatherText`: short display text, such as `26°C` or `多云 26°C`.
- `budgetEstimate` or `budget`: trip-level budget display string.
- `assumptions[]`: assumptions made because the user or sources did not specify details.
- `transportPlans[]`: reviewed rich round-trip options. Each record may contain `title`, `summary`, `price`, `warning`, `url`, and `linkLabel`.
- `transportNote`: short note displayed below the transport options.
- `accommodationLabel`: optional accordion title, such as `住宿方案（5晚固定）`.
- `accommodationPlans[]`: reviewed hotel options. Each record may contain `rank`, `name`, `price`, `note`, `risk`, `tone`, and `mapLinks`.
- `accommodationNote`: short note displayed below the hotel options.

## `sources[]`

Required:

- `id`: stable ID used by `sourceIds`.
- `platform`: `xhs`, `user`, `web`, or another explicit source type.
- `title`: string or `null`.
- `url`: string or `null`.

Recommended:

- `author`
- `publishedDate`
- `capturedAt`
- `excerpt`
- `mobileShareUrl`: platform-generated share/copy-link URL for the mobile button, typically `https://xhslink.com/...`.
- `desktopUrl`: optional PC browser note URL when distinct from `url`.
- `query`: query phrase used to discover the note.
- `likes`: visible like count when shown.
- `collects`: visible save/favorite count when shown.
- `comments`: visible comment count when shown.

Every source should have either `url` or `title`; otherwise it is too hard to audit. For XHS mobile output, capture `mobileShareUrl` using the note's own share/copy-link action rather than constructing it. Retain at least 15 opened sources for 2-4 days and 20 for 5+ days, unless access limitations are recorded. Engagement fields are observation-only: do not infer or fabricate unavailable counts.

## `places[]`

Required:

- `id`: stable ID.
- `name`: display name.
- `city`: city or destination.
- `category`: `attraction`, `restaurant`, `cafe`, `shop`, `hotel`, `neighborhood`, `transport`, or `other`.
- `confidence`: `confirmed`, `candidate`, `avoid`, or `assumption`.
- `sourceIds[]`: IDs from `sources[]`; empty only for explicit assumptions.

Recommended:

- `area`
- `address`
- `coordinates`: `{ "lat": number, "lng": number }`
- `tags[]`
- `reason`
- `tips[]`
- `imageUrl` or `image`: remote image URL for route cards.
- `cost`: short display string such as `人均 ¥90`.
- `mapLinks`: `{ "amap": string, "baidu": string }`

Deduplicate by normalized `name + city + area`. Preserve branch names when they change the visitor experience.

## `warnings[]`

Required:

- `id`
- `title`
- `severity`: `low`, `medium`, or `high`.
- `sourceIds[]`

Recommended:

- `placeId`
- `dayId`
- `detail`
- `mitigation`
- `category`: `queue`, `reservation`, `price`, `transport`, `closure`, `weather`, `tourist-trap`, or `other`.

## `days[]`

Required:

- `id`: stable ID, such as `day-1`.
- `dayNumber`: integer.
- `distanceEstimate`: optional reviewed route-length estimate shown in the legacy map summary, for example `18.6 km`. When omitted, the HTML renderer derives an approximate value from valid place coordinates and labels it with `约`.
- `durationEstimate`: optional reviewed moving-time estimate shown in the legacy map summary, for example `1 小时 32 分钟`. When omitted, the HTML renderer derives an approximate value from coordinates and transport modes.
- `title`
- `items[]`

Each `items[]` entry requires:

- `id`
- `timeBlock`: morning, lunch, afternoon, dinner, evening, flexible, or a display string.
- `title`
- `placeId` when tied to a known place.
- `sourceIds[]`

Recommended per item:

- `duration`
- `transportToNext`
- `mapLinks`
- `imageUrl` or `image`
- `cost`
- `notes[]`
- `warningIds[]`
- `alternatives[]`

Recommended per day:

- `summary`
- `budgetEstimate` or `budget`
- `backup`: rainy-day or queue-heavy alternative plan.
- `mapStops[]`: optional reviewed map-only stop list when the route cannot be represented by `items[]` alone. Each stop uses a stable `id`, `title`/`name`, optional `time`/`meta`, `category`, verified `coordinates` or `latitude`/`longitude`, and optional legacy label hints `side`/`labelY`.
- `mapLegs[]`: optional ordered route segments between `mapStops`. Each leg uses `from` and `to` stop IDs plus `summary`/`transport` and optional `mode` (`walk`, `ride`, or `drive`). Use this for loops such as the final return to the fixed hotel without duplicating the hotel marker. If omitted, the renderer connects stops sequentially; never add an unstated closing leg.

Example of an explicit return to the fixed hotel:

```json
{
  "mapStops": [
    { "id": "hotel", "title": "固定酒店", "meta": "START", "category": "hotel", "latitude": 38.915, "longitude": 121.5875 },
    { "id": "dinner", "title": "晚餐", "meta": "17:30", "category": "restaurant", "latitude": 38.88, "longitude": 121.66 }
  ],
  "mapLegs": [
    { "from": "hotel", "to": "dinner", "mode": "drive", "summary": "打车 · 约 20 分钟" },
    { "from": "dinner", "to": "hotel", "mode": "drive", "summary": "打车 · 返回固定酒店 · 约 22 分钟" }
  ]
}
```

## Map Links

Use 高德 as primary and 百度 as fallback:

```json
{
  "mapLinks": {
    "amap": "https://uri.amap.com/search?keyword=...",
    "baidu": "https://map.baidu.com/search/..."
  }
}
```

When exact coordinates are unknown, generate search links from `name + city + area/address`. Do not invent coordinates.
