# Decisions

## Phase 1
- **Toolchain:** AGP 9.4.0, Gradle wrapper 9.6.0, Kotlin 2.4.20 (AGP built-in Kotlin, so no `kotlin-android` plugin), KSP 2.3.12, Room 2.8.5, Compose BOM 2026.09.00, JDK 21. Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` is still needed. Confirmed by a working build.
- **Spec file** renamed from `planned.md` to `seasons-build-prompt.md`.
- **SDK location:** `~/Library/Android/sdk` (from the Android Studio install). `local.properties` holds `sdk.dir` and is git-ignored.
- **Amounts are `Double`.** Display code hides trailing zeros (`12`, not `12.00`).
- **Dates stored as epoch-day `Long`** via a Room TypeConverter.
- **Room schema export on** (`app/schemas/`) so migrations stay possible.
- **Table `groups`** (class `TrackerGroup`): `Group` is awkward as a Kotlin/SQL name.
- **Band rows:** unique on (trackerId, effectiveFrom). Saving a band for the same date replaces that row. Different dates add rows, so history is never rewritten.
- **No delete for trackers/groups yet** (destructive, not needed in Phase 1).
- **Log validation** lives in `Repository`: amount > 0, startDate <= date <= today.
- **Deadline today or in the past:** days remaining is clamped to 1, so needed = everything left, today.
- **Finish projection:** `today + ceil(left / rate)` days, exactly as the spec says. The spec formula is not "inclusive of today".
- **"On track" without a deadline** (spec leaves open): with a band, pace >= band lower bound. With no band, pace > 0.
- **Weekly streaks:** a week's band is the band in effect on the week's Monday (or the start date, if the tracker started mid-week). No band row counts as lower = 1. Active days before the start date are ignored.
- **Streak runs:** an unmet today is left out of the run list rather than ending a run. Yesterday and today both unmet gives current = 0.
- **`Repository` is not unit-tested in Phase 1.** It needs a real database (instrumented test). Stat logic is pure Kotlin and fully tested.

## Phase 2
- **Navigation:** plain back stack in a `NavViewModel` (`mutableStateListOf<Screen>`) plus `BackHandler`. No navigation library. Phase 7 deep links push onto the same stack.
- **Library versions fixed:** activity-compose 1.13.0, lifecycle 2.11.0 (Phase 1 had guessed older ones).
- **Date picker** returns UTC millis. Converted with `ZoneOffset.UTC`, not the phone zone, to avoid off-by-one days.
- **Edit tracker** changes name, unit, color, target, deadline only. Type, band period, start date are locked after creation. Band changes come with the Phase 3 "change band" action.
- **No group field / group headers** until Phase 5.
- **Home shows ACTIVE trackers only.** Archived and completed ones appear in Phases 5 (archive, trophy shelf).
- **Home progress bar:** GOAL = done / target. ONGOING daily band = today / upper bound. Otherwise empty.
- **Weekly streak on Home** shows a `w` suffix (`🔥 3w`).
- **"Today" on Home** is read when data changes. If the app stays open past midnight, rows refresh on the next change. Widgets and the midnight refresh are Phase 7.
- **After a log,** the "Logging for" date resets to today so a backfill date is not reused by accident.
- **Delete log** asks for confirmation. Tracker delete is not offered yet.

## Phase 2 follow-ups
- **No emojis in the app.** The spec (§7, §8) mentions ✅/❌/🔥. Replaced with text: "Met today" / "Not yet" and "Streak N" ("Streak N wk" for weekly). Met-today text uses the tracker color. Spec file left as written.
- **Delete tracker** added (asked for explicitly). Lives at the bottom of the Edit screen, behind a confirm dialog. It permanently deletes the tracker, its logs and its band rows (ON DELETE CASCADE). Archive (Phase 5) is the non-destructive option.
- **Testing:** I run `./gradlew test` and `./gradlew connectedDebugAndroidTest` myself, and drive the app on the phone over adb. `connectedDebugAndroidTest` uninstalls the app when it finishes, so it wipes the app's data on the phone. I reinstall with `installDebug` after.
- **Instrumented tests** (`app/src/androidTest`) run `Repository` against an in-memory Room database on the phone. Added androidx.test runner 1.7.0 and ext-junit 1.3.0.

## Phase 3
- **Tracker header** is built by a pure function (`buildTrackerSummary`, `ui/TrackerSummary.kt`) so every text is unit-tested. GOAL: `done / target unit`, then `N left · P%`. ONGOING daily: `today / lower–upper unit`. ONGOING weekly: `active days this week / lower–upper days`.
- **Status line** is shown for GOAL only. Three forms (deadline, daily band, none) as in spec §5. "On track" uses the tracker color, "Behind" uses amber, "Done" is plain. Pace 0 gives `finish —`.
- **Percent** is rounded to a whole number and can pass 100 when over target. The progress bar is capped at full.
- **Streaks** shown as Current / Best / Average with the unit (days, or weeks for weekly bands). Average has one decimal.
- **Menu** (top right "Menu"): Edit tracker, Change band. Change band only appears when the tracker has a band. A GOAL with no band cannot add one later (not in the spec). Archive joins the menu in Phase 5.
- **Change band:** new row effective from a chosen date (default today, min = start date, future dates allowed). Same-date change replaces that one row. Dialog is prefilled with today's band.
- **Goal completion** (celebration, "Move to trophy shelf") is left to Phase 5, as agreed. A finished goal shows `Done`, `0 left`.
- **Test runs may wipe app data on the phone.** Confirmed OK: all data is dummy until the app is complete.

## Phase 4
- **Chart data is built by pure functions** (`ui/charts/ChartModels.kt`) and unit-tested. `ui/charts/Charts.kt` only draws them with `Canvas`. No chart library.
- **Tabs** are a plain grey text row (selected = bright + bold). On track shows for GOAL trackers only.
- **Daily:** 14 bars. Band stripe only for DAILY-band trackers, built per day so it steps when the band changes. Bars below that day's lower bound are dim grey (`#616161`). Only today's bar gets a value label. Band range is labeled at the right end. Bars start at zero, no grid.
- **Weekly:** 11 Monday-start weeks. WEEKLY-band trackers plot active days with the band stripe, using the band on the week's Monday (same rule as the weekly streak). Weeks before the tracker started get no stripe. Weeks below the lower bound are NOT dimmed (spec only dims on Daily).
- **Season heatmap:** 7 rows x `ceil(length/7)` columns, filled down each column in season-day order. Shows the season that contains today. Colour = `lerp(#2C2C2C, trackerColor, 0.3 + 0.7 * ratio)`, where `ratio = total / max(that day's upper bound, season max)`. Days that met the streak rule are fully opaque, partial days 55% opaque. Zero days `#2C2C2C`. Future days and days before the tracker started are `#2C2C2C` at 40%. Today has a thin outline. Tap a day to see its date and value.
- **Default settings:** on first use, a Settings row is created with season start = today and length 77 (`insertIfAbsent`, so it never overwrites). Phase 6 adds the screen to change them. The heatmap therefore starts almost empty on a fresh install.
- **On track:** cumulative line in the tracker color from the start date to today. Grey dashed line = needed pace (start,0 to deadline,target) with a deadline, or projection (today's total to projected finish at target) without one. The projection uses the spec's rule (band lower bound, otherwise pace). No projection line when pace is 0 or the goal is done. End labels are direct. The cumulative label has a background patch so the dashed line does not cross it.
- **Testing the charts:** I seeded a dummy database with 4 trackers (goal with deadline, daily-band ongoing with a band change, weekly-band ongoing, goal with band) by editing the app's database over adb, then read screenshots of every tab.

## Phase 5
- **Groups:** Groups screen (create, open), group detail (rename/recolor via "Edit group", **Delete group** in its menu), reorder with Up/Down buttons. Delete only removes the group. Its trackers stay and become ungrouped (`ON DELETE SET NULL`). Asked for by you. The spec's "combined widget preview" on the group page waits for Phase 7.
- **Group order:** `sortOrder` is rewritten as 0, 1, 2... on every move. New groups go to the end.
- **Tracker group** is a chip row on the create/edit form (None + each group). The row is hidden if no groups exist. Editing can change the group.
- **Home:** group headers (color dot + name) in group order, ungrouped trackers last under "Ungrouped". Headers appear only if at least one group exists. Groups with no active trackers are not shown.
- **Home links** (Groups, Trophies, Archive) are text buttons under the title. Settings joins them in Phase 6.
- **Goal reached:** when `done >= target` on an ACTIVE goal, the tracker screen shows "Goal reached" and a **Move to trophy shelf** button. It sets COMPLETED and `completedDate` = today (the day you tap it). No "move back" action.
- **Trophy shelf:** 2-column grid. Card: name, color bar, total + unit, days taken (inclusive of both dates), `start -> finish`, best streak. Total and best streak ignore logs after the finish date. Tap opens the normal tracker screen.
- **Archive:** "Archive" in the tracker menu (with confirm). Archive screen lists archived trackers. Tap gives "Continue where you left off" (status back to ACTIVE, all logs kept; the days away count as missed for streaks) or "Start fresh".
- **Start fresh** copies name, unit, type, color, group, target, band period and the band in effect today. Start date = today. The deadline is copied only if it is today or later. The old tracker stays archived with its logs.
- **Visual fixes:** the + button was the default purple. Now grey (`#2C2C2C`) with light text, so only trackers carry color. The "Custom" button wrapped its text ("Custo / m"). It now stays on one line.

## Phase 6
- **First launch:** if there are no settings, Home shows a dialog that cannot be dismissed: season length (default 77, 7-365) and first start date (default today, any past date). This replaces Phase 4's silent default. 77 exists only as `Settings.DEFAULT_SEASON_LENGTH`.
- **Snapshots:** on each app open, every season that has fully ended and has no snapshot gets one. It is frozen: backfilled logs afterwards do not change it. Snapshots are matched by start date (no schema change). A mutex stops two callers saving the same one.
- **Snapshot content (`payloadJson`, built with the built-in `org.json`):** days active (days with at least one log, any tracker), and per tracker: total in the season, best streak inside the season (clipped to the season; weeks for weekly-band trackers), plus the trophies finished inside the season (with total up to the finish). A tracker is listed if it had a log in the season, or it was ACTIVE and had started by the season's end.
- **Summary screen:** shown once on the first open after a season ends, oldest unseen first. "Done", the back link and system back all mark it seen (so it cannot loop). Seasons with zero active days are skipped silently and the seen marker is moved past them. `lastSeasonSummarySeen` holds the highest seen season number.
- **Season numbers (display):** `seasonBase + spec formula`, where seasonBase = how many snapshots began before the current start date. So after a length change the numbering carries on (Season 4 after three snapshots) instead of restarting at 1. The spec's formula is unchanged and is still what `seasonNumber()` computes. The Season tab label and Settings use the base.
- **Change season** (Settings): new length + new start date (default today). The start date may be from the start of the running season up to today. Seasons that already ended are snapshotted first, then the running season's part before the new start date is snapshotted (skipped if empty), then settings change. Past snapshots are untouched. The partial summary shows on the next Home open like any other.
- **Not allowed:** a start date before the running season began, or in the future. A wrong start set at first launch can therefore not be moved earlier later (agreed).
- **History:** "Seasons" link on Home lists snapshots newest first. Tap opens the same summary screen.
- **Home links** now wrap onto two lines (Groups, Trophies, Archive, Seasons, Settings).
- **Test dependency:** `org.json:json:20260814` for unit tests only. Android's stub jar cannot run `org.json` in plain JVM tests.

## Fixes after Phase 6 (your feedback)
- **Removed the pace / on-track / finish-date calculation and stat completely.** Gone from the tracker screen, the code (`pace`, `goalForecast`, status label, finish dates) and the tests. The On track chart no longer draws a projection line for goals without a deadline. It shows only the cumulative line there.
- **Needed pace stays, as a plain line** under the header, for goals with a deadline only: `Need 10 pages/day · 10 days left`. It is `left / days remaining`, counting today as a day (100 pages, deadline 9 days away = 10 days = 10 a day). It shows no on-track/behind label and no finish date. Past deadline: `Deadline passed · N left`. No line without a deadline, for ongoing trackers, or once the goal is reached.
- **The "Goal reached" banner** now keys off `done >= target` directly.
- **Keyboard on first tap:** Custom, Edit entry, Change band and the group dialogs now put the cursor in the field and open the keyboard automatically. In Edit entry the existing amount is selected, so typing replaces it.
- **Start fresh:** the old tracker now gets a new status `REPLACED`. It is kept as history (logs stay, and count in season summaries) but leaves the Archive list, so it cannot be restarted twice. The action runs in a database transaction and does nothing if the tracker is no longer archived, so repeated taps cannot create duplicates. `unarchiveContinue` has the same guard. This changes the spec ("the old one stays archived as history"): the old one is kept but no longer listed. Nothing shows replaced trackers yet.

## Phase 7
- **Two widgets** (Glance 1.2.0): "Seasons tracker" (one tracker) and "Seasons group" (list). Both resize in both directions (`resizeMode horizontal|vertical`) and use `SizeMode.Exact`, so the content is rebuilt for every size. The single widget shows more lines the taller it gets (big number only, then the left/percent line, then the needed-pace line). The group widget scrolls.
- **Choice per widget** is kept in Glance state (`PreferencesGlanceStateDefinition`), not SharedPreferences. First version used SharedPreferences and the widgets stayed on "Tap to choose": Glance keeps the first drawing of a widget alive and only redraws when its own state changes, so a choice stored elsewhere was never re-read. Reading the state inside `provideContent` is what makes the redraw happen. Glance deletes a widget's state when the widget is removed.
- **Config activity** (`WidgetConfigActivity`) serves both widgets. It starts with `RESULT_CANCELED`, so backing out does not add an empty widget. The widgets are `reconfigurable`.
- **Refresh:** `WidgetUpdater.updateAll` bumps a "refresh" counter in each widget's state, then updates it. The counter is what forces a rebuild when nothing else changed (for example at midnight). The app watches trackers, logs, bands and groups, and calls it 400 ms after any change, and once at start.
- **Midnight:** `MidnightWorker` (WorkManager) runs about 5 seconds after local midnight, refreshes the widgets, then books the next midnight (`APPEND_OR_REPLACE`). Every app start re-books it with `REPLACE`, which fixes it after a timezone change. The delay uses `java.time` on the zone's calendar, so a daylight-saving day is 23 or 25 hours. `updatePeriodMillis` is 1 hour as a backup. Not covered: a clock or timezone change while the app is closed (the hourly backup catches that).
- **Deep links:** widgets start `MainActivity` with the tracker or group id as an extra, plus a `seasons://...` data URI so each target is a different PendingIntent. `MainActivity` is `singleTop` and handles `onNewIntent`. A link replaces the back stack with Home + target, so Back goes to Home. The extra is removed once used. Single widget: tap opens that tracker. Group widget: a row opens that tracker, the group name opens the group page. A deleted tracker or group shows "not found" on the widget, and the link lands on a blank tracker screen with Back.
- **Group page widget preview** is a Compose lookalike built from the same texts as the widget.
- **Shared texts:** `HomeRow.mainText()/statusText()/streakText()` are used by Home, widgets and the preview, so the three always agree.
- **Testing note:** force-stopping the app cancels its widgets' pending intents, so widget taps do nothing until the app runs again (an Android rule, not a bug). Opening the app restores them. `connectedDebugAndroidTest` uninstalls the app, which also removes placed widgets from the home screen.
- **Tracker widget graph:** the single-tracker widget shows the Daily bar chart (last 14 days) between the text and the progress bar. Glance has no Canvas, so the chart is drawn into a bitmap (`ChartBitmap.kt`) from the same `buildDailyChart` data as the app, at exactly the size it is shown (`ContentScale.FillBounds`). Same rules as the app: tracker color for bars, band stripe and dim grey days for daily-band trackers, today's value only, no grid or legend. The chart appears only when at least 56 dp of height is left after the text lines, so a small widget keeps its text-only layout. The height budget includes a 30 dp margin because widget text takes more room than its font size suggests (first try cut off the bottom row). It uses Glance's own `LocalContext`: Compose's `LocalContext` throws "not present" inside a widget.

## Phase 8
- **Export/import live in Settings -> Backup:** "Export backup (JSON)", "Export logs (CSV)", "Import backup". No cloud, no accounts, no new permission (still no internet). Files leave the app only through the share sheet and come in only through the system file picker (`OpenDocument`, shows all files because some apps label JSON as generic binary; a wrong file is rejected by the check).
- **Share sheet:** the file is written to `cache/exports/` (older exports are deleted first) and shared with `FileProvider` (`<authority>.fileprovider`, `exported=false`, only the `exports/` cache folder is exposed) plus `FLAG_GRANT_READ_URI_PERMISSION` and a `ClipData`.
- **JSON backup (format version 1):** `app`, `version`, `exportedAt`, then `settings`, `groups`, `trackers`, `bands`, `logs`, `summaries`. All ids are kept, so widgets and links still point at the right trackers after a restore. Enums by name, dates ISO (`yyyy-MM-dd`), `null` for empty optional fields. Includes the REPLACED (restarted) trackers and the "last summary seen" marker. Does not include widget placement or widget choices.
- **Import checks everything before touching anything:** valid JSON, `app = Seasons`, version (a newer one is refused), every enum value, every date, positive finite log amounts, positive targets, band bounds (lower >= 0, upper >= lower), season length 7-365, goals have a target, no duplicate or non-positive ids, no two band rows for one tracker and day, every log, band and group reference points at something in the file, season summaries parse and end after they start, names and units not blank. Extra unknown fields are ignored.
- **Import replaces everything in one Room transaction:** children are deleted first, then everything is inserted in dependency order. If anything fails, the transaction rolls back and the old data stays. Tested on the phone with a log that breaks a foreign key. After an import, new ids continue above the imported ones.
- **Confirmation dialog** names what the file holds ("3 trackers, 48 log entries, ...") and says that it replaces all current data and cannot be undone. No automatic safety copy (agreed).
- **CSV of logs:** `date,tracker,group,amount,unit,logged_at`, oldest first, CRLF line ends, RFC 4180 quoting. Amounts have no trailing zeros or exponent. A text field that starts with `=`, `+`, `-`, `@`, tab or CR gets a leading apostrophe so a spreadsheet cannot run it as a formula.
- **Import size limit:** 100 MB.
