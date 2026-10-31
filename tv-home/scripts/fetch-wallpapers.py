#!/usr/bin/env python3
"""Download freely licensed nature photos from Wikimedia Commons "Featured pictures".

Featured pictures are community-reviewed and all carry a free licence (public domain or
Creative Commons). Most CC licences require attribution, so the author and licence of every
photo go into credits.tsv, which TV Home shows as a small caption.

Standard library only. Usage:
    python3 fetch-wallpapers.py [--count 24] [--out ../state/wallpapers]
"""
import argparse
import html
import json
import os
import random
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
# Wikimedia asks for a descriptive User-Agent: https://meta.wikimedia.org/wiki/User-Agent_policy
UA = "TVHome-wallpaper-fetch/1.0 (personal, non-commercial home screen)"
CATEGORIES = [
    "Featured pictures of landscapes",
    "Featured pictures of mountains",
    "Featured pictures of forests",
    "Featured pictures of lakes",
    "Featured pictures of coasts",
    "Featured pictures of waterfalls",
    "Featured pictures of deserts",
]
FREE = re.compile(r"^(CC0|CC BY(-SA)? [0-9.]+|Public domain|PD.*)", re.I)


def get(params):
    params = dict(params, format="json", formatversion="2")
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def strip(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()


def candidates(category, limit=200):
    out, cont = [], {}
    while len(out) < limit:
        data = get(dict(action="query", generator="categorymembers", gcmtitle="Category:" + category,
                        gcmtype="file", gcmlimit="50", prop="imageinfo",
                        iiprop="url|size|mime|extmetadata", iiurlwidth="1920", **cont))
        for p in data.get("query", {}).get("pages", []):
            ii = (p.get("imageinfo") or [{}])[0]
            meta = ii.get("extmetadata", {})
            lic = strip(meta.get("LicenseShortName", {}).get("value"))
            w, h = ii.get("width", 0), ii.get("height", 0)
            if ii.get("mime") != "image/jpeg" or w < 1920 or h == 0 or not (1.3 <= w / h <= 2.4):
                continue  # need a landscape-shaped JPEG at least Full-HD wide
            if not FREE.match(lic):
                continue
            title = strip(meta.get("ObjectName", {}).get("value")) or p["title"][5:].rsplit(".", 1)[0]
            artist = strip(meta.get("Artist", {}).get("value")) or "unknown"
            out.append(dict(page=p["title"], url=ii.get("thumburl") or ii["url"], title=title,
                            artist=artist, license=lic))
        if "continue" not in data:
            break
        cont = data["continue"]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=24)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "state", "wallpapers"))
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    pool = []
    for c in CATEGORIES:
        try:
            found = candidates(c)
        except Exception as e:  # a missing category should not stop the others
            print(f"  {c}: skipped ({e})", file=sys.stderr)
            continue
        print(f"  {c}: {len(found)} usable photos")
        pool += found
    if not pool:
        sys.exit("No photos found - is commons.wikimedia.org reachable?")

    random.shuffle(pool)
    picked, seen = [], set()
    for p in pool:
        if p["page"] not in seen:
            seen.add(p["page"])
            picked.append(p)
        if len(picked) == args.count:
            break

    lines = []
    for i, p in enumerate(picked, 1):
        name = f"nature-{i:02d}.jpg"
        req = urllib.request.Request(p["url"], headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as r, open(os.path.join(args.out, name), "wb") as f:
            f.write(r.read())
        caption = f'{p["title"][:80]} · Foto: {p["artist"][:60]} ({p["license"]}, Wikimedia Commons)'
        lines.append(f"{name}\t{caption}")
        print(f"  {name}  {caption}")
        time.sleep(0.5)  # be polite to Wikimedia
    with open(os.path.join(args.out, "credits.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(args.out, "SOURCES.md"), "w", encoding="utf-8") as f:
        f.write("# Wallpaper sources\n\n")
        for (line, p) in zip(lines, picked):
            f.write(f"- {line.split(chr(9))[0]}: https://commons.wikimedia.org/wiki/"
                    f"{urllib.parse.quote(p['page'].replace(' ', '_'))} - {p['artist']} - {p['license']}\n")
    print(f"Saved {len(picked)} photos + credits to {os.path.abspath(args.out)}")


if __name__ == "__main__":
    main()
