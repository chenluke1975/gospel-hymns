#!/usr/bin/env python3
"""Turn the Monterey Park 大本詩歌 index into category metadata.

The page lists category names, subcategory names, and hymn numbers. This script
keeps that outline only. It does not download lyrics or audio.
"""

from __future__ import annotations

import json
import re
import sys
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

INDEX_URL = "https://churchinmontereypark.org/Docs/Hymn/firstBookHymnIndex.html"
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "catalog" / "categories.json"


def hymn_number(href: str | None, text: str) -> int | None:
    if href:
        match = re.search(r"/ch/(\d+)", href)
        if match:
            return int(match.group(1))
    stripped = text.strip()
    if stripped.isdigit():
        return int(stripped)
    return None


class IndexParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.categories: list[dict] = []
        self.current: dict | None = None
        self.sub_name = ""
        self.pending: list[str] = []
        self.href: str | None = None
        self.in_anchor = False
        self.anchor_text: list[str] = []
        self.expect_title = False
        self.appendix_number: int | None = None
        self.appendix_tag = ""

    def _start_sub(self, name: str) -> None:
        assert self.current is not None
        clean = re.sub(r"\s+", " ", name).strip()
        if not clean:
            clean = "一般"
        subs = self.current["subcategories"]
        if not subs or subs[-1]["name"] != clean:
            subs.append({"name": clean, "hymns": []})
        self.sub_name = clean

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        ad = {k: v or "" for k, v in attrs}
        if tag == "a" and ad.get("name", "").isdigit():
            self.appendix_number = None
            self.appendix_tag = ""
            self.pending = []
            self.current = {"id": ad["name"], "name": "", "subcategories": []}
            self.categories.append(self.current)
            self.expect_title = True
            self.sub_name = ""
            return
        if tag == "a" and ad.get("href"):
            self.href = ad["href"]
            self.in_anchor = True
            self.anchor_text = []
            return
        if tag == "br" and not self.in_anchor:
            self._break()

    def handle_endtag(self, tag: str) -> None:
        if tag != "a" or not self.in_anchor:
            return
        text = "".join(self.anchor_text).strip()
        href = self.href
        self.in_anchor = False
        self.href = None
        self.anchor_text = []
        if self.current is None:
            return
        number = hymn_number(href, text)
        if number is None:
            return
        if self.current["id"] == "30":
            self.appendix_number = number
            self.appendix_tag = text
            self.pending = []
            return
        label = re.sub(r"\s+", "", "".join(self.pending))
        self.pending = []
        if label:
            self._start_sub(label)
        elif not self.current["subcategories"]:
            self._start_sub("一般")
        self.current["subcategories"][-1]["hymns"].append(number)

    def handle_data(self, data: str) -> None:
        if self.in_anchor:
            self.anchor_text.append(data)
            return
        if self.current is None:
            return
        if self.expect_title and data.strip():
            self.current["name"] = re.sub(r"\s+", "", data.strip())
            self.expect_title = False
            return
        if data.strip():
            self.pending.append(data)

    def _break(self) -> None:
        if self.current is None:
            return
        if self.current["id"] == "30" and self.appendix_number is not None:
            label = re.sub(r"\s+", " ", "".join(self.pending)).strip()
            tag = self.appendix_tag or f"附{self.appendix_number}"
            shown = f"{tag} {label}".strip() if label else tag
            self._start_sub(shown)
            self.current["subcategories"][-1]["hymns"].append(self.appendix_number)
            self.appendix_number = None
            self.appendix_tag = ""
        self.pending = []


def parse_html(html: str) -> list[dict]:
    parser = IndexParser()
    parser.feed(html)
    return [c for c in parser.categories if c["name"]]


def validate(categories: list[dict]) -> None:
    seen: dict[int, str] = {}
    for cat in categories:
        for sub in cat["subcategories"]:
            for number in sub["hymns"]:
                if number in seen:
                    raise SystemExit(f"duplicate hymn {number}: {seen[number]} and {cat['name']}/{sub['name']}")
                seen[number] = f"{cat['name']}/{sub['name']}"
    missing = [n for n in range(1, 781) if n not in seen]
    if missing:
        raise SystemExit(f"missing hymns: {missing}")
    for extra in range(1001, 1007):
        if extra not in seen:
            raise SystemExit(f"missing appendix hymn {extra}")
    if len(categories) != 30:
        raise SystemExit(f"expected 30 categories, got {len(categories)}")
    expect = {
        1: ("頌讚三一神", "祂的計劃"),
        247: ("得救的證實與快樂", "藉恩得救"),
        780: ("終極的顯出", "永世的生活"),
        1001: ("附", "附1 讚美主－一般"),
        1006: ("附", "附6 經歷神－神完整的救恩"),
    }
    for number, (cat, sub) in expect.items():
        actual = seen[number]
        if actual != f"{cat}/{sub}":
            raise SystemExit(f"hymn {number} mapped to {actual}, expected {cat}/{sub}")


def main() -> None:
    if len(sys.argv) > 1:
        html = Path(sys.argv[1]).read_text(encoding="utf-8")
    else:
        req = urllib.request.Request(INDEX_URL, headers={"User-Agent": "GospelHymnJukebox/1.0"})
        with urllib.request.urlopen(req, timeout=40) as resp:
            html = resp.read().decode("utf-8")
    categories = parse_html(html)
    validate(categories)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"categories": categories}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    total = sum(len(h) for c in categories for h in [s["hymns"] for s in c["subcategories"]])
    print(f"wrote {OUT} categories={len(categories)} hymns={total}")


if __name__ == "__main__":
    main()
