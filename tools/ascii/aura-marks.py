#!/usr/bin/env python3
"""Aura terminal marks: every ASCII / block-character version of the Aura mark,
for the Aura Code CLI and the Aura OS terminal (aura-term), in one place.

    python3 aura-marks.py                 # show every mark, true colour
    python3 aura-marks.py --colors 16     # as a 16-colour console (TTY, greetd) sees them
    python3 aura-marks.py --colors none   # NO_COLOR / pipes / CI logs
    python3 aura-marks.py --only mini --product OS
    python3 aura-marks.py --html > marks.html   # the same output as a web page

Geometry comes from aura-retro-design brand/glyphs.py (3-unit stroke on a
10-unit x-height, four stripes 2.4 wide leaning 5 over 10, Sinclair order).
MARK_PIXELS is the hand-tuned 10-pixel raster already shipped in aura-code
src/cli/diamond.ts and aura-os bin/aura-os-logo: keep all three in sync.
"""
import argparse
import html
import sys

# ── palette (BRAND.md: the stripes are fixed, never themed) ─────────────────
PAPER = (0xF2, 0xF1, 0xF5)
STRIPES = {'r': (0xE4, 0x31, 0x2B), 'y': (0xF8, 0xB9, 0x1E),
           'g': (0x2F, 0xAE, 0x4E), 'c': (0x1A, 0xA6, 0xE0)}
DIM = (0x8A, 0x94, 0xA6)
FAINT = (0x4A, 0x55, 0x68)
# nearest colours on a 16-colour console: bright red/yellow/green/cyan, white
ANSI16 = {'r': 91, 'y': 93, 'g': 92, 'c': 96, '#': 97, 'dim': 37, 'faint': 90}

# ── the 10-pixel mark (x-height 10, stroke 3), as shipped ───────────────────
MARK_PIXELS = [
    '...#######..###....###..#######......#######.......rryyggcc',
    '.#########..###....###..#########..#########.......rryyggcc',
    '.#########..###....###..#########..#########......rryyggcc.',
    '###....###..###....###..####......###....###......rryyggcc.',
    '###....###..###....###..###.......###....###.....rryyggcc..',
    '###....###..###....###..###.......###....###.....rryyggcc..',
    '###....###..###....###..###.......###....###....rryyggcc...',
    '.#########...#########..###........#########....rryyggcc...',
    '.#########...#########..###........#########...rryyggcc....',
    '...#######.....#######..###..........#######...rryyggcc....',
]


# ── a tiny cell model: each terminal cell is (glyph, fg, bg) ────────────────
# fg/bg are keys: '#' letters, 'r y g c' stripes, 'dim', 'faint', or None
class Canvas:
    def __init__(self):
        self.rows = []

    def add(self, cells):
        self.rows.append(list(cells))
        return self

    def text(self, s, fg='#', bold=False):
        return [(ch, fg, None, bold) for ch in s]


def cells_from(s, fg='#', bold=False):
    return [(ch, fg, None, bold) for ch in s]


def half_blocks(px):
    """Two pixel rows per text row. A cell holds at most two inks: fg + bg."""
    out = []
    for y in range(0, len(px), 2):
        top, bot = px[y], px[y + 1] if y + 1 < len(px) else '.' * len(px[y])
        row = []
        for t, b in zip(top, bot):
            if t == '.' and b == '.':
                row.append((' ', None, None, False))
            elif t == '.':
                row.append(('▄', b, None, False))
            elif b == '.':
                row.append(('▀', t, None, False))
            elif t == b:
                row.append(('█', t, None, False))
            else:
                row.append(('▀', t, b, False))
        out.append(row)
    return out


QUAD = {  # (tl, tr, bl, br) filled -> glyph
    (0, 0, 0, 0): ' ', (1, 0, 0, 0): '▘', (0, 1, 0, 0): '▝', (0, 0, 1, 0): '▖', (0, 0, 0, 1): '▗',
    (1, 1, 0, 0): '▀', (0, 0, 1, 1): '▄', (1, 0, 1, 0): '▌', (0, 1, 0, 1): '▐',
    (1, 0, 0, 1): '▚', (0, 1, 1, 0): '▞', (1, 1, 1, 0): '▛', (1, 1, 0, 1): '▜',
    (1, 0, 1, 1): '▙', (0, 1, 1, 1): '▟', (1, 1, 1, 1): '█',
}


def quadrants(px):
    """2x2 pixels per cell: the same mark at half the width."""
    w = len(px[0]) + (len(px[0]) % 2)
    px = [r.ljust(w, '.') for r in px]
    out = []
    for y in range(0, len(px), 2):
        a, b = px[y], px[y + 1]
        row = []
        for x in range(0, w, 2):
            q = [a[x], a[x + 1], b[x], b[x + 1]]
            inks = [k for k in dict.fromkeys(q) if k != '.']
            if not inks:
                row.append((' ', None, None, False)); continue
            fg = inks[0]
            bg = inks[1] if len(inks) > 1 else None
            if bg is None:
                row.append((QUAD[tuple(int(k == fg) for k in q)], fg, None, False))
            else:  # two inks and no gap (the stripes): the fg shape on the bg ink
                row.append((QUAD[tuple(int(k == fg) for k in q)], fg, bg, False))
        out.append(row)
    return out


def stripe_run(n=4):
    """One text row of stripes: each ▟ in the next colour on the one before."""
    keys = 'rygc'[:n]
    row = [('▟', keys[0], None, False)]
    for prev, k in zip(keys, keys[1:]):
        row.append(('▟', k, prev, False))
    row.append(('▘', keys[-1], None, False))
    return row


def stripe_block(rows=6, width=2):
    """The four stripes alone, big: `width` columns each, leaning one column per
    row (5 over 10 at terminal cell proportions), edges smoothed with ▟ ▘."""
    out = []
    for r in range(rows):
        lead = rows - 1 - r
        row = [(' ', None, None, False)] * lead
        prev = None
        for k in 'rygc':
            row.append(('▟', k, prev, False))
            row += [('█', k, None, False)] * (width - 1)
            prev = k
        row.append(('▘', 'c', None, False))
        out.append(row)
    return out


def pad(row, n):
    return row + [(' ', None, None, False)] * max(0, n - len(row))


def spaced(word):
    return '  '.join(word) if len(word) <= 4 else ' '.join(word)


# ── the marks ───────────────────────────────────────────────────────────────
def m_lockup(product):
    """Current CLI/aura-term hero mark (5 rows x 59 cols) with the product under
    the stripes. Keep: it is the SVG lockup, pixel for pixel."""
    rows = half_blocks(MARK_PIXELS)
    start = MARK_PIXELS[-1].index('r') + 1
    rows.append([(' ', None, None, False)] * start + cells_from(spaced(product), bold=True))
    return rows


def m_mini(product):
    """NEW. The same 10-pixel letters in quadrant blocks, 5 rows x 33 cols: half
    the width, for 40-80 column terminals and split panes. The stripes use the
    one-line mark's ▟-on-previous-colour trick, one row per step, so they stay
    smooth at this size. Product label beside the foot of the stripes."""
    letters = quadrants([r[:46] for r in MARK_PIXELS])
    stripes = stripe_block(rows=len(letters), width=1)
    rows = [pad(l, 24) + s for l, s in zip(letters, stripes)]
    rows[-1] += cells_from('  ' + product, bold=True)
    return rows


def m_line(product):
    """One line, for pinned headers, prompts and window titles (current `compact`)."""
    return [cells_from('aura ', bold=True) + stripe_run() + cells_from(' ' + product, bold=True)]


def m_ascii(product):
    """NEW. 7-bit safe: no Unicode, readable with no colour at all. For CI logs,
    `--no-color`, Windows conhost, serial consoles and /etc/issue."""
    art = [
        r"  __ _ _  _ _ _ __ _     ////",
        r" / _` | || | '_/ _` |   ////",
        r" \__,_|\_,_|_| \__,_|  ////   " + product.lower(),
    ]
    rows = []
    for line in art:
        row = []
        slash = 0
        for i, ch in enumerate(line):
            if ch == '/' and i > 20:
                row.append((ch, 'rygc'[slash % 4], None, True)); slash += 1
            elif i > 28:
                row.append((ch, 'dim', None, True))
            else:
                row.append((ch, '#', None, True))
        rows.append(row)
    return rows


def m_tile(product):
    """NEW, for Aura OS. A square-ish tile for fetch-style screens (logo left,
    system facts right): big stripes over the wordmark in text."""
    rows = [pad(r, 14) for r in stripe_block(rows=6, width=2)]
    rows.append([(' ', None, None, False)] * 14)
    rows.append(cells_from(' aura ', bold=True) + cells_from(product.lower(), fg='dim', bold=True))
    return rows


def m_working(product):
    """NEW, for both. The 'working' indicator: the stripes light up in order
    (4 frames, ~120 ms each), dim stripes in faint. Replaces a generic spinner."""
    frames = []
    for lit in range(4):
        row = cells_from('aura ', bold=True)
        prev = None
        for i, k in enumerate('rygc'):
            ink = k if i <= lit else 'faint'
            row.append(('▟', ink, prev, False)); prev = ink
        row.append(('▘', prev, None, False))
        row += cells_from('  working', fg='dim')
        frames.append(row)
    return frames


MARKS = {
    'lockup': (m_lockup, 'Hero lockup, 5 rows x 59 cols (current; keep)'),
    'mini': (m_mini, 'Mini lockup, 5 rows x 33 cols, quadrant blocks (new)'),
    'line': (m_line, 'One-line mark for headers and titles (current compact; keep)'),
    'ascii': (m_ascii, 'ASCII-safe, 3 rows, works with no Unicode and no colour (new)'),
    'tile': (m_tile, 'Fetch tile for Aura OS, 8 rows x 14 cols (new)'),
    'working': (m_working, 'Working indicator, 4 frames (new)'),
}


# ── output ──────────────────────────────────────────────────────────────────
def rgb_of(key):
    if key == '#':
        return PAPER
    if key in STRIPES:
        return STRIPES[key]
    return {'dim': DIM, 'faint': FAINT}.get(key)


def sgr(fg, bg, bold, colors):
    if colors == 'none':
        return ''
    parts = ['0']
    if bold:
        parts.append('1')
    if colors == '16':
        if fg:
            parts.append(str(ANSI16[fg]))
        if bg:
            parts.append(str(ANSI16[bg] + 10))
    else:
        if fg:
            parts.append('38;2;%d;%d;%d' % rgb_of(fg))
        if bg:
            parts.append('48;2;%d;%d;%d' % rgb_of(bg))
    return '\x1b[' + ';'.join(parts) + 'm'


def ansi_row(row, colors):
    out, cur = '', None
    for ch, fg, bg, bold in row:
        key = (fg, bg, bold) if ch != ' ' or bg else (None, None, False)
        if key != cur:
            out += sgr(*key, colors) if key != (None, None, False) or cur else ''
            if key == (None, None, False) and cur and colors != 'none':
                out += '\x1b[0m'
            cur = key
        out += ch
    if cur and cur != (None, None, False) and colors != 'none':
        out += '\x1b[0m'
    return out.rstrip()


def html_row(row):
    out = ''
    for ch, fg, bg, bold in row:
        style = []
        if fg:
            style.append('color:#%02x%02x%02x' % rgb_of(fg))
        if bg:
            style.append('background:#%02x%02x%02x' % rgb_of(bg))
        if bold:
            style.append('font-weight:700')
        c = html.escape(ch)
        out += '<span style="%s">%s</span>' % (';'.join(style), c) if style else c
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--colors', choices=['true', '16', 'none'], default='true')
    ap.add_argument('--only', choices=list(MARKS))
    ap.add_argument('--product', default=None, help='CODE, OS, DROID ... (default: show CODE and OS)')
    ap.add_argument('--html', action='store_true', help='emit an HTML page instead of ANSI')
    a = ap.parse_args()

    names = [a.only] if a.only else list(MARKS)
    products = [a.product] if a.product else ['CODE', 'OS']
    blocks = []
    for name in names:
        fn, title = MARKS[name]
        for product in products if name not in ('working',) else products[:1]:
            blocks.append((name, title, product, fn(product)))

    if a.html:
        print('<!doctype html><meta charset="utf-8"><title>Aura terminal marks</title>'
              '<style>body{background:#0B0A10;color:#F2F1F5;font:14px/1.25 "DejaVu Sans Mono",monospace;padding:28px}'
              'h2{font:600 13px/1.4 system-ui,sans-serif;color:#8A94A6;margin:26px 0 8px;letter-spacing:.02em}'
              'pre{margin:0;font:inherit;line-height:1.0;letter-spacing:0}pre.text{line-height:1.25}</style>')
        for name, title, product, rows in blocks:
            cls = ' class="text"' if name in ('ascii', 'line', 'working') else ''
            print('<h2>%s · %s</h2><pre%s>%s</pre>' % (html.escape(name), html.escape(title + ' — ' + product), cls,
                                                   '\n'.join(html_row(r) for r in rows)))
        return
    for name, title, product, rows in blocks:
        head = '── %s · %s · %s ' % (name, title, product)
        print(('\x1b[2m%s\x1b[0m' % head) if a.colors != 'none' else head)
        print()
        for r in rows:
            print(ansi_row(r, a.colors))
        print()


if __name__ == '__main__':
    try:
        main()
    except BrokenPipeError:
        sys.exit(0)
