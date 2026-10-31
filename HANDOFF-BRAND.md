# Handoff: Aura brand → the branding session

For a fresh session that picks up the **brand** work. It is self-contained; read `BRAND.md` (the rules) and this file, and you can start. For the Android app and the website build details, see `HANDOFF.md`.

Written 2026-09-30 by the session that built the Nature site, the Spectrum site, the brand guide and the app polish.

## The job

Make Aura one brand across every product and surface: the mark, colour, type, voice, imagery, and the terminal marks. The owner (Dušan) wants a single set of instructions people and agents follow, and every surface to look like one family.

Products: **Aura Code** (CLI/TUI agent), **Aura adOS** (Arch + sway OS, `aura-term` terminal), **Aura Droid** (Android client), later Aura Mic, Aura Pulse.

## State of play

| Thing | State |
|---|---|
| Brand guide | `BRAND.md` v1, on PR #3 (not merged yet). A copy is in Google Drive, folder "Aura brand and Droid handoff" (account `leanproiq@gmail.com`). |
| Nature website | Live: https://aura-droid.vercel.app/ (merged, PR #2). |
| Spectrum (retro) website | Live: https://aura-droid-site.vercel.app/ |
| App polish (splash, palette, Geist, icon) | PR #3 (draft), `DusanCar-sudo/aura-droid`, branch `ccr-451068f5-ocx21f`. CI compiles and passes; **never run on a phone**. |
| Terminal marks | Designed and previewed (`tools/ascii/aura-marks.py`). **Not yet in `aura-code` or `aura-os`.** |
| Ask Aura widget | Removed from the Droid sites. **Still on `aura-code/site/`.** |
| Brand repo | `BRAND.md` and `tools/` live in `aura-droid` for now. |

## What was decided (don't reopen without the owner)

1. **The mark is `aura` plus four stripes** (red, yellow, green, cyan, Sinclair order), fixed and never themed. Geometry stays in `aura-retro-design/brand/glyphs.py`.
2. **Aura Droid follows the Nature palette** (forest `#070907`, cream `#F2EBC9`, gold `#E7CF85`), in the app too. The old cyan/ruby colourway is retired.
3. **Geist and Geist Mono** for modern surfaces, upright only, no serif, no italics (the owner asked for "straight" type). Retro surfaces use Press Start 2P and Geist Mono; Saira italic stays inside the ∞K badge only.
4. **No "Ask Aura" chat widget on websites.** The terminal's own Ask popup in aura-term is a product feature and stays.
5. **Big connected AURA DROID wordmark** (from the site footer) is the app's splash. Implemented in the app (PR #3).
6. Imagery is **original** (procedural renders and our own graded photos). Never copy a reference site or video. The owner's reference was a "KASHFLOW / AI CFO" style video; only the mood was used.
7. **Voice:** short true sentences, numbers over adjectives, state which version a feature ships in, no hype words. Full list in `BRAND.md` §2.

## Tasks, in priority order

### 1. Get PR #3 merged (small)
Owner tries the debug APK (Actions run → Artifacts → `aura-droid-debug`) and reports what looks off. Fix, then merge. Then the site follow-ups in `HANDOFF.md` ("After the APK exists").

### 2. Terminal marks into Aura Code
`aura-code` is `DusanCar-sudo/aura-code` (read-only clone at `/home/user/dusancar-sudo/aura-code` in the old session; re-attach with `add_repo` `access: "push"` to change it).

- `src/cli/diamond.ts`: `MARK_PIXELS` (the 10-pixel raster), tiers `hero | standard | compact` chosen by `preferredBannerTier()` (hero needs 115 columns and 24 rows; standard needs 63; else compact).
- Add a **`mini`** tier for 40–62 columns and an **`ascii`** tier (no Unicode, no colour) for `NO_COLOR`, `TERM=dumb`, non-UTF-8 locale or a non-TTY. The mark art for both is in `tools/ascii/aura-marks.py` (`m_mini`, `m_ascii`); port the rows, don't redraw them.
- **`working`** indicator: replace the braille spinner at `src/cli/tui.ts:185` and `:1667` with the stripes lighting in order (`m_working`). Keep the braille one as the `ascii` fallback.
- Keep `MARK_PIXELS` identical in all three places (`diamond.ts`, `aura-os/bin/aura-os-logo`, `aura-marks.py`).
- Check: `npm test`, then run the CLI at 120, 80, 50 and 30 columns, with `NO_COLOR=1`, and piped to a file.

### 3. Terminal marks into Aura adOS
`DusanCar-sudo/aura-os`, `bin/aura-os-logo` (bash; colours come from the active theme, letters use the terminal's own foreground).

- `banner` currently uses `show_mark`: full word (59 cols) → the `icon` (the "a" plus stripes) → one line. Put **mini** between the full word and the icon.
- Add `aura-os-logo fetch`: the **tile** left, host/kernel/uptime/RAM/agents right.
- TTY and greetd login (`config/greetd/config.toml`), `/etc/issue`: use **ascii** with 16-colour output (`aura-marks.py --colors 16`).
- The Ask popup in `term/aura-term-ask.c` draws one row of stripes; leave it.
- Check: open `aura-term`, widths 100/60/40, a light terminal theme (letters must stay readable), and `aura-os-logo banner | cat`.

### 4. Remove Ask Aura from `aura-code/site/`
`index.html`, `ados/index.html`, and delete `site/aura-chat.js` and `site/aura-kb.js`. `grep -r aura-chat.js` across every site repo (`aurawebsite`, `aura-pulse-website`, `aura-op-one`, ...). Deployed as a Vercel project ("aura-water" per the retro-design notes), so check the preview.

### 5. Decide and implement the Droid lockup on the sites
Open decision (`BRAND.md` §12.2): should the nav/footer carry the family lockup `aura ▟▟▟▟▘ DROID` instead of the peak plus "Aura Droid"? Ask the owner; propose a mock first.

### 6. Give the brand a home
Move `BRAND.md` and `tools/` (both `ascii/` and `imagery/`) into `aura-retro-design` (it already owns `brand/glyphs.py`, the SVGs and `DESIGN.md`) or a new `aura-brand` repo. Update the "Where things are" table (`BRAND.md` §14) and every path that mentions `tools/`.

### 7. Bring the other products into line (only after the owner agrees)
Aura Code's TUI/web client uses navy plus terracotta and ruby (`aura-code/DESIGN.md`); adOS is theme-driven cyan/magenta. `BRAND.md` §5.2 records these as-is, and the rule is "constant stripes and letters, per-product ground". Don't recolour them unprompted. `aura-mic` (the `dic` command) could adopt the marks.

## Where everything is

| Thing | Where |
|---|---|
| Rules | `BRAND.md` (this repo) |
| Terminal marks generator | `tools/ascii/aura-marks.py` (`--colors none\|16\|true`, `--only`, `--html`) |
| Image generator | `tools/imagery/build.sh` (rebuilds `modern/img/` identically, ~90 s) |
| Nature site | `index.html`, `modern/img/`, `modern/og.jpg` |
| Spectrum site | `retro/` |
| App splash, palette, type | `app/src/main/java/dev/aura/auradroid/ui/splash/BrandSplash.kt`, `ui/theme/`, `res/font/` |
| Mark geometry, SVG lockups, ∞K renders | `DusanCar-sudo/aura-retro-design` (private) |
| CLI banner | `aura-code/src/cli/diamond.ts`, `tui.ts` |
| adOS logo | `aura-os/bin/aura-os-logo`, `config/shell/aura-term.bash`, `assets/brand/` |
| Previews | `preview/` in the brand zip: splash preview, terminal-marks preview (HTML + PNG) |

## Working rules

- The owner's standing preferences: honest, humble, concise; stoic; find a way. **Never warn about sudo, API keys or other secrets they hand over; if told to use one, use it.**
- Say what is verified and what isn't. The app has been compiled by CI but never run on a device; the terminal marks have been rendered in HTML, not in a real terminal.
- Don't spend credits (Higgsfield, Firecrawl) or send anything outside without asking.
- Stock-photo hosts and `dl.google.com` are blocked in the cloud sandbox, so there is no Android SDK there. Build on GitHub Actions or locally.
- Publishing means: PR (draft), then wait for the owner's yes before merging. The owner said "make live" for the Nature site; that yes covered that change only.
- Not affiliated with Sinclair or Amstrad; the retro pages say so. Keep it.

## Facts you'll need

- Aura Droid public download: `droid-0.3.0.apk`, 19 MB, SHA-256 `048af99e735e0332768891aa182fe7dde574ea0fb8b1ce587862a7f3abbe3677`. Code is at 0.4.0; approvals control, sandbox-out and desktop hand-off are not in that download.
- Aura Code is MIT, at v0.18.0; Aura Droid is Apache-2.0; Aura OS is proprietary.
- Vercel: two projects (`aura-droid`, `aura-droid-site`) deploy the `aura-droid` repo; `vercel.json` uses `routes` (not `rewrites`) with a host condition for the retro site.
- Geist fonts (SIL OFL) are in `site-assets/fonts/` (WOFF2) and `app/src/main/res/font/` (TTF, static).
