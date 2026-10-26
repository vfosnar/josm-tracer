# Tracer-cuzk: RUIAN straight from ČÚZK

This branch (`cuzk-direct`) of mkyral/josm-tracer reads RUIAN buildings and parcels from the
ČÚZK RÚIAN map service instead of josm.poloha.net. poloha.net runs the old PHP proxy kept in
`servers/`, and its PHP was switched off after a PHP vulnerability on 2026-10-21, back on
2026-10-22 and down again by 2026-10-26 (talk-cz thread "poloha.net dole - RUIAN nejede").

## Data sources

| Module | Service | Requests |
| --- | --- | --- |
| RUIAN | `https://ags.cuzk.gov.cz/arcgis/rest/services/RUIAN/Prohlizeci_sluzba_nad_daty_RUIAN/MapServer`, layer 3 `StavebniObjekt` | one point query per click |
| RUIAN Lands | same service, layer 5 `Parcela` | one point query per click |
| LPIS | `https://mze.gov.cz/public/app/wms/plpis_wfs.fcgi` (WFS, `LPIS_DPB_UCINNE`) | unchanged by this branch |
| Classic | local TracerServer | unchanged |

Queries are in WGS84 (`inSR=outSR=4326`). The plugin takes the polygon part that contains the
click, with its holes (Esri outer rings run clockwise). Code: `src/.../tracer/CuzkRuian.java`.
The custom RUIAN server URL setting is gone: it pointed the modules at a poloha.net-protocol
server, and nothing speaks that protocol for this code any more.

## Tags

Buildings get the same tags poloha.net produced: `building`, `ref:ruian:building`,
`building:ruian:type`, `building:levels`, `building:flats`, `start_date`, `source=cuzk:ruian`.
Addresses were fetched by the old code but never tagged, so they are not fetched.

- **`building=*` per usage code** (`zpusobvyuzitikod`, SC_ZP_VYUZITI_BUD): poloha.net's table
  (`osmtables.zpusob_vyuziti_objektu`) was never published. The table in `RuianRecord` is the
  column "Současný stav dle Petra" of https://wiki.openstreetmap.org/wiki/Cs:RÚIAN (section
  Stavební objekty, read 2026-10-26). Codes whose poloha.net key was not `building` (22-26 dams,
  weirs) and code 30 (absent from the wiki) give `building=yes`, as the old plugin did.
- **Cross-check against OSM**, Postpass 2026-10-26, ways and relations inside the Czech bbox with
  `source=cuzk:ruian`, pairs above 2000 objects:

  ```sql
  SELECT tags->>'building:ruian:type' t, tags->>'building' b, count(*) n FROM postpass_polygon
  WHERE tags ? 'building:ruian:type' AND tags->>'source'='cuzk:ruian'
    AND geom && ST_MakeEnvelope(12.09,48.55,18.86,51.06,4326)
  GROUP BY 1,2 HAVING count(*)>2000 ORDER BY 1,3 DESC;
  ```

  The top value per code matches the table: 1 industrial 25646, 2 farm_auxiliary 21842,
  3 residential 732485, 5 civic 56710, 6 residential 93127 (apartments 6633), 7 house 1142704,
  8 yes 218544 (cabin 36538), 10 commercial 5853, 11 hotel 6087, 12 warehouse 40414,
  13 barn 155589, 14 commercial 7746, 15 civic 52266, 16 industrial 50865,
  17 transportation 6699, 18 garage 506106, 19 yes 346711, 20 yes 6726.
- **Dates**: ArcGIS returns epoch ms at the day's UTC midnight. Building 21646295 reads
  -1578009600000, VDP shows "Datum dokončení: 31.12.1919", and the plugin writes `1919-12-31`.
- **RUIAN Lands keys** come from the mapping left commented out in `RuianLandsRecord`, keyed
  by ČÚZK codes (`druhpozemkukod`, `zpusobyvyuzitipozemku`); poloha.net's `ruian.landuse_view`
  was never published either. Changes: `orchard=orchard` → `landuse=orchard`, and gardens
  (zahrada, code 5) get no tagging, so tracing one reports an error: RUIAN gardens do not match
  gardens on the ground (@vfosnar 2026-10-26). The rest is as the author wrote it, including
  `landuse=river`, `landuse=highway` and `landuse=common` for greenhouses. A parcel type with no
  mapping is reported as an error naming the parcel and both codes.

## Build and publish

`tools/build-plugin-site.sh OUT BASE_URL` compiles against the installed JOSM (`--release 11`),
writes `Tracer-cuzk.jar`, the source archive (GPL) and a JOSM plugin list `plugins.txt`.
`Plugin-Version` is the commit time, so each new commit is an update in JOSM. The published
copy lives at https://s.vfosnar.cz/josm/ (a2, `/var/lib/www/static/josm`).

The plugin is named `Tracer-cuzk` so it never shares a name with the official list's
`Tracer-testing`. Both register the same classes and the `T` shortcut: only one may be enabled.

## Install (JOSM)

Checked 2026-10-26 on JOSM 19613 starting from an installed `Tracer-testing`, in English and Czech
(Czech labels in brackets). The friend-facing page with the same steps is `tools/site/index.html`.

1. Preferences (Předvolby, F12) → Plugins (Doplňky). Tick Expert Mode (Režim pro experty), bottom
   left: "Configure sites..." (Nastavit zdroje...) only shows in expert mode.
2. Configure sites → Add (Přidat) → `https://s.vfosnar.cz/josm/plugins.txt` → OK → OK.
   "Configure sites..." is the bottom-right button of the Plugins page. A tester first clicked
   Add in Advanced Preferences (Nastavení pro pokročilé), so the page shows annotated screenshots
   (`tools/site/*.png`, from the 2026-10-26 Czech test run).
3. Download list (Stáhnout seznam), search `tracer`, tick `Tracer-cuzk`, untick `Tracer-testing`,
   OK, Restart (Restartovat).
4. More tools → Tracer (Další nástroje → Trasovač) or `T`, click inside a building.

The enabled-module list (`tracer.modulesenabled`) is shared with Tracer-testing, so a former
Tracer-testing user traces at once. With an empty list Tracer refuses to start ("No active module
found"): Tracer plugin settings (Konfigurace Trasovače) → move `ruian` to Enabled modules → OK →
restart JOSM. In the test, tracing did not start until that restart.
