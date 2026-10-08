package com.seasons.app

import com.seasons.app.data.BackupData
import com.seasons.app.data.BackupException
import com.seasons.app.data.BandHistory
import com.seasons.app.data.BandPeriod
import com.seasons.app.data.LogEntry
import com.seasons.app.data.SeasonPayload
import com.seasons.app.data.SeasonSummary
import com.seasons.app.data.SeasonTrackerStat
import com.seasons.app.data.Settings
import com.seasons.app.data.Tracker
import com.seasons.app.data.TrackerGroup
import com.seasons.app.data.TrackerStatus
import com.seasons.app.data.TrackerType
import com.seasons.app.data.csvField
import com.seasons.app.data.logsToCsv
import com.seasons.app.data.parseBackup
import com.seasons.app.data.toJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupTest {
    private val exportedAt = Instant.parse("2026-10-08T09:00:00Z")
    private val d = LocalDate.of(2026, 10, 7)

    private val payload = SeasonPayload(
        3,
        listOf(SeasonTrackerStat(1, "The \"Mom\" Test", "pages", 5, 12.5, 4, false)),
        emptyList(),
    ).toJson()

    private fun sample() = BackupData(
        settings = Settings(seasonStartDate = d.minusDays(30), seasonLength = 30, lastSeasonSummarySeen = 2),
        groups = listOf(TrackerGroup(1, "Study, \"deep\" work", -16711936, 0), TrackerGroup(2, "Fit", 7, 1)),
        trackers = listOf(
            Tracker(
                id = 1, name = "The Mom Test é 日本", unit = "pages", type = TrackerType.GOAL, color = -12345, groupId = 1,
                target = 200.5, deadline = d.plusDays(10), bandPeriod = BandPeriod.DAILY, startDate = d.minusDays(20),
                status = TrackerStatus.ACTIVE, completedDate = null, sortOrder = 0,
            ),
            Tracker(
                id = 2, name = "Run", unit = "laps", type = TrackerType.ONGOING, color = 99, groupId = null, target = null,
                deadline = null, bandPeriod = BandPeriod.WEEKLY, startDate = d.minusDays(60), status = TrackerStatus.ARCHIVED,
                completedDate = null, sortOrder = 3,
            ),
            Tracker(
                id = 3, name = "Done goal", unit = "x", type = TrackerType.GOAL, color = 1, groupId = 2, target = 10.0,
                deadline = null, bandPeriod = BandPeriod.NONE, startDate = d.minusDays(5), status = TrackerStatus.COMPLETED,
                completedDate = d.minusDays(1), sortOrder = 4,
            ),
            Tracker(
                id = 4, name = "Old", unit = "x", type = TrackerType.ONGOING, color = 1, groupId = null, target = null,
                deadline = null, bandPeriod = BandPeriod.DAILY, startDate = d.minusDays(5), status = TrackerStatus.REPLACED,
                completedDate = null, sortOrder = 5,
            ),
        ),
        bands = listOf(
            BandHistory(1, 1, d.minusDays(20), 3.0, 6.0),
            BandHistory(2, 1, d.minusDays(2), 5.5, 8.0),
            BandHistory(3, 2, d.minusDays(60), 2.0, 5.0),
        ),
        logs = listOf(
            LogEntry(1, 1, d, 0.1 + 0.2, 1_700_000_000_123L),
            LogEntry(2, 1, d.minusDays(1), 1e-3, 1_700_000_000_456L),
            LogEntry(3, 2, d.minusDays(3), 1234567.89, 0L),
        ),
        summaries = listOf(
            SeasonSummary(1, 1, d.minusDays(60), d.minusDays(31), 30, payload),
        ),
    )

    private fun json(mutate: (JSONObject) -> Unit): String {
        val root = JSONObject(sample().toJson(exportedAt))
        mutate(root)
        return root.toString()
    }

    private fun assertRejected(containing: String, text: String) {
        try {
            parseBackup(text)
            fail("expected BackupException containing \"$containing\"")
        } catch (e: BackupException) {
            assertTrue("message was: ${e.message}", e.message!!.contains(containing, ignoreCase = true))
        }
    }

    // ---------- round trip ----------

    @Test fun roundTrip_keepsEverythingExactly() {
        val original = sample()
        assertEquals(original, parseBackup(original.toJson(exportedAt)))
    }

    @Test fun roundTrip_noSettings_emptyLists() {
        val empty = BackupData(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, parseBackup(empty.toJson(exportedAt)))
    }

    @Test fun roundTrip_doublesComeBackBitForBit() {
        val parsed = parseBackup(sample().toJson(exportedAt))
        assertEquals(0.1 + 0.2, parsed.logs[0].amount, 0.0)
        assertEquals(1e-3, parsed.logs[1].amount, 0.0)
        assertEquals(200.5, parsed.trackers[0].target!!, 0.0)
    }

    @Test fun export_hasAppNameVersionAndTime() {
        val root = JSONObject(sample().toJson(exportedAt))
        assertEquals("Seasons", root.getString("app"))
        assertEquals(1, root.getInt("version"))
        assertEquals("2026-10-08T09:00:00Z", root.getString("exportedAt"))
    }

    // ---------- rejected files ----------

    @Test fun rejects_notJson() = assertRejected("not valid JSON", "hello, this is not json")

    @Test fun rejects_emptyFile() = assertRejected("not valid JSON", "")

    @Test fun rejects_jsonArrayInsteadOfObject() = assertRejected("not valid JSON", "[1,2,3]")

    @Test fun rejects_otherAppsJson() = assertRejected("not a Seasons backup", """{"hello":"world"}""")

    @Test fun rejects_wrongAppName() = assertRejected("not a Seasons backup", json { it.put("app", "Other") })

    @Test fun rejects_newerVersion() = assertRejected("newer version", json { it.put("version", 2) })

    @Test fun rejects_missingVersion() = assertRejected("version", json { it.remove("version") })

    @Test fun rejects_missingSection() = assertRejected("damaged or incomplete", json { it.remove("logs") })

    @Test fun rejects_unknownEnumValue() = assertRejected("Unknown tracker type", json {
        it.getJSONArray("trackers").getJSONObject(0).put("type", "WEIRD")
    })

    @Test fun rejects_badDate() = assertRejected("Bad date", json {
        it.getJSONArray("logs").getJSONObject(0).put("date", "07/10/2026")
    })

    @Test fun rejects_nonPositiveAmount() {
        assertRejected("not positive", json { it.getJSONArray("logs").getJSONObject(0).put("amount", 0) })
        assertRejected("not positive", json { it.getJSONArray("logs").getJSONObject(0).put("amount", -3) })
    }

    @Test fun rejects_logPointingAtMissingTracker() = assertRejected("tracker that is not in the file", json {
        it.getJSONArray("logs").getJSONObject(0).put("trackerId", 99)
    })

    @Test fun rejects_bandPointingAtMissingTracker() = assertRejected("tracker that is not in the file", json {
        it.getJSONArray("bands").getJSONObject(0).put("trackerId", 99)
    })

    @Test fun rejects_trackerPointingAtMissingGroup() = assertRejected("group that is not in the file", json {
        it.getJSONArray("trackers").getJSONObject(0).put("groupId", 99)
    })

    @Test fun rejects_duplicateIds() {
        assertRejected("share the same id", json { it.getJSONArray("trackers").getJSONObject(1).put("id", 1) })
        assertRejected("share the same id", json { it.getJSONArray("logs").getJSONObject(1).put("id", 1) })
        assertRejected("share the same id", json { it.getJSONArray("groups").getJSONObject(1).put("id", 1) })
    }

    @Test fun rejects_zeroOrNegativeId() = assertRejected("invalid id", json {
        it.getJSONArray("logs").getJSONObject(0).put("id", 0)
    })

    @Test fun rejects_badBandBounds() {
        assertRejected("invalid bounds", json { it.getJSONArray("bands").getJSONObject(0).put("lower", 9).put("upper", 2) })
        assertRejected("invalid bounds", json { it.getJSONArray("bands").getJSONObject(0).put("lower", -1) })
    }

    @Test fun rejects_twoBandRowsForTheSameDay() = assertRejected("same date", json {
        val bands = it.getJSONArray("bands")
        bands.getJSONObject(1).put("trackerId", 1).put("effectiveFrom", bands.getJSONObject(0).getString("effectiveFrom"))
    })

    @Test fun rejects_seasonLengthOutOfRange() {
        assertRejected("Season length", json { it.getJSONObject("settings").put("seasonLength", 6) })
        assertRejected("Season length", json { it.getJSONObject("settings").put("seasonLength", 366) })
    }

    @Test fun rejects_goalWithoutTarget() = assertRejected("no target", json {
        it.getJSONArray("trackers").getJSONObject(0).put("target", JSONObject.NULL)
    })

    @Test fun rejects_damagedSummaryPayload() = assertRejected("damaged summary", json {
        it.getJSONArray("summaries").getJSONObject(0).put("payloadJson", "{nope")
    })

    @Test fun rejects_summaryEndingBeforeItStarts() = assertRejected("ends before it starts", json {
        it.getJSONArray("summaries").getJSONObject(0).put("endDate", d.minusDays(100).toString())
    })

    @Test fun rejects_blankNames() {
        assertRejected("no name or unit", json { it.getJSONArray("trackers").getJSONObject(0).put("name", "  ") })
        assertRejected("no name", json { it.getJSONArray("groups").getJSONObject(0).put("name", "") })
    }

    @Test fun acceptsAnEmptyButValidBackup() {
        val text = """{"app":"Seasons","version":1,"settings":null,"groups":[],"trackers":[],"bands":[],"logs":[],"summaries":[]}"""
        assertEquals(BackupData(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), parseBackup(text))
    }

    @Test fun ignoresExtraUnknownFields() {
        val parsed = parseBackup(json { it.put("somethingNew", JSONArray().put(1)) })
        assertEquals(sample(), parsed)
    }

    // ---------- CSV ----------

    @Test fun csv_headerAndRowsOldestFirst() {
        val data = sample()
        val lines = logsToCsv(data.trackers, data.groups, data.logs).split("\r\n").filter { it.isNotEmpty() }
        assertEquals("date,tracker,group,amount,unit,logged_at", lines[0])
        assertEquals(3, lines.size - 1)
        assertTrue(lines[1].startsWith("2026-10-04,")) // oldest
        assertTrue(lines[3].startsWith("2026-10-07,")) // newest
    }

    @Test fun csv_groupNameAndNoGroup() {
        val data = sample()
        val lines = logsToCsv(data.trackers, data.groups, data.logs).split("\r\n")
        assertEquals("2026-10-04,Run,,1234567.89,laps,1970-01-01T00:00:00Z", lines[1])
        assertEquals(
            "2026-10-07,The Mom Test é 日本,\"Study, \"\"deep\"\" work\",0.3,pages,2023-11-14T22:13:20.123Z".replace("0.3,", "0.30000000000000004,"),
            lines[3],
        )
    }

    @Test fun csv_endsEveryLineWithCrlf_andHasNoBareNewlines() {
        val text = logsToCsv(sample().trackers, sample().groups, sample().logs)
        assertTrue(text.endsWith("\r\n"))
        assertEquals(0, text.replace("\r\n", "").count { it == '\n' })
    }

    @Test fun csvField_quotesCommasQuotesAndNewlines() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", csvField("line1\nline2"))
        assertEquals("\"a\rb\"", csvField("a\rb"))
        assertEquals("", csvField(""))
    }

    @Test fun csvField_stopsFormulaInjection() {
        assertEquals("'=SUM(A1)", csvField("=SUM(A1)"))
        assertEquals("'+1", csvField("+1"))
        assertEquals("'-1", csvField("-1"))
        assertEquals("'@cmd", csvField("@cmd"))
        assertEquals("a=b", csvField("a=b")) // only a leading character matters
        assertEquals("\"'=1,2\"", csvField("=1,2")) // guard first, then quoting
    }

    @Test fun csv_amountsHaveNoTrailingZerosOrExponent() {
        val t = sample().trackers
        val logs = listOf(
            LogEntry(1, 1, d, 5.0, 0), LogEntry(2, 1, d, 0.5, 0), LogEntry(3, 1, d, 1e-7, 0), LogEntry(4, 1, d, 1e10, 0),
        )
        val rows = logsToCsv(t, sample().groups, logs).split("\r\n").drop(1).filter { it.isNotEmpty() }
        val amounts = rows.map { it.split(",").let { f -> f[f.size - 3] } }
        assertEquals(listOf("5", "0.5", "0.0000001", "10000000000"), amounts)
    }

    @Test fun csv_noLogs_isJustTheHeader() {
        assertEquals("date,tracker,group,amount,unit,logged_at\r\n", logsToCsv(emptyList(), emptyList(), emptyList()))
    }
}
