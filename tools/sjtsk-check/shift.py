#!/usr/bin/env python3
"""Measures which S-JTSK -> WGS84 transformation the ČÚZK RÚIAN service applies (outSR=4326) and how
far its output sits from RUIAN buildings already in OSM, per region.

Usage: shift.py WORKDIR
Requests: 1 Postpass query (sample.sql), then ČÚZK layer 3 in batches of 200 codes, once per outSR.
Needs cct and projinfo (PROJ >= 9) with cz_cuzk_table_-y-x_3_v1710.tif reachable.
"""
import collections
import json
import math
import os
import statistics as st
import subprocess
import sys
import time
import urllib.parse
import urllib.request

work = sys.argv[1]
here = os.path.dirname(os.path.abspath(__file__))
UA = {'User-Agent': 'josm-tracer-sjtsk-check'}
LAYER = 'https://ags.cuzk.gov.cz/arcgis/rest/services/RUIAN/Prohlizeci_sluzba_nad_daty_RUIAN/MapServer/3/query'


def post(url, fields):
    req = urllib.request.Request(url, data=urllib.parse.urlencode(fields).encode(), headers=UA)
    return json.load(urllib.request.urlopen(req, timeout=120))


osm_path = os.path.join(work, 'osm_sample.json')
if not os.path.exists(osm_path):
    sample = post('https://postpass.geofabrik.de/api/interpreter',
                  {'data': open(os.path.join(here, 'sample.sql')).read(), 'options[geojson]': 'false'})
    json.dump(sample, open(osm_path, 'w'))
osm = json.load(open(osm_path))['result']

cuzk_path = os.path.join(work, 'cuzk.json')
if not os.path.exists(cuzk_path):
    kods = sorted({int(x['kod']) for x in osm if x['kod'] and x['kod'].isdigit()})
    cuzk = {}
    for sr in ('4326', '5514'):
        cuzk[sr] = {}
        for i in range(0, len(kods), 200):
            d = post(LAYER, {'where': 'kod IN (%s)' % ','.join(map(str, kods[i:i + 200])), 'outFields': 'kod',
                             'returnGeometry': 'true', 'outSR': sr, 'f': 'json'})
            if 'error' in d:
                sys.exit(f'ČÚZK error: {d["error"]}')
            cuzk[sr].update({str(f['attributes']['kod']): f['geometry']['rings'] for f in d['features']})
            time.sleep(1)
    json.dump(cuzk, open(cuzk_path, 'w'))
cuzk = json.load(open(cuzk_path))

# single-ring buildings present in both CRSs with matching vertex counts
region, sjtsk, wgs, osm_rings = [], [], [], []
for x in osm:
    k = x['kod']
    g = json.loads(x['g'])['coordinates']
    a, b = cuzk['5514'].get(k), cuzk['4326'].get(k)
    if not a or not b or len(a) != 1 or len(b) != 1 or len(a[0]) != len(b[0]) or len(g) != 1 or len(g[0]) != 1:
        continue
    region += [x['name']] * (len(a[0]) - 1)
    sjtsk += a[0][:-1]
    wgs += b[0][:-1]
    osm_rings.append((x['name'], g[0][0][:-1], b[0][:-1], a[0][:-1]))

ops = {'ČÚZK table (EPSG:5514 -> 5516 grid + Helmert)': ['-I'] + open(os.path.join(here, 'etrs89-to-sjtsk.pipeline')).read().split()}
names = subprocess.run(['projinfo', '-s', 'EPSG:5514', '-t', 'EPSG:4326', '--spatial-test', 'intersects',
                        '--grid-check', 'none', '--summary'], capture_output=True, text=True).stdout.splitlines()[1:]
strings = [l for l in subprocess.run(['projinfo', '-s', 'EPSG:5514', '-t', 'EPSG:4326', '--spatial-test', 'intersects',
                                      '--grid-check', 'none', '-o', 'PROJ', '--single-line'],
                                     capture_output=True, text=True).stdout.splitlines() if l.startswith('+proj')]
for n, s in zip(names, strings):
    ops[n.split(', ')[1]] = s.split()


def run(args, pts):
    out = subprocess.run(['cct', '-d', '12'] + args, input='\n'.join(f'{e} {n} 0 0' for e, n in pts) + '\n',
                         capture_output=True, text=True).stdout.splitlines()
    return [(float(l.split()[1]), float(l.split()[0])) for l in out]


def dne(a, b):
    return ((a[1] - b[1]) * 110574, (a[0] - b[0]) * 111320 * math.cos(math.radians(a[1])))


print(f'{len(sjtsk)} vertices of {len(osm_rings)} buildings')
print('Transformation applied to the S-JTSK vertices, minus the service outSR=4326 vertex, metres:')
for name, args in ops.items():
    d = [dne(t, w) for t, w in zip(run(args, sjtsk), wgs)]
    h = sorted(math.hypot(*x) for x in d)
    print(f'  {name}: median {st.median(h):.3f}, p95 {h[int(.95 * len(h))]:.3f}')


def centroid(r):
    k = 111320 * math.cos(math.radians(r[0][1]))
    xy = [((p[0] - r[0][0]) * k, (p[1] - r[0][1]) * 110574) for p in r]
    a = cx = cy = 0
    for (x1, y1), (x2, y2) in zip(xy, xy[1:] + xy[:1]):
        c = x1 * y2 - x2 * y1
        a += c
        cx += (x1 + x2) * c
        cy += (y1 + y2) * c
    a /= 2
    return r[0][0] + cx / (6 * a) / k, r[0][1] + cy / (6 * a) / 110574, abs(a)


print('Per region, median N/E metres, buildings whose OSM area is within 3 % of ČÚZK:')
print('  region            n   OSM - service      OSM - ČÚZK table')
by = collections.defaultdict(list)
for name, o, w, s in osm_rings:
    table = [(p[0], p[1]) for p in run(ops['ČÚZK table (EPSG:5514 -> 5516 grid + Helmert)'], s)]
    co, cw, ct = centroid(o), centroid(w), centroid(table)
    if 0.97 < co[2] / cw[2] < 1.03 and math.hypot(*dne(co, cw)) < 20:
        by[name].append((dne(co, cw), dne(co, ct)))
for name, rows in by.items():
    print(f'  {name:16s} {len(rows):3d}   {st.median(r[0][0] for r in rows):+.2f} {st.median(r[0][1] for r in rows):+.2f}'
          f'      {st.median(r[1][0] for r in rows):+.2f} {st.median(r[1][1] for r in rows):+.2f}')
