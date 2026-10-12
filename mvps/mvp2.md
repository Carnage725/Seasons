# MVP 2: Seasons, next round

MVP 1 (`mvps/mvp1.md`) is built and shipped as v1.0 (DB version 1, backup format 1). This project keeps evolving. **The kanban board takes priority over old MVP specs.** Where this file and `mvp1.md` disagree, this file wins. Everything else in `mvp1.md` still applies: offline, no notifications, dark only, grey except the tracker color, no legends, bars start at zero, the stat definitions in section 5.

## Overrides to MVP 1

| MVP 1 | MVP 2 |
|---|---|
| §9: time-spent tracking out of scope | Allowed, through the stopwatch only (section 4) |
| §7.4 Groups, §8 group widget | Removed (section 1) |
| §7.6 Archive: "Continue" or "Start fresh" | Unarchive just resumes (section 2) |
| §7.2 text "Menu" buttons | Three-dot icon (section 3) |
| Backup format 1 | Format 2, format 1 still imports (section 4.4) |

## Technical baseline (verified in the code, 2026-10-12)

- Room database `seasons.db`, `version = 1`, `exportSchema = true`, schema file `app/schemas/.../1.json`. **No migration exists yet, and none may use `fallbackToDestructiveMigration()`.** The phone has real data. Every schema change in this MVP is a written `Migration` that keeps trackers, logs, bands, settings and summaries.
- Entities in `data/Entities.kt`: `Tracker` (has `groupId` FK to `groups`, `status` enum with `REPLACED`), `TrackerGroup` (table `groups`), `BandHistory`, `LogEntry(id, trackerId, date, amount, createdAt)`, `Settings`, `SeasonSummary`.
- Dates are stored as epoch-day `Long` (`Converters.kt`). Amounts are `Double`.
- Files that mention groups: `data/{AppDatabase, Backup, Daos, Entities, Repository, SeasonSnapshot, TrackerCopies}.kt`, `MainActivity.kt`, `SeasonsApp.kt`, `ui/{AppViewModels, BackupViewModel, Components, DeepLink, HomeScreen, HomeViewModel, Organize, OrganizeScreens, OrganizeViewModels, TrackerFormScreen, TrackerViewModels}.kt`, `widget/{WidgetConfigActivity, WidgetModel, Widgets}.kt`. Tests that touch them: `OrganizeTest`, `BackupTest`, `WidgetTest`, `RepositoryTest` (androidTest), and others found by compiling.
- `BACKUP_VERSION = 1` in `data/Backup.kt`. Import refuses `version > BACKUP_VERSION`.
- Per project rule 2, **read the current official docs before each phase** (Room migrations and `AutoMigration`, SQLite `ALTER TABLE` limits on API 26, Compose icons and date/time pickers, Glance state) and write a short Findings note before coding.

Migration plan: **one migration per phase**, DB version 2, 3, 4. Each has an instrumented migration test (`MigrationTestHelper`) that creates the old schema with data, runs the migration, and checks the data. Export the new schema JSON each time.

---

## 1. Remove the Groups feature completely

**Why:** not needed for now, adds clutter.

### Database (migration 1 to 2)
- Drop table `groups`. Remove `Tracker.groupId` and its index `index_trackers_groupId` and FK.
- SQLite on API 26 is 3.18, which has no `ALTER TABLE DROP COLUMN` (added in 3.35). So the migration **recreates the table**: create `trackers_new` without `groupId`, copy rows, drop `trackers`, rename, recreate indices. Run with foreign keys off (`PRAGMA foreign_keys=OFF` handled by Room's migration flow; check docs) so the child tables `band_history` and `log_entries` (ON DELETE CASCADE to `trackers`) are not wiped. **Test that logs and bands still exist after the migration.** A mistake here deletes every log.
- Alternative to check in the docs: `@AutoMigration` with `@DeleteColumn` and `@DeleteTable`. Room generates the same table recreate.
- Remove `TrackerGroup`, `GroupDao`, `Repository` group functions, and `groupId` from `Tracker`, `TrackerCopies.kt` and `SeasonSnapshot.kt`.

### UI and widget
- Remove: Groups screen, group page (`Organize*`), group chip row in `TrackerFormScreen`, group headers and "Ungrouped" in `HomeScreen`/`HomeViewModel`, the Groups entry in the Home menu, deep-link targets for groups in `DeepLink.kt` and `MainActivity.kt`.
- Remove the **"Seasons group" widget**: its receiver, provider XML, preview drawable, manifest entry, `WidgetConfigActivity` group branch, `WidgetModel` group code. Keep the single-tracker widget untouched.
- A group widget already placed on the home screen becomes a dead widget. The user removes it by hand. Note this in `DECISIONS.md`.

### Backup / export
- Export format 2 has no `groups` and no `groupId`.
- **Import of format 1 still works:** `groups` and `groupId` are read and ignored. The "every group reference points at something" check is dropped.
- CSV export drops the `group` column. New header: `date,tracker,amount,unit,logged_at` (plus the stopwatch columns in section 4).

### Done when
- `./gradlew test` and `connectedDebugAndroidTest` pass, including the migration test (insert groups, trackers, logs and bands in schema 1, migrate, check trackers, logs, bands, settings and summaries are all still there).
- `installDebug` over the existing data keeps every tracker and log.
- `grep -ri group` over `src/main` finds nothing in code or strings (apart from comments about the migration).
- A real format 1 backup file imports.

---

## 2. Simplify archive

**Why:** the "Continue where you left off / Start fresh" question is confusing.

- **Archive** (tracker menu, with confirm) sets `status = ARCHIVED`, as now.
- **Unarchive** from the Archive screen sets `status = ACTIVE` in one tap (a confirm dialog is fine). No question. The existing guarded "continue" code (`unarchiveContinue`, which does nothing if the tracker is no longer archived) becomes the only path.
- Remove `Repository` "start fresh" (the transaction that copies a tracker and sets the old one to `REPLACED`), `TrackerCopies.kt` if nothing else uses it, and the dialog.
- Remove `TrackerStatus.REPLACED`.
- **Migration 2 to 3:** `UPDATE trackers SET status = 'ARCHIVED' WHERE status = 'REPLACED'`. Room stores enums by name through the default converter, so this is a plain SQL update. Check the stored form in the schema.
- **Import:** a `REPLACED` status in a format 1 file is read as `ARCHIVED`. The enum validation must accept it as an alias. Format 2 never writes it.
- Streaks: the days spent archived count as missed (same as the old "Continue"). No stat change.
- Season snapshots built earlier keep their content. Snapshot code that checked `REPLACED` is simplified.

### Done when
- Unarchive resumes with all logs kept and no choice dialog.
- No `REPLACED` in `src/main` except in the migration and the import alias.
- Migration test: a `REPLACED` tracker becomes `ARCHIVED`, appears in the Archive list, and can be resumed or deleted.

---

## 3. Three-dot menu icon everywhere

- Replace every text "Menu" button with a **vertical three-dot icon button** at the top right (Material `Icons.Default.MoreVert`, check which icon artifact is needed in the docs before adding a dependency; `material-icons-core` may already cover it, and do not add `material-icons-extended`).
- Content description "More options" for screen readers. Tap target at least 48 dp.
- Home menu items: Trophies, Archive, Seasons, Settings.
- Tracker menu items: Edit tracker, Change band (only with a band), Archive.
- Any other place that shows a text menu gets the same icon. Search the UI for `DropdownMenu`.

### Done when
- No text "Menu" label is left on any screen, and every menu item still opens its screen or dialog (driven over `adb` on the phone).

---

## 4. Stopwatch with editable start and end times

### 4.1 Behavior

- Every tracker card on Home has a **stopwatch button at the top right**. The tracker screen has the same control. Tap once to start, tap again to stop. Placement of everything else stays as it is.
- **Start** writes a row to a new table `running_timer` (`trackerId`, `startTime` as epoch millis). Nothing ticks in the background. The elapsed time is always `now - startTime`, so it survives closing the app, locking the phone and process death. The UI recomputes it once a second while visible (a `LaunchedEffect` ticker), never from a service.
- **One timer at a time.** The table holds at most one row (`id` fixed to 1, same pattern as `Settings`). Starting a second tracker shows "Stop [name] first?", and confirming stops the old one into the Edit time screen.
- While running, the card shows "Running 2:13:05" in the tracker color, so a forgotten timer stays visible. **No notification is used.**
- **Stop** does not save anything yet. It opens the **Edit time screen** (4.2) with `start = startTime`, `end = now` and an empty amount.
- **Save** needs `amount > 0` (the existing log rule is kept) and `end > start`. It deletes the `running_timer` row and inserts one `LogEntry` in one Room transaction, so a crash cannot leave a log and a running timer both.
- **Cancel / back** on the stop screen **discards the time**: the `running_timer` row is deleted and nothing is logged. The user is asked to confirm, since the time is lost.
- **No manual minutes entry anywhere.** +1, +5 and Custom create entries with no start and end. This is on purpose, so users cannot guess or invent time. Time comes only from the stopwatch, and can then be corrected on the Edit time screen.
- Using a tracker with or without the stopwatch is the user's choice. Both kinds of entries are valid and appear side by side.
- **Log date** of a stopwatch entry is the **local date of `endTime`**. A timer left running overnight counts for the day it was stopped (agreed).

### 4.2 Edit time screen (modeled on the user's reference screenshot)

One screen, used in three places: stop, editing a running timer, editing a stopwatch entry in History. Same placement as the reference, plain text labels.

Top to bottom:
1. **Header strip** in the tracker color: tracker name, `1:19 PM - 1:34 PM`, and the duration (`15m`) at the right. It updates live with every edit.
2. **Start box:** label "Start", then date and time, for example `Oct 5  1:19 PM` (date in a muted tone, time large).
3. **Start adjust row:** seven buttons `-30  -5  -1  +1  +5  +30  Now`. Each changes the start time by that many **minutes**. `Now` sets it to the current time.
4. **End box:** label "End", same layout.
5. **End adjust row:** same seven buttons for the end time.
6. **Amount field** in the tracker's unit (the field the Custom popup has now), with the keyboard rules from MVP 1 (opens focused, scrolls above the keyboard, uses `FormDialog` behavior or a full screen).
7. **Delete** (only when editing an existing entry, with confirm) and **Save** (full width, bottom).

Not included from the reference: Statistics, Activity, Tag, Comment, Actions.

Rules:
- Tapping the date or time text opens the system date or time picker. Date pickers return UTC millis: convert with `ZoneOffset.UTC` for the picked date (as in Phase 2, `DECISIONS.md`), then combine with the local time.
- `end` must be after `start`, and `end` cannot be in the future. Otherwise Save is disabled and a short plain message explains why. For a running timer, the End box is not shown (it is still running).
- **Full editing power, only for timer data:** the start, end and amount of a stopwatch entry may be changed freely, within the two rules above. When a running timer is edited, only Start can change, and `running_timer.startTime` is updated on Save. Entries without start and end (plain +1, +5, Custom) keep the small amount-only edit dialog and cannot gain a time.
- Overlap between entries is allowed. No overlap check.
- Duration shown is `end - start` in whole minutes, floor, with `h` and `m` (`1h 5m`, `15m`). Under one minute shows `<1m`. Stored values are exact epoch millis.

### 4.3 Data (migration 3 to 4)

- `log_entries` gets two **nullable** columns: `startTime INTEGER` and `endTime INTEGER` (epoch millis). Added with `ALTER TABLE ... ADD COLUMN` (nullable, no default needed, fine on API 26). Old rows stay `NULL`. A row is a stopwatch entry exactly when both are non-null. Add a repository check that rejects only one of them being set.
- New table `running_timer(id INTEGER PRIMARY KEY, trackerId INTEGER NOT NULL, startTime INTEGER NOT NULL)` with an FK to `trackers(id)` ON DELETE CASCADE, so deleting a tracker clears its timer.
- Validation in `Repository` (extends `addLog`): `amount > 0`, `trackerId` exists, `startDate <= date <= today`, and for stopwatch entries `endTime > startTime`, `endTime <= now`, and `date == local date of endTime`.
- Editing a stopwatch entry's end time to another day changes its `date` to the new end date.
- **Midnight and the widget:** a running timer does not change widgets. Widgets are untouched by this MVP.
- Deleting a tracker with a running timer clears the timer by cascade.
- Changing the timezone mid-run is not special-cased: `startTime` is an absolute instant. The `date` is computed at Save time in the zone then in effect.

### 4.4 Backup and export

- **JSON backup format 2** (`BACKUP_VERSION = 2`): adds `startTime` and `endTime` (epoch millis, `null` when absent) to each log. It also writes the running timer as `"runningTimer": null | {"trackerId": n, "startTime": n}`. Groups are gone (section 1).
- **Format 1 backups must keep importing and must never become unusable.** Import accepts versions 1 and 2, refuses anything higher. A format 1 file has no time fields, so every log gets `NULL`. Missing `runningTimer` means none. The existing all-or-nothing import transaction and checks stay.
- New import checks for format 2: `startTime` and `endTime` both present or both `null`, `endTime > startTime`, the running timer's tracker exists, and `startTime` is not in the future.
- Add a **test fixture**: a real format 1 file (taken from the user's own backup, with names changed if needed) in `src/test/resources/`, imported by a unit test. This is the guarantee for "v1 data does not become unusable".
- **CSV** columns: `date,tracker,amount,unit,start,end,minutes,logged_at`. `start` and `end` are ISO local date-time (`yyyy-MM-dd'T'HH:mm`), `minutes` is whole minutes, all empty for plain entries. Keep the existing quoting and the formula-injection guard.

### 4.5 Stats and display

- History rows show the amount and, when present, the time range and duration (`15m`) in muted grey.
- **No existing stat changes**: done, left, streaks, needed pace, season snapshots and the Home texts use `amount` only. Time is extra data. Snapshots (`payloadJson`) do not store time in this MVP.
- Tracker screen gets a small totals line when the tracker has any stopwatch entries: today's time and the 14-day time total. Format `1h 5m`. Nothing is shown when there is no time data.

### Done when
- Start, force-stop the app, reopen: the timer shows the right elapsed time and Stop gives the right start and end.
- Starting a second tracker's timer asks first, and only one `running_timer` row ever exists.
- Cancel on the stop screen discards the time and logs nothing. Save logs amount, start and end in one transaction.
- Every adjust button changes the time by exactly its minutes, `Now` sets the current time, and Save is blocked when `end <= start`, `end` is in the future, or amount is empty or zero.
- A stopwatch entry opens the Edit time screen from History. A plain entry opens the old amount dialog.
- Migration 3 to 4 test: old logs survive with `NULL` times.
- Format 1 fixture imports. A format 2 export imports back to identical data (round trip test).

---

## 5. Time on the Daily chart

**Book rule (ch. 2, "Secondary y-axis: generally not a good idea"):** different units must not share a second y-axis. Pull the charts apart vertically and share the x-axis, each with its own left axis.

- Data: add `minutesPerDay` to the Daily chart model in `ui/charts/ChartModels.kt` (pure function, unit-tested): for each of the 14 days, the sum of `(endTime - startTime)` of stopwatch entries whose `date` is that day, in minutes.
- Draw in `ui/charts/Charts.kt` (Canvas, no chart library): the existing amount chart stays exactly as is. Under it, a **thin minutes strip** over the same 14 x positions: grey bars (`#616161`, label `#9E9E9E`), zero baseline, no gridlines, no legend. Only today's value is labeled, as in the amount chart. The strip's own scale starts at 0 and ends at the 14-day max minutes.
- The strip appears only when at least one of the 14 days has minutes. A tracker with no stopwatch data renders pixel-identical to MVP 1.
- The single-tracker widget graph (`widget/ChartBitmap.kt`) is **not changed** in this MVP.
- Extend the Daily chart's screen-reader description (pure function, `UiLogicTest`) with the minutes.
- Weekly and Season tabs are unchanged.

### Done when
- Unit tests cover the minutes per day (no data, one entry, two entries on one day, an entry crossing midnight counted on the end date).
- On the phone: a tracker with stopwatch entries shows the strip under the bars, a tracker without them looks unchanged, and the chart follows `mvp1.md` section 3.

---

## Not in MVP 2

- **UI revamp** (Apple-style or any other redesign). Postponed to another day. Placement of everything stays as it is.
- **Season calendar and richer season control** (board item). Not discussed yet.
- **Default goal and deadline for every tracker.** Decided: stays as is (deadline optional, GOAL only).
- Time on the Weekly or Season tabs, on widgets, or in season snapshots.
- Manual minutes entry, tags, comments, notifications.

## Open questions

1. Minutes strip on the Weekly tab later?
2. Should time totals ever appear on Home cards besides the "Running" label?

## Build phases (one at a time, each verified before the next)

| # | Phase | DB | Notes |
|---|---|---|---|
| 1 | Remove groups + three-dot menu icon | v1 to v2 | Highest data risk. Migration test first |
| 2 | Archive simplification | v2 to v3 | `REPLACED` becomes `ARCHIVED` |
| 3 | Stopwatch data layer: `startTime`/`endTime`, `running_timer`, repository rules, backup v2, v1 import fixture, CSV | v3 to v4 | Pure logic and tests only, no UI |
| 4 | Stopwatch UI: Home button, tracker control, Edit time screen, History | none | Test on the phone with `adb` |
| 5 | Daily chart minutes strip + description | none | Pure model tests, then visual check |
| 6 | Final pass against `mvp1.md` §3, `DECISIONS.md`, version bump | none | Release build only if asked |

Per phase, in this order (project rules): short Findings note from the docs, short plan, user approval, implement, `./gradlew test`, `./gradlew connectedDebugAndroidTest`, `./gradlew installDebug`, drive the app over `adb`, then a "How to test on your phone" checklist. Commit only after the user confirms it works. Log every non-obvious choice in `DECISIONS.md`.

**Warning for every phase:** `connectedDebugAndroidTest` uninstalls the app and wipes its data on the phone. Export a JSON backup first, and re-import it after the tests.

---

## Status: built (version 1.1)

All six phases are built, tested (207 unit tests, 46 instrumented tests including the database migrations) and committed. Differences from the plan above, all agreed in the chat or found while building:

- **Long press** on a Home card edits the start of that tracker's running timer (the tracker screen also has an "Edit start" button). Added at your request.
- **Zero minutes shows "0m"**, not "<1m".
- **Backup format 2** was introduced in phase 1 (without groups) and extended in phase 3 (with stopwatch fields). Format 1 files still import; `REPLACED` in an old file is read as `ARCHIVED`.
- **Timer screens carry a token** so each opening gets a fresh view model.
- **The three-dot icon is drawn** with a Canvas instead of an icon library.
- **Debug builds** pin kotlinx-serialization 1.8.1 so `room-testing` works. Release builds are untouched.
- The details of every choice are in `DECISIONS.md` ("MVP 2, Phase 1" to "Phase 6").
- **Section 5 changed after phase 6:** the time is now a **line over the Daily bars** (book option: label the points directly, no second axis), not a separate strip. See `DECISIONS.md`.
