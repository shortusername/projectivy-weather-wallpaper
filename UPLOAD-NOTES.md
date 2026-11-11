# Fix: every place in the rotation showed the previous place's weather

Sixteen files, cumulative. Use THIS ONE: it contains everything from
projectivy-weather-location-fix.zip, so don't also apply that one. No build.gradle.kts change.
Only WallpaperProviderService.kt and PreferencesManager.kt differ from that zip.

## The report

Someone in SW France added four extra locations. Locally the forecast was about right. Cauterets
(mountains) was 7 degrees too warm, Montreal and St-Sauveur 1-2 degrees off, and Rio de Janeiro more
than 20 degrees too cold.

## Cause

Each refresh did this, in order: fetch the weather for the location that was active, THEN switch to
the next location in the rotation, THEN draw with the new location's name. So every place was shown
with the weather of the one before it. Rio wearing a Montreal or French November temperature is
exactly this; so is mountain Cauterets wearing valley weather; Montreal and St-Sauveur are 80 km
apart, so passing each other's data looks only slightly wrong. The home location looks right on the
first pass because nothing precedes it.
A small model of the old order reproduces all of that (7, 2, 1 and 25 degree errors with
illustrative temperatures) and shows the new order correct. The selection comment in the old code
already said "before anything fetches or draws"; the call had simply been placed after the fetch.

## It reached further than temperature

Only the forecast, yesterday's high and air quality were reset when the rotation moved on. The
severe-weather alert, the aurora line and the wave conditions were cached by time alone, so an alert
for one place could stay on screen over the next one.

## What changed (WallpaperProviderService.kt, PreferencesManager.kt)

1. The location is chosen first, then the weather is fetched.
2. One function, dropCachesIfLocationChanged, resets EVERY location-specific cache (forecast,
   yesterday's high, alert, air quality, aurora, waves) whenever the coordinates differ from the ones
   they were fetched for. That also covers changing your location in settings, and the first-run IP
   lookup, which can change the primary after the rotation was chosen.
3. The rotation follows real time. The settings say "about every 15 minutes" and "about every half
   hour", but the code counted refreshes, and a refresh can come far more often than every 15 minutes
   (the clock alone asks for one each minute). The chosen place is now re-applied between switches, so
   restarting the service doesn't make it jump on. Checked in a model: a refresh every minute and two
   restarts still gave a change exactly every 15 minutes.
   A new persisted value, locationSwitchedAt, records the last switch. It isn't exported with settings.

## Not changed, but worth knowing

- The world weather watch also counts refreshes (refreshIndex % n) rather than time, so it has the same
  too-often problem when refreshes are frequent. I left it alone; it's a separate feature.
- If a mountain place is still several degrees off after this, that can be real: forecasts for
  mountain towns depend on the exact coordinates' elevation, and a town and the ridge above it differ
  by several degrees. I can't tell from here.

## Honest limits

Not compiled or run from here. The checks target the mistakes that have cost builds before.

## Verified

Balanced braces; no duplicate declarations; strings defined and used; cross-file calls resolve; the
selection call precedes the weather fetch; all twelve location-specific cache variables (six values and
their timestamps) are reset together; no uncalled private functions; no leftover refresh-count rotation.
