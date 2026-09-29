"""Grade the repo's two nature photos into the site's forest + gold palette,
and cut the crops the page uses. Sources: assets/mist.jpg, assets/landscape.jpg
(already in the repo, used on an earlier version of the site).

    python3 photos.py <out_dir>
"""
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter

R = str(Path(__file__).resolve().parents[2] / 'assets') + '/'


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def grade(img, warmth=1.0, keep=0.40, lift=0.0, contrast=1.2, vignette=0.38):
    a = np.asarray(img.convert('RGB'), np.float32) / 255
    L = a @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    # tritone: deep forest shadows, olive mids, cream-gold highlights
    sh = np.array([0.018, 0.03, 0.02]); md = np.array([0.29, 0.35, 0.19]); hi = np.array([1.0, 0.90, 0.63])
    t1 = smooth(0.0, 0.48, L)[..., None]; t2 = smooth(0.40, 0.95, L)[..., None]
    tri = sh * (1 - t1) + md * t1
    tri = tri * (1 - t2) + hi * t2
    # keep a little of the photo's own colour, warmed and with the blue pulled out
    warm = a * np.array([1.03, 1.0, 0.80 / warmth])
    Lw = warm @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    desat = Lw[..., None] + (warm - Lw[..., None]) * 0.75
    out = tri * (1 - keep) + desat * keep
    # gentle S-curve
    out = np.clip((out - 0.5) * contrast + 0.5 + lift, 0, 1)
    out = out ** 1.04
    H, W = L.shape
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    v = 1 - vignette * smooth(0.45, 1.1, np.hypot((xx / W - 0.5) * 1.2, (yy / H - 0.5) * 1.2))
    out *= v[..., None]
    return Image.fromarray(np.clip(out * 255, 0, 255).astype(np.uint8))


def cut(img, box, size):
    return img.crop(box).resize(size, Image.LANCZOS)


if __name__ == '__main__':
    out = sys.argv[1]
    mist = grade(Image.open(R + 'mist.jpg'))
    alps = grade(Image.open(R + 'landscape.jpg'), warmth=1.1, keep=0.4)
    mist.save(out + '/valley.png'); alps.save(out + '/summit.png')
    # portrait card crops (3:4)
    crops = {
        'sunbreak': (mist, (1480, 200, 2080, 1000)),
        'cliff':    (mist, (330, 600, 930, 1400)),
        'road':     (mist, (1480, 630, 2080, 1430)),
        'ridge':    (mist, (640, 330, 1240, 1130)),
        'weisshorn': (alps, (230, 330, 830, 1130)),
        'cloudsea': (alps, (900, 700, 1500, 1500)),
        'afterglow': (alps, (1720, 280, 2320, 1080)),
    }
    for name, (src, box) in crops.items():
        cut(src, box, (900, 1200)).save(f'{out}/card-{name}.png')
    # wide crops for the two runtime cards (2:1)
    cut(alps, (0, 250, 1600, 1050), (1400, 700)).save(out + '/wide-peaks.png')
    cut(alps, (700, 650, 2400, 1500), (1400, 700)).save(out + '/wide-cloudsea.png')
    print('ok')
