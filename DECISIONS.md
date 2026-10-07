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
