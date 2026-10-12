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

## Phase 9 (polish pass)
- **Group, Change band, Change season and first-launch dialogs** now use `FormDialog`: it shrinks above the keyboard and its content scrolls, so Save and Cancel are never hidden. (The auto-opening keyboard from the Phase 2 fix had covered the buttons in the group dialog.)
- **Home:** the five links (Groups, Trophies, Archive, Seasons, Settings) are one "Menu" next to the title. Status ("Met today") and streak are stacked on the right of each row, so a large system font cannot squeeze the tracker name into a thin column. Checked at font scale 2.0 and 1.0.
- **Neutral greys:** Material's dark containers are purple-tinted by default. The theme now sets neutral greys for dialogs, menus and containers (`#1E1E1E`, `#242424`, `#2C2C2C`) and no surface tint.
- **Contrast:** the one piece of text that used `#616161` (the "days" under the streak numbers) now uses `#9E9E9E`. The dark grey is kept for lines and dim bars only.
- **Screen-reader descriptions** for the Daily, Weekly and On track charts and the Season heatmap (pure functions, unit-tested).
- **Widget picker previews** (vector drawables) for both widgets.
- **Left as is:** amber for Delete buttons and error text (it is the only warning color), free-text units ("1 sessions"), and no app icon (not in the spec).
- **System font size:** while testing I changed the phone's font size to 2.0 and 1.0, then set it back to your original 1.15.

## Release build
- **Version 1.0** (`versionCode 1`). Built with `./gradlew assembleRelease`, output `app/build/outputs/apk/release/app-release.apk`, copied to `~/Desktop/Seasons-release/seasons-1.0.apk`.
- **Signing:** a new key, `~/.seasons-signing/seasons-release.jks` (RSA 2048, valid 10000 days, alias `seasons`). Its passwords are in `keystore.properties` in the project root. Both are git-ignored (`keystore.properties`, `*.jks`, `*.keystore`) and the key is outside the project. **Back the key up.** A release build signed with a different key cannot update an installed one; you would have to uninstall it first and lose its data. If `keystore.properties` is missing, `assembleRelease` makes an unsigned APK.
- **Code shrinking (R8) is off.** Room, Glance and WorkManager use generated and reflective code, and a shrunk build that crashes on a widget tap is worse than a few extra MB. APK is about 10 MB.
- **Permissions in the APK:** wake lock, network state, boot completed and foreground service (all added by WorkManager). No internet permission.
- **A release build cannot be installed over the debug build** (different signing key). The debug app has to be uninstalled first, which deletes its data. Export a backup first (Settings -> Backup).

## MVP 2, Phase 1 (remove groups, three-dot menu)
- **Source of truth is now `mvps/mvp2.md`.** The kanban board takes priority over old MVP specs. `mvps/mvp1.md` is history.
- **Groups removed completely:** the Groups screens, group chips on the tracker form, Home headers, the "Seasons group" widget, `TrackerGroup`, `GroupDao`, `Tracker.groupId`, and the `group` column in the CSV.
- **Database version 2** uses Room's `@AutoMigration` with `@DeleteTable("groups")` and `@DeleteColumn("trackers", "groupId")`. Room recreates the trackers table itself. No destructive fallback. Tested on the phone (`MigrationTest`): rows in every table survive, the foreign keys do not wipe logs or bands, `PRAGMA foreign_key_check` is clean, and the app's own builder opens the migrated file.
- **`room-testing`** needs kotlinx-serialization 1.8+, but the app's dependencies pin 1.7.3 and the test run has to match the app. Fix: `debugImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")`. Release builds are not touched.
- **Backup format is now 2.** Exports have no `groups` and no `groupId`. Format 1 files still import: `groups` and `groupId` are read past and ignored, so even a broken group reference no longer blocks an import. Version 3 or higher is refused. Test fixture: `app/src/test/resources/backup_v1.json`. Phase 3 adds the time fields to format 2 (old format 2 files from this phase have none, which is fine because they are optional).
- **A group widget already on the home screen** stops working and has to be removed by hand. Its old saved choice (`group:<id>`) decodes to nothing.
- **Three-dot menu:** `MoreButton` in `ui/Components.kt`, three dots drawn with `Canvas` (no icon library: `material-icons-core` is not on the classpath and adding a dependency for one icon is not worth it). Content description "More options", 48 dp tap target. Used on Home and on the tracker screen (the only two menus).
- **Test run wipes the phone's data.** The phone had the release build (different signing key), so Gradle's uninstall removed it. After this phase the phone has a fresh debug install with one dummy tracker "Test". Re-import your backup from Settings -> Backup -> Import backup.

## MVP 2, Phase 2 (simplify archive)
- **Unarchive is one tap:** Archive screen -> tap a tracker -> "Resume "name"?" -> Resume. Status goes back to ACTIVE and all logs are kept. The days spent archived count as missed for streaks, as the old "Continue" did. `Repository.unarchive` does nothing if the tracker is not archived (stale second tap).
- **"Start fresh" removed:** `Repository.startFresh`, `freshCopy` and `TrackerCopies.kt` are deleted, together with their tests. **`TrackerStatus.REPLACED` removed.**
- **Database version 3:** a manual `MIGRATION_2_3` (`UPDATE trackers SET status = 'ARCHIVED' WHERE status = 'REPLACED'`), registered in `AppDatabase.get`. Schema is otherwise identical. The migration tests check statuses and that all logs survive, and that a version 1 file walks 1 -> 2 -> 3 with the app's own builder.
- **Import:** `REPLACED` in an older backup is read as `ARCHIVED`. Any other unknown status is still rejected. The version 1 test fixture now holds a `REPLACED` tracker.

## MVP 2, Phase 3 (stopwatch data layer, no UI yet)
- **Schema (DB version 4, `@AutoMigration(3, 4)`):** `log_entries` gets nullable `startTime` and `endTime` (epoch millis). New table `running_timer` (`id` fixed 1, `trackerId` FK ON DELETE CASCADE, `startTime`). Old logs stay plain (both null). A log is a stopwatch entry when both are set (`LogEntry.isTimed`). Tested on the phone: all rows survive and a version 1 file walks 1 -> 2 -> 3 -> 4.
- **One timer at a time** is enforced by the table: `id = 1` and an `ABORT` insert. `Repository.startTimer` returns false (changes nothing) if one is running, so the UI can ask "Stop X first?". Only an ACTIVE tracker can be timed.
- **Elapsed time is `now - startTime`**, never a ticking counter, so it survives the app closing and the process dying.
- **`finishTimer`** inserts the log and deletes the timer in one transaction. If validation fails, nothing changes and the timer keeps running. Rules: amount > 0, end after start, end not in the future, and the end date is not before the tracker's start date. The entry's `date` is the **local date of the end time** (agreed). An overnight timer counts for the day it was stopped.
- **`editRunningStart`** moves the start of the running timer (not into the future). **`editTimedLog`** edits start, end and amount of a stopwatch entry with the same rules, and the date follows the end time. **Full editing freedom** was agreed; the only limits are the rules above. `editLog` (plain edit) refuses a stopwatch entry, and `editTimedLog` refuses a plain one, so a plain entry can never gain times (no manual minutes entry, on purpose).
- **Overlaps between entries are allowed.** No check.
- **Pure rules** live in `domain/Timer.kt` (date of end, whole minutes rounded down, error texts, `15m` / `1h 5m` / `2h`, `2:13:05`). They take the zone and the time as arguments, so they are unit-tested without a clock.
- **Backup format 2** now also holds `startTime`/`endTime` per log and `runningTimer` (`null` or `{trackerId, startTime}`). Format 1 files (and format 2 files without these) import with plain entries and no timer. New import checks: both times or neither, end after start, the timer's tracker exists, the timer does not start in the future (`parseBackup(text, now)`). Import replaces the running timer too.
- **CSV** columns are now `date,tracker,amount,unit,start,end,minutes,logged_at`. `start`/`end` are local `yyyy-MM-ddTHH:mm`, `minutes` is whole minutes rounded down, all three empty for plain entries.
- **Stats are untouched:** done, left, streaks, needed pace and season snapshots use `amount` only.

## MVP 2, Phase 4 (stopwatch UI)
- **Stopwatch button** (a small drawn stopwatch, no icon library) at the top right of every tracker card on Home and in the tracker screen header. Grey when idle, tracker color while its timer runs. Content description "Start timer" / "Stop timer". Only ACTIVE trackers show it.
- **Running label:** "Running 2:13:05 · hold to edit start" under the name on Home (a clock that refreshes once a second only while a timer runs; no service, no notification). **Long-pressing the card edits the start of that tracker's running timer** (asked for by you); it does nothing on a card with no running timer. The tracker screen also has an "Edit start" button. Long press gives haptic feedback.
- **One timer at a time:** starting another tracker's timer asks "Stop X first?". "Stop it" opens X's Stop screen. The new timer is started afterwards by tapping again.
- **Edit time screen** (`ui/TimeScreens.kt`), laid out like your reference: tracker-color header strip (name, `1:19 PM - 1:34 PM`, length), Start box (date small, time big), the row `-30 -5 -1 +1 +5 +30 Now`, End box with the same row, "Counts for Today", Amount field, Save. Tapping the date or time opens the system pickers. Save is disabled, with a plain message, when end is not after start, end is in the future, or the amount is empty or 0. No Statistics, Activity, Tag, Comment or Actions.
- **Three uses of one screen:** Stop (end = the moment the screen opens, amount empty), Running (only Start, "Save start time"), Entry (a saved stopwatch entry from History, with "Delete entry"). Plain entries (+1, +5, Custom) keep the small amount dialog and cannot gain time.
- **Discarding:** on the Stop screen, Back, the system back and "Discard time" all ask "Discard this time?" first, so a stray tap cannot lose it. Confirming deletes the timer and logs nothing.
- **View models are kept by key**, so each opening of a timer screen carries a token (`Screen.TimerStop(token)` etc.) to get a fresh one. Otherwise a second Stop would show the first one's old times.
- **History** shows `12:52 PM – 1:24 PM · 31m` under the date of a stopwatch entry. The tracker screen shows `Time today 31m · last 14 days 31m` when the tracker has any stopwatch entries.
- **Tested on the phone over adb:** start, reinstall the app while the timer ran (the clock continued, so it survives process death), long press -> Edit start -30 -> 31m, stop, amount, Save, History row, Edit entry opens with the same values.

## MVP 2, Phase 5 (time on the Daily chart)
- **Rule from the book (ch. 2, "Secondary y-axis: generally not a good idea"):** different units must not share a second y-axis. The charts are pulled apart vertically and share the same days, each with its own scale starting at zero.
- **Daily tab:** the amount chart is unchanged. Under it, a thin **time strip** (`TimeStrip` in `ui/charts/Charts.kt`): grey bars (`#616161`, so color stays reserved for the tracker's amount), zero baseline, no grid, no legend. Caption "Time per day · longest 1h 5m" is a direct label. Only today's value is labeled. The strip uses the same left and right padding as the bar chart, so the 14 days line up.
- **Hidden when there is no time:** `buildTimeStrip` returns null when none of the last 14 days has stopwatch minutes, so a tracker without stopwatch data looks exactly as before. Each day's minutes are the whole minutes of the entries on that date (the end date), rounded down per entry, added up.
- **Screen readers:** the strip has its own description (`describeTimeStrip`): today's time and the longest day.
- **Not changed:** Weekly and Season tabs, the widget graph, season snapshots.

## MVP 2, Phase 6 (final pass)
- **Checked against `mvp1.md` §3** on the phone, with dummy data: dark only, grey except the tracker color (the time strip is grey), no legends (the strip caption is a direct label), no gridlines or borders, bars start at zero, no trailing zeros, the biggest number is still the biggest thing on Home and the tracker screen. A tracker without stopwatch data (Spanish words) looks exactly as before.
- **Zero minutes reads "0m"**, not "<1m" (a day with no time showed "<1m" under the strip, which was wrong). Entries under one minute also round down to "0m".
- **Version 1.1** (`versionCode 2`). No release build was made. An installed release build (signed with the release key) cannot be updated by a debug build, and the other way round.
- **Dummy data:** an importable backup (`seasons-dummy-backup.json`, format 2, 4 trackers, 95 logs, 34 stopwatch entries) was put in the phone's Downloads. Pushing a database file straight into the app's private storage was refused by the tool permission check, so it was imported through the app's own Settings -> Backup -> Import instead. Importing replaces all data, so export your own backup first if the phone ever holds real data.
- **Still open from `mvp2.md`:** time on the Weekly tab, and time totals on Home cards beyond the "Running" label.

## MVP 2, change after Phase 6: time as a line over the bars (option B)
- **Replaces the grey time strip** under the Daily chart (Phase 5). Asked for by you. The book (ch. 2) allows two ways to show a second unit: pull the charts apart (the strip) or **label the points directly on the same plot** (this one). It also warns that a shared plot can suggest a link between the series.
- **Drawn inside `BarChart`** (`time` parameter, Daily tab only): a light grey line with dots, with a dark halo so it reads over a bar of any color. Its scale is hidden: zero at the baseline, the longest day at the top of the plot. No second axis and no legend. The line **breaks on days without stopwatch time**, because "not timed" is not "0 minutes". A day on its own is a single dot.
- **Direct labels:** the longest day, and today when it has time (and is not the longest). Each label has a background patch. A label that would leave the top of the chart is placed below its point. Which days get labels is a pure function (`timeLabelIndices`, unit-tested).
- **Not changed:** the bars, band stripe and today's amount label. Weekly, Season and On track tabs. The widget graph. A tracker with no stopwatch time looks as before.
- **Known drawback, seen with the dummy data:** when time is only tracked on some days (about every other day), the line breaks into short pieces and single dots, which is choppy. Real use may be steadier. Options if it stays choppy: join across gaps with a faint dashed line, or go back to the strip.

## MVP 2, change after the line chart: time in its own chart above the bars, both with a y axis
- **Replaces the line over the bars** (it was too cluttered). The Daily tab now stacks two charts that share the same 14 days: **Time on top, amount bars below**. This is the book's "pull the graphs apart vertically, each with its own y axis on the left, same x axis" option (ch. 2, Secondary y-axis). Asked for by you.
- **Time chart** (`TimeChart`): a grey line, one dot per day with stopwatch time, **one continuous line through those days** (asked for, "no choppy"). The line is only a join over days with no time; those days have no dot, so real data is still visible. Left axis labels: 0, half and a round maximum (10m, 20m, 30m, 40m, 1h, 1h 30m, 2h ... at or above the longest day). A "Time" caption is its axis title. Only today's value is labeled, when today has time. Baseline at zero, no gridlines, no legend. Hidden when no day in the last 14 has time.
- **Bar chart now has a y axis too** (you asked where it went: it never had one, only direct labels). On the Daily tab it shows 0, half and a round maximum (1, 2, 3, 4, 5, 6, 8, 10, 20 ... at or above the highest bar). The bars are drawn to that round maximum. Today's value and the band range are still labeled directly. **Weekly** and the other tabs are unchanged (no axis). Both Daily charts reserve the same left space (`ChartLeftPad`) so the days line up.
- **Pure, unit-tested:** `niceMinutesMax`, `timeAxisTicks`, `niceAmountMax`, `amountAxisTicks`, `timedDays`.

## MVP 2, change: the stopwatch and everything time-related is removed
- **Why:** you decided it is unnecessary. Everything else from MVP 2 stays: groups gone, one-tap archive, three-dot menus, and the **y axis on the Daily bar chart** (0, half, a round maximum).
- **Removed:** the stopwatch buttons and "Running" label on Home and the tracker screen, long-press to edit, the Edit time screen and its three uses, "Time today ..." line, history time ranges, the time chart (and the strip and overlay versions before it), the time fields in the backup and CSV, and all their code and tests (`domain/Timer.kt`, `ui/TimeStats.kt`, `ui/TimeEditViewModel.kt`, `ui/TimeScreens.kt`, `TimerTest`, `TimeStatsTest`, `TimeStripTest`). The UI files that only the stopwatch had changed were restored from the commit before it (`e5b171d`), then the bar-chart axis was re-applied.
- **Database version 5** (`@AutoMigration(4, 5)`): drops the `running_timer` table and the `startTime` and `endTime` columns of `log_entries`. **Logged amounts, dates and creation times are kept**; only the recorded time of day and duration of stopwatch entries is lost (agreed by asking for the removal). The earlier migrations (1 -> 2, 2 -> 3, 3 -> 4) stay, so any old database still upgrades in one go. Tested on the phone (`MigrationTest`): sums of amounts are unchanged, the columns and table are gone, no foreign key is broken, and a version 1 database walks all the way to 5 with the app's own builder.
- **Backups:** new files have no time fields and no `runningTimer` (format stays 2). **Files made while the stopwatch existed still import:** `startTime`, `endTime` and `runningTimer` are ignored, even when they are inconsistent (tested). The backup on your phone's Downloads (dummy data) was imported to check this.
- **CSV** is back to `date,tracker,amount,unit,logged_at`.
- **Not removed:** the earlier 3 -> 4 migration code and `ChartLeftPad` (still used for the bar chart axis).

## Chart back to original, and an app icon
- **Daily bar chart is back to the original** (no y axis, only direct labels: today's value and the band range), as asked. `Charts.kt`, `ChartModels.kt` and `TrackerScreen.kt` were restored exactly from the commit before the stopwatch work (`e5b171d`), since the axis was the only change to them. The axis helpers and their test (`ChartAxisTest`) were deleted. Weekly, Season and On track were never changed.
- **App icon (new, the app had none):** an adaptive icon. A ring of four arcs, one per season, with a small light-grey dot in the middle ("today"), on the app's near-black (`#161616`). Colors: spring `#81C784`, summer `#FFCA28`, autumn `#FF7043`, winter `#4FC3F7`. The arcs are 80 degrees each with 10 degree gaps and flat ends, so the ring reads as four clean pieces. It sits inside the 66 dp safe zone of the 108 dp layer, so no launcher mask can cut it.
- **Files:** `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` (vector drawables, no image files), `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, and `android:icon` / `android:roundIcon` in the manifest. minSdk is 26, so the adaptive icon covers every supported phone and no older bitmap versions are needed.
- **Themed icon (Android 13+):** a `<monochrome>` layer with the same shape in one color, so the icon follows the wallpaper theme when the user turns themed icons on. Not tested on the phone (only the normal colored icon was checked in the launcher).
- **Colors:** the four season colors are the only place the app uses several colors at once. The app's own rule (one accent per tracker, grey otherwise) is for charts and screens; an icon is allowed to be recognisable.

## Seasons calendar, and tracker stats
- **Seasons screen (Home menu -> Seasons)** now opens with the **current season as a calendar**: "Season N", start and end date (`Oct 8, 2026 – Nov 23, 2026`), and "Day 5 of 47 · 42 days left". The numbering is the same as Settings and the Season chart (it carries on after a season length change). If the first season has not started yet, that first season is shown with "Starts in N days".
- **Month blocks, Monday first** (agreed): one block per month the season touches, a Mon-Sun header, and days in week rows. Only days **inside the season** are drawn, so the first block starts on the season's first day and the last block ends on its last day; the cells around them stay empty. Today has an outline, future days are dimmer, and the first and last day of the season sit on a grey tile. Grey only, no tracker colors, **no logging information** (agreed). Each day has a screen-reader description ("Oct 12, 2026, today").
- **Pure and unit-tested** (`ui/SeasonCalendar.kt`, `SeasonCalendarTest`, 18 tests): the season window, the month blocks, the Monday-first weeks, and the progress text. The tests cover season lengths 7, 30, 77, 90 and 365 from several start dates (including a leap day and a year end), and check that every day appears once and under its right weekday.
- **Past seasons** stay as a list under the calendar, unchanged.
- **Tracker stats** (tracker screen): "Current / Best / Average" are replaced by **"Current streak" and "Avg streak"** (with days, or weeks for weekly bands). "Best" is no longer shown on the tracker screen. It is still computed and still used on the trophy shelf and in season summaries.
- **Instrumented tests were not run for these two changes** (nothing in the data layer changed). They wipe the app's data on the phone, and your own data is on it now.
- **No unit under the streak stats** (asked for): the tracker screen shows just the number with "Current streak" and "Avg streak". This also drops "weeks" for weekly-band trackers, so a weekly streak of 3 reads as `3` with no unit.

## Icon redesign and release 1.1
- **New icon (replaces the season ring):** a **staggered line climbing up with an arrowhead at the end**, white on black (asked for: black background, white line, no dot, an arrow). Each step is higher than the last with small dips on the way, so it reads as progress. The whole drawing sits inside the 66 dp safe circle of the 108 dp layer (the first version had its end dot clipped by the round launcher mask, so the drawing was scaled down; the farthest point is 26 dp from the center plus the stroke). Background `#000000`, line `#FFFFFF`. The themed (Android 13+) layer is the same shape in one color. Still vector drawables only. Checked in the launcher on the phone.
- **Release APK 1.1** (`versionCode 2`, `versionName 1.1`): built with `./gradlew assembleRelease`, copied to `~/Desktop/Seasons-release/seasons-1.1.apk` (about 10 MB). Signed with the existing release key (`~/.seasons-signing/seasons-release.jks`, certificate `CN=Seasons, O=Personal`), checked with `apksigner verify`. Permissions are the same as 1.0 (wake lock, network state, boot completed, foreground service; **no internet**). Code shrinking is still off.
- **Not installed on the phone** (asked for). The phone keeps the debug build. A release build is signed with a different key, so installing it over the debug build would need the debug app uninstalled first, which deletes its data. Export a JSON backup first if you do that, then import it into the release app.
