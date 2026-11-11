# Wallpaper text fully translatable, plus the wind map and stronger translation checks

Thirteen files. Use THIS ONE: it contains everything from projectivy-weather-wind-map.zip
(and the geocoding fix), so don't also apply those, or an older strings.xml could
overwrite this one and break the build. No build.gradle.kts change.

New:      TextFormats.kt, WindClient.kt, WindFlowRenderer.kt
Modified: WeatherRenderer.kt, OpenMeteoClient.kt, Advisories.kt, Backgrounds.kt,
          SettingsFragment.kt, strings.xml, README.md, TRANSLATING.md,
          templates/strings-template.xml, .github/scripts/validate_translations.py

## Why

A first-time contributor submitted a full Spanish translation and pointed out
that a lot of text drawn on the wallpaper was still hardcoded in Kotlin. They
were right, and the real list was longer than their four examples.

## What now comes from strings.xml (41 new strings)

- The detail line: Feels like, H/L, Wind (+ direction)
- "N degrees warmer/cooler than yesterday"
- Stats line: Humidity, UV, Dew, Visibility
- Sunrise, Sunset, "Xh Ym of daylight left"
- Nowcast: all nine "Rain/Snow starting/easing/continuing" phrases
- "Update available" notice and the "Updated" stamp
- The 16 compass directions

Rain and snow are separate strings, not one sentence with a swapped noun, because
the surrounding words change with the noun in many languages.

## What needs no translation at all (new TextFormats.kt)

Clock times, hourly labels, weekday names, dates and AM/PM used to be built in
English by hand. They now use the device locale's own patterns, and the 12/24-hour
setting now reaches the hourly strip and sunrise/sunset too (it only reached the
clock before).

VISIBLE CHANGE FOR ENGLISH: the clock date follows the locale's ordering. A US
device now shows "Mon, Nov 9" where it showed "Mon 9 Nov". Times and hour labels
read the same as before in en-US.

## Bugs found and fixed along the way

- "Xh Ym of daylight left" compared sunset (a time at the displayed place) with the
  DEVICE's time of day, so it was wrong anywhere in a different timezone. Same
  class as the earlier New Zealand fix; that one was missed. Uses the location's
  clock now.
- The clock's date was formatted in the device timezone even when given a
  location-adjusted calendar, which could show the wrong date. It now formats in
  the calendar's own zone.
- Credit lines vanished on cached backgrounds (see the wind-map notes).

## Deliberately left in English

Data-source credits at the foot of the wallpaper ("Radar: RainViewer", "Wind:
Open-Meteo"). They're attributions built around proper names. Also units (km/h,
mph, hPa, inHg) and "AQI", as before.

## The translation validator caught up

It already checked names and plugin_uuid. It now also checks, per file:
- placeholders match the English string exactly (a dropped or extra %1$s throws
  when the wallpaper is drawn)
- no stray single % (a literal percent must be %%)
- no unescaped apostrophe (L'altitude fails the build; L\'altitude is right), which
  is common in French, Italian and Catalan
- <plurals>: known name, an "other" item, no foreign placeholders

Tested against deliberately broken files; it flags each case and passes a good file
that reorders placeholders and uses %%, \' and a "many" plural.

NOTE for the Spanish PR: a pull request runs the workflow from its own merge
commit, so it will keep using the old validator until the contributor updates
their branch from main after this lands.

## Honest limits

- Not compiled or run on a device from here. The checks below target the mistakes
  that have cost builds before; a real build is still the real test.
- Contributors' existing translations won't have the new strings yet. That's fine:
  anything missing falls back to English, string by string.

## Verified

Balanced braces; no duplicate strings or declarations; every R.string reference
defined and used; cross-file calls resolve; argument counts match for every changed
function; all 25 TextFormats.tr() calls supply exactly the arguments their strings
need; no unescaped %N$ in Kotlin; no stale references; no uncalled private
functions; template matches strings.xml (302 entries including plurals).
