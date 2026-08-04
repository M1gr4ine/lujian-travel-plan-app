#!/usr/bin/env python3
"""旅笺模板的确定性回归门禁。"""

from __future__ import annotations

from html.parser import HTMLParser

from create_static_html import build_html, build_lujian_payload


class StaticCoverParser(HTMLParser):
    """提取安卓无脚本缩略图使用的完整静态封面。"""

    _VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
    _FIELD_CLASSES = {"brand-title": "brand_title", "brand-sub": "brand_sub"}

    def __init__(self) -> None:
        super().__init__()
        self.cover_count = 0
        self._depth = 0
        self._parts: list[str] = []
        self._field_depths: dict[str, int] = {}
        self._field_parts: dict[str, list[str]] = {
            "brand_title": [],
            "brand_sub": [],
            "headline": [],
        }

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if self._depth:
            if tag not in self._VOID_TAGS:
                self._depth += 1
        elif "data-lujian-cover" in attributes:
            self.cover_count += 1
            self._depth = 1
        if not self._depth:
            return
        classes = set((attributes.get("class") or "").split())
        for class_name, field in self._FIELD_CLASSES.items():
            if class_name in classes:
                self._field_depths[field] = self._depth
        if tag == "h1":
            self._field_depths["headline"] = self._depth

    def handle_endtag(self, tag: str) -> None:
        if not self._depth:
            return
        ended_fields = [field for field, depth in self._field_depths.items() if depth == self._depth]
        for field in ended_fields:
            del self._field_depths[field]
        self._depth -= 1

    def handle_data(self, data: str) -> None:
        if self._depth:
            self._parts.append(data)
            for field in self._field_depths:
                self._field_parts[field].append(data)

    @property
    def text(self) -> str:
        return "".join(self._parts).strip()

    def field_text(self, field: str) -> str:
        return "".join(self._field_parts[field]).strip()

def fixture(day_count: int) -> dict:
    places = []
    days = []
    for index in range(day_count):
        place_id = f"place-{index + 1}"
        places.append(
            {
                "id": place_id,
                "name": f"测试地点 {index + 1}",
                "city": "大连",
                "category": "attraction",
                "confidence": "assumption",
                "sourceIds": [],
                "coordinates": {"lat": 38.91 + index * 0.01, "lng": 121.61 + index * 0.01},
            }
        )
        day = {
                "id": f"day-{index + 1}",
                "dayNumber": index + 1,
                "title": f"第 {index + 1} 天",
                "items": [
                    {
                        "id": f"item-{index + 1}",
                        "timeBlock": "09:00",
                        "title": f"测试地点 {index + 1}",
                        "placeId": place_id,
                        "sourceIds": [],
                    }
                ],
            }
        if index == 0:
            day["mapStops"] = [
                {"id": "hotel", "title": "固定酒店", "meta": "START", "category": "hotel", "latitude": 38.915, "longitude": 121.5875},
                {"id": "place", "title": "测试地点", "meta": "09:00", "category": "attraction", "latitude": 38.929, "longitude": 121.6551},
            ]
            day["mapLegs"] = [
                {"from": "hotel", "to": "place", "mode": "ride", "summary": "骑行前往"},
                {"from": "place", "to": "hotel", "mode": "drive", "summary": "打车 · 返回固定酒店"},
            ]
        days.append(day)
    return {
        "trip": {
            "destination": "大连",
            "dayCount": day_count,
            "coverSubtitle": "9月24日晚出发 · 5天4晚 · SOLO TRIP",
            "style": "慢游 · 美食",
            "transportPlans": [
                {
                    "title": "去程 · 测试高铁",
                    "summary": "08:00 测试站 → 12:00 大连北",
                    "price": "二等座参考¥438",
                    "warning": "以实际开售页面为准。",
                    "url": "https://www.12306.cn/index/",
                }
            ],
            "transportNote": "往返交通补充说明。",
            "accommodationLabel": "住宿方案（固定）",
            "accommodationPlans": [
                {
                    "rank": "固定住宿",
                    "name": "测试酒店",
                    "price": "约¥400/晚",
                    "note": "只订免费取消。",
                    "risk": "注意临街噪音。",
                    "mapLinks": {"amap": "https://uri.amap.com/search?keyword=测试酒店"},
                }
            ],
            "accommodationNote": "住宿价格以实时页面为准。",
            "destinations": [
                {
                    "name": "大连",
                    "latitude": 38.913962,
                    "longitude": 121.614786,
                }
            ],
        },
        "sources": [],
        "places": places,
        "warnings": [],
        "days": days,
    }


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    five = build_lujian_payload(fixture(5))
    six = build_lujian_payload(fixture(6))
    html = build_html(fixture(5))
    cover = StaticCoverParser()
    cover.feed(html)

    require(five["title"] == "大连旅行计划", "计划库标题不应混入天/晚数")
    require(five["headline"] == "五天说走就走，把大连吃个痛快。", "五天美食主标题回归")
    require(six["headline"] == "六天说走就走，把大连吃个痛快。", "六天美食主标题回归")
    require("coverTitle" not in five, "封面计划名不得与计划 title 分成两个字段")
    require(five.get("coverSubtitle") == "9月24日晚出发 · 5天4晚 · SOLO TRIP", "安卓封面副标题未进入旅笺载荷")
    require(cover.cover_count == 1, "安卓无脚本预览必须有且仅有一个静态封面节点")
    require(cover.field_text("brand_title") == five["title"], "安卓静态封面未输出计划名")
    require(cover.field_text("brand_sub") == five.get("coverSubtitle"), "安卓静态封面未输出副标题")
    require(cover.field_text("headline") == five["headline"], "安卓静态封面未输出完整主标题")
    require("renderHeadline($('plan-title'))" in html, "手机端未使用共享主标题")
    require("renderHeadline($('desktop-hero-title'))" in html, "桌面端未使用共享主标题")
    require("font:900 20px/1" in html, "笺字图标未固定加粗")
    require("const scrollable = days.length > 5;" in html, "桌面滚动阈值不是大于五天")
    require("class=\"desktop-schedule\"" in html, "桌面行程板缺失")
    require("<details class=\"desktop-info-card\">" in html, "住宿交通未使用紧凑折叠栏")
    require("function destinationCenter()" in html, "地图缺少目的地中心兜底")
    require("function clearMapRoute()" in html, "地图切日未清理旧路线")
    require("new maplibregl.Map(" in html, "MapLibre 初始化缺失")
    require('data-view="itinerary"' in html and 'data-view="map"' in html, "行程/地图视图切换缺失")
    require("function setPageView(view)" in html, "旧版地图视图切换逻辑缺失")
    require('class="pin-dot"' in html, "旧版地图插针圆点缺失")
    require('class="pin-line"' in html, "旧版地图插针引线缺失")
    require('class="pin-card"' in html, "旧版地图地点卡片缺失")
    require("static-marker-dot" not in html, "地图仍在使用新版简化水滴插针")
    require('<small>预计里程</small>' in html, "旧版预计里程统计缺失")
    require('<small>移动时间</small>' in html, "旧版移动时间统计缺失")
    require("function estimateRoute(points,legs)" in html, "无显式统计时缺少路线估算兜底")
    require("gap:18px;align-items:start" in html, "底部折叠栏仍会被 CSS Grid 强制等高")
    require("data:image/svg+xml" in html and "stroke='%23F2B43A'" in html, "标题下方未使用连续 SVG 波浪线")
    require("repeating-radial-gradient(ellipse at 12px 2px" not in html, "标题下方仍使用字符状渐变纹理")
    require("function transportPlanHtml(" in html, "旧版去返程详情渲染器缺失")
    require("function accommodationPlanHtml(" in html, "旧版住宿方案渲染器缺失")
    require('class="transport-leg"' in html, "旧版交通详情卡缺失")
    require('class="hotel-item"' in html, "旧版住宿详情卡缺失")
    desktop_detail = html.split("function desktopItemHtml", 1)[1].split("function desktopDayHtml", 1)[0]
    require('class="desktop-detail-note"' in desktop_detail, "桌面地点展开框未使用旧版说明结构")
    require('class="desktop-detail-transport"' in desktop_detail, "桌面地点展开框未使用旧版下一程结构")
    require("sourceHtml(sourceIds)" not in desktop_detail, "桌面地点展开框仍混入来源卡")
    require("百度备用" not in desktop_detail, "桌面地点展开框仍混入新版百度按钮")
    require('.desktop-card-detail{border-top:1px solid rgba(42,37,32,.1);background:rgba(255,255,255,.22);padding:10px 14px 12px' in html, "桌面小卡展开框未恢复旧版紧凑尺寸")
    require('.desktop-detail-note{margin:0 0 4px;color:var(--muted);font-size:11px;line-height:1.5}' in html, "桌面小卡说明字号未锁定为旧版 11px")
    require('font-size:11px;font-weight:500;line-height:1.35' in html, "桌面小卡地图按钮字号/字重未恢复旧版")
    require(five["days"][0]["mapLegs"][-1]["to"] == "hotel", "闭环返酒店路线未进入旅笺载荷")
    require("function mapLegData(" in html, "地图缺少独立路线段建模")
    require("renderMapRoutes(stops,legs)" in html, "地图路线列表仍只能按行程卡顺序生成")
    require('class="map-controls"' in html and 'data-map-ctrl="zoom-in"' in html and 'data-map-ctrl="zoom-out"' in html and 'data-map-ctrl="drag"' in html, "旧版地图放大/缩小/拖拽锁定控件缺失")
    require("map.dragPan.disable()" in html and "mapState.drag" in html, "地图未默认锁定拖拽")
    require('class="map-pin' in html, "路线地点旁的旧版大头针缺失")

    print("OK: 旅笺模板回归门禁通过（旧版展开详情/底部方案、旧版插针/视图切换、5天铺满、6天滚动）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
