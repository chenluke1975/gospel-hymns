#!/usr/bin/env python3
"""Fill credits the Chinese hymn page left blank by reading the linked English page.

Only names and years are stored. The script never writes lyrics, titles, or audio.
"""

from __future__ import annotations

import json
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fetch_author_metadata import UA, extract  # noqa: E402

UNKNOWN = {
    "anonymous", "anon", "anon.", "unknown", "佚名", "不詳", "無", "n/a", "na", "none", "—", "-", "–",
}
DELAY = 2.1


def known(people: list[dict]) -> list[dict]:
    out = []
    for person in people:
        name = (person.get("name") or "").strip()
        if name and name.lower() not in UNKNOWN:
            out.append({"name": name, "years": person.get("years")})
    return out


def fetch_english(number: int) -> dict:
    url = f"https://www.hymnal.net/en/hymn/h/{number}"
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=45) as resp:
        html = resp.read().decode("utf-8", "replace")
    meta = extract(html)
    blob = json.dumps({"lyricists": meta["lyricists"], "composers": meta["composers"]}, ensure_ascii=False)
    if len(blob) > 4000:
        raise RuntimeError(f"english metadata too large for h/{number}")
    return meta


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/hymn-meta/authors.jsonl")
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    targets = []
    for row in rows:
        if row.get("error"):
            continue
        if not known(row.get("lyricists") or []) or not known(row.get("composers") or []):
            if row.get("englishNumber"):
                targets.append(row)
    print(f"english lookups {len(targets)}", flush=True)
    for index, row in enumerate(targets):
        en = row["englishNumber"]
        try:
            meta = fetch_english(en)
        except Exception as exc:
            print(f"FAIL h/{en} for ch/{row['number']} {exc}", flush=True)
            time.sleep(DELAY)
            continue
        fills = row.setdefault("fills", [])
        if not known(row.get("lyricists") or []) and known(meta["lyricists"]):
            row["lyricists"] = known(meta["lyricists"])
            fills.append({
                "role": "lyricist",
                "names": [p["name"] for p in row["lyricists"]],
                "source": "hymnal.net English hymn page",
                "note": f"https://www.hymnal.net/en/hymn/h/{en}",
            })
            print(f"FILL {row['number']} lyricist {fills[-1]['names']}", flush=True)
        if not known(row.get("composers") or []) and known(meta["composers"]):
            row["composers"] = known(meta["composers"])
            fills.append({
                "role": "composer",
                "names": [p["name"] for p in row["composers"]],
                "source": "hymnal.net English hymn page",
                "note": f"https://www.hymnal.net/en/hymn/h/{en}",
            })
            print(f"FILL {row['number']} composer {fills[-1]['names']}", flush=True)
        if index != len(targets) - 1:
            time.sleep(DELAY)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
    print("wrote", path, flush=True)


if __name__ == "__main__":
    main()
