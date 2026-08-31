# Contributing a wallpaper pack

You don't need to write any code. A pack is a set of images (or animations) plus
one entry in `packs/index.json`. Open a pull request and CI checks the rest.

## How packs work

The plugin downloads `packs/index.json`, the user picks a pack in settings, and
assets are fetched on demand and cached on the device. New packs go live as soon
as your PR merges. No app release, no waiting on me.

## 1. Pick a kind

| Kind | Format | Weather readout |
|---|---|---|
| `static` | JPG, PNG or WebP | Yes, drawn over your image |
| `lottie` | Lottie JSON | Yes, embedded into the animation |
| `video` | MP4 (H.264) | **No** |

Lottie keeps the readout because the plugin injects the weather panel into your
animation as an image layer before handing it to the launcher. Your timing and
easing are untouched — we only append one layer on top.

Video can't carry the readout: the launcher decodes the file directly and there's
no point at which the plugin can draw on it. Users are warned when they select
one.

## 2. Make the assets

**Canvas: 1920x1080.** Other sizes work but get centre-cropped. Lottie files
should declare `w` and `h`; the panel is scaled to match.

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

**Keep files small.** Under 800 KB per still, under 2 MB per Lottie. These
download over home wifi to a TV box with a modest heap. Video under 8 MB and
under 15 seconds, seamlessly looping.

## 3. Name assets by condition

Keys are resolved most specific first, falling back down this chain:

```
clear-night  →  clear  →  night  →  default
```

So a pack needs only `default` to be valid. Available keys:

- Buckets: `clear`, `cloud`, `rain`, `snow`, `storm`
- With time: `clear-day`, `clear-night`, `cloud-day`, and so on
- Time only: `day`, `night`
- Fallback: `default`

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

Lottie packs are the most interesting and the most likely to surprise you. The
plugin adds an image layer at index 0 with your file's `ip`/`op` as its in and
out points. If your animation does something unusual with precomps or time
remapping, test it on a device before submitting.

Text layers in your own animation may not render, because font resolution
happens inside the launcher's process where neither of us can install a font.
Convert text to shapes.
