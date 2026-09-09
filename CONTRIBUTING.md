# Contributing a wallpaper pack

You don't need to write any code. A pack is a set of images (or animations) plus
one entry in `packs/index.json`. Open a pull request and CI checks the rest.

## How packs work

The plugin downloads `packs/index.json`, the user picks a pack in settings, and
assets are fetched on demand and cached on the device. New packs go live as soon
as your PR merges. No app release, no waiting on me.

## 1. Pick a kind

| Kind | Format | Weather readout | Status |
|---|---|---|---|
| `static` | JPG, PNG or WebP | Yes, drawn over your image | Working |
| `video` | MP4 (H.264) | **No** | Working |
| `lottie` | Lottie JSON | In principle yes | **Unproven — see below** |

**Please submit `static` or `video` for now.** Lottie support is written but has
not been shown to work on real hardware.

The intent is that Lottie keeps the readout: the plugin injects the weather
panel into your animation as an image layer before handing it to the launcher,
leaving your timing and easing untouched.

**In practice this is unverified.** The plugin's own animated radar, which uses
the same mechanism, renders a blank screen on an Nvidia Shield. The suspected
cause is that the launcher can't load a Lottie from the `content://` URI a
plugin is able to offer — still images work fine that way, but Lottie is
usually loaded by a different code path that doesn't accept it. The reference
plugin only ever demonstrates Lottie from an `android.resource://` URI, and its
example file contains no image layers at all.

Until that's settled, animated packs are behind the **Experimental features**
toggle in plugin settings, and selecting one without it enabled falls back to
the still wallpaper. If you'd like to help settle it, a pack plus a report of
what you see would be genuinely useful.

Video can't carry the readout: the launcher decodes the file directly and there's
no point at which the plugin can draw on it. Users are warned when they select
one.

## 2. Make the assets

**Canvas: 1920x1080.** Other sizes work but get centre-cropped. Lottie files
should declare `w` and `h`; the panel is scaled to match.

**Start from the template.** `templates/pack-template-1920x1080.psd` is a
layered file with every safe zone marked. Opens in Photoshop, Affinity Photo,
GIMP, Krita and Photopea.

- Put your art on the **YOUR ARTWORK** layer at the bottom
- Toggle **PREVIEW - plugin scrim** on to see roughly how much the plugin
  darkens the top-left, so you can check your art still reads through it
- Hide or delete the **GUIDE** layers before exporting
- **GUIDE - thirds** is off by default if you want composition guides

Not using a layered editor? `templates/pack-guides-overlay-1920x1080.png` is the
same guides as a single transparent PNG — drop it over your artwork, check the
fit, delete it. `pack-scrim-preview-1920x1080.png` is the scrim on its own.

**Respect the safe zones.** The launcher and the plugin both draw over your art:

```
┌────────────────────────────────────────────┐
│ WEATHER PANEL                    clock ▸   │  ← top-left text, top-right clock
│ 71°F                                       │
│ Overcast                        ← keep     │
│ Feels like 75° · H 78° L 67°      focal    │
│ ┌────────┐ ┌────────┐             point    │
│ │ hourly │ │ daily  │             here     │
│ └────────┘ └────────┘                      │
│                                            │
│  [app] [app] [app] [app] [app]             │  ← app row, bottom ~20%
└────────────────────────────────────────────┘
```

- Left 55%, top 60%: covered by text. Keep it quiet.
- Bottom 20%: app shelf. Nothing important here.
- Top right: launcher clock and status.
- **Right 40%, middle band:** this is where your art actually reads. Put the
  focal point here.

A scrim is drawn over the top-left regardless, so text stays legible on bright
art. Don't pre-darken your images for this; you'll end up with mud.

**Keep files small.** Under 800 KB per still, under 2 MB per Lottie, under
8 MB per video. These download over home wifi to a TV box with a modest heap.

## 2b. If you're submitting video

Video packs work, and they're the one animated format on solid ground — media
players handle the plugin's URIs natively, which isn't reliably true of Lottie.

**The trade:** the launcher plays your file directly, so nothing can be drawn
over it. A video pack shows no temperature, no conditions, no severe weather
banner. Users are warned when they select one. Make sure your video is worth
that, or submit stills instead.

**Encoding.** This recipe produces something every Android TV device can play:

```bash
ffmpeg -i input.mov \
  -c:v libx264 -profile:v main -level 4.0 -pix_fmt yuv420p \
  -vf "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080" \
  -r 30 -b:v 4M -maxrate 5M -bufsize 8M \
  -g 30 -movflags +faststart -an \
  output.mp4
```

Each part matters:

- `libx264` with `main` profile — H.264 is the only codec decoded in hardware on
  every TV box. HEVC, AV1 and VP9 all fail somewhere.
- `yuv420p` — some decoders reject other pixel formats outright
- `-an` — strip audio. It's dead weight in a wallpaper and some launchers will
  happily play it.
- `+faststart` — puts the metadata at the front so playback starts without
  seeking to the end of the file
- `-g 30` — a keyframe every second, so looping doesn't stutter

**Length: 6 to 15 seconds.** Long enough not to feel repetitive, short enough to
stay under 8 MB at a watchable bitrate.

**Make it loop seamlessly.** The last frame should flow into the first. Cross-fade
the ends, or pick footage where it doesn't matter — drifting clouds, falling
snow, rain on glass. A visible cut every ten seconds is worse than a still
image.

**Keep motion slow and peripheral.** This sits behind a home screen someone is
navigating. Fast pans and hard cuts are actively unpleasant at ten feet.

CI checks the container brand, the codec, whether metadata is at the front, and
whether you left an audio track in. It reports what it finds either way.

## 3. Name assets by condition

Keys are resolved most specific first, falling back down this chain:

```
clear-dusk  →  clear-night  →  clear  →  dusk  →  night  →  default
```

So a pack needs only `default` to be valid. Available keys:

- Buckets: `clear`, `cloud`, `rain`, `snow`, `storm`
- With time: `clear-day`, `clear-night`, `cloud-dawn`, `storm-dusk`, and so on
- Time only: `day`, `night`, `dawn`, `dusk`
- Fallback: `default`

`dawn` and `dusk` cover the 40 minutes either side of sunrise and sunset. They
are optional: a pack that only supplies `day` and `night` keeps working exactly
as before, because twilight falls back to whichever of those applies.

`cloud` covers fog. `rain` covers drizzle and showers. `storm` covers
thunderstorms and hail.

## 4. Add your assets and index entry

Put files in `packs/<your-pack-id>/`, then add an entry to `packs/index.json`:

```json
{
  "id": "misty-mountains",
  "name": "Misty Mountains",
  "author": "Your Name",
  "license": "CC0-1.0",
  "kind": "static",
  "assets": {
    "clear-day": "https://cdn.jsdelivr.net/gh/OWNER/REPO@main/packs/misty-mountains/clear-day.jpg",
    "default": "https://cdn.jsdelivr.net/gh/OWNER/REPO@main/packs/misty-mountains/default.jpg"
  }
}
```

Use `cdn.jsdelivr.net/gh/...` URLs rather than `raw.githubusercontent.com`. It's
CDN-backed and won't rate-limit when several thousand TVs check in at once.

You can host assets on your own domain instead; the URL just has to be HTTPS and
publicly reachable without authentication.

## 5. Licensing — please read this one

**Only submit work you have the right to license.** Your index entry must
declare one of: `CC0-1.0`, `CC-BY-4.0`, `CC-BY-SA-4.0`, `Apache-2.0`, `MIT`.

By opening the PR you're asserting that you either created the assets or have
permission, and that you're licensing them as declared. Your name and the licence
are displayed on screen when your pack is active.

Things that will get a PR closed:

- Images scraped from Google, Pinterest, or a wallpaper site
- Stock photos under a licence that forbids redistribution
- Screenshots from films, games, or TV
- Recognisable people who haven't consented
- AI-generated images where the generator's terms don't permit redistribution
  (check yours; several don't)

"I found it online and it didn't say anything" is not a licence. If you're
unsure about a specific image, ask in the PR before spending time on the rest of
the pack.

## 6. Open the PR

CI validates automatically:

- `index.json` against `packs/schema.json`
- Unique pack ids
- Every asset URL returns HTTP 200
- Declared licence is on the allowed list
- File sizes within limits

Green checks mean the format is right. I'll still look at the art and the
licensing claim before merging.

## Testing before you submit

Point the plugin at your own fork by editing `INDEX_URL` in `PackManager.kt`,
building, and selecting your pack. Or drop assets straight into
`Android/data/tv.projectivy.plugin.wallpaperprovider.weather/files/wallpapers/`
and use the "Your photos" background source, which follows the same naming
convention.

## If you're testing with the radar background

Radar draws over a plain dark backdrop unless you've supplied your own basemap
tile URL in settings. The plugin ships without one on purpose, because
OpenStreetMap's [tile usage policy](https://operations.osmfoundation.org/policies/tiles/)
forbids distributing an app that uses their servers. Tiles reading "not
supported" mean your configured provider is rejecting the requests.

This doesn't affect packs, which never touch the basemap. It's here because it
confuses people testing the plugin for the first time.

## A note on Lottie

Lottie packs are the most interesting, the most likely to surprise you, and
currently the least proven — see the status warning above before investing time. The
plugin adds an image layer at index 0 with your file's `ip`/`op` as its in and
out points. If your animation does something unusual with precomps or time
remapping, test it on a device before submitting.

Text layers in your own animation may not render, because font resolution
happens inside the launcher's process where neither of us can install a font.
Convert text to shapes.
