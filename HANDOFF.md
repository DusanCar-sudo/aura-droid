# Handoff: Aura Droid → Claude Code CLI (Linux)

For the Claude Code terminal CLI on your Linux machine. Read `BRAND.md` next; it holds the brand rules (§13 is the Android app).

## What was done

| Area | State |
|---|---|
| **Nature website** | Live at https://aura-droid.vercel.app/ (`index.html`, images in `modern/img/`). `/modern/` redirects to `/`. |
| **Spectrum (retro) website** | Live at https://aura-droid-site.vercel.app/ (`retro/`). |
| **Android app polish** | Compiles and passes unit tests on GitHub Actions (debug APK built, PR #3). **Not yet run on a phone** (see "First thing to do"). |
| **Brand guide** | `BRAND.md` |
| **Terminal marks** | `tools/ascii/aura-marks.py` (not yet in aura-code / aura-os) |

### The app polish (branch `ccr-451068f5-ocx21f`)

- **Splash.** `installSplashScreen()` shows the peak on forest, then `ui/splash/BrandSplash.kt` draws the landscape, peak, tagline, lighting stripes and the big connected **AURA DROID** wordmark from the site footer. About 1.5 s, once per launch, tap to skip, shortened if system animations are off. `MainActivity.kt` overlays it on the nav host.
- **Palette.** `ui/theme/Color.kt` is now the Nature palette as Material 3 roles (dark default, light too), every text pair at least 4.5:1. The old cyan and ruby constants are gone. The stripes are `StripeRed/Yellow/Green/Cyan`.
- **Type.** Geist and Geist Mono are bundled (`res/font/`, static instances of the site's fonts, OFL text in `third_party/`). `Type.kt` sets the tracking; all `FontFamily.Monospace` uses became `GeistMono`.
- **Icon.** `ic_launcher_foreground.xml` redrawn cream and gold on forest, plus a monochrome layer for Android 13 themed icons.
- **Logo.** `AuraLogo(bodyColor, coreColor)` replaces `cyanColor/rubyColor`; call sites use the theme's `onBackground` and `primary`.
- **Build.** New dependency `androidx.core:core-splashscreen:1.0.1`. Removed the machine-specific `org.gradle.java.home` from `gradle.properties` (use JDK 17+ via `JAVA_HOME`). Release signing reads the environment (below).
- **CI.** `.github/workflows/android.yml` runs unit tests and builds a debug APK on every push and PR (download it from the run's Artifacts), and a signed release APK when the signing secrets exist.

## First thing to do

The cloud sandbox this was written in blocks `dl.google.com`, so nothing could be compiled there. GitHub Actions did compile it: on PR #3 the unit tests pass and the debug APK builds (download it from the run's Artifacts, `aura-droid-debug`). It has not been run on a device. To build locally:

```sh
git clone https://github.com/DusanCar-sudo/aura-droid && cd aura-droid
export JAVA_HOME=/path/to/jdk17          # 17 to 21
export ANDROID_HOME=$HOME/Android/Sdk    # needs platforms;android-35 and build-tools
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then check on a real phone:

1. Cold start: system splash, then the brand splash, then chat. Try tapping during it and rotating during it.
2. Dark and light theme (Settings). Look at chat, sessions, settings, pairing and memos for any colour that looks wrong or unreadable.
3. Long text and the chat's code blocks in Geist Mono. Check nothing clips at font scale 1.3.
4. Launcher icon (round and square masks) and a themed icon on Android 13+.
5. Airplane mode: the splash must still leave after about 1.5 s.

Tuning knobs, all in `BrandSplash.kt`: durations (900 ms animation plus 650 ms hold), wordmark width `0.94f`, wordmark sink `0.10f`, tagline size.

## Signing (read before shipping the APK)

An APK only updates over an installed one if it has the **same signing key**. I could not tell which key signed `droid-0.3.0.apk`. If you still have that keystore, use it; if not, the new build must be uninstalled and reinstalled once.

Release signing takes four environment variables, so no key is ever in the repo:

```sh
export AURA_KEYSTORE=/path/to/aura.jks AURA_KEYSTORE_PASSWORD=... AURA_KEY_ALIAS=... AURA_KEY_PASSWORD=...
./gradlew assembleRelease            # app/build/outputs/apk/release/
```

For CI, add these repository secrets: `AURA_KEYSTORE_BASE64` (`base64 -w0 aura.jks`), `AURA_KEYSTORE_PASSWORD`, `AURA_KEY_ALIAS`, `AURA_KEY_PASSWORD`. Without a keystore, `assembleRelease` gives an unsigned APK that won't install; `assembleDebug` gives a debug-signed one that installs on a fresh device.

## After the APK exists: update the website

The site still offers `droid-0.3.0.apk` and labels the 0.4.0 features as "not in the download yet". When the 0.4.0 APK is built:

1. Copy it to the repo root as `droid-0.4.0.apk` (`.gitignore` allows only named APKs; add the new name) and remove the old one if you like.
2. In `index.html`: download links and `download="aura-droid-0.4.0.apk"`, the file size, the SHA-256 (`sha256sum`), the version line, and drop the "0.4.0 only" caveat; the facts row and the FAQ mention 0.3.0 too. Search for `0.3.0`.
3. In `retro/index.html`: the same.
4. **Retake the app screenshots.** `site-assets/0*.jpg` and `screenshots/` show the old cyan UI. Retake them on the new build (chat thread, sessions, memory, quick menu, settings dark) and keep the same file names so the pages pick them up.
5. Update `README.md` (screenshots, the "Roadmap" checkboxes) and `BRAND.md` §2's fact sheet.

## Other open items

1. **Ask Aura** still runs on `aura-code/site/` (`index.html`, `ados/index.html`, `aura-chat.js`, `aura-kb.js`). Remove it there (BRAND.md §11). The unused `aura-chat.js` and `aura-kb.js` in this repo's root can also go.
2. **Terminal marks** into `aura-code/src/cli/diamond.ts` (new `mini` and `ascii` tiers, `working` indicator) and `aura-os/bin/aura-os-logo` (`banner` → mini, new `fetch` → tile). See BRAND.md §4.2. Those repos are read-only from the cloud sandbox; do it locally.
3. **Move `BRAND.md` and `tools/`** into `aura-retro-design` or a new `aura-brand` repo (BRAND.md §12).
4. **Aura Mic** (`aura-mic`, the `dic` command) was cloned for reference but not touched; it could adopt the same marks.

## Setup and testing on Linux

```sh
python3 -m http.server 8765 &        # preview the sites: http://127.0.0.1:8765/ and /retro/
pip install numpy pillow fonttools brotli
tools/imagery/build.sh               # rebuild modern/img (about 90 s, output identical)
python3 tools/ascii/aura-marks.py    # preview terminal marks
```

Website tests used playwright-core with Chromium (screenshots, overflow at 10 widths from 320 to 1920 px, axe-core) and Lighthouse 12. Serve with gzip when measuring performance; Python's plain `http.server` doesn't compress.

The app fonts were made from the site's variable Geist: `fontTools.varLib.instancer.instantiateVariableFont(font, {"wght": 600})`, saved as TTF, in `res/font/geist_{regular,medium,semibold}.ttf` and `geist_mono_{regular,medium}.ttf`. They are Latin-only; other scripts fall back to the system font.

## Gotchas

- Vercel: `vercel.json` uses `routes`, not `rewrites`, for the retro host: rewrites run after the filesystem check and `index.html` exists. Two Vercel projects deploy this one repo (`aura-droid` and `aura-droid-site`); Netlify also builds previews.
- The 0.4.0 features (approvals control, sandbox-out, desktop hand-off) are not in the downloadable 0.3.0 APK; don't advertise them as available until the new APK ships.
- Res files must be lowercase `a-z0-9_`. Don't put a README inside `res/font` or another resource folder: it breaks the build.
- The `Text` composable from Material 3 overrides a `brush` in the style with the content colour; the splash uses `BasicText` for the gradient wordmark on purpose.
- Stock-photo hosts may be blocked in the cloud sandbox; imagery is generated or comes from `assets/`.
- Don't use the Higgsfield or Firecrawl connectors (credits) without asking. Don't copy imagery from reference sites or videos.
- `pkill -f` / `pgrep -f` can match their own shell command; use the `[h]ttp.server` pattern.
