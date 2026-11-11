# Translating this plugin

No coding involved — this is a text file you edit and a pull request, same
shape as contributing a wallpaper pack, just with words instead of images.

## The one rule that actually matters

**Never translate or change `plugin_uuid`.** It's how Projectivy recognises
this plugin, and it has to be byte-identical in every language. If you're
not sure what to do with it, the safest choice is to just leave that line
out of your file entirely — see below, this is explicitly fine.

Everything else in this guide is convenience. That one is a hard rule, and
it's checked automatically on every pull request.

## How to start

1. Copy [`templates/strings-template.xml`](templates/strings-template.xml)
   to a new file at `weather/src/main/res/values-XX/strings.xml`, where
   `XX` is your language's two-letter code — `de` for German, `es` for
   Spanish, `fr` for French, and so on: the standard two-letter
   [ISO 639-1](https://developer.android.com/reference/java/util/Locale)
   codes, if you're unsure of yours.
2. Translate the text between each pair of tags. Leave every
   `name="..."` attribute exactly as it is — that's what Android matches
   against, not the position or the surrounding text.
3. Open a pull request. A GitHub Action checks the file automatically
   (valid XML, no typo'd names, `plugin_uuid` untouched) before a human
   looks at it.

## A partial translation is genuinely useful

You don't need to do all about 300 strings in one sitting. Android falls back to
English automatically for anything you haven't translated yet, string by
string — nothing breaks, nothing looks obviously wrong, it just shows
English for whatever's missing. Submit 20 strings, submit 200, submit more
later. All of it helps.

If you only have a little time, this is roughly the order of what's most
worth doing, since it's what actually stays on screen rather than being
read once in a settings menu:

1. **Weather condition descriptions** (`wx_*`) — "Clear", "Partly cloudy",
   "Thunderstorm"... these are drawn on the wallpaper itself and visible
   more or less constantly.
2. **Advisories** (`adv_*`) — "Frost likely tonight", "Good drying day".
   Also on-screen, though only some of the time.
3. **Aurora, marine, and air quality/pollen** (`aurora_*`, `marine_*`,
   `aqi_*`, `pollen_*`) — on-screen when those features are enabled and
   relevant.
4. **The detail, stats, sun and nowcast lines** (`detail_*`, `stat_*`, `sun_*`,
   `yesterday_*`, `nowcast_*`, `compass_*`) — "Feels like", "Humidity",
   "Sunrise", "Rain starting shortly", the wind direction letters. Visible
   whenever those panels are on.
5. Everything else — the settings menu itself (`setting_*` and friends).
   Read occasionally while configuring the plugin, not stared at all day.

## Placeholders

Some strings carry a live value — a temperature, a wind speed, a Kp index —
substituted in when the wallpaper is drawn. These show up as `%1$s`,
`%1$d`, `%2$s`, and so on. For example:

```xml
<string name="adv_dry_until">Dry until about %1$s</string>
```

Move `%1$s` to wherever it naturally belongs in your language's word order —
that's expected and fine. Just don't delete it, retype it, or change its
number. A string with two placeholders (`%1$s` and `%2$s`) needs both to
appear somewhere in your translation, each exactly once.

## Apostrophes, percent signs and plurals

The automated check catches these, but they're worth knowing up front:

- **Apostrophes** are written `\'`: `L\'altitude`, not `L'altitude`. An
  unescaped one stops the app from building at all. It comes up constantly in
  French, Italian and Catalan.
- **A literal percent sign** is written `%%` in any string that also has a
  placeholder, e.g. `Humedad %1$d%%`. A single `%` makes the string unusable.
- **Plurals** are the `<plurals>` block near the end of the file (currently one,
  the search-results message). It needs an `other` item, plus `one` if your
  language has a singular form, plus any further forms yours uses (`few`,
  `many`). The `one` item may leave the number out ("Un resultado"); the others
  keep `%1$d`.

## Nothing to translate for times, dates and weekdays

Clock times, the hourly labels, weekday names, dates, and AM/PM on the
wallpaper all follow the device's language automatically, and respect the
12/24-hour setting. There's no string for them in the file; they'll simply come
out in your language.

## The compass letters

`compass_n` through `compass_nnw` are the wind-direction abbreviations. Use
your language's own: Spanish writes `O` for west (oeste), so `SW` becomes `SO`
and `WNW` becomes `ONO`.

## What's deliberately left in English

A few things are kept in their original form rather than translated, since
that's already common practice across many languages: unit abbreviations
like `km/h`, `mph`, `m`, `ft`, and the letters `AQI`. The small data-source
credits at the foot of the wallpaper ("Radar: RainViewer", "Wind: Open-Meteo")
also stay in English: they're attributions built around proper names. If your language
genuinely does write these differently, translating them is fine too — this
is a default, not a rule.

## Regional variants

If your language has meaningfully different wording by region — European
vs. Latin American Spanish, for instance — Android supports that too, as
`values-es-rES` or `values-es-rMX` and so on, alongside a plain `values-es`
as the shared fallback. Only worth doing if you have a specific reason to;
a single `values-XX` per language is the normal, sufficient case.

## Testing your own translation

Not required before opening a PR — the automated check plus a maintainer's
review before merging catches the mechanical problems — but if you'd like
to see it rendered for real: change the TV device's system language to
yours under its own Settings, then reinstall the plugin. Android will pick
up your `values-XX` folder automatically, no code changes needed.
