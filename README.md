# Weather Wallpaper for Projectivy

A wallpaper provider plugin that turns your Projectivy Launcher home screen into
a weather display. Current conditions, optional forecast panels, and backgrounds
that react to the weather.

Weather data from [Open-Meteo](https://open-meteo.com/). No API key, no account,
no tracking.

<!-- Add 2-3 screenshots here. Use demo mode and an illustrated scene or pack;
     the radar background reveals your location even with the marker hidden. -->

## Install

**Easiest:** download the APK from the [latest release][releases], side-load it,
then in Projectivy go to **Settings → Appearance → Wallpaper → Launcher
wallpaper → Plugins → Weather Wallpaper**.

No computer? Install **Downloader** by AFTVnews on the TV and type the release
APK's URL.

**Or build it yourself** — recommended, and it takes about two minutes with no
local toolchain. Fork the repo, go to **Actions → Build APK → Run workflow**,
and download the artifact when it finishes. The APK in releases is debug-signed,
so building it yourself means not having to trust a stranger's binary.

On first run the plugin estimates your location from your IP so it works
immediately. Correct it in settings via the gear icon beside the plugin name.

## What it shows

Always: temperature, conditions, feels-like, today's high and low, and wind with
direction.

Optional, each toggled independently in settings:

- **Hourly** — next 6 hours with temperature, icon and rain chance
- **Daily** — next 5 days with highs and lows
- **Extra stats** — humidity, UV index, dew point, visibility, pressure
- **Sun times** — sunrise, sunset, daylight remaining

## Backgrounds

| Source | Needs | Notes |
|---|---|---|
| Illustrated scenes | nothing | Drawn in code, matched to conditions. Default. |
| Live radar | nothing | [RainViewer](https://www.rainviewer.com/) precipitation tiles |
| Your photos | files on the TV | Drop images in the plugin's folder, named by condition |
| Community packs | nothing | Contributed images and animations, downloaded on demand |
| Plain gradient | nothing | If you want it quiet |

### Radar and basemaps

Radar draws precipitation over a plain dark backdrop by default. No map ships
with the plugin, deliberately: OpenStreetMap's [tile usage policy][osm-policy]
forbids distributing an app that pulls from their community-funded servers, and
blocked requests come back as an error tile rather than failing cleanly.

If you want geography behind the radar, supply your own tile URL in settings
under **Radar basemap tile URL**, using `{z}`, `{x}` and `{y}` placeholders:

```
https://tiles.example.com/dark/{z}/{x}/{y}.png?key=YOUR_KEY
```

Several providers offer free tiers that permit app use. Whichever you pick, put
their required credit in **Basemap credit line** so it renders on screen.

The illustrated scenes change with both conditions and time of day: star fields
and a crescent moon at night, cloud banks when overcast, rain streaks, falling
snow, lightning in storms. Randomness is seeded per day, so a scene holds steady
across refreshes but differs tomorrow.

## Contribute a wallpaper pack

**No code required.** A pack is images (or a Lottie animation) plus one entry in
`packs/index.json`. Open a pull request, CI validates it, and it goes live
without an app release.

Animated Lottie packs keep the weather readout — the panel is injected into your
animation as an image layer, leaving your timing and easing untouched.

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for the format, canvas safe zones,
size limits, and licensing rules.

## Demo mode

Hides your location for screenshots: replaces the location name and removes the
radar marker. The forecast still uses your real coordinates; only what's drawn
changes.

If you've configured a basemap, radar can't be fully anonymised this way: the
map itself shows your area whether or not the marker is drawn. With no basemap
the risk is smaller, though the shape of a storm front is still a hint. For
anything you post publicly, the illustrated scenes or a pack are safest, and
consider turning off sun times, which narrow down your coordinates.

## How refresh works

Three limits stack:

| Layer | Value | Controls |
|---|---|---|
| `itemsCacheDurationMillis` | 15 min | How long the launcher reuses the last response |
| `MIN_FETCH_INTERVAL_MS` | 10 min | Floor on actual Open-Meteo calls |
| Pack index cache | 24 hours | How often the pack list is re-checked |

Expect the reading to update roughly every 15 minutes. Projectivy's own wallpaper
rotation interval doesn't apply, since the plugin returns a single wallpaper with
nothing to cycle through. There's a **Refresh now** action in settings.

A failed fetch reuses the last good reading rather than blanking the screen.

## Troubleshooting

**Plugin isn't in the wallpaper list.** Force-stop Projectivy and reopen; it
caches the plugin list at startup.

**Background is black or won't change.** Usually a URI permission problem:

```bash
adb logcat | grep -iE "projengmenu|SecurityException|OpenMeteoClient|PackManager"
```

**Panel is cut off at the screen edges.** TV overscan. Raise `MARGIN` in
`WeatherRenderer.kt`.

**Pack list is empty.** Hit **Refresh pack list**. If it still fails, check that
`INDEX_URL` in `PackManager.kt` points at this repo.

**Radar shows tiles reading "not supported".** Your basemap provider is
rejecting the requests. If the URL points at `tile.openstreetmap.org`, that is
expected and won't be fixable — their policy doesn't permit app use. Clear the
field to fall back to the plain backdrop, or switch providers.

## Built with AI assistance

Most of the Kotlin in this repo was written by Claude, with me directing,
testing and debugging on real hardware. I'm not an Android developer.

Practical implications: I can read and explain the code, but I'd be slow on a
deep bug. It's tested on one device. Build from source if you'd rather not trust
a debug-signed APK from a stranger. Issues are welcome and I'll do what I can.

## Credits and licence

Built on [spocky's wallpaper provider template][template] (Apache 2.0), for
[Projectivy Launcher][projectivy].

Weather by [Open-Meteo](https://open-meteo.com/), radar by
[RainViewer](https://www.rainviewer.com/), optional stock photos via
[Unsplash](https://unsplash.com/). All credited on screen when in use. Any
basemap you configure is yours to source and credit.

Licensed under Apache 2.0. See [LICENSE](LICENSE).

[releases]: ../../releases
[template]: https://github.com/spocky/projectivy-plugin-wallpaper-provider
[projectivy]: https://projectivylauncher.com/
[osm-policy]: https://operations.osmfoundation.org/policies/tiles/
