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
