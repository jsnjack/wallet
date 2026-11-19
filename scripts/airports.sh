#!/usr/bin/env bash
# Builds the bundled airport table (IATA code → city) from OurAirports,
# whose data is public domain: https://ourairports.com/data/
# Usage: scripts/airports.sh app/src/main/assets/airports.tsv
set -euo pipefail

out=${1:?usage: $0 OUTPUT.tsv}
url=https://davidmegginson.github.io/ourairports-data/airports.csv
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

curl -fsSL --max-time 120 -o "$work/airports.csv" "$url"

python3 -I - "$work/airports.csv" "$out" <<'PY'
import csv, re, sys

source, target = sys.argv[1], sys.argv[2]
rank = {"large_airport": 0, "medium_airport": 1, "small_airport": 2}
best = {}
with open(source, newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        code = row["iata_code"].strip()
        if not re.fullmatch(r"[A-Z]{3}", code) or row["type"] not in rank:
            continue
        if row["scheduled_service"] != "yes":
            continue
        city = (row["municipality"] or row["name"]).split(" (")[0].strip().replace("\t", " ")
        key = (rank[row["type"]], city)
        if code not in best or key < best[code]:
            best[code] = key

with open(target, "w", encoding="utf-8") as f:
    for code in sorted(best):
        f.write(f"{code}\t{best[code][1]}\n")
print(f"{len(best)} airports written to {target}")
PY
