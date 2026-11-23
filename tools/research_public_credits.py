#!/usr/bin/env python3
"""Look up still-blank credits on hymnary.org.

Reads the metadata jsonl, asks hymnal.net only for the short title of hymns that
are still missing an author or composer, then checks hymnary text results.
Writes tools/overrides.json with names and source URLs. Does not write lyrics.
"""

from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
UNKNOWN = {"anonymous", "anon", "anon.", "unknown", "traditional", "佚名", "不詳", "無", "n/a", "na", "none", "—", "-", "–"}
STOP = {"a", "an", "the", "of", "and", "to", "in", "my", "o", "oh", "for", "on", "with"}


def get(url: str) -> str:
    last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html", "Accept-Language": "en"})
            with urllib.request.urlopen(req, timeout=40) as resp:
                return resp.read().decode("utf-8", "replace")
        except Exception as exc:
            last = exc
            time.sleep(3 * (attempt + 1))
    raise last


def known(people: list[dict] | None) -> bool:
    for person in people or []:
        name = (person.get("name") or "").strip().lower()
        if name and name not in UNKNOWN:
            return True
    return False


def title_from(html: str) -> str:
    match = re.search(r'id="song-title-text">(.*?)</span>', html, re.S)
    if not match:
        match = re.search(r"<h1[^>]*>(.*?)</h1>", html, re.S)
    if not match:
        return ""
    text = re.sub(r"<[^>]+>", " ", match.group(1))
    return re.sub(r"\s+", " ", text).strip()


def norm(text: str) -> set[str]:
    words = re.sub(r"[^a-z0-9 ]", " ", text.lower())
    return {word for word in words.split() if word not in STOP and len(word) > 2}


def similar(left: str, right: str) -> bool:
    a, b = norm(left), norm(right)
    if not a or not b:
        return False
    overlap = a & b
    phrase = re.sub(r"[^a-z0-9 ]", " ", left.lower())
    other = re.sub(r"[^a-z0-9 ]", " ", right.lower())
    phrase = re.sub(r"\s+", " ", phrase).strip()
    other = re.sub(r"\s+", " ", other).strip()
    if len(overlap) < 2 and phrase != other:
        return False
    return len(overlap) / min(len(a), len(b)) >= 0.67


def hymnary_results(query: str) -> list[dict]:
    url = "https://hymnary.org/search?qu=" + urllib.parse.quote(query + " in:texts")
    html = get(url)
    found = []
    pattern = re.compile(
        r'<h2>\s*<a href="(/result/[^"]+)">(.*?)</a>\s*</h2>(.*?)(?=<h2>\s*<a href="/result/|$)',
        re.S,
    )
    for href, title_html, rest in pattern.findall(html)[:5]:
        title = re.sub(r"<[^>]+>", " ", title_html)
        title = re.sub(r"\s+", " ", title).strip()
        labeled = re.findall(
            r"data-fieldName='([^']+)'.*?<b[^>]*>[^<]*</b>:\s*([^<]+)",
            rest[:3000],
            re.S,
        )
        fields = {key: re.sub(r"\s+", " ", value).strip() for key, value in labeled}
        appeared = re.search(r"Appears in ([\d,]+) hymnals", rest)
        digits = appeared.group(1).replace(",", "") if appeared else ""
        found.append({
            "title": title,
            "href": href,
            "author": fields.get("author", ""),
            "composer": fields.get("composer", "") or fields.get("composerName", ""),
            "count": int(digits) if digits else 0,
        })
    return found


def acceptable(name: str) -> bool:
    cleaned = name.strip()
    return bool(cleaned) and cleaned.lower() not in UNKNOWN and len(cleaned) < 80


def main() -> None:
    src = Path("/tmp/hymn-meta/authors.jsonl")
    rows = [json.loads(line) for line in src.read_text(encoding="utf-8").splitlines() if line.strip()]
    overrides: dict[str, dict] = {}
    looked = 0
    for row in rows:
        need_l = not known(row.get("lyricists"))
        need_m = not known(row.get("composers"))
        if not need_l and not need_m:
            continue
        number = row["number"]
        title = ""
        if row.get("englishNumber"):
            try:
                title = title_from(get(f"https://www.hymnal.net/en/hymn/h/{row['englishNumber']}"))
            except Exception as exc:
                print(f"title fail {number} {exc}", flush=True)
            time.sleep(2.1)
        if not title or not norm(title):
            print(f"skip {number} no Latin title", flush=True)
            continue
        try:
            results = hymnary_results(title)
        except Exception as exc:
            print(f"hymnary fail {number} {exc}", flush=True)
            time.sleep(2.0)
            continue
        time.sleep(2.0)
        looked += 1
        candidates = [item for item in results if similar(title, item["title"]) and acceptable(item["author"])]
        match = max(candidates, key=lambda item: item["count"]) if candidates else None
        if not match:
            print(f"no match {number} title={title!r} candidates={[r['title'] for r in results[:3]]}", flush=True)
            continue
        entry: dict = {"source": "hymnary.org", "note": "https://hymnary.org" + match["href"]}
        if need_l:
            entry["lyricists"] = [
                {"name": part.strip(), "years": None}
                for part in match["author"].split(";")
                if acceptable(part.strip())
            ]
        if need_m and acceptable(match["composer"]):
            entry["composers"] = [{"name": match["composer"], "years": None}]
        if "lyricists" in entry or "composers" in entry:
            overrides[str(number)] = entry
            out = ROOT / "tools" / "overrides.json"
            out.write_text(json.dumps(overrides, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"ok {number} L={entry.get('lyricists')} M={entry.get('composers')} <= {match['title']}", flush=True)
        else:
            print(f"matched without usable credit {number} {match['title']}", flush=True)
    out = ROOT / "tools" / "overrides.json"
    out.write_text(json.dumps(overrides, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"looked {looked} overrides {len(overrides)} -> {out}", flush=True)


if __name__ == "__main__":
    main()
