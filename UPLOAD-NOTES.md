# Fix: the location search needed typing twice, and got stuck after a pick

Sixteen files, cumulative. Use THIS ONE: it contains everything from projectivy-weather-rotation-fix.zip.
Only SettingsFragment.kt, strings.xml and templates/strings-template.xml differ from that zip.
No build.gradle.kts change.

## The report (from testing the 2.10 build)

Type a place, press Enter: the row says "x matches - open to choose", but clicking it just makes you type
again; after typing and pressing Enter a second time you finally get the list. Picking an option saves the
location but leaves you stuck on the selection screen, so you have to back out of settings.

## Causes

1. I hung the matches off the very row you type into, as a sub-menu. A click on a row with editable text means
   "edit", so the first click re-opened the keyboard instead of the menu.
2. The pick handlers rebuilt the whole list while that sub-menu was still open. The other pickers (background,
   clock, scale) just update a row's text in place and never rebuild; only the location picks did.
3. Same shape of risk for saved places (a sub-menu, then a rebuild).

## What changed (SettingsFragment.kt)

- Matches are ordinary rows directly under the row that was searched (Find my location or Add another location).
  Select one to use it, or to add it to the rotation. No sub-menu, and the keyboard row is never overloaded.
- After a search, focus moves to the first match.
- Each saved place is a row. Selecting it opens two more ordinary rows underneath ("Use as my location" and
  "Remove"); select the place again to close them.
- Nothing on the Location screen is a sub-menu any more. Removed the old "None" placeholder sub-action too: any
  sub-menu click that nothing handles falls through to the background picker, so that placeholder would have
  reset the background to the illustrated scene if clicked.
- Plurals: locations_pick_match replaced by locations_matches_below ("N matches below - select one").
  New strings: locations_result_add, locations_row_open.

## Honest limits

- Not compiled or run from here, and I can't see Leanback's focus behaviour. The one part I'm least sure of is
  the automatic focus jump to the first match (a polish step wrapped so it can't crash). If focus stays on the
  search row, one press of Down reaches the first match.
- If a translation defines locations_pick_match, the validator will flag it as an unknown name; delete that
  one block.

## Verified

Balanced braces; no duplicate declarations; strings and plurals defined and used with none left over; no stale
references to the removed sub-menu code; the sub-menu handler no longer touches location rows; all four numbered
row ranges map to the Location category; no ID collisions or range overlaps (12 ranges); every new helper has
the expected number of callers; template matches strings.xml.
