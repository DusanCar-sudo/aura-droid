#!/usr/bin/env bash
# Rebuild every image in modern/img/ from scratch.
#   1. render the light-point landscapes (terrain.py)
#   2. grade and crop the two repo photos (photos.py)
#   3. export WebP at the sizes and qualities the page expects
# Needs: python3, numpy, pillow.  Takes a few minutes; full-res PNGs go to $OUT
# (default tools/imagery/out, git-ignored), WebPs to $DEST (default modern/img).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="${OUT:-$HERE/out}"
DEST="${DEST:-$ROOT/modern/img}"
mkdir -p "$OUT"

python3 "$HERE/terrain.py" hero  "$OUT/t-hero.png"  2400 1400
python3 "$HERE/terrain.py" tall  "$OUT/t-tall.png"  1080 1800
python3 "$HERE/terrain.py" peak  "$OUT/t-peak.png"  900 1200
python3 "$HERE/terrain.py" dusk  "$OUT/t-dusk.png"  2400 1300
python3 "$HERE/terrain.py" cream "$OUT/t-cream.png" 2400 1300
python3 "$HERE/photos.py" "$OUT"

OUT="$OUT" DEST="$DEST" python3 - <<'EOF'
import os
from PIL import Image
S = os.environ['OUT'] + '/'; D = os.environ['DEST'] + '/'
os.makedirs(D, exist_ok=True)

def save(im, name, size=None, q=80):
    if size: im = im.resize(size, Image.LANCZOS)
    im.save(D + name, 'WEBP', quality=q, method=6)

hero = Image.open(S + 't-hero.png'); save(hero, 'hero-2400.webp', q=78); save(hero, 'hero-1600.webp', (1600, 933), q=80)
save(Image.open(S + 't-tall.png'), 'hero-tall.webp', q=78)
dusk = Image.open(S + 't-dusk.png'); save(dusk, 'dusk-2400.webp', q=78); save(dusk, 'dusk-1600.webp', (1600, 867), q=80)
save(Image.open(S + 't-cream.png'), 'cream-2000.webp', (2000, 1083), q=82)
save(Image.open(S + 'valley.png'), 'valley-2000.webp', (2000, 1192), q=80)
save(Image.open(S + 'summit.png'), 'summit-2000.webp', (2000, 1333), q=80)
for n in ['afterglow', 'cliff', 'cloudsea', 'ridge', 'road', 'sunbreak', 'weisshorn']:
    save(Image.open(S + f'card-{n}.png'), f'card-{n}.webp', (600, 800), q=80)
save(Image.open(S + 't-peak.png'), 'card-peak.webp', (600, 800), q=80)
save(Image.open(S + 'wide-peaks.png'), 'wide-peaks.webp', (1120, 560), q=80)
save(Image.open(S + 'wide-cloudsea.png'), 'wide-cloudsea.webp', (1120, 560), q=80)
road = Image.open(S + 'card-road.png'); save(road.crop((0, 300, 900, 862)), 'thumb-road.webp', (240, 150), q=82)
print('modern/img rebuilt')
EOF
