# TV Home: debloat + minimal home screen for the "ok. 2K TV" (Android TV 11)

TV details (from Settings → Info): model `ok 2K TV`, Android TV OS 11 (API 30),
build `RTM4.220307.154`, security patch 1 May 2022, kernel 4.19.116.

## What's here

| Path | What it does |
|---|---|
| `dist/tvhome.apk` | The home screen app (25 KB, no libraries, no network access, no tracking) |
| `launcher/` | Its source: plain Java + 2 PNGs |
| `scripts/00-connect.sh` | Connects over Wi-Fi ADB and takes a screenshot |
| `scripts/01-backup.sh` | **Read-only** snapshot: package states, settings, current home screen, screenshot |
| `scripts/02-inventory.sh` | Lists every enabled package as PROTECTED / CANDIDATE / undecided |
| `scripts/03-debloat.sh <list>` | Disables the listed packages (`pm disable-user`, never uninstalls) and logs each one |
| `scripts/04-install-launcher.sh` | Installs TV Home, pushes wallpapers, makes it the default home screen |
| `scripts/tune.sh` | Optional: halves animation durations (feels snappier) |
| `scripts/restore.sh` | **Undoes everything**, newest first, then re-checks against the backup |
| `scripts/fetch-wallpapers.py` | Downloads freely licensed nature photos (Wikimedia Commons Featured pictures) with credits |
| `debloat/protected.txt` | Never disabled (system, Play Services, Cast, voice search, mirroring, your apps) |
| `debloat/candidates.txt` | Known ad/telemetry/junk package names, used as a starting point only |
| `tools/` | Build without the Android SDK (`fetch-toolchain.sh`, then `build.sh`) |

## Safety model

* **Back up first.** `03` and `04` refuse to run until `01-backup.sh` has run.
* **Reversible only.** Apps are disabled for user 0, never uninstalled; their data stays on the TV.
  `restore.sh` re-enables them. A factory reset re-enables them too.
* **Logged.** Every change goes to `state/changes.tsv`. `restore.sh` replays it backwards, then
  re-enables anything disabled since the backup, even changes made by hand.
* **Protected.** On top of `protected.txt`, the scripts ask the TV which app performs HDMI switching,
  which packages provide TV inputs, and which keyboard is active, and refuse to disable those.
* **Checked with screenshots.** Every script that changes something saves a screenshot to
  `state/screenshots/`.
* The scripts were tested end to end against a simulated TV (fake `adb`): backup → debloat →
  install → tune → restore came back byte-identical to the original package and settings state.
  They have **not** run against the real TV yet.

## What has and hasn't been verified for the app

* Compiled against the real Android 11 API, dexed for minSdk 26, v2 signature verified,
  `resources.arsc` stored and 4-byte aligned (required for targetSdk 30).
* **Not yet run on Android**: no emulator or device was available where it was built.
  First install on the TV is the real test, which is why every step takes a screenshot.

## Run it (on a Mac/Linux computer on the same Wi-Fi as the TV)

1. On the TV: Settings → Device Preferences → Info → press "Build" 7× → Developer options →
   enable **USB debugging** (on Android TV this usually also opens network ADB on port 5555; if `00-connect.sh` cannot reach the TV, look for a separate "Network debugging" switch). Find the TV's IP under
   Settings → Network.
2. On the computer: install platform-tools (`brew install android-platform-tools` on macOS),
   then:
   ```bash
   cd tv-home
   export TV=192.168.x.y                      # the TV's IP
   scripts/00-connect.sh                      # accept the prompt on the TV, tick "always allow"
   scripts/01-backup.sh                       # read-only
   scripts/02-inventory.sh                    # review state/inventory.tsv together
   # put the approved packages in debloat/approved.txt, then:
   scripts/03-debloat.sh debloat/approved.txt --dry-run
   scripts/03-debloat.sh debloat/approved.txt
   python3 scripts/fetch-wallpapers.py        # ~24 photos into state/wallpapers
   scripts/04-install-launcher.sh
   ```
3. Use TV Home for a day **before** disabling the stock launcher
   (`echo com.google.android.tvlauncher > debloat/launcher.txt && scripts/03-debloat.sh debloat/launcher.txt`),
   then reboot and check (screenshot) that TV Home comes back after the reboot.

**Undo everything:** `TV=192.168.x.y scripts/restore.sh` (or `--keep-launcher` to keep TV Home).
Keep the `state/` folder: it holds the backup that restore checks against.

## Using TV Home

* Dock: YouTube, Netflix, Prime Video, Crunchyroll, Spotify, HDMI, then **All apps**.
  Apps that aren't installed are skipped. **Settings** is the second tile in *All apps*.
* **Long-press OK** (or the Menu key) on a tile: move left/right, remove from dock, app info.
  In *All apps*, long-press to add an app to the dock.
* HDMI: one input switches straight away; with several you get a list (marked "not connected" when unplugged).
* A new wallpaper every 3 minutes with a 1.5 s cross-fade; no animation runs in between, so the
  TV's GPU is idle while you browse. Add your own photos to
  `/sdcard/Android/data/app.claudelius.tvhome/files/wallpapers/` (JPG/PNG/WebP).

## Not handled by package disabling (needs a look in the TV menus)

* Google "Usage & diagnostics" and "Ads personalisation" toggles (Settings → Device Preferences / Google).
* Manufacturer viewing-data/ACR or "smart TV data" opt-ins, if this firmware has them.
* Ads **inside** apps (YouTube etc.) are not affected by any of this.

## Rebuilding

`tools/fetch-toolchain.sh && tools/build.sh` uses only Maven Central. The signing key is created
in `keystore/`, which is not committed. A build signed with a different key cannot update an
installed copy: run `adb uninstall app.claudelius.tvhome` first (this only resets the dock order).
