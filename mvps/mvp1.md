# Build Prompt: "Seasons" — a learning tracker for Android

You are building a native Android app called **Seasons**. Read this whole spec before writing code. Build it in the phases listed at the end, and verify each phase before moving on. If something in this spec is ambiguous, pick the simplest option that keeps the rules below true and note it in `DECISIONS.md`.

---

## 1. Purpose (never lose sight of this)

Track a personal **learning journey**: anything that can be counted (pages, topics, lessons, problems, laps, minutes). The user's daily question is:

> **"Am I on track?"** → look back at **how much is done** → **how much is left** → **focus on today**.

The app's #1 job is to stay **in the user's face** through home-screen widgets, because the user forgets trackers that aren't visible. **No notifications. Ever.** Logging must be fast. Friction is the enemy.

---

## 2. Tech stack

- **Kotlin**, **Jetpack Compose** (Material 3), single-activity
- **Room** for local storage, **Kotlin Flow** + **ViewModel** (MVVM)
- **Jetpack Glance** for home-screen widgets
- **WorkManager** for the midnight widget refresh
- **Charts: custom Compose `Canvas`.** No chart library. Full control is needed for the design rules below.
- `minSdk 26`, `targetSdk` latest stable
- **Fully offline.** No internet permission. No analytics. No cloud.
- `java.time.LocalDate` everywhere. A day ends at **local midnight**. The week starts on **Monday**.

---

## 3. Visual design rules (from *Storytelling with Data*, non-negotiable)

- **Dark mode only.** Background near-black (`#121212`), surfaces `#1E1E1E`.
- **Everything is grey by default**: axes, labels, gridlines, bands (greys `#9E9E9E`, `#616161`, `#2C2C2C`).
- **Only the tracker's own color carries data.** One accent per tracker. Never rainbow.
- **No legends.** Label data directly on or next to the chart.
- **No gridlines, no chart borders, no 3D, no pie/donut charts, no diagonal text, no trailing zeros** (`12`, not `12.00`).
- **Bar charts always start at zero.**
- **The most important number is the biggest thing on screen.**
- Left-align text. Generous white space. One clear visual hierarchy per screen.
- Font: system default (Roboto).

---

## 4. Data model

### Tracker
| field | notes |
|---|---|
| `id` | |
| `name` | e.g. "The Mom Test" |
| `unit` | free text, e.g. "pages", "topics", "laps" |
| `type` | `GOAL` or `ONGOING` |
| `color` | ARGB int, user-picked. Colors may repeat across trackers |
| `groupId` | nullable. **A tracker belongs to at most one group** |
| `target` | GOAL only: total to reach (e.g. 200) |
| `deadline` | GOAL only, **optional** date |
| `bandPeriod` | `DAILY`, `WEEKLY`, or `NONE` |
| `startDate` | first day counted |
| `status` | `ACTIVE`, `ARCHIVED`, or `COMPLETED` |
| `completedDate` | set when it moves to the trophy shelf |
| `sortOrder` | |

- `GOAL` may use `DAILY` or `NONE` band.
- `ONGOING` must use `DAILY` or `WEEKLY` band.

### BandHistory (bounds change over time — this is a core feature)
| field | notes |
|---|---|
| `trackerId` | |
| `effectiveFrom` | date |
| `lower` | |
| `upper` | |

- The user's bounds **grow over time to show progress**. Editing a band **never rewrites history**. It inserts a new row effective from a chosen date (default: today).
- The band for any date = the row with the latest `effectiveFrom ≤ date`.
- For `WEEKLY` band trackers, lower/upper mean **active days per week** (e.g. 5–7). A day is "active" if its value is > 0.

### LogEntry
| field | notes |
|---|---|
| `id` | |
| `trackerId` | |
| `date` | the day it counts for (backfill allowed: any date ≥ tracker `startDate` and ≤ today) |
| `amount` | positive number |
| `createdAt` | timestamp |

- Logging is **additive**. Day total = sum of entries for that date.
- Entries can be edited or deleted.

### Group
`id`, `name`, `color`, `sortOrder`.

### Settings
`seasonStartDate` (user-set), `seasonLength` (days, user-set, **default 77**, allowed 7–365), `lastSeasonSummarySeen`.

### SeasonSummary (stored snapshot)
`seasonNumber`, `startDate`, `endDate`, `length`, `payloadJson` (per-tracker totals, best streaks, trophies, days active). Summaries are **saved as snapshots when a season ends**, so later settings changes never rewrite past seasons.

---

## 5. Stat definitions (implement exactly; unit-test every one)

Let `total(d)` = the sum of logs on date `d`, and `band(d)` = the active band on `d`.

### Done / Left (GOAL)
- `done = sum of all logs`
- `left = max(0, target − done)`
- `% = done / target`

### Your pace
- `pace = sum of logs over the last 7 days including today ÷ 7`

### Needed pace & projection (GOAL, priority order)
1. **Has a deadline:** `needed = left ÷ days remaining (inclusive of today)`. On track if `pace ≥ needed`. Show the projected finish date too.
2. **No deadline, has a daily band:** project the finish at the **band's lower bound**: `today + ceil(left ÷ lower)`. Also show the projection at your current pace.
3. **No deadline, no band:** projected finish at pace = `today + ceil(left ÷ pace)`. If pace = 0, show "—".

Status label:
- `On track` in the tracker's color
- `Behind` in amber `#FFB300`, with the gap: "need 3 more/day"
- `Done` when finished

### Streak day (DAILY band or NONE)
- **With a band:** the day counts if `total(d) ≥ band(d).lower`. **Going above the upper bound does NOT break the streak.** It is fine and still counts.
- **No band:** the day counts if `total(d) > 0`.
- **No freezes, no rest days.**

### Streaks
- **Current streak:** consecutive counting days ending today. **If today isn't met yet, count back from yesterday.** Today being in progress never breaks the streak.
- **Best streak:** the longest run ever.
- **Average streak length:** the mean length of all runs of length ≥ 1, including the current one. Show 1 decimal.

### WEEKLY band trackers
- Streaks are counted in **weeks** (Mon–Sun). A week counts if its active days ≥ `lower`.
- The current, unfinished week never breaks the streak.

### Goal completion
- When `done ≥ target`: show a celebration and a **"Move to trophy shelf"** action. The status becomes `COMPLETED` and `completedDate` is set.

---

## 6. Seasons (user-defined length, default 77 days)

- The user sets `seasonStartDate` and `seasonLength` in Settings (asked on first launch; defaults: today, 77). **Never hardcode 77** anywhere except the default value.
- Season `n = floor((date − seasonStartDate) / seasonLength) + 1`.
- Heatmap grid = **7 rows × ceil(seasonLength / 7) columns**. With the default 77 that's 11 × 7, a perfect grid. Unused trailing cells are not drawn.
- **Changing the season length:** ask for the date the new length starts (default today). That date becomes the new `seasonStartDate`. The current partial season is snapshotted as a summary first. Past summaries stay untouched.
- **Season summary:** on the first app open after a season ends, show a summary screen: per-tracker totals, best streaks within the season, trophies won during the season, and days active. Past summaries are browsable in a "Seasons" history screen.

---

## 7. Screens

### 7.1 Home
- Trackers grouped by group (ungrouped at the bottom).
- Each row shows: color dot, name, `done / target` (or today's value for ONGOING), a thin progress bar in the tracker color, today ✅/❌ vs band, and streak 🔥 N.
- FAB → new tracker.

### 7.2 Tracker detail (the core screen — hierarchy top to bottom)
1. **Header:**
   - GOAL: **big** `124 / 200 pages`, then `76 left · 62%`, plus a progress bar.
   - ONGOING: **big** today's value vs band.
2. **Status line:** `You 9/day · Need 6/day → On track · finish ~Oct 16`
3. **Today:** a quick-log row with **+1 / +5 / custom** buttons, plus a date chip (default today) to backfill. Show today's total vs band.
4. **Streaks:** current 🔥 · best · avg length.
5. **Chart tabs: Daily · Weekly · Season · On track**
   - **Daily:** bars for the last 14 days in the tracker color. The band is a faint grey stripe behind the bars, **stepping as the band changes over time** (that visible growth is the point). Days below the lower bound are drawn in dim grey. Label only today's bar value.
   - **Weekly:** one bar per week (last ~11 weeks). For WEEKLY band trackers, plot active days with the band stripe.
   - **Season:** a heatmap with one cell per season day (7 rows × ceil(length/7) columns), in season-day order. Color intensity = tracker color scaled by `total ÷ max(upper, the season max)`. Zero = `#2C2C2C`. Days that met the streak rule get full opacity; days with partial progress get reduced opacity. Show a "Season N · day X/length" label. Tap a cell to see its value.
   - **On track** (GOAL only): a **cumulative line** in the tracker color vs a straight grey dashed "needed" line from start to (deadline, target). With no deadline, draw the projection line instead. Direct labels at the line ends.
6. **History:** a list of log entries with edit/delete.
7. Menu: edit tracker, change band (new effective date), archive.

### 7.3 Create / edit tracker
- Name, unit, type (Goal / Ongoing), target, optional deadline, band period, lower/upper, group, start date.
- **Color picker:** a palette of ~16 colors that read well on dark backgrounds, plus a hex input.

### 7.4 Groups
- Create, rename, recolor, reorder. A group detail page shows its trackers and the combined widget preview.

### 7.5 Trophy shelf
- Grid of completed goals. Each card shows name, color, total + unit, days taken, start → finish dates, and best streak.

### 7.6 Archive
- Archived trackers. **Unarchive** asks: **"Continue where you left off"** (keep all logs) or **"Start fresh"** (create a new tracker with the same settings and a new start date; the old one stays archived as history).
- There is no pause outside of archive.

### 7.7 Settings
- Season start date and **season length** (days, default 77).
- **Export:** a full JSON backup and CSV of logs, via the Android **share sheet** (`ACTION_SEND`, FileProvider).
- **Import:** a JSON backup via the file picker. Import replaces all data after confirmation.
- No cloud, no accounts.

---

## 8. Widgets (Glance)

- **Single-tracker widget:** name, big `done/left` (or today vs band), progress bar in the tracker color, today ✅/❌, streak 🔥.
- **Group widget:** a list of the group's trackers, each with a color dot, mini progress bar, today ✅/❌, and streak. Resizable.
- A configuration activity picks the tracker or group.
- **Tap → deep-link into the app on that exact tracker or group.**
- Refresh immediately after any log or edit, and at **midnight** via WorkManager (so the "today" values roll over).
- Dark styling consistent with the app.

---

## 9. Explicitly out of scope

Notifications, reminders, cloud sync, Google Drive, accounts, importing the user's old CSVs, light mode, time-spent tracking, notes on logs, streak freezes.

---

## 10. Build phases (verify each before continuing)

1. **Project setup + Room schema + repository.** Unit-test every stat in section 5, including these edge cases: backfill, band changes mid-streak, over-upper days, today not yet met, weekly streaks, a deadline in the past, pace = 0.
2. **Home + create/edit tracker + logging (+1/+5/custom/backfill) + history edit/delete.**
3. **Tracker detail header, status line, streaks.**
4. **The four charts** in Canvas, following section 3 strictly.
5. **Groups, archive/unarchive, trophy shelf.**
6. **Seasons + season summary snapshots + history.** Test season lengths 7, 30, 77, 90, plus a mid-season length change.
7. **Widgets (single + group) + deep links + midnight refresh.**
8. **Export/import via share sheet and file picker.**
9. **Polish pass:** check every screen against section 3. Can "Am I on track?" be answered in under 3 seconds on the tracker screen and widget?

After each phase: build, run the tests, and summarize what was done and what's next. Keep `DECISIONS.md` updated.