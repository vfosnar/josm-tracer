# S-JTSK to ETRS89 in the RUIAN-ČÚZK modules

The RUIAN-ČÚZK and RUIAN-Lands-ČÚZK modules ask the ČÚZK RÚIAN service for geometry in S-JTSK
(`inSR=outSR=5514`) and convert it in the plugin with `SJtsk.java`, the transformation ČÚZK
publishes: the S-JTSK to S-JTSK/05 table (`cz_cuzk_table_-y-x_3_v1710`), Modified Krovak and the
S-JTSK/05 to ETRS89 Helmert (EPSG:5226). It is the operation PROJ reports as
`ETRS89_TO_S_JTSK_E_N`, accuracy 0.035 m. Coordinates are written to OSM as ETRS89.

## Why not `outSR=4326`

Asked for WGS84, the service converts with EPSG:1623 "S-JTSK to WGS 84 (1)", a 7-parameter
Helmert with 1 m accuracy. Measured 2026-10-29 on 2857 vertices of 370 buildings in 15 regions
(`tools/sjtsk-check/shift.py`, sample `tools/sjtsk-check/sample.sql`), each PROJ operation applied to
the service's S-JTSK vertices minus the service's own `outSR=4326` vertex:

| Operation | Median | p95 |
| --- | --- | --- |
| EPSG:1623 S-JTSK to WGS 84 (1) | 0.002 m | 0.002 m |
| EPSG:5239 S-JTSK to WGS 84 (5) | 0.059 m | 0.075 m |
| ČÚZK table (this plugin) | 0.163 m | 0.436 m |
| S-JTSK to WGS 84 (3), 3 parameters, PROJ's first choice | 8.694 m | 14.767 m |

RUIAN buildings imported into OSM earlier through josm.poloha.net sit on the ČÚZK table: median
centroid offset OSM minus table within 0.03 m in 14 of 15 regions, against up to 0.42 m for OSM
minus service (Cheb +0.37 N +0.21 E, Ostrava +0.40 N, Zlín +0.12 N +0.22 E, Jeseník -0.15 N
-0.21 E). The 15th, Praha, holds buildings traced with these modules before this fix.

The service's `datumTransformation` parameter does not help: with the table-based pair 5241+5227
(listed by its GeometryServer `findTransformations`) the output stayed 0.43 m (median) from the
table on 543 vertices around Cheb, Ostrava and Jeseník.

## Implementation and checks

- `SJtsk` ports PROJ 9.7 `krovak.cpp` (Modified Krovak) and `gridshift.cpp` (biquadratic
  interpolation, edge rules, non-iterative inverse). Against `cct` with the pipeline in
  `tools/sjtsk-check/etrs89-to-sjtsk.pipeline` (`tools/sjtsk-check/compare.py`, 53048 random,
  node, half-cell and edge points): S-JTSK to ETRS89 median and max 0.004 mm; ETRS89 to S-JTSK
  median 0.004 mm, max 2.3 mm at one point exactly on a half-cell, where biquadratic
  interpolation switches its 3x3 window. Both refuse exactly the same points.
- Outside the table (Czechia only) tracing fails with an error naming the S-JTSK position.
- The table lives in `data/cz_cuzk_table_-y-x_3_v1710.txt.gz`, written by `tools/cuzk-grid.sh` from
  https://cdn.proj.org/cz_cuzk_table_-y-x_3_v1710.tif (sha256 checked in the script). The values
  are whole millimetres, so the text copy is lossless. The JDK TIFF reader cannot read the
  original file (non-standard DateTime tag, floating point predictor), and GeoTools in the geotools
  plugin has neither Modified Krovak nor projected offset grids.
- Table licence: © ČÚZK, CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
