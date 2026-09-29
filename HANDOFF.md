# Handoff: Aura Droid sites → Claude Code CLI (Linux)

For the Claude Code terminal CLI on your Linux machine. Read `BRAND.md` next; it holds the brand rules.

## State (2026-09-29)

| Site | URL | Source | Status |
|---|---|---|---|
| **Nature edition** (modern) | https://aura-droid.vercel.app/ | `index.html`, `modern/img/`, `modern/og.jpg` | **Live** (promoted from `/modern/`; `/modern/` is a redirect stub) |
| **Spectrum edition** (retro) | https://aura-droid-site.vercel.app/ | `retro/` | Live |
| Android app | (APK) `droid-0.3.0.apk` | `app/` | 0.4.0 in code, 0.3.0 in the download |

Repo: `DusanCar-sudo/aura-droid`, branch `main`. Two Vercel projects deploy this one repo:

- `aura-droid` serves the repo root.
- `aura-droid-site` serves `/` from `retro/index.html`, through a `routes` entry in `vercel.json` with a `has: host` condition. Use `routes`, not `rewrites`: rewrites run after the filesystem check, and `index.html` exists.

Netlify also builds previews (`deploy-preview-N--aura-droid.netlify.app`, public); Vercel previews sit behind SSO.

## Done

- Retro (Spectrum) edition: live, clear screenshots by default, spinning tetrahedron logo.
- Nature edition: Geist upright, no italics, forest/cream/gold, landscape imagery, 5-step app tour, carousel, interactive approvals panel, download with SHA-256.
- "Ask Aura" widget removed from all three pages (`aura-chat.js`/`aura-kb.js` files are still in the repo root, unused; delete them if you want).
- Lighthouse on the Nature page, mobile with gzip: performance 99–100, accessibility 100, best practices 100. axe: 0 violations at 390 and 1440 px.
- `BRAND.md`: the brand guide. `tools/ascii/aura-marks.py`: terminal marks. `tools/imagery/`: regenerates every image (verified identical).

## Open / next

1. Ask Aura still lives in `aura-code/site/` (`index.html`, `ados/index.html`, `aura-chat.js`, `aura-kb.js`). Remove it there (BRAND.md §11).
2. Decide the terminal marks (BRAND.md §4.2, §12) and add them to `aura-code/src/cli/diamond.ts` (new `mini`/`ascii` tiers, `working` indicator) and `aura-os/bin/aura-os-logo` (`banner` → mini, new `fetch` → tile). Those repos were only read here, not changed.
3. Public download is 0.3.0; 0.4.0 features are labelled on the page. Ship the 0.4.0 APK, then update `droid-0.3.0.apk` references, the SHA-256 and the "0.4.0" note.
4. Decide the app palette and Geist in the app (BRAND.md §12).
5. Move `BRAND.md` and `tools/` into `aura-retro-design` or a new `aura-brand` repo.

## Setup on Linux

```sh
git clone https://github.com/DusanCar-sudo/aura-droid && cd aura-droid
python3 -m http.server 8765 &        # preview at http://127.0.0.1:8765/ and /retro/
pip install numpy pillow             # only for tools/
tools/imagery/build.sh               # rebuild modern/img (about 90 s)
python3 tools/ascii/aura-marks.py    # preview terminal marks
```

Tests used here: playwright-core with Chromium (screenshots, overflow at 10 widths 320–1920, axe-core), and Lighthouse 12 with `--preset=desktop` for desktop. Serve with gzip when measuring performance; Python's plain `http.server` doesn't compress and understates the score.

## Gotchas

- Rewrites vs routes on Vercel: see above.
- The 0.4.0 features (approvals control, sandbox-out, desktop hand-off) are not in the downloadable APK; don't advertise them as available.
- Stock-photo hosts may be blocked in the cloud sandbox; the imagery is generated or comes from `assets/`.
- Don't use the Higgsfield or Firecrawl connectors (credits) without asking.
- `pkill -f` / `pgrep -f` can match their own shell command; use the `[h]ttp.server` pattern.
- Lazy images show as "broken" in naive checkers when offscreen; check HTTP status instead.
- Don't copy imagery from reference sites or videos.
