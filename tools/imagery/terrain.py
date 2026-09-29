"""Procedural 'digital nature': mountains drawn as fields of light points, a
glowing path winding up the valley, storm clouds and god rays.

Original renders for the Aura Droid site. Nothing here is traced from a
reference; it is noise, a camera and a lot of dots.

    python3 terrain.py <preset> <out.png> [width height]
"""
import sys, math
import numpy as np
from PIL import Image, ImageFilter, ImageChops


# ── noise ──────────────────────────────────────────────────────────────────
class Noise:
    def __init__(self, seed, period=512):
        self.g = np.random.default_rng(seed).random((period, period)).astype(np.float32)
        self.p = period

    def value(self, x, z):
        p = self.p
        xi = np.floor(x).astype(np.int64); zi = np.floor(z).astype(np.int64)
        xf = (x - xi).astype(np.float32); zf = (z - zi).astype(np.float32)
        u = xf * xf * (3 - 2 * xf); v = zf * zf * (3 - 2 * zf)
        x0 = xi % p; x1 = (xi + 1) % p; z0 = zi % p; z1 = (zi + 1) % p
        a = self.g[x0, z0]; b = self.g[x1, z0]; c = self.g[x0, z1]; d = self.g[x1, z1]
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v

    def fbm(self, x, z, octaves=6, ridged=False, lac=2.03, gain=0.5):
        tot = np.zeros_like(x, dtype=np.float32); amp = 0.5; f = 1.0; norm = 0.0
        prev = 1.0
        for o in range(octaves):
            n = self.value(x * f + o * 17.31, z * f - o * 9.73)
            if ridged:
                n = 1 - np.abs(2 * n - 1)
                n = n * n
                n = n * (0.55 + 0.45 * prev)  # sharper where the coarser octave peaks
                prev = n
            tot += amp * n; norm += amp; amp *= gain; f *= lac
        return tot / norm


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# ── presets ────────────────────────────────────────────────────────────────
PRESETS = {
    # the hero: a long valley, the path of light running to a pass under the rays
    'hero': dict(seed=7, W=2400, H=1400, cam_h=5.2, pitch=-0.07, fov=62, z_far=150,
                 sun=(0.5, 0.04), path_amp=3.1, path_freq=0.16, path_phase=0.35,
                 mtn=1.0, valley=2.6, rows=470, cols=1500, clouds=1.0, rays=1.0,
                 dot='olive', bg='dark'),
    'tall': dict(seed=7, W=1200, H=2000, cam_h=5.6, pitch=-0.13, fov=44, z_far=150,
                 sun=(0.5, 0.03), path_amp=2.2, path_freq=0.16, path_phase=0.35,
                 mtn=1.0, valley=2.4, rows=520, cols=900, clouds=1.0, rays=1.0,
                 dot='olive', bg='dark'),
    # a single ridge climbing into light: card art
    'peak': dict(seed=21, W=1100, H=1400, cam_h=3.2, pitch=0.02, fov=48, z_far=90,
                 sun=(0.62, 0.05), path_amp=1.4, path_freq=0.22, path_phase=1.9,
                 mtn=1.35, valley=1.6, rows=430, cols=900, clouds=0.6, rays=1.2,
                 dot='olive', bg='dark'),
    # wide low valley at dusk: footer / banner
    'dusk': dict(seed=33, W=2400, H=1300, cam_h=7.5, pitch=-0.09, fov=66, z_far=170,
                 sun=(0.7, 0.18), path_amp=3.6, path_freq=0.12, path_phase=2.6,
                 mtn=0.9, valley=3.0, rows=470, cols=1500, clouds=1.0, rays=0.8,
                 dot='olive', bg='dark'),
    # pale dunes of dots on cream: section background
    'cream': dict(seed=5, W=2400, H=1300, cam_h=6.0, pitch=-0.05, fov=64, z_far=140,
                  sun=(0.6, 0.2), path_amp=3.0, path_freq=0.14, path_phase=1.1,
                  mtn=0.8, valley=3.2, rows=300, cols=1300, clouds=0.0, rays=0.0,
                  dot='cream', bg='cream'),
}


def render(name, W=None, H=None):
    P = dict(PRESETS[name])
    if W: P['W'] = W
    if H: P['H'] = H
    W, H = P['W'], P['H']
    nz = Noise(P['seed']); nz2 = Noise(P['seed'] + 101)

    def path_x(Z):
        a = P['path_amp']
        return (a * np.sin(Z * P['path_freq'] + P['path_phase']) * np.exp(-Z / 90.0)
                + 0.45 * a * np.sin(Z * P['path_freq'] * 2.3 + 1.3) * np.exp(-Z / 60.0)
                + 0.28 * a * np.sin(Z * 0.85 + 0.4) * np.exp(-Z / 22.0)
                - a * math.sin(P['path_phase']) * np.exp(-Z / 12.0))

    def height(X, Z):
        k = 0.062 / (1.0 + Z / 45.0)
        m = nz.fbm(X * k + 40.0, Z * 0.062 + 12.0, 7, ridged=True)
        m = np.power(m, 1.75)
        d = np.abs(X - path_x(Z))
        vw = P['valley'] + 0.09 * Z
        valley = smoothstep(0.0, 1.0, d / vw)
        scale = (1.4 + 0.15 * Z) * P['mtn']
        # a back range that closes the valley, with a pass where the path ends
        back = smoothstep(P['z_far'] * 0.55, P['z_far'] * 0.9, Z) * (0.8 + 0.6 * nz2.fbm(X * 0.03 + 5, Z * 0.01, 4))
        h = m * scale * (0.05 + 0.95 * valley) + back * scale * 0.55 * smoothstep(0.0, 1.0, d / (vw * 1.6))
        h += 0.22 * nz2.fbm(X * 0.7, Z * 0.7, 3) * (0.25 + 0.75 * valley)  # rocky grain
        return h

    # camera
    cam_y = P['cam_h'] + float(height(np.array([0.0]), np.array([1.5]))[0]) * 0.4
    f = (W / 2) / math.tan(math.radians(P['fov'] / 2))
    cp, sp = math.cos(P['pitch']), math.sin(P['pitch'])
    cx, cy = W / 2, H * 0.5

    def project(X, Y, Z):
        y = Y - cam_y
        zc = Z * cp - y * sp
        yc = Z * sp + y * cp
        return cx + f * X / zc, cy - f * yc / zc, zc

    # buffers
    acc = np.zeros((3, H * W), np.float32)
    ybuf = np.full(W, np.inf, np.float32)

    palette = {
        'olive': (np.array([0.40, 0.46, 0.27]), np.array([0.98, 0.90, 0.62])),
        'cream': (np.array([0.62, 0.58, 0.44]), np.array([1.0, 0.99, 0.94])),
    }[P['dot']]
    gold = np.array([1.0, 0.82, 0.42])
    sun_sx, sun_sy = P['sun'][0] * W, P['sun'][1] * H

    rows, cols = P['rows'], P['cols']
    z0, z1 = 1.2, P['z_far']
    Zs = z0 * (z1 / z0) ** (np.arange(rows) / (rows - 1)) ** 1.15
    Ls = np.array([-0.35, 0.75, 0.56]); Ls /= np.linalg.norm(Ls)
    colsx = np.arange(W, dtype=np.float32)

    pend_i, pend_w = [], []

    def flush():
        if not pend_i:
            return
        idx = np.concatenate(pend_i); wts = np.concatenate(pend_w)
        for c in range(3):
            acc[c] += np.bincount(idx, weights=wts[:, c], minlength=W * H).astype(np.float32)
        pend_i.clear(); pend_w.clear()

    def splat(sx, sy, col, w):
        ix = np.floor(sx).astype(np.int64); iy = np.floor(sy).astype(np.int64)
        fx = sx - ix; fy = sy - iy
        for dx, dy, ww in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)),
                           (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
            x = ix + dx; y = iy + dy
            ok = (x >= 0) & (x < W) & (y >= 0) & (y < H)
            pend_i.append(y[ok] * W + x[ok])
            pend_w.append(col[ok] * (w[ok] * ww[ok])[:, None])

    rng = np.random.default_rng(P['seed'] + 7)
    for i, Z in enumerate(Zs):
        half = Z * math.tan(math.radians(P['fov'] / 2)) * 1.25 + 2
        X = np.linspace(-half, half, cols).astype(np.float32)
        X += (rng.random(cols).astype(np.float32) - 0.5) * (2 * half / cols) * 0.6
        Zr = np.full_like(X, Z)
        Y = height(X, Zr)
        e = 0.05 + 0.002 * Z
        gx = (height(X + e, Zr) - height(X - e, Zr)) / (2 * e)
        gz = (height(X, Zr + e) - height(X, Zr - e)) / (2 * e)
        n = np.stack([-gx, np.ones_like(gx), -gz], 1); n /= np.linalg.norm(n, axis=1, keepdims=True)
        sx, sy, zc = project(X, Y, Zr)
        on = (sx > -2) & (sx < W + 1) & (zc > 0.2)
        # occlusion against nearer terrain (voxel-space horizon)
        ci = np.clip(sx.astype(np.int64), 0, W - 1)
        vis = on & (sy < ybuf[ci] + 1.2)
        # shading
        lam = np.clip(n @ Ls, 0, 1)
        rim = np.clip(1 - n[:, 1], 0, 1) ** 1.5
        hgt = np.clip(Y / (2.0 + 0.26 * Z * P['mtn']), 0, 1)
        t = np.clip(0.08 + 0.55 * lam ** 1.8 + 0.42 * hgt ** 1.3 + 0.2 * rim, 0, 1)
        # light falls off away from the sun column, rises toward it
        dsun = np.hypot((sx - sun_sx) / W, (sy - sun_sy) / H)
        t *= 0.55 + 0.75 * np.exp(-dsun * 2.2)
        col = palette[0][None] * (1 - t[:, None]) + palette[1][None] * t[:, None]
        fog = 1 - np.exp(-Z / (P['z_far'] * 0.42))
        haze = np.array([0.55, 0.56, 0.44]) if P['bg'] == 'dark' else np.array([0.97, 0.95, 0.86])
        col = col * (1 - fog * 0.55) + haze * fog * 0.55
        # the path of light
        dpath = np.abs(X - path_x(Zr)) / (0.10 + 0.0055 * Z)
        glow = np.exp(-dpath ** 2) * (0.75 + 0.25 * np.sin(Z * 3.1 + X * 2))
        if P['dot'] == 'olive':
            col = col * (1 - glow[:, None]) + gold[None] * glow[:, None] * 2.4
        w = (0.55 + 0.9 * t + 1.2 * glow) * (1.0 - 0.35 * fog)
        w *= 1.5 if P['dot'] == 'olive' else 1.0
        if P['dot'] == 'cream':
            w *= 0.75
        # nearer rows get fatter dots
        size = 1 if Z > 10 else (2 if Z > 4 else 3)
        vv = vis
        for ox in range(size):
            for oy in range(size):
                splat(sx[vv] + ox * 0.9, sy[vv] + oy * 0.9, col[vv], w[vv] / (size * size) ** 0.6)
        if P['dot'] == 'olive':
            # the river of light itself: a dense, sparkling strand on the valley floor
            xp = float(path_x(np.array([Z]))[0])
            spread = 0.05 + 0.004 * Z
            m_ = 36 if Z < 30 else 18
            Xp = (xp + rng.normal(0, spread, m_)).astype(np.float32)
            Zp = np.full_like(Xp, Z)
            Yp = height(Xp, Zp) + 0.02
            psx, psy, pzc = project(Xp, Yp, Zp)
            pci = np.clip(psx.astype(np.int64), 0, W - 1)
            pv = (psx > 0) & (psx < W - 1) & (pzc > 0.2) & (psy < ybuf[pci] + 1.5)
            spark = 0.6 + 1.4 * rng.random(m_) ** 3
            pc = np.tile(np.array([1.0, 0.86, 0.5]), (m_, 1)) * (1 - np.exp(-Z / 40))[None] * 0 + np.array([1.0, 0.86, 0.5])
            splat(psx[pv], psy[pv], pc[pv], (spark[pv] * 2.2 * (1 - 0.4 * fog)).astype(np.float32))
        if i % 48 == 47:
            flush()
        # update the horizon with everything this row covers
        order = np.argsort(sx[on])
        if order.size > 1:
            xs = sx[on][order]; ys = sy[on][order]
            lo = max(int(np.ceil(xs[0])), 0); hi = min(int(xs[-1]), W - 1)
            if hi >= lo:
                seg = colsx[lo:hi + 1]
                ybuf[lo:hi + 1] = np.minimum(ybuf[lo:hi + 1], np.interp(seg, xs, ys))

    flush()
    img = acc.reshape(3, H, W).transpose(1, 2, 0)

    # ── sky, clouds, rays ──────────────────────────────────────────────────
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    horizon = np.clip(ybuf, 0, H)  # per-column top of terrain
    sky_mask = (yy < horizon[None, :]).astype(np.float32)
    sky_mask = np.asarray(Image.fromarray((sky_mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2)), np.float32) / 255
    if P['bg'] == 'dark':
        top = np.array([0.020, 0.028, 0.022]); low = np.array([0.16, 0.17, 0.12])
        grad = (yy / H)[..., None]
        sky = top * (1 - grad) + low * grad
        dsun = np.hypot((xx - sun_sx) / W * 1.3, (yy - sun_sy) / H)
        sky += np.array([0.95, 0.86, 0.60]) * (np.exp(-dsun * 2.8) * 0.5 + np.exp(-dsun * 9) * 0.45)[..., None]
        if P['clouds'] > 0:
            u = xx / W * 3.2; v = yy / W * 5.0
            warp = nz2.fbm(u * 0.9 + 3, v * 0.9 + 9, 4)
            c = nz2.fbm(u + warp * 1.1, v + warp * 0.8, 7)
            raw = c
            c = smoothstep(0.34, 0.60, raw) * P['clouds']
            c *= np.clip(1.45 - yy / (H * 0.5), 0, 1)
            lit = np.exp(-dsun * 2.2)
            edge = 1 - smoothstep(0.34, 0.75, raw)  # thin cloud edges catch the light
            body = smoothstep(0.45, 0.9, raw)       # thick cores stay dark
            cloud_col = (np.array([0.13, 0.14, 0.11]) * (1 - 0.5 * body)[..., None]
                         + np.array([0.98, 0.88, 0.62]) * (lit * (0.2 + 1.1 * edge))[..., None])
            sky = sky * (1 - c[..., None] * 0.94) + cloud_col * c[..., None] * 0.94
            gaps = (1 - c) * np.exp(-dsun * 1.4)
        img = img + sky * sky_mask[..., None] * 1.0
        # ground base under the dots
        img += np.array([0.012, 0.016, 0.011]) * (1 - sky_mask[..., None])
    else:
        top = np.array([0.94, 0.92, 0.81]); low = np.array([0.93, 0.90, 0.78])
        grad = (yy / H)[..., None]
        base = top * (1 - grad) + low * grad
        img = base * (1 - np.clip(img.mean(2, keepdims=True) * 0.0, 0, 1)) + img * 0.55

    if P['rays'] > 0:
        # zoom blur from the sun over the bright parts gives the beams
        src = np.clip(img - 0.22, 0, None) * sky_mask[..., None]
        if P['clouds'] > 0:
            src = src * (0.15 + 0.85 * gaps[..., None])
        s8 = Image.fromarray(np.clip(src * 255, 0, 255).astype(np.uint8))
        accr = np.zeros_like(img)
        steps = 34
        for k in range(steps):
            sc = 1 + 1.6 * k / steps
            w2, h2 = int(W * sc), int(H * sc)
            big = s8.resize((w2, h2), Image.BILINEAR)
            ox = int(sun_sx * sc - sun_sx); oy = int(sun_sy * sc - sun_sy)
            crop = big.crop((ox, oy, ox + W, oy + H))
            accr += np.asarray(crop, np.float32) / 255 * (1 - k / steps)
        accr /= steps * 0.5
        ang = np.arctan2(yy - sun_sy, xx - sun_sx)
        beams = 0.7 + 0.3 * smoothstep(0.3, 0.75, nz.value(ang * 3 + 50, np.zeros_like(ang) + 3.3))
        fall = np.exp(-np.clip(yy - sun_sy, 0, None) / (H * 0.9))
        img += accr * beams[..., None] * fall[..., None] * 1.5 * P['rays'] * np.array([1.0, 0.93, 0.72])

    # bloom
    if P['bg'] == 'dark':
        bright = np.clip(img - 0.55, 0, None)
        b8 = Image.fromarray(np.clip(bright * 180, 0, 255).astype(np.uint8))
        bl = (np.asarray(b8.filter(ImageFilter.GaussianBlur(W / 180)), np.float32) / 180
              + np.asarray(b8.filter(ImageFilter.GaussianBlur(W / 45)), np.float32) / 180 * 0.8)
        img += bl * np.array([1.0, 0.9, 0.62]) * 0.9
        # filmic curve + vignette + grain
        img = 1 - np.exp(-img * 1.35)
        vig = 1 - 0.45 * smoothstep(0.35, 1.05, np.hypot((xx / W - 0.5) * 1.1, (yy / H - 0.45) * 1.3))
        img *= vig[..., None]
    img += (np.random.default_rng(3).random((H, W, 1)).astype(np.float32) - 0.5) * 0.018
    out = Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8))
    return out


if __name__ == '__main__':
    name, path = sys.argv[1], sys.argv[2]
    W = int(sys.argv[3]) if len(sys.argv) > 3 else None
    H = int(sys.argv[4]) if len(sys.argv) > 4 else None
    render(name, W, H).save(path)
    print('wrote', path)
