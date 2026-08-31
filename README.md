# Projectivy Weather Wallpaper

A weather panel for Projectivy Launcher, built as a wallpaper provider plugin —
the launcher's only public extension point. Weather data from Open-Meteo: no API
key, no account.

This is a complete, ready-to-build Gradle project. You don't need Android
Studio, a JDK, or adb on your machine.

## Build the APK

1. Create a new repository on GitHub (private is fine).
2. Upload the contents of this folder — **Add file → Upload files**, then drag
   everything in. Keep the folder structure.
3. Go to the **Actions** tab. If prompted, click **I understand my workflows,
   enable them**.
4. **Build APK** in the left sidebar → **Run workflow** → **Run workflow**.
5. Wait ~4 minutes. Open the finished run, and download the
   `weather-wallpaper-debug` artifact from the Artifacts section at the bottom.
6. Unzip it. Inside is `weather-debug.apk`.

To get a permanent download URL instead, create a release tagged `v1.0`; the
workflow attaches the APK to it automatically. Useful for step 2 below.

The APK is debug-signed on purpose — side-loading needs no release keystore, and
it keeps the workflow secret-free so it runs on a fresh repo with no setup.

## Install it on the TV

**No computer needed.** Install **Downloader** by AFTVnews from the Play Store
on the TV and type the URL of your release APK. Allow installs from unknown
sources when prompted. A URL shortener saves a lot of remote-typing.

**Or adb:** `adb connect <TV_IP>:5555 && adb install -r weather-debug.apk`
(enable ADB debugging under Settings → System → Developer options first).

**Or USB:** copy the APK to a stick and open it with any file manager.

## Turn it on

1. **Projectivy settings** — long-press home, or the gear icon on the home screen
2. **Appearance → Wallpaper → Launcher wallpaper**
3. Scroll to **Plugins** → **Weather Wallpaper**

That's it. On first run the plugin derives an approximate location from your
public IP, so the panel appears without configuring anything.

To correct the location or switch to Celsius, highlight the plugin and press the
**gear icon** beside its name. Anything set there overrides the IP guess
permanently. Coordinates come from latlong.net or a Google Maps URL.

Typing signed decimals with a remote is unpleasant, so settings also accept
intent extras:

```bash
adb shell am start -n tv.projectivy.plugin.wallpaperprovider.weather/.SettingsActivity \
  --es latitude "41.157" --es longitude "-73.862" \
  --es placeLabel "Home" --ez useMetric false --ez close true
```

## What's already configured

| Thing | Value |
|---|---|
| `appId` | `tv.projectivy.plugin.wallpaperprovider.weather` |
| `plugin_uuid` | Pre-generated, unique to this project |
| `updateMode` | `1` (TIME_ELAPSED) |
| `itemsCacheDurationMillis` | 900000 (15 min) |

Nothing needs editing before the first build.

## How refresh behaves

Three limits stack. `itemsCacheDurationMillis` (15 min) governs how long the
launcher reuses our last response; `MIN_FETCH_INTERVAL_MS` (10 min) caps actual
Open-Meteo calls; Projectivy's own rotation interval is irrelevant here because
we return a single wallpaper with nothing to cycle through. Expect updates about
every 15 minutes. There's a **Refresh now** action in settings for immediate.

On a failed fetch the service reuses the last good reading rather than blanking
the screen.

## Project layout

| Path | Role |
|---|---|
| `weather/…/WallpaperProviderService.kt` | AIDL stub; renders and returns a `content://` URI |
| `weather/…/WeatherRenderer.kt` | Canvas compositing, 1920×1080 PNG into `cacheDir` |
| `weather/…/OpenMeteoClient.kt` | HTTP + WMO weather-code mapping |
| `weather/…/IpLocationClient.kt` | One-time approximate location |
| `weather/…/PreferencesManager.kt` | Settings, JSON export/import |
| `weather/…/Settings{Activity,Fragment}.kt` | Leanback settings screens |
| `api/` | spocky's AIDL contract — **do not modify** |
| `.github/workflows/build.yml` | Cloud build |

## Two things that will bite you if you modify it

**The URI handoff.** The launcher reads the image from another process, so a
bare `file://` path fails on anything modern. Hence the FileProvider, the
explicit `grantUriPermission()` to `com.spocky.projengmenu`, and the `<queries>`
manifest entry.

**`updateMode`.** `CARD_FOCUSED` (4) fires roughly once a second during home
screen navigation. Subscribing would re-render a bitmap that often.

## Troubleshooting

**Build fails.** Open the failed Actions run and read the red step. Compile
errors name a file and line.

**Plugin missing from the wallpaper list.** Force-stop Projectivy and reopen —
it caches the plugin list at startup.

**Black or unchanged wallpaper.** Almost always the URI grant:

```bash
adb logcat | grep -iE "projengmenu|SecurityException|OpenMeteoClient|IpLocation"
```

**Panel cut off at the edges.** TV overscan — raise `MARGIN` in
`WeatherRenderer.kt`.

## Privacy

First run calls ipapi.co, which sees the device's public IP. Set
`AUTO_LOCATE_ENABLED = false` in `IpLocationClient.kt` to disable it and require
manual setup. Weather requests go to Open-Meteo with coordinates only.

## Licensing

Built on spocky's `projectivy-plugin-wallpaper-provider` template, Apache 2.0 —
see `LICENSE`. Open-Meteo's free tier is non-commercial and asks for
attribution, which the `Wallpaper` object's `author` and `source` fields carry.
