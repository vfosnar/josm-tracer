#!/usr/bin/env bash
# Builds Tracer-cuzk.jar, its source archive and a JOSM plugin list into OUT.
# Usage: tools/build-plugin-site.sh OUT BASE_URL [JOSM_JAR]
set -euo pipefail
out=$1
base=${2%/}
josm=${3:-/usr/share/java/josm/josm.jar}
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

version=$(git -C "$repo" log -1 --format=%ct)
date=$(git -C "$repo" log -1 --format=%cd --date=format:'%Y-%m-%d %H:%M:%S %z')
rev=$(git -C "$repo" rev-parse --short HEAD)

mkdir -p "$work/classes" "$out"
find "$repo/src" -name '*.java' > "$work/sources"
javac --release 11 -nowarn -encoding UTF-8 -cp "$josm" -d "$work/classes" @"$work/sources"
cp -r "$repo/images" "$repo/data" "$work/classes/"

cat > "$work/manifest.long" <<MF
Plugin-Class: org.openstreetmap.josm.plugins.tracer.TracerPlugin
Plugin-Mainversion: 18494
Plugin-Version: $version
Plugin-Date: $date
Plugin-Description: Tracer with RUIAN buildings and parcels read directly from ČÚZK (no poloha.net). Build $rev of https://github.com/mkyral/josm-tracer plus changes; source: $base/Tracer-cuzk-src.tar.gz
Author: Jan Bilak, Petr Dlouhý, Marián Kyral, Martin Švec
Plugin-Link: $base/
Plugin-Icon: data:image/png;base64,$(base64 -w0 "$repo/images/mapmode/tracer-sml.png")
Plugin-Canloadatruntime: true
Plugin-Minimum-Java-Version: 11
MF
# jar refuses manifest lines over 72 bytes: split them into leading-space continuations
LC_ALL=C awk '{s=$0; print substr(s,1,72); s=substr(s,73); while (length(s)>0) {print " " substr(s,1,71); s=substr(s,72)}}' \
  "$work/manifest.long" > "$work/MANIFEST.MF"
jar cfm "$out/Tracer-cuzk.jar" "$work/MANIFEST.MF" -C "$work/classes" .
git -C "$repo" archive --format=tar.gz --prefix=josm-tracer-cuzk/ -o "$out/Tracer-cuzk-src.tar.gz" HEAD

# JOSM plugin list: "<file>;<url>" then the jar's manifest, each line tab-indented
{
  printf 'Tracer-cuzk.jar;%s/Tracer-cuzk.jar\n' "$base"
  # a manifest wraps lines at 72 bytes with a leading-space continuation; the list wants them whole
  unzip -p "$out/Tracer-cuzk.jar" META-INF/MANIFEST.MF | tr -d '\r' \
    | awk '/^ /{line=line substr($0,2); next} {if (line!="") print "\t" line; line=$0} END{if (line!="") print "\t" line}'
} > "$out/plugins.txt"
echo "built $out (Plugin-Version $version, $rev)"
