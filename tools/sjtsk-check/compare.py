#!/usr/bin/env python3
"""Compares SJtsk.java with PROJ (cct) in both directions on random and grid-edge points.

Usage: compare.py CLASSPATH   (plugin classes + josm.jar + this directory's compiled H.class)
Needs cct (PROJ >= 9) with cz_cuzk_table_-y-x_3_v1710.tif reachable (PROJ_DATA or PROJ_NETWORK=ON).
"""
import math
import os
import random
import subprocess
import sys

CP = sys.argv[1]
PIPE = open(os.path.join(os.path.dirname(__file__), 'etrs89-to-sjtsk.pipeline')).read().split()

random.seed(11)
pts = [(random.uniform(-910000, -425000), random.uniform(-1235000, -927000)) for _ in range(20000)]
pts += [(-908000 + 2000 * i + o, -930000 - 2000 * j - o)
        for i in range(0, 241, 3) for j in range(0, 152, 3)
        for o in (0, 1000, 999.999, 1000.001, 0.03, -0.03, 0.05, -0.05)]


def java(mode, lines):
    return subprocess.run(['java', '-cp', CP, 'H', mode], input='\n'.join(lines) + '\n',
                          capture_output=True, text=True, check=True).stdout.splitlines()


def cct_one(args, line):
    out = subprocess.run(['cct', '-d', '12'] + args + PIPE, input=f'{line} 0 0\n',
                         capture_output=True, text=True).stdout.split()
    return None if not out or out[0] == '#' else (float(out[0]), float(out[1]))


def cct(args, lines):
    res = {}
    out = subprocess.run(['cct', '-d', '12'] + args + PIPE,
                         input='\n'.join(f'{x} 0 0 i{k}' for k, x in enumerate(lines)) + '\n',
                         capture_output=True, text=True).stdout
    for line in out.splitlines():
        f = line.split()
        if f and not line.startswith('#') and f[-1].startswith('i'):
            res[int(f[-1][1:])] = (float(f[0]), float(f[1]))
    return res


def compare(name, args, lines, scale):
    got = java('inv' if args else 'fwd', lines)
    ref = cct(args, lines)
    dist, stat = [], {'both refuse': 0, 'only Java refuses': 0, 'only PROJ refuses': 0}
    for k, j in enumerate(got):
        mine = None if j.startswith('ERR') else tuple(map(float, j.split()))
        theirs = ref.get(k)

        def d():
            return math.hypot((mine[0] - theirs[0]) * scale[0], (mine[1] - theirs[1]) * scale[1])
        # a cct batch reuses grid cells cached from earlier records: recheck any mismatch alone
        if (mine is None) != (theirs is None) or (mine and d() > 1e-3):
            theirs = cct_one(args, lines[k])
        if mine is None and theirs is None:
            stat['both refuse'] += 1
        elif mine is None:
            stat['only Java refuses'] += 1
        elif theirs is None:
            stat['only PROJ refuses'] += 1
        else:
            dist.append(d())
    dist.sort()
    print(f'{name}: {len(lines)} points, {len(dist)} compared, median {dist[len(dist) // 2] * 1e3:.4f} mm,'
          f' max {dist[-1] * 1e3:.4f} mm, {stat}')
    return ref


ref = compare('S-JTSK -> ETRS89', ['-I'], [f'{e} {n}' for e, n in pts], (111000, 72000))
compare('ETRS89 -> S-JTSK', [], [f'{lat} {lon}' for lat, lon in ref.values()], (1, 1))
