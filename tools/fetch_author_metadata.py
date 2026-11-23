#!/usr/bin/env python3
"""Fetch bibliographic credits for Chinese hymns 1-780 and appendix 1001-1006.

Stores only author/composer names, life years, the linked English hymn number,
and whether the page exposes an mp3 or midi element. Lyrics, titles, and audio
URLs are discarded and never written.
"""
import json, re, sys, time, urllib.request, urllib.error
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "build" / "author-metadata.jsonl"
NUMS = list(range(1, 781)) + list(range(1001, 1007))
DELAY = 2.1
UA = "GospelHymnJukeboxMetadata/1.0 (personal catalog; credits only; crawl-delay 2)"

def done():
    got = set()
    if OUT.exists():
        for line in OUT.read_text().splitlines():
            if not line.strip():
                continue
            got.add(json.loads(line)["number"])
    return got

def strip_tags(s):
    s = re.sub(r"<[^>]+>", " ", s)
    s = s.replace("&nbsp;", " ").replace("&#8211;", "-").replace("&amp;", "&")
    return re.sub(r"\s+", " ", s).strip()

def people_from_dd(dd_html):
    people = []
    # each anchor is a credited name; years may follow the anchor
    parts = re.split(r"<a\b[^>]*>", dd_html, flags=re.I)
    # also capture anchor texts in order
    anchors = []
    for attrs, inner in re.findall(r'<a\b([^>]*)>(.*?)</a>', dd_html, flags=re.I|re.S):
        href = ""
        hm = re.search(r'href="([^"]*)"', attrs)
        if hm:
            href = hm.group(1)
        if href and ("/search/all/author/" not in href and "/search/all/composer/" not in href):
            continue
        anchors.append(inner)
    if not anchors:
        text = strip_tags(dd_html)
        text = re.sub(r"\([^)]*\)", "", text).strip(" ,;/")
        if text:
            years = re.search(r"\(([^)]+)\)", strip_tags(dd_html))
            people.append({"name": text, "years": years.group(1).strip() if years else None})
        return people
    for a in anchors:
        name = strip_tags(a)
        if not name:
            continue
        people.append({"name": name, "years": None})
    # years: look at full dd text for a single (YYYY-YYYY) if one person
    years = re.findall(r"\((\d{4}\s*[-–]\s*\d{4})\)", strip_tags(dd_html))
    if len(people) == 1 and years:
        people[0]["years"] = years[0].replace("–", "-").replace(" ", "")
    elif len(people) == len(years):
        for p, y in zip(people, years):
            p["years"] = y.replace("–", "-").replace(" ", "")
    return people

def extract(html):
    about = html
    i = html.find('id="about"')
    if i >= 0:
        about = html[i:i+12000]
    rows = dict(re.findall(r"<dt>\s*([^<]+?)\s*</dt>\s*<dd>(.*?)</dd>", about, flags=re.S|re.I))
    lyricists = people_from_dd(rows.get("Lyrics", ""))
    composers = people_from_dd(rows.get("Music", ""))
    # fallback JSON-LD names only, never lyrics/title
    if not lyricists or not composers:
        for m in re.finditer(r'<script type="application/ld\+json">(.*?)</script>', html, re.S):
            raw = m.group(1)
            if '"MusicComposition"' not in raw:
                continue
            try:
                data = json.loads(raw)
            except Exception:
                continue
            if not lyricists:
                for person in data.get("lyricist") or []:
                    name = (person.get("name") or "").strip()
                    if name:
                        lyricists.append({"name": name, "years": None})
            if not composers:
                for person in data.get("composer") or []:
                    name = (person.get("name") or "").strip()
                    if name:
                        composers.append({"name": name, "years": None})
            break
    en = re.search(r'/en/hymn/h/(\d+)', html)
    other = None
    if not en:
        m = re.search(r'/en/hymn/([a-z]{1,3})/(\d+)', html)
        if m and m.group(1) != "ch":
            other = {"book": m.group(1), "number": int(m.group(2))}
    has_mp3 = 'type="audio/mpeg"' in html or "audio/mpeg" in html
    has_midi = "audio/midi" in html or bool(re.search(r'\.mid(?:i)?(?:["\'?])', html, re.I))
    return {
        "lyricists": lyricists,
        "composers": composers,
        "englishNumber": int(en.group(1)) if en else None,
        "englishOther": other,
        "hasMp3": has_mp3,
        "hasMidi": has_midi,
    }

def fetch(n):
    url = f"https://www.hymnal.net/en/hymn/ch/{n}"
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=45) as resp:
        html = resp.read().decode("utf-8", "replace")
    meta = extract(html)
    # guard: never persist long prose
    blob = json.dumps(meta, ensure_ascii=False)
    if len(blob) > 4000:
        raise RuntimeError(f"metadata unexpectedly large for {n}")
    meta["number"] = n
    return meta

def main():
    global OUT
    if len(sys.argv) > 1:
        OUT = Path(sys.argv[1])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    have = done()
    todo = [n for n in NUMS if n not in have]
    print(f"todo {len(todo)} already {len(have)}", flush=True)
    for i, n in enumerate(todo):
        err = None
        meta = None
        for attempt in range(4):
            try:
                meta = fetch(n)
                err = None
                break
            except Exception as e:
                err = str(e)
                time.sleep(5 * (attempt + 1))
        if meta is None:
            meta = {"number": n, "error": err, "lyricists": [], "composers": [], "englishNumber": None, "englishOther": None, "hasMp3": False, "hasMidi": False}
            print(f"FAIL {n} {err}", flush=True)
        else:
            lyr = ",".join(p["name"] for p in meta["lyricists"]) or "-"
            comp = ",".join(p["name"] for p in meta["composers"]) or "-"
            print(f"OK {n} L={lyr[:80]} M={comp[:60]} en={meta['englishNumber']} mp3={meta['hasMp3']}", flush=True)
        with OUT.open("a") as f:
            f.write(json.dumps(meta, ensure_ascii=False) + "\n")
        if i != len(todo) - 1:
            time.sleep(DELAY)

if __name__ == "__main__":
    main()
