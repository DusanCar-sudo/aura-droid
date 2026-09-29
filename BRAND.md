# Aura — brand guide

One brand for the Aura family: Aura Code, Aura adOS, Aura Droid, and whatever
ships next. This file is the unified instruction set. Read it before you
design, write copy, draw a mark or build a page for any Aura product, whether
you are a person or an agent.

Version 1 · 2026-09-29. It builds on the existing identity in
`aura-retro-design/DESIGN.md` and `brand/glyphs.py`, which stay the source of
truth for the mark's geometry.

---

## 0. The rules, short

1. **One mark: `aura` plus four stripes.** The stripes are red, yellow, green
   and cyan in that order, leaning right. They are fixed and never themed or
   recoloured. The product name sits under the stripes: CODE, adOS, DROID.
2. **The ground changes; the stripes don't.** Each product has its own
   background palette (see §5). Only the stripes and the paper-white letters
   travel between products.
3. **Light is the accent.** Each surface has exactly one warm or bright
   accent: gold, glow, terracotta or bright yellow. Use it for the one thing
   that matters on screen: the primary action, focus, or the path.
4. **Upright type only.** Use Geist and Geist Mono. No serif, no italics on
   the modern surfaces. Emphasis comes from colour, not slant.
5. **Short, true sentences.** Use numbers instead of adjectives and say what
   isn't done yet. No hype words (list in §2).
6. **Real product, real pictures.** Screenshots are real and unedited.
   Illustrations are original: our own renders or our own photos, graded to
   the palette. Never lift imagery from a reference.
7. **No chat widgets on websites.** The "Ask Aura" bubble is retired
   everywhere (§11). A page explains itself; the product is the thing to
   talk to.
8. **Clarity before style.** Every effect has a plain fallback: reduced
   motion, no colour, no Unicode, a 320 px phone.
9. **Accessible by default.** Meet WCAG 2.2 AA. axe must report 0
   violations and Lighthouse accessibility must score 100 before shipping.
10. **Fast by default.** Self-host the fonts, lazy-load everything below the
    fold, and load heavy media only on a click. Mobile Lighthouse performance
    must be at least 90, measured on the host with compression on.

---

## 1. What Aura is

Aura is a family of products built by Dušan Milosavljević around one agent,
Aura, who acts on your behalf and shows her work.

| Product | One line | Where |
|---|---|---|
| **Aura Code ∞K** | The coding agent that remembers. Open source and model-agnostic; it keeps lessons, not chat history, on your machine. | `aura-code` · CLI and TUI, web client · leanproiq.com |
| **Aura adOS** | The agent-driven operating system: Arch Linux and sway, under 350 MB idle. Aura installs, themes, fixes and backs it up, and takes a snapshot first. | `aura-os`, `aura-os-shell` · aura-term |
| **Aura Droid** | Your coding agent, in your pocket: the Android client for Aura Code. Paired with your PC, or on the phone alone. | `aura-droid` · aura-droid.vercel.app |
| Aura Mic, Aura Pulse, … | Later products join under the same rules. | own repos |

**The brand idea: you ask, Aura does it, and she shows her work.** It rests on
three product truths:

- **She acts.** The Aura Code tagline is *praktess · she who acts and
  executes*.
- **She verifies.** The CLI motto is *"I don't try. I verify."*
- **She remembers.** ∞K means unlimited memory; she keeps lessons.

You stay in control throughout: approvals, snapshots before changes, and the
last step always on screen.

**Personality:**
- calm, capable and exact
- honest about limits
- quietly warm
- playful only in the retro editions

**Persona:**
- Aura is "she" wherever she speaks: the CLI, the OS, and the app's own
  voice.
- Marketing pages use her name more than the pronoun.
- Product names are never "she": Aura Droid is "it" or "the app".

---

## 2. Voice and copy

**Headlines have two beats.** The first half is plain and the second half
carries the accent colour, never italics:

- "Your coding agent, *in your pocket.*"
- "Built for the thumb, *not the desk.*"
- "Small things, *done properly.*"

**Sentences:**
- Short and declarative.
- Name concrete things: "19 MB", "Android 8+", "`aura serve --lan`".
- Use verbs for what people do: ask, review, approve, pair, run, keep.

**Honesty:**
- State the version a feature ships in: "0.4.0 only".
- Say when the download is behind the code.
- Never imply an affiliation we don't have, e.g. "not affiliated with
  Sinclair / Amstrad" on the retro pages.

**Avoid these words:**
- revolutionary, supercharge, unleash, seamless, game-changing, magic
- "AI-powered" as an adjective
- exclamation marks
- emoji in product copy

**Case:**
- Sentence case for headings, buttons and navigation.
- UPPER CASE only in the wordmark lockups (CODE, DROID) and the retro
  editions.

**Names:**

| Right | Wrong |
|---|---|
| Aura Code, Aura adOS, Aura Droid | AuraCode, Aura droid, AURA Droid in running text |
| Aura (the agent) | the AI, the bot |
| Coder, Gazelle, Architect (modes, capitalised) | coder mode, gazelle |
| Paired · On the phone alone (Droid runtimes) | client mode, offline mode |
| Nature edition · Spectrum edition (sites) | v2 site, 8-bit site |

**Calls to action:**
- Primary: "Download the APK"
- Secondary: "See the app", "View source ↗"

### Aura Droid fact sheet (single source for copy)

| Fact | Value |
|---|---|
| Public download | `droid-0.3.0.apk`, 19 MB, SHA-256 `048af99e735e0332768891aa182fe7dde574ea0fb8b1ce587862a7f3abbe3677` |
| Code version | `versionName 0.4.0`. Approvals control, sandbox-out and desktop hand-off are 0.4.0 features and are **not in the download yet**. |
| Requirements | Android 8.0+ (API 26) |
| License | Apache-2.0 (Aura Code itself is MIT) |
| Modes | Coder, Gazelle, Architect |
| Runtimes | Paired with Aura Code on your PC (`aura serve --lan` over Wi-Fi), or on the phone alone |

---

## 3. Marks

### 3.1 The master mark: `aura` plus the stripes

- **Letters:** a 3-unit stroke on a 10-unit x-height, geometric, lowercase.
- **Stripes:** four parallelograms, each 2.4 units wide, leaning 5 over 10,
  in Sinclair order: red `#E4312B`, yellow `#F8B91E`, green `#2FAE4E`,
  cyan `#1AA6E0`.
- **Colour:** letters are Paper `#F2F1F5` on dark grounds and Ink `#0B0A10`
  on light grounds.
- **Geometry:** `aura-retro-design/brand/glyphs.py`.
- **Files:** `brand/aura-lockup-*.svg`, `aura-mark-transparent{,-ink}.svg`
  and `aura-icon.svg`, all generated by `brand/make_svg.py`. Copies live in
  `aura-code/assets/brand/` and `aura-os/assets/brand/`.

### 3.2 Product lockups

The master mark with the product name set small under the stripes,
right-aligned to their foot, as in the SVG lockups:

```
aura ▟▟▟▟▘        aura ▟▟▟▟▘        aura ▟▟▟▟▘
        CODE              adOS             DROID
```

In one line: `aura ▟▟▟▟▘ CODE`.

### 3.3 Product symbols (app icons, favicons, the smallest sizes)

Each product may have one symbol for places the lockup can't fit. All three
are an **A as a peak**: the letter of the name and the mountain of the Nature
imagery.

- **Aura Droid:** the rounded peak. One path drawn three times: a halo at
  24% opacity (stroke 19), a body (stroke 10.5) and a core (stroke 4.5).
  - One colourway everywhere, web and app: cream `#F2EBC9` body and a gold
    `#C8AD5C` core on forest `#070907`. On light grounds the body is ink.
    (The old cyan and ruby colourway is retired.)
  - Source: `site-assets/favicon.svg`, `ui/theme/AuraLogo.kt` and the
    launcher icon `res/drawable/ic_launcher_foreground.xml`.
- **Aura adOS:** the striped A with the sun behind it (`aura-os-logo`, the
  boot splash). The sun follows the theme accent.
- **Aura Code:** the `a` plus stripes icon (`aura-icon.svg`).

### 3.4 Using the marks

- **Clear space:** keep a gap of at least the x-height all round.
- **Minimum size:**
  - lockup: 96 px wide on screen
  - symbol: 16 px, or a one-line terminal mark
- **Don'ts:**
  - Don't recolour, reorder, un-slant, outline or gradient the stripes.
  - Don't set the wordmark in a font; it is drawn.
  - Don't put the mark on a busy part of an image without a glass panel
    behind it.
  - Don't rotate it. The one exception is the retro edition's spinning
    wireframe tetrahedron, which is the peak in 3D.

---

## 4. Terminal marks: Aura Code CLI and Aura adOS terminal

All of these are generated by one tool, so every surface draws the same
pixels:

```sh
python3 tools/ascii/aura-marks.py                 # every mark, true colour
python3 tools/ascii/aura-marks.py --colors 16     # as a Linux console / greetd sees it
python3 tools/ascii/aura-marks.py --colors none   # NO_COLOR, pipes, CI logs
python3 tools/ascii/aura-marks.py --only mini --product DROID
python3 tools/ascii/aura-marks.py --html > marks.html
```

The 10-pixel raster (`MARK_PIXELS`) is copied in three places:
- `aura-code/src/cli/diamond.ts`
- `aura-os/bin/aura-os-logo`
- `tools/ascii/aura-marks.py`

Change all three together, or none.

### 4.1 The set (plain-text previews; colour in the tool)

**lockup:** 5 rows × 59 columns. This is the current hero; keep it.

```
 ▄▄███████  ███    ███  ███████▄▄  ▄▄███████       ████████
▄██▀▀▀▀███  ███    ███  ████▀▀▀▀▀ ▄██▀▀▀▀███      ████████
███    ███  ███    ███  ███       ███    ███     ████████
▀██▄▄▄▄███  ▀██▄▄▄▄███  ███       ▀██▄▄▄▄███    ████████
 ▀▀███████   ▀▀███████  ███        ▀▀███████   ████████
                                                C  O  D  E
```

**mini (new):** 5 rows × 33 columns. The same letters in quadrant blocks, at
half the width.

```
▗▟███ █▌ ▐█ ███▙▖▗▟███      ▟▟▟▟▘
▟▛▀▜█ █▌ ▐█ ██▀▀▘▟▛▀▜█     ▟▟▟▟▘
█▌ ▐█ █▌ ▐█ █▌   █▌ ▐█    ▟▟▟▟▘
▜▙▄▟█ ▜▙▄▟█ █▌   ▜▙▄▟█   ▟▟▟▟▘
▝▜███ ▝▜███ █▌   ▝▜███  ▟▟▟▟▘  CODE
```

**line:** one row. This is the current compact mark; keep it.

```
aura ▟▟▟▟▘ CODE
```

**ascii (new):** 3 rows of 7-bit ASCII. It works with no Unicode and no
colour.

```
  __ _ _  _ _ _ __ _     ////
 / _` | || | '_/ _` |   ////
 \__,_|\_,_|_| \__,_|  ////   code
```

**tile (new):** 8 rows × 14 columns, for fetch-style screens.

```
     ▟█▟█▟█▟█▘
    ▟█▟█▟█▟█▘
   ▟█▟█▟█▟█▘
  ▟█▟█▟█▟█▘
 ▟█▟█▟█▟█▘
▟█▟█▟█▟█▘

 aura os
```

**working (new):** the stripes light up in order, red, yellow, green, cyan,
about 120 ms per frame. Unlit stripes are faint `#4A5568`. It replaces a
generic spinner.

### 4.2 Which mark where (recommendation)

**Aura Code CLI:**

| Where | Mark | Rule |
|---|---|---|
| Launch banner, ≥ 115 columns | lockup + session card beside it | today's `hero` tier |
| Launch banner, 63–114 columns | lockup + card under it | today's `standard` tier |
| **Launch banner, 40–62 columns** | **mini** + card under it | new tier: split panes and phones over SSH stop falling back to one line |
| TUI pinned header, < 40 columns | line | today's `compact` |
| `NO_COLOR`, `TERM=dumb`, non-UTF-8 locale, not a TTY | **ascii** (stripes plain `////`) | new: CI logs and Windows conhost show a mark, not mojibake |
| While the agent works | **working** | new: the brand is the progress indicator |

**Aura adOS terminal (aura-term):**

| Where | Mark | Rule |
|---|---|---|
| Banner atop each aura-term window (`aura-os-logo banner`) | **mini** + who/what text beside it | 5 rows, same letters as the CLI |
| Fetch or system-info screen (a new `aura-os-logo fetch`) | **tile** left, facts right (host, kernel, uptime, RAM, agents) | new |
| TTY, greetd login, `/etc/issue`, serial | **ascii** with `--colors 16` | 16-colour safe |
| Window titles, waybar, status lines | line | as today |

**Detecting the terminal:**
- Use the ascii mark when `NO_COLOR` is set, `TERM` is `dumb`, the locale
  isn't UTF-8, or stdout isn't a TTY.
- Otherwise use true colour when `COLORTERM` is `truecolor` or `24bit`, and
  the 16 bright ANSI colours when it isn't.
- Letters use the terminal's own foreground (`39`) wherever a light theme is
  possible.

---

## 5. Colour

### 5.1 Constants (every product)

| Token | Hex | Use |
|---|---|---|
| Ink | `#0B0A10` | the master mark's dark ground, letters on light |
| Paper | `#F2F1F5` | letters and text on dark |
| Stripe red | `#E4312B` | stripe 1 (brighter tint on dark: `#F0584F`) |
| Stripe yellow | `#F8B91E` | stripe 2 |
| Stripe green | `#2FAE4E` | stripe 3 (brighter tint on dark: `#3CC45E`) |
| Stripe cyan | `#1AA6E0` | stripe 4 |

### 5.2 Grounds (per product and edition)

| Surface | Ground | Text | Accent (the light) | Source |
|---|---|---|---|---|
| Aura Code TUI and web client | navy `#0F1724`, panel `#1C2739` | `#E8E6E3`, dim `#8A94A6` | terracotta `#CC785C` (chrome), ruby `#9B1B30` (accents) | `aura-code/DESIGN.md`, `diamond.ts` |
| Aura adOS | theme-driven: accent default `#00E5D0`, magenta `#FF6AC7` | `#D9FFFC` | the theme accent | `aura-os` themes, waybar `theme.css` |
| Aura Droid app | the Nature palette, as Material 3 roles: forest `#070907` / `#0C100C`, moss `#141A14` / `#1A211A` (light: cream `#F2EBC9`) | cream `#F2EBC9` (light: ink `#10150F`) | gold `#E7CF85` (light: olive `#535C37`) | `app/.../ui/theme/Color.kt` |
| Droid web, Nature edition | forest `#070907`, `#0C100C`, moss cards `#141A14` / `#1A211A` | cream `#F2EBC9`, `#E2DBB7`, dim `#B9BAA2`, mute `#8B8E7A` | gold `#E7CF85`, straw `#D8CB95` | `modern/index.html` `:root` |
| Droid web, Nature cream sections | cream `#F2EBC9` | ink `#10150F`, `#3B4232`, olive `#535C37` | ink knob on cream | same |
| Droid web, Spectrum edition | black, border blue `#0000D7` | `#F2F2F2`, `#BDBDBD` | bright yellow `#FFFF00` | `retro/index.html` `:root` (the 15 ZX Spectrum colours) |

### 5.3 Contrast (measured)

| Pair | Ratio | Allowed use |
|---|---|---|
| cream `#F2EBC9` on forest `#070907` | 16.7 | all text |
| gold `#E7CF85` on forest | 13.0 | all text |
| dim `#B9BAA2` on forest | 10.1 | body |
| mute `#8B8E7A` on forest | 5.9 | small print, AA |
| ink `#10150F` on cream | 15.4 | all text |
| olive `#535C37` on cream | 5.9 | small print, AA |
| Paper on Ink (master) | 18+ | all text |
| cyan `#6ED0EA` on app ink | 10.7 | all text |
| ruby `#D24B30` on app ink | **4.3** | large text and graphics only, never small text |

---

## 6. Type

**Modern surfaces (the default):**
- **Geist** 400–700 for everything, and **Geist Mono** 400–500 for facts,
  labels, counters and code.
- Self-hosted WOFF2 (`site-assets/fonts/`), SIL OFL.
- Upright only.

| Role | Size / line-height | Weight | Tracking |
|---|---|---|---|
| H1 | `clamp(46px, 7.4vw, 112px)` / 0.98 | 500 | −0.055em |
| H2 | `clamp(36px, 4.6vw, 64px)` / 1.04 | 500 | −0.045em |
| H3 | 24–44px / 1.06–1.1 | 500 | −0.035 to −0.045em |
| Body | 17px / 1.6 | 400 | 0 |
| Button | 15px | 600 | −0.01em |
| Label (mono) | 12–13px | 400–500 | +0.02 to +0.1em |
| Wordmark (footer) | `clamp(64px, 16.2vw, 250px)` | 600 | −0.07em |

The rule: large type runs tight and small mono runs open.

**Retro surfaces:**
- **Press Start 2P** for pixel headings and labels only, never body text.
- Body text in Geist Mono.
- The ∞K identity's *Saira ExtraBold Italic* stays inside the ∞K badge and
  the renders. It is the one sanctioned italic, and it does not leave the
  retro edition.

**Terminal:**
- The marks use only block elements U+2580–259F. Every modern mono font has
  them.
- Text uses the user's terminal font.

**Android app:**
- Geist and Geist Mono are bundled (`res/font/`, static 400/500/600 instances
  of the site's fonts; licence in `third_party/Geist-OFL.txt`).
- Body and titles use Geist, tracked like the web (tight on large sizes,
  none on body). The small label styles and all code, terminal and
  monospace text use Geist Mono.

---

## 7. Imagery

### 7.1 Nature ("landscapes of light"): the modern editions

The mood:
- late light, mist, ridges and a cloud sea
- a path of light winding up a valley toward a pass under the sun

The path is Aura doing the climb while you choose each step. No people, no
cities, and no tech clichés: circuit boards, robots, glowing brains, code
rain.

**Two sources, both original:**

1. **Procedural light-point landscapes** (`tools/imagery/terrain.py`).
   - Mountains drawn as fields of dots, with a glowing path, clouds, god rays
     and bloom.
   - Presets: `hero`, `tall`, `peak`, `dusk`, `cream`.
2. **Our own photos, graded** (`tools/imagery/photos.py`).
   - Sources: `assets/mist.jpg` and `assets/landscape.jpg`.
   - The grade is a tritone:
     - shadows `(0.018, 0.03, 0.02)`
     - mids `(0.29, 0.35, 0.19)`
     - highlights `(1.0, 0.90, 0.63)`
   - Keep 40% of the original colour, warmed: R ×1.03, B ×0.80, saturation
     0.75.
   - Finish with contrast 1.2 and a vignette at 0.38.

`tools/imagery/build.sh` rebuilds every file in `modern/img/`
byte-for-byte: 19 WebP files, about 1.9 MB.

- **Exports:**
  - hero: 1600w at q80 and 2400w at q78
  - phone hero: 1080×1800 at q78
  - cards: 600×800 at q80
  - wide cards: 1120×560 at q80
- **Loading:** lazy-load everything below the fold.
- **Keep the phone hero at 1080 wide.** Lighthouse's "properly size images"
  flag on it is a false positive: the tall `object-fit: cover` frame
  upscales a narrower file.

### 7.2 Product truth

- Real, unedited app screenshots (`site-assets/0*-*.jpg`) in a phone frame.
- Never mock up UI that doesn't exist.
- The demo video (26 MB) loads only when Play is pressed.

### 7.3 Retro

- **ZX Spectrum conversion:** 8×8 attribute cells, 2 colours per cell and
  Bayer dithering, on a toggle. Clear screenshots are the default.
- **∞K renders:** the machine, the CRT and the horizon, made in Blender in
  `aura-retro-design`.

---

## 8. Layout, components, motion (web)

**Frame:**
- Max width 1240 px; gutter `clamp(18px, 4.5vw, 56px)`.
- Rounded inset frames: radius 26 px, inset `clamp(8px, 1vw, 14px)`.

**Cards:**
- Radius 22 px.
- Hairlines in cream at 11% and 22% alpha.

**Buttons:**
- Pills, at least 54 px tall.
- **Primary:** cream fill, with the ink "knob" circle holding an arrow.
- **Secondary:** glass (blur 12 px), with a cream knob.
- **Tertiary:** ghost outline.

**Glass:**
- Forest at 52% alpha with a 10–18 px blur.
- Only over imagery, never over flat colour.

**Section chips:** "01 · The app", "06 · Download". Numbers run in page
order.

**Big numbers:** 48–72 px Geist for facts ("19 MB"), with a mono caption
under each.

**Motion:** slow and ambient.
- The hero "breathes": a 38 s scale from 1.02 to 1.09.
- Reveals are a 0.9 s fade with an 18 px rise.
- The retro spinner steps every 80 ms.
- `prefers-reduced-motion` stops all of it.
- No bounce, no parallax gimmicks, no scroll-jacking.

**Focus:** a visible outline in the light colour, gold `#E7CF85` at 2 px.
Never remove it.

---

## 9. Quality gates (every page, every push)

- No horizontal scroll at 320, 360, 390, 600, 768, 820, 1024, 1280, 1440
  and 1920 px.
- axe-core reports 0 violations at 390 and 1440 px, and Lighthouse
  accessibility scores 100.
- Lighthouse on the host, or locally with gzip:
  - mobile performance at least 90
  - best practices 100
  - SEO 100 (staging pages carry `noindex` on purpose)
- No console errors, and every image resolves.
- Interactions work by keyboard: tour, carousel, switches, copy buttons,
  FAQ.
- Reduced motion is honoured.

---

## 10. Editions

| Edition | What | Where |
|---|---|---|
| **Nature** (modern, default) | forest, cream and gold; landscapes of light; Geist | aura-droid.vercel.app (staged at `/modern/` until approved) |
| **Spectrum** (retro) | an 8-colour ZX Spectrum homage, clarity first, spinning tetrahedron | aura-droid-site.vercel.app (live) |
| **∞K** (retro, Aura Code) | the machine Aura would have shipped on in 1982 | leanproiq.com, built from `aura-retro-design` |

The same content and facts appear in every edition; only the skin changes.
Every edition links to the others in its footer.

---

## 11. Websites: what is always true

- **No chat widget.** The "Ask Aura" bubble (`aura-chat.js` plus
  `aura-kb.js`) is retired on every site.
  - The Aura Droid pages (root, `/modern/`, `/retro/`) no longer load it.
  - Still to remove: `aura-code/site/index.html` and
    `aura-code/site/ados/index.html`, then delete `site/aura-chat.js` and
    `site/aura-kb.js` there.
  - `grep -r aura-chat.js` every other site repo.
  - The terminal's Ask popup in aura-term is a product feature, not a
    website widget; this rule doesn't cover it.
- Fonts are self-hosted. There are no third-party scripts, trackers or
  cookie banners.
- Staging pages carry `noindex` and a canonical URL. Promotion removes the
  `noindex`.
- Every page has an OG image at 1200×630, with the headline set in Geist on
  the edition's imagery.

---

## 12. Open decisions (owner)

1. ~~Aura Droid app palette.~~ Decided: the app follows Nature (§13).
2. **The Droid lockup on the sites.** The Nature and Spectrum sites use the
   peak symbol and a text "Aura Droid". Should the nav or footer carry the
   family lockup `aura ▟▟▟▟▘ DROID`?
3. ~~Geist in the app.~~ Decided: bundled (§6, §13).
4. **Terminal marks.** Which of the new marks (mini, ascii, tile, working)
   ship in aura-code and aura-os? §4.2 has the recommendation.
5. **Where this guide lives.** Move it and `tools/` into
   `aura-retro-design`, or a new `aura-brand` repo, once approved.

---

## 13. The Android app

The app follows the same brand as the sites; only the medium changes.

- **Launch:** the system splash shows the peak on forest, then the brand
  splash takes over (`ui/splash/BrandSplash.kt`): the light-point landscape,
  the peak, "Your coding agent, *in your pocket.*", the four stripes lighting
  in order, and the big connected **AURA DROID** wordmark from the site
  footer (Geist 600, −0.07em, the cream-to-olive gradient), sized to 94% of
  the screen width and sunk slightly below the bottom edge.
  - It runs about 1.5 s, once per launch, and a tap skips it.
  - With system animations off it appears drawn and leaves in 0.5 s.
  - Never add a loading delay to show it; it covers real start-up only.
- **Colour:** Nature as Material 3 roles (§5.2). Every role pair is at least
  4.5:1 in dark and light. Gold is the one accent per screen. The stripes
  never appear as UI colour, only in the splash and the mark.
- **Type:** Geist and Geist Mono (§6). Use the theme's text styles; don't set
  a font family by hand except `GeistMono` for code.
- **Icon:** the peak, cream with a gold core on forest; a monochrome layer is
  provided for Android 13+ themed icons.
- **Accessibility:** touch targets at least 48 dp, a `contentDescription` on
  every icon that acts alone (decorative icons beside text use `null`),
  system font scale respected, no colour-only state.
- **No third-party SDKs, analytics or ads.** The permissions in the manifest
  are the whole list.
- **Signing:** releases are signed with the owner's key, supplied through the
  environment (`HANDOFF.md`). The key is never in the repo.

---

## 14. Where things are

| Thing | Path |
|---|---|
| Mark geometry, SVGs, ∞K renders | `aura-retro-design/brand/` (`glyphs.py`, `make_svg.py`), `renders/` |
| Terminal marks | `tools/ascii/aura-marks.py` (this repo) · `aura-code/src/cli/diamond.ts` · `aura-os/bin/aura-os-logo` |
| Nature imagery | `tools/imagery/` (this repo): `terrain.py`, `photos.py`, `build.sh` |
| Nature site | `modern/index.html`, `modern/img/`, `modern/og.jpg` |
| Spectrum site | `retro/` |
| Droid symbol | `site-assets/favicon.svg`, `app/src/main/java/dev/aura/auradroid/ui/theme/AuraLogo.kt`, `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| Android splash, palette, type | `app/src/main/java/dev/aura/auradroid/ui/splash/`, `ui/theme/`, `app/src/main/res/font/`, `res/drawable-nodpi/splash_bg.webp` |
| Aura Code UI tokens | `aura-code/DESIGN.md` |
