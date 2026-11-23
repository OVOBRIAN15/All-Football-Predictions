package com.example.data.sources

import android.util.Log
import com.example.domain.model.CompetitionInfo
import com.example.domain.model.DataSourceMetadata
import com.example.domain.model.FixtureItem
import com.example.domain.model.FixtureStatus
import com.example.domain.model.GenderCategory
import com.example.domain.model.MatchResultRecord
import com.example.domain.model.SourceHealth
import com.example.domain.model.StandingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class OpenLigaDbAdapter : FootballDataAdapter {
    override val sourceId: String = "openligadb"

    override val defaultMetadata: DataSourceMetadata
        get() = FootballSourceCatalog.initialMetadata().first { it.sourceId == sourceId }

    private val leagueShortcutMap = mapOf(
        "openliga_bl1" to "bl1",
        "openliga_fbl1" to "fbl1",
        "openliga_bl2" to "bl2",
        "openliga_dfb" to "dfb"
    )

    override suspend fun fetchCompetitions(): List<CompetitionInfo> =
        FootballSourceCatalog.defaultCompetitions.filter { it.sourceId == sourceId }

    override suspend fun fetchUpcomingAndRecent(
        competition: CompetitionInfo
    ): Pair<List<FixtureItem>, List<MatchResultRecord>> = withContext(Dispatchers.IO) {
        val shortcut = leagueShortcutMap[competition.id] ?: return@withContext Pair(emptyList(), emptyList())
        val seasonYear = currentEuropeanSeasonYear()
        val url = "https://api.openligadb.de/getmatchdata/$shortcut/$seasonYear"
        val now = System.currentTimeMillis()
        val fixtures = mutableListOf<FixtureItem>()
        val results = mutableListOf<MatchResultRecord>()

        try {
            val req = Request.Builder().url(url).build()
            SharedHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("OpenLigaDbAdapter", "HTTP ${resp.code} for $url")
                    return@withContext Pair(emptyList(), emptyList())
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext Pair(emptyList(), emptyList())

                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val matchId = "oldb_${obj.optInt("matchID")}"
                    val kickoffMs = parseIsoUtc(obj.optString("matchDateTimeUTC")) ?: continue
                    val finished = obj.optBoolean("matchIsFinished", false)

                    val team1 = obj.optJSONObject("team1") ?: continue
                    val team2 = obj.optJSONObject("team2") ?: continue
                    val homeId = "oldb_t_${team1.optInt("teamId")}"
                    val awayId = "oldb_t_${team2.optInt("teamId")}"
                    val homeName = team1.optString("teamName").ifBlank { team1.optString("shortName") }
                    val awayName = team2.optString("teamName").ifBlank { team2.optString("shortName") }
                    val homeIcon = team1.optString("teamIconUrl").takeIf { it.startsWith("http") }
                    val awayIcon = team2.optString("teamIconUrl").takeIf { it.startsWith("http") }

                    val matchResults = obj.optJSONArray("matchResults")
                    var ftHome: Int? = null
                    var ftAway: Int? = null
                    var htHome: Int? = null
                    var htAway: Int? = null

                    if (matchResults != null) {
                        for (r in 0 until matchResults.length()) {
                            val resObj = matchResults.optJSONObject(r) ?: continue
                            val typeId = resObj.optInt("resultTypeID", -1)
                            if (typeId == 2) {
                                ftHome = resObj.optInt("pointsTeam1", 0)
                                ftAway = resObj.optInt("pointsTeam2", 0)
                            } else if (typeId == 1) {
                                htHome = resObj.optInt("pointsTeam1", 0)
                                htAway = resObj.optInt("pointsTeam2", 0)
                            }
                        }
                    }

                    // Extract minute-level goals for First-10-Min verified model input
                    val goalsArr = obj.optJSONArray("goals")
                    val homeGoalMins = mutableListOf<Int>()
                    val awayGoalMins = mutableListOf<Int>()
                    if (goalsArr != null && goalsArr.length() > 0) {
                        var prevHome = 0
                        var prevAway = 0
                        val sortedGoals = (0 until goalsArr.length()).mapNotNull { goalsArr.optJSONObject(it) }
                            .sortedBy { it.optInt("goalID", 0) }
                        for (g in sortedGoals) {
                            val min = g.optInt("matchMinute", -1)
                            val s1 = g.optInt("scoreTeam1", prevHome)
                            val s2 = g.optInt("scoreTeam2", prevAway)
                            if (min in 1..120) {
                                if (s1 > prevHome) homeGoalMins.add(min)
                                else if (s2 > prevAway) awayGoalMins.add(min)
                            }
                            prevHome = s1
                            prevAway = s2
                        }
                    }

                    val elapsedMinutes = ((now - kickoffMs) / 60_000L).toInt()
                    val status = when {
                        finished -> FixtureStatus.FINISHED
                        elapsedMinutes in 0..115 -> FixtureStatus.LIVE
                        else -> FixtureStatus.SCHEDULED
                    }

                    if (finished && ftHome != null && ftAway != null) {
                        results.add(
                            MatchResultRecord(
                                matchId = matchId,
                                dateEpochMs = kickoffMs,
                                competition = competition.name,
                                homeTeamId = homeId,
                                homeTeam = homeName,
                                awayTeamId = awayId,
                                awayTeam = awayName,
                                homeGoals = ftHome,
                                awayGoals = ftAway,
                                halftimeHomeGoals = htHome,
                                halftimeAwayGoals = htAway,
                                homeGoalMinutes = homeGoalMins,
                                awayGoalMinutes = awayGoalMins,
                                sourceName = "OpenLigaDB"
                            )
                        )
                    }

                    fixtures.add(
                        FixtureItem(
                            matchId = matchId,
                            homeTeamId = homeId,
                            homeTeam = homeName,
                            homeCrestUrl = homeIcon,
                            awayTeamId = awayId,
                            awayTeam = awayName,
                            awayCrestUrl = awayIcon,
                            competitionId = competition.id,
                            competition = competition.name,
                            country = competition.country,
                            gender = competition.gender,
                            kickoffEpochMs = kickoffMs,
                            status = status,
                            homeScore = ftHome,
                            awayScore = ftAway,
                            liveMinute = if (status == FixtureStatus.LIVE) elapsedMinutes.coerceIn(1, 90) else null,
                            historicalMatchesSample = 0,
                            sourceId = sourceId,
                            sourceName = "OpenLigaDB",
                            sourceUrl = "https://www.openligadb.de",
                            retrievedEpochMs = now
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("OpenLigaDbAdapter", "Failed fetching $url: ${e.message}")
        }
        Pair(fixtures, results)
    }

    override suspend fun fetchStandings(competition: CompetitionInfo): List<StandingRow> = withContext(Dispatchers.IO) {
        val shortcut = leagueShortcutMap[competition.id] ?: return@withContext emptyList()
        val seasonYear = currentEuropeanSeasonYear()
        val url = "https://api.openligadb.de/getbltable/$shortcut/$seasonYear"
        val rows = mutableListOf<StandingRow>()
        try {
            val req = Request.Builder().url(url).build()
            SharedHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    rows.add(
                        StandingRow(
                            position = i + 1,
                            teamId = "oldb_t_${obj.optInt("teamInfoId")}",
                            teamName = obj.optString("teamName"),
                            crestUrl = obj.optString("teamIconUrl").takeIf { it.startsWith("http") },
                            played = obj.optInt("matches"),
                            won = obj.optInt("won"),
                            drawn = obj.optInt("draw"),
                            lost = obj.optInt("lost"),
                            goalsFor = obj.optInt("goals"),
                            goalsAgainst = obj.optInt("opponentGoals"),
                            goalDifference = obj.optInt("goalDiff"),
                            points = obj.optInt("points")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("OpenLigaDbAdapter", "Failed standings $url: ${e.message}")
        }
        rows
    }

    private fun currentEuropeanSeasonYear(): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return if (month >= 7) year else year - 1
    }

    private fun parseIsoUtc(raw: String): Long? {
        if (raw.isBlank()) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
