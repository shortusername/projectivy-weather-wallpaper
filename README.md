# Weather Wallpaper for Projectivy

A wallpaper provider plugin that turns your Projectivy Launcher home screen into
a weather display. Current conditions, optional forecast panels, and backgrounds
that react to the weather.

Weather data from [Open-Meteo](https://open-meteo.com/). No API key, no account,
no tracking.

<img width="3840" height="2160" alt="Radar background with the bundled vector map" src="https://github.com/user-attachments/assets/c788bfaa-eedd-430e-b7a3-2fc857fb0340" />

<img width="3840" height="2160" alt="Illustrated scene background" src="https://github.com/user-attachments/assets/4f1b5a87-19e7-4c43-9a21-94cb62f94e74" />

## Install

**Easiest — no computer needed.** Install **Downloader** by AFTVnews on the TV,
then enter code **6704396**. That pulls the latest release directly and will
keep pointing at future versions too.

**Or side-load the APK** from the [latest release][releases] by USB or adb.

**Or build it yourself** — recommended, and it takes about two minutes with no
local toolchain. Fork the repo, go to **Actions → Build APK → Run workflow**, and
download the artifact. The released APK is debug-signed, so building it yourself
means not having to trust a stranger's binary.

Then in Projectivy: **Settings → Appearance → Wallpaper → Launcher wallpaper →
Plugins → Weather Wallpaper**.

On first run the plugin estimates your location from your IP, so it works
immediately. Correct it in settings via the gear icon beside the plugin name —
locations are added by name, not coordinates.

## What it shows

Always: temperature, conditions, feels-like, today's high and low, and wind with
direction.

Each of these can be toggled independently:

| Panel | What it adds |
|---|---|
| **Rain and snow timing** | "Rain starting in about 25 min", from 15-minute data. On by default. |
| **Helpful advice** | "Frost likely tonight", "dry until 4 PM", "good drying day". On by default. |
| **Hourly** | Next 6 hours with temperature, icon and rain chance |
| **Daily** | Next 5 days with highs and lows |
| **Extra stats** | Humidity, UV index, dew point, visibility, pressure |
| **Sun times** | Sunrise, sunset, daylight remaining |
| **Air quality** | AQI in the stats line, a warning line when it's poor. Pollen where available. |
| **Aurora watch** | A line when the K-index makes aurora plausible at your latitude |
| **Compare to yesterday** | "4° cooler than yesterday" |
| **Severe weather alerts** | Active US National Weather Service warnings, as a banner |
| **Marine conditions** | Wave height and period, where there's a large enough body of water nearby (ocean or a Great Lake) |
| **Clock** | Optional, with position, size, style and format options |

Readout size is adjustable from 80% to 120% if the default is too small or too
large for your room.

## Backgrounds

| Source | Needs | Notes |
|---|---|---|
| Illustrated scenes | nothing | Drawn in code, matched to conditions. Default. |
| Live radar | nothing | [RainViewer](https://www.rainviewer.com/) over a built-in vector map |
| Your photos or videos | files on the TV | Drop them in the plugin's folder, named by condition |
| Community packs | nothing | Contributed images and videos, downloaded on demand |
| Plain gradient | nothing | If you want it quiet |

Everything follows sunrise and sunset automatically, with a twilight window
either side — four phases rather than a day/night flip. Optional holiday themes
colour-grade the whole scene around eight occasions a year.

**World weather watch** will periodically show a notable weather event elsewhere
in the world instead of your local conditions, sourced from GDACS. Off by
default, and laid out so it can't be mistaken for local weather.

### Radar and basemaps

Radar draws precipitation over a map of coastlines, borders, state lines, lakes
and about 31,000 place names, all **drawn from vector data bundled in the APK**.
No tile server, no API key, works offline. The data is [Natural Earth][ne] 1:50m
and [GeoNames][geonames], both freely licensed.

This replaces an earlier version that pulled tiles from OpenStreetMap, which
their [tile usage policy][osm-policy] does not permit for a distributed app.

There's a **colour-blind friendly** option that recolours radar onto a
blue-to-yellow ramp. Standard radar palettes run green to red, where heavy rain
becomes indistinguishable from drizzle for the roughly eight percent of men with
deuteranopia.

If you'd rather have a full raster basemap, supply your own tile URL under
**Custom basemap tile URL**, using `{z}`, `{x}` and `{y}` placeholders:

```
https://tiles.example.com/dark/{z}/{x}/{y}.png?key=YOUR_KEY
```

Put the provider's required credit in **Basemap credit line**. Leave the URL
blank to use the built-in map.

## Fitting your layout

The plugin can't see where Projectivy draws its app row — the plugin API exposes
no layout information at all. So **Where your app row starts** is a setting, from
70% to 88% of screen height, and the forecast strips respect it.

When the launcher reports itself idle nothing is covering the wallpaper, so the
forecast can use the whole frame. That's on by default.

## Contribute a wallpaper pack

**No code required.** A pack is images or a video plus one entry in
`packs/index.json`. Open a pull request, CI validates the format and licensing,
and it goes live without an app release.

There's a layered **PSD template** in `templates/` with every safe zone marked,
plus PNG guide overlays for other editors.

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for the format, safe zones, size
limits, video encoding, and licensing rules.

## Experimental features

The two animation options sit behind an **Experimental features** toggle, off by
default: **Animate radar** and **Animate rain and snow**.

They were originally built with Lottie, which turned out not to work here.
Testing on an Nvidia Shield showed the launcher renders Lottie shape layers
correctly but never displays embedded image assets — and radar is raster by
nature, so the animation was simply blank. Both now encode a short H.264 video
on the device instead, which the launcher plays through a media player. That
keeps the radar, the vector map, the panel and the alert banner all intact, and
it's confirmed working end to end.

Encoding happens in the background rather than blocking the wallpaper request,
so turning this on may show a still frame for the first refresh while the
initial video builds. After that it updates on its own. Everything still falls
back to a still wallpaper if anything goes wrong.

They stay behind the toggle because on-device video encoding is meaningfully
heavier than drawing a still image, and I'd rather that stay something you opt
into than the default.

**They may stop your screensaver from starting while active — confirmed, not
hypothetical.** Video playback commonly signals the system to stay awake while
it plays, which is normal and desirable for someone watching an actual video,
and exactly the wrong behaviour for a background wallpaper loop. That signal
comes from whatever plays the video back, not from this plugin, so it isn't
something the plugin can control or override. If your screensaver matters to
you, leave these off, or turn them off before you'd normally step away.

This almost certainly applies to **any** video wallpaper, not just these two —
including community video packs, which use the identical mechanism. Worth
knowing before turning either on for a device where the screensaver matters.

Animated wallpaper packs are no longer gated — they work, but like video packs
they can't show the weather readout, since the launcher renders them itself.

### Known failure on some budget hardware

On at least one low-power projector, animated radar and animated rain/snow
reliably go black a few seconds into playback, every time, on every fresh
loop. A logcat capture traced this to the device's own hardware decoder
(tag `OMX.MS.AVC.Decoder`, part of the MStar chipset family common in budget
Android TV boxes and cheap projectors) refusing a routine output-buffer
renegotiation:

```
D/MS_OMX_VDEC: Notify : OMX_EventPortSettingsChanged
W/ACodec: setting nBufferCountActual to 11 failed: -22
W/ACodec: setting nBufferCountActual to 10 failed: -22
E/MM_CP_ERR_OMX_VIDEO_API: [MsVdecCloseLock] blackScreenInfo.bEnable = 1
```

That last line is the vendor's own driver deliberately blanking the screen
after giving up — not a crash, an intentional response. Two different
buffer counts were rejected with the identical error, which reads as a flat
refusal of the renegotiation rather than a specific count being too high —
the signature of a vendor firmware limitation, not something tunable from
the encoder side. Several encoder settings were tried (H.264 profile,
level, keyframe interval) without resolving it.

If you hit a black screen that matches this pattern — plays a few seconds,
blanks, repeats — grabbing a similar logcat is the fastest way to confirm
whether you're looking at the same thing:

```bash
adb logcat -v time | grep -iE "MediaCodec|OMX|Codec2|WeatherWallpaper"
```

If you see the same `blackScreenInfo` or an identical buffer-count
rejection, this plugin has no visibility into playback once it hands over
the video URI, and no way to detect or work around a decoder failing on the
launcher's side. The practical fix is to leave both animation options off
on that specific device; everything else in the plugin is unaffected.

## Demo mode

Hides your location for screenshots: replaces the location name and removes the
radar marker. The forecast still uses your real coordinates; only what's drawn
changes.

Radar can't be fully anonymised this way — the map still shows your area. For
anything you post publicly, the illustrated scenes or a pack are safest, and
consider turning off sun times, which narrow down your coordinates.

## How refresh works

| Layer | Value | Controls |
|---|---|---|
| `itemsCacheDurationMillis` | 15 min | How long the launcher reuses the last response |
| `MIN_FETCH_INTERVAL_MS` | 10 min | Floor on actual Open-Meteo calls |
| Background cache | 9 min | Reuses the composed background between renders |
| Pack index cache | 24 hours | How often the pack list is re-checked |

Expect the reading to update roughly every 15 minutes. Projectivy's own wallpaper
rotation interval doesn't apply, since the plugin returns a single wallpaper with
nothing to cycle through. There's a **Refresh now** action in settings.

A failed fetch reuses the last good reading rather than blanking the screen.

**With the clock enabled** the plugin asks for a re-render every minute, since a
clock that's fifteen minutes stale is worse than no clock. The background is
cached so this doesn't refetch radar tiles, but it is more work — and it may
interfere with your screensaver starting.

## Keeping it updated

**Check for updates** in settings looks at this repo's GitHub releases directly
— no store, no companion app. It only ever tells you what it finds; nothing
downloads or installs without you choosing to.

It also checks on its own schedule: **weekly by default**, or fortnightly,
monthly, or never — your choice, in settings. A small notice appears next to the
on-screen credits when something newer is available, and clears itself once
you've updated.

**The one-time catch:** Android treats "let this app install other apps" as a
special permission granted in system settings, not a popup you can accept from
inside the app. If Check for updates finds something newer but nothing happens
when you try to install it, the plugin needs that permission first. On a Shield
that's **Settings → Device Preferences → Security & restrictions → Unknown
sources**, then enable it for Weather Wallpaper. Grant it once and updates
install normally after that.

## Troubleshooting

There's a **Create debug report** action in settings. It writes a redacted
diagnostic file — coordinates rounded, location name and API keys stripped —
which makes bug reports far easier to act on.

**Plugin isn't in the wallpaper list.** Force-stop Projectivy and reopen; it
caches the plugin list at startup.

**Background is black or won't change.**

```bash
adb logcat | grep -iE "projengmenu|SecurityException|OpenMeteoClient|PackManager"
```

**Panel is cut off at the screen edges.** TV overscan. Raise `MARGIN` in
`WeatherRenderer.kt`.

**Forecast strips don't appear.** They're dropped rather than overlapped when
there's no room. Try a smaller readout size, fewer optional panels, or adjust
where your app row starts.

**Pack list is empty.** Hit **Refresh pack list**. If it still fails, check that
`INDEX_URL` in `PackManager.kt` points at this repo.

**Radar shows tiles reading "not supported".** You've set a custom basemap URL
and that provider is rejecting the requests. If it points at
`tile.openstreetmap.org`, that's expected and not fixable — their policy doesn't
permit app use. Clear the field to fall back to the built-in map.

**Found an update but it won't install.** See [Keeping it updated](#keeping-it-updated)
above — the plugin needs permission to install apps, granted once in system
settings.

## Built with AI assistance

Most of the Kotlin in this repo was written by Claude, with me directing, testing
and debugging on real hardware. I'm not an Android developer.

Practical implications: I can read and explain the code, but I'd be slow on a
deep bug. It's tested on a projector and an Nvidia Shield — no idea how it
behaves on a Fire Stick, a Chromecast, or anything else. Build from source if
you'd rather not trust a debug-signed APK from a stranger. Issues are welcome and
I'll do what I can, no promises on turnaround.

## Credits and licence

Built on [spocky's wallpaper provider template][template] (Apache 2.0), for
[Projectivy Launcher][projectivy].

Weather and geocoding by [Open-Meteo](https://open-meteo.com/), radar by
[RainViewer](https://www.rainviewer.com/), map geometry from
[Natural Earth](https://www.naturalearthdata.com/) (public domain), place names
from [GeoNames][geonames] (CC BY), severe weather alerts and the K-index from
[NOAA](https://www.weather.gov/), world events from
[GDACS](https://www.gdacs.org/), optional stock photos via
[Unsplash](https://unsplash.com/). All credited on screen when in use. Any custom
basemap you configure is yours to source and credit.

Licensed under Apache 2.0. See [LICENSE](LICENSE).

[releases]: ../../releases
[template]: https://github.com/spocky/projectivy-plugin-wallpaper-provider
[projectivy]: https://projectivylauncher.com/
[osm-policy]: https://operations.osmfoundation.org/policies/tiles/
[ne]: https://www.naturalearthdata.com/
[geonames]: https://www.geonames.org/
