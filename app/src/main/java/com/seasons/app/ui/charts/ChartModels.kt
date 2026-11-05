package com.seasons.app.ui.charts

import com.seasons.app.data.BandPeriod
import com.seasons.app.data.Tracker
import com.seasons.app.domain.Band
import com.seasons.app.domain.bandOn
import com.seasons.app.domain.dayMet
import com.seasons.app.domain.heatmapColumns
import com.seasons.app.domain.seasonDay
import com.seasons.app.domain.seasonNumber
import com.seasons.app.domain.seasonStartDate
import com.seasons.app.ui.formatAmount
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

// Pure data for the charts. The Canvas code only draws these. No Android, so they are unit-tested.

fun shortDate(date: LocalDate, today: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, yyyy", Locale.US))

private fun range(b: Band) = formatAmount(b.lower)

// ---------- Bars (Daily and Weekly) ----------

data class BarSpec(
    val value: Double,
    /** The band minimum for that bar, drawn as a line. Null when there is no band for that bar. */
    val lower: Double?,
    /** Drawn dim grey instead of the tracker color. */
    val dim: Boolean,
    /** Says which day or week the bar is, shown when the bar is tapped. */
    val label: String = "",
)

/** Which bar a tap at [x] hits, or null when the tap is outside the plot. */
fun barIndexAt(x: Float, plotWidth: Float, barCount: Int): Int? {
    if (barCount <= 0 || plotWidth <= 0f || x < 0f || x >= plotWidth) return null
    return (x / (plotWidth / barCount)).toInt().coerceIn(0, barCount - 1)
}

data class BarChartModel(
    val bars: List<BarSpec>,
    val maxY: Double,
    val bandLabel: String?,
    val firstLabel: String,
    val lastLabel: String,
)

/** Last [days] days ending today. Minimum line only for DAILY band trackers. */
fun buildDailyChart(
    tracker: Tracker,
    totals: Map<LocalDate, Double>,
    bands: List<Band>,
    today: LocalDate,
    days: Int = 14,
): BarChartModel {
    val daily = tracker.bandPeriod == BandPeriod.DAILY
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val bars = dates.map { d ->
        val value = totals[d] ?: 0.0
        val band = if (daily) bandOn(bands, d) else null
        BarSpec(value, band?.lower, dim = band != null && value < band.lower, label = shortDate(d, today))
    }
    val todayBand = if (daily) bandOn(bands, today) else null
    return BarChartModel(
        bars = bars,
        maxY = maxY(bars),
        bandLabel = todayBand?.let { range(it) },
        firstLabel = shortDate(dates.first(), today),
        lastLabel = "Today",
    )
}

/** Last [weeks] Monday-start weeks ending with the current one. WEEKLY band trackers plot active days. */
fun buildWeeklyChart(
    tracker: Tracker,
    totals: Map<LocalDate, Double>,
    bands: List<Band>,
    today: LocalDate,
    weeks: Int = 11,
): BarChartModel {
    val weekly = tracker.bandPeriod == BandPeriod.WEEKLY
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val mondays = (weeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
    val bars = mondays.map { monday ->
        val days = (0L..6L).map { monday.plusDays(it) }.filter { !it.isAfter(today) }
        if (weekly) {
            val active = days.count { (totals[it] ?: 0.0) > 0.0 }.toDouble()
            // Same rule as the weekly streak: the band in effect on the week's Monday (or the start date).
            val beforeStart = monday.plusDays(6).isBefore(tracker.startDate)
            val band = if (beforeStart) null else bandOn(bands, laterOf(monday, tracker.startDate))
            BarSpec(active, band?.lower, dim = false, label = "Week of ${shortDate(monday, today)}")
        } else {
            BarSpec(days.sumOf { totals[it] ?: 0.0 }, null, dim = false, label = "Week of ${shortDate(monday, today)}")
        }
    }
    val currentBand = if (weekly) bandOn(bands, laterOf(thisMonday, tracker.startDate)) else null
    return BarChartModel(
        bars = bars,
        maxY = maxY(bars),
        bandLabel = currentBand?.let { range(it) },
        firstLabel = shortDate(mondays.first(), today),
        lastLabel = "This week",
    )
}

private fun laterOf(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) a else b

private fun maxY(bars: List<BarSpec>): Double =
    max(1.0, bars.maxOf { max(it.value, it.lower ?: 0.0) })

// ---------- Season heatmap ----------

data class SeasonCell(
    val index: Int,
    val date: LocalDate,
    val total: Double,
    /** total / max(that day's minimum, season max), 0..1. */
    val ratio: Float,
    /** Met the streak rule that day. */
    val met: Boolean,
    /** False for days before the tracker started or still in the future. */
    val counted: Boolean,
)

data class SeasonModel(
    val number: Int,
    val dayOfSeason: Int,
    val length: Int,
    val columns: Int,
    val cells: List<SeasonCell>,
)

/** The season that contains [today]. Null if the first season has not started yet. */
fun buildSeasonModel(
    tracker: Tracker,
    totals: Map<LocalDate, Double>,
    bands: List<Band>,
    today: LocalDate,
    seasonStart: LocalDate,
    seasonLength: Int,
    /** Seasons already snapshotted before this start date. Added to the spec's season number for display. */
    numberBase: Int = 0,
): SeasonModel? {
    if (today.isBefore(seasonStart)) return null
    val formulaNumber = seasonNumber(today, seasonStart, seasonLength)
    val number = numberBase + formulaNumber
    val first = seasonStartDate(formulaNumber, seasonStart, seasonLength)
    val daily = tracker.bandPeriod == BandPeriod.DAILY

    val dates = (0 until seasonLength).map { first.plusDays(it.toLong()) }
    fun counted(d: LocalDate) = !d.isBefore(tracker.startDate) && !d.isAfter(today)
    val seasonMax = dates.filter(::counted).maxOfOrNull { totals[it] ?: 0.0 } ?: 0.0

    val cells = dates.mapIndexed { i, d ->
        val isCounted = counted(d)
        val total = if (isCounted) totals[d] ?: 0.0 else 0.0
        val band = if (daily) bandOn(bands, d) else null
        val denominator = max(band?.lower ?: 0.0, seasonMax)
        SeasonCell(
            index = i,
            date = d,
            total = total,
            ratio = if (denominator > 0.0) (total / denominator).toFloat().coerceIn(0f, 1f) else 0f,
            met = isCounted && dayMet(total, band),
            counted = isCounted,
        )
    }
    return SeasonModel(
        number = number,
        dayOfSeason = seasonDay(today, seasonStart, seasonLength),
        length = seasonLength,
        columns = heatmapColumns(seasonLength),
        cells = cells,
    )
}

// ---------- On track (goals) ----------

data class ChartPoint(val dayOffset: Int, val value: Double)

data class OnTrackModel(
    val target: Double,
    val maxY: Double,
    /** Largest x: days from the start date to the last thing drawn. At least 1. */
    val maxDayOffset: Int,
    val cumulative: List<ChartPoint>,
    /** Goals with a deadline: straight line from (start, 0) to (deadline, target). */
    val needed: Pair<ChartPoint, ChartPoint>?,
    val cumulativeLabel: String,
    val lineLabel: String?,
    val firstLabel: String,
    val lastLabel: String,
)

fun buildOnTrackChart(
    tracker: Tracker,
    totals: Map<LocalDate, Double>,
    today: LocalDate,
): OnTrackModel? {
    val target = tracker.target ?: return null
    val start = tracker.startDate
    if (today.isBefore(start)) return null

    val todayOffset = ChronoUnit.DAYS.between(start, today).toInt()
    var running = 0.0
    val cumulative = (0..todayOffset).map { i ->
        running += totals[start.plusDays(i.toLong())] ?: 0.0
        ChartPoint(i, running)
    }
    val done = running
    fun offset(d: LocalDate) = ChronoUnit.DAYS.between(start, d).toInt()

    var needed: Pair<ChartPoint, ChartPoint>? = null
    var lineLabel: String? = null
    var endDate = today

    if (tracker.deadline != null) {
        val dl = tracker.deadline
        needed = ChartPoint(0, 0.0) to ChartPoint(offset(dl), target)
        lineLabel = "${formatAmount(target)} by ${shortDate(dl, today)}"
        if (dl.isAfter(endDate)) endDate = dl
    } else {
        // No deadline: the target is a flat line. The slope of the progress line is the pace.
        needed = ChartPoint(0, target) to ChartPoint(max(1, offset(endDate)), target)
        lineLabel = "Target ${formatAmount(target)}"
    }

    return OnTrackModel(
        target = target,
        maxY = max(target, done),
        maxDayOffset = max(1, offset(endDate)),
        cumulative = cumulative,
        needed = needed,
        cumulativeLabel = formatAmount(done),
        lineLabel = lineLabel,
        firstLabel = shortDate(start, today),
        lastLabel = shortDate(endDate, today),
    )
}


// ---------- Pace for trackers without a target: average per day ----------

data class AveragePaceModel(
    /** One point per day since the tracker started, at most [days]. dayOffset 0 is the first point. */
    val points: List<ChartPoint>,
    val maxY: Double,
    val currentLabel: String,
    val firstLabel: String,
    val lastLabel: String,
)

/**
 * For each of the last [days] days: the average per day over the [window] days ending that day.
 * Near the start of the tracker the window is shorter (only days since the start count).
 * Null before the tracker starts.
 */
fun buildAveragePace(
    tracker: Tracker,
    totals: Map<LocalDate, Double>,
    today: LocalDate,
    days: Int = 30,
    window: Int = 7,
): AveragePaceModel? {
    val start = tracker.startDate
    if (today.isBefore(start)) return null
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.filter { !it.isBefore(start) }
    val values = dates.map { d ->
        val from = if (d.minusDays((window - 1).toLong()).isBefore(start)) start else d.minusDays((window - 1).toLong())
        val count = ChronoUnit.DAYS.between(from, d).toInt() + 1
        (0 until count).sumOf { totals[from.plusDays(it.toLong())] ?: 0.0 } / count
    }
    val points = values.mapIndexed { i, v -> ChartPoint(i, v) }
    val peak = values.max()
    return AveragePaceModel(
        points = points,
        maxY = if (peak > 0.0) peak else 1.0,
        currentLabel = "${formatAmount(Math.round(values.last() * 10) / 10.0)}/day",
        firstLabel = shortDate(dates.first(), today),
        lastLabel = "Today",
    )
}

// ---------- Spoken descriptions (for screen readers) ----------

fun describeBarChart(title: String, model: BarChartModel, unit: String): String = buildString {
    val last = model.bars.last()
    append(title).append(". ").append(model.lastLabel).append(": ").append(formatAmount(last.value)).append(' ').append(unit).append('.')
    model.bandLabel?.let { append(" Minimum ").append(it).append('.') }
    append(" Highest bar ").append(formatAmount(model.bars.maxOf { it.value })).append('.')
}

fun describeSeason(model: SeasonModel): String =
    "Season ${model.number} heatmap, day ${model.dayOfSeason} of ${model.length}. " +
        "${model.cells.count { it.met }} days met their goal. Tap a day to hear its value below."

fun describeAveragePace(model: AveragePaceModel, unit: String): String =
    "Pace chart. Average per day over the last 7 days, now ${model.currentLabel.removeSuffix("/day")} $unit per day."

fun describeOnTrack(model: OnTrackModel, unit: String): String = buildString {
    append("Progress chart. Done ").append(model.cumulativeLabel).append(" of ").append(formatAmount(model.target)).append(' ').append(unit).append('.')
    model.lineLabel?.let { append(" Line: ").append(it).append('.') }
}
