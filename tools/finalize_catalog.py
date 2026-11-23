#!/usr/bin/env python3
"""Turn credit metadata into the app catalog, bios, and attribution reports.

Input records may contain only names, years, hymn numbers, and fill notes.
This script refuses to copy through lyrics or audio fields.
"""

from __future__ import annotations

import csv
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bios_zh import ALIASES, BIOS, bio_for  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
UNKNOWN = {
    "anonymous", "anon", "anon.", "unknown", "佚名", "不詳", "無", "n/a", "na", "none", "—", "-", "–",
}
PREFIX = re.compile(r"^(translated|adapted|arranged|alt\.|altered)\s+from\s+", re.I)


def clean_name(name: str) -> str:
    name = PREFIX.sub("", name.strip())
    name = re.sub(r"\s+", " ", name).strip(" ,;")
    return name


def from_hymnal(raw: list[dict] | None) -> list[dict]:
    out = []
    for person in raw or []:
        name = clean_name(person.get("name") or "")
        if not name or name.lower() in UNKNOWN:
            continue
        out.append({"name": name, "years": person.get("years") or None, "source": "hymnal.net"})
    return out


def from_override(raw: list[dict] | None, source: str) -> list[dict]:
    out = []
    for person in raw or []:
        name = clean_name(person.get("name") or "")
        if not name or name.lower() in UNKNOWN:
            continue
        out.append({"name": name, "years": person.get("years") or None, "source": source})
    return out


def main() -> None:
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/hymn-meta/authors.jsonl")
    overrides_path = ROOT / "tools" / "overrides.json"
    overrides = json.loads(overrides_path.read_text(encoding="utf-8")) if overrides_path.exists() else {}
    rows = [json.loads(line) for line in src.read_text(encoding="utf-8").splitlines() if line.strip()]
    hymns = []
    fill_rows = []
    unknown_rows = []
    names: dict[str, str | None] = {}
    for row in rows:
        if row.get("error"):
            continue
        number = int(row["number"])
        override = overrides.get(str(number), {})
        note = override.get("note", "")
        source = override.get("source", "")
        lyricists = from_hymnal(row.get("lyricists"))
        composers = from_hymnal(row.get("composers"))
        if not lyricists and override.get("lyricists"):
            lyricists = from_override(override.get("lyricists"), source)
            for person in lyricists:
                fill_rows.append([number, "作詞", person["name"], person.get("years") or "", person["source"], note])
        if not composers and override.get("composers"):
            composers = from_override(override.get("composers"), source)
            for person in composers:
                fill_rows.append([number, "作曲", person["name"], person.get("years") or "", person["source"], note])
        for person in lyricists + composers:
            names.setdefault(person["name"], person.get("years"))
        for item in row.get("fills") or []:
            for name in item.get("names") or []:
                fill_rows.append([number, "作詞" if item["role"] == "lyricist" else "作曲", name, "", item["source"], item.get("note", "")])
        if not lyricists:
            unknown_rows.append([number, "作詞"])
        if not composers:
            unknown_rows.append([number, "作曲"])
        hymns.append({
            "number": number,
            "englishNumber": row.get("englishNumber"),
            "hasMp3": bool(row.get("hasMp3")),
            "hasMidi": bool(row.get("hasMidi")),
            "lyricists": lyricists,
            "composers": composers,
        })
    hymns.sort(key=lambda item: item["number"])
    forbidden = json.dumps(hymns, ensure_ascii=False)
    for bad in ('"lyrics"', '"stanzas"', '"audioUrl"', ".mp3", "http"):
        if bad in forbidden:
            raise SystemExit(f"refusing to write catalog containing {bad}")
    catalog = ROOT / "app" / "src" / "main" / "assets" / "catalog" / "authors.json"
    catalog.write_text(json.dumps({"hymns": hymns}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    bios = {}
    for name, years in sorted(names.items()):
        canon = ALIASES.get(name, name)
        text = bio_for(canon, years) if canon in BIOS else bio_for(name, years)
        bios[canon] = text
        bios[name] = text
    bios["佚名"] = "作者不詳。中文頁、英文對應頁與核對過的公開目錄都沒有留下可確定的姓名。"
    bios_path = ROOT / "app" / "src" / "main" / "assets" / "catalog" / "bios.json"
    alias_out = {key: value for key, value in ALIASES.items() if key in names or value in names}
    bios_path.write_text(
        json.dumps({"bios": bios, "aliases": alias_out}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    fills_path = ROOT / "tools" / "author_fills.csv"
    unknown_path = ROOT / "tools" / "author_unknown.csv"
    with fills_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["number", "role", "name", "years", "source", "note"])
        writer.writerows(fill_rows)
    with unknown_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["number", "role"])
        writer.writerows(unknown_rows)
    print(f"hymns {len(hymns)} names {len(names)} fills {len(fill_rows)} unknown {len(unknown_rows)}")


if __name__ == "__main__":
    main()
