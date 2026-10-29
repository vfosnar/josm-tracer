#!/usr/bin/env bash
# Regenerates data/cz_cuzk_table_-y-x_3_v1710.txt.gz from the PROJ-data GeoTIFF of the ČÚZK
# S-JTSK -> S-JTSK/05 table, read by SJtsk.java. Needs curl, gdal_translate, awk, gzip.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

name=cz_cuzk_table_-y-x_3_v1710
curl -sSfL -o "$work/$name.tif" "https://cdn.proj.org/$name.tif"
echo "9f7b519e8928ec9663b916c07708d956821d09a0e25b61c742e31995d183148c  $work/$name.tif" | sha256sum -c --quiet

# XYZ lists node centres row by row from the north; values are whole millimetres
gdal_translate -q -of XYZ -b 1 "$work/$name.tif" "$work/e.xyz"
gdal_translate -q -of XYZ -b 2 "$work/$name.tif" "$work/n.xyz"
{
  echo "# ČÚZK table S-JTSK (EPSG:5514) -> S-JTSK/05 (EPSG:5516, minus 5000000 m), easting and northing offsets in metres"
  echo "# Source: https://cdn.proj.org/$name.tif (PROJ-data), (c) ČÚZK, licence CC BY 4.0 https://creativecommons.org/licenses/by/4.0/"
  echo "# columns rows west_node_easting north_node_northing step, then one 'dE dN' line per node, rows from the north, '-' = no data"
  paste -d' ' "$work/e.xyz" "$work/n.xyz" | awk '
    NR == 1 { x0 = $1; y0 = $2 }
    NR == 2 { step = $1 - x0 }
    $2 == y0 { cols++ }
    { rows = ($2 == last) ? rows : rows + 1; last = $2
      v[NR] = ($3 == -9999 || $6 == -9999) ? "-" : sprintf("%.3f %.3f", $3, $6) }
    END { print cols, rows, x0, y0, step; for (i = 1; i <= NR; i++) print v[i] }'
} | gzip -n -9 > "$repo/data/$name.txt.gz"
echo "wrote data/$name.txt.gz"
