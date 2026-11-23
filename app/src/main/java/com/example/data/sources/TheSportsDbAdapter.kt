package com.example.data.sources

import android.util.Log
import com.example.domain.model.CompetitionInfo
import com.example.domain.model.DataSourceMetadata
import com.example.domain.model.FixtureItem
import com.example.domain.model.FixtureStatus
import com.example.domain.model.GenderCategory
import com.example.domain.model.MatchResultRecord
import com.example.domain.model.StandingRow
import com.example.domain.model.TeamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TheSportsDbAdapter : FootballDataAdapter {
    override val sourceId: String = "thesportsdb"

    override val defaultMetadata: DataSourceMetadata
        get() = FootballSourceCatalog.initialMetadata().first { it.sourceId == sourceId }

    private val baseUrl = "https://www.thesportsdb.com/api/v1/json/3"

    private val leagueIdMap = mapOf(
        "tsdb_4328" to "4328",
        "tsdb_4335" to "4335",
        "tsdb_4332" to "4332",
        "tsdb_4334" to "4334",
        "tsdb_4480" to "4480",
        "tsdb_4899" to "4899"
    )

    override suspend fun fetchCompetitions(): List<CompetitionInfo> =
        FootballSourceCatalog.defaultCompetitions.filter { it.sourceId == sourceId }

    override suspend fun fetchUpcomingAndRecent(
        competition: CompetitionInfo
    ): Pair<List<FixtureItem>, List<MatchResultRecord>> = withContext(Dispatchers.IO) {
        val leagueId = leagueIdMap[competition.id] ?: return@withContext Pair(emptyList(), emptyList())
        val fixtures = mutableListOf<FixtureItem>()
        val results = mutableListOf<MatchResultRecord>()
        val now = System.currentTimeMillis()

        // 1. Next league events
        fetchEvents("$baseUrl/eventsnextleague.php?id=$leagueId")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseFixture(obj, competition, now)?.let { fixtures.add(it) }
            }
        }

        // 2. Past league events (for historical form & settlement)
        fetchEvents("$baseUrl/eventspastleague.php?id=$leagueId")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseFixture(obj, competition, now)?.let { fix ->
                    fixtures.add(fix)
                    if (fix.status == FixtureStatus.FINISHED && fix.homeScore != null && fix.awayScore != null) {
                        results.add(
                            MatchResultRecord(
                                matchId = fix.matchId,
                                dateEpochMs = fix.kickoffEpochMs,
                                competition = competition.name,
                                homeTeamId = fix.homeTeamId,
                                homeTeam = fix.homeTeam,
                                awayTeamId = fix.awayTeamId,
                                awayTeam = fix.awayTeam,
                                homeGoals = fix.homeScore,
                                awayGoals = fix.awayScore,
                                sourceName = "TheSportsDB"
                            )
                        )
                    }
                }
            }
        }

        Pair(fixtures, results)
    }

    override suspend fun fetchStandings(competition: CompetitionInfo): List<StandingRow> = withContext(Dispatchers.IO) {
        val leagueId = leagueIdMap[competition.id] ?: return@withContext emptyList()
        val url = "$baseUrl/lookuptable.php?l=$leagueId"
        val rows = mutableListOf<StandingRow>()
        try {
            val req = Request.Builder().url(url).build()
            SharedHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val json = JSONObject(body)
                val arr = json.optJSONArray("table") ?: return@withContext emptyList()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    rows.add(
                        StandingRow(
                            position = obj.optString("intRank").toIntOrNull() ?: (i + 1),
                            teamId = "tsdb_t_${obj.optString("idTeam")}",
                            teamName = obj.optString("strTeam"),
                            crestUrl = obj.optString("strBadge").takeIf { it.startsWith("http") },
                            played = obj.optString("intPlayed").toIntOrNull() ?: 0,
                            won = obj.optString("intWin").toIntOrNull() ?: 0,
                            drawn = obj.optString("intDraw").toIntOrNull() ?: 0,
                            lost = obj.optString("intLoss").toIntOrNull() ?: 0,
                            goalsFor = obj.optString("intGoalsFor").toIntOrNull() ?: 0,
                            goalsAgainst = obj.optString("intGoalsAgainst").toIntOrNull() ?: 0,
                            goalDifference = obj.optString("intGoalDifference").toIntOrNull() ?: 0,
                            points = obj.optString("intPoints").toIntOrNull() ?: 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("TheSportsDbAdapter", "Standings error $url: ${e.message}")
        }
        rows
    }

    suspend fun searchTeams(query: String): List<TeamInfo> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$baseUrl/searchteams.php?t=$encoded"
        val now = System.currentTimeMillis()
        val teams = mutableListOf<TeamInfo>()
        try {
            val req = Request.Builder().url(url).build()
            SharedHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val json = JSONObject(body)
                val arr = json.optJSONArray("teams") ?: return@withContext emptyList()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (!obj.optString("strSport").equals("Soccer", ignoreCase = true)) continue
                    val teamId = obj.optString("idTeam")
                    val name = obj.optString("strTeam")
                    val league = obj.optString("strLeague")
                    val isWomen = name.contains("Women", ignoreCase = true) ||
                        name.contains("Frauen", ignoreCase = true) ||
                        league.contains("Women", ignoreCase = true) ||
                        league.contains("WSL", ignoreCase = true)
                    teams.add(
                        TeamInfo(
                            id = "tsdb_t_$teamId",
                            name = name,
                            shortName = obj.optString("strTeamShort").ifBlank { name.take(3).uppercase() },
                            country = obj.optString("strCountry").ifBlank { "International" },
                            competition = league.ifBlank { "Football" },
                            gender = if (isWomen) GenderCategory.WOMEN else GenderCategory.MEN,
                            crestUrl = obj.optString("strBadge").takeIf { it.startsWith("http") },
                            overview = obj.optString("strDescriptionEN").take(400),
                            stadium = obj.optString("strStadium"),
                            sourceName = "TheSportsDB",
                            sourceUrl = "https://www.thesportsdb.com/team/$teamId",
                            retrievedEpochMs = now
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("TheSportsDbAdapter", "Team search failed: ${e.message}")
        }
        teams
    }

    suspend fun fetchTeamEvents(rawTeamId: String): Pair<List<FixtureItem>, List<MatchResultRecord>> = withContext(Dispatchers.IO) {
        val id = rawTeamId.removePrefix("tsdb_t_")
        val now = System.currentTimeMillis()
        val fixtures = mutableListOf<FixtureItem>()
        val results = mutableListOf<MatchResultRecord>()

        val placeholderComp = CompetitionInfo("tsdb_team", "Club Competition", "International", GenderCategory.MEN, "League/Cup", sourceId)

        fetchEvents("$baseUrl/eventsnext.php?id=$id")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseFixture(obj, placeholderComp, now)?.let { fixtures.add(it) }
            }
        }

        fetchEvents("$baseUrl/eventslast.php?id=$id")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseFixture(obj, placeholderComp, now)?.let { fix ->
                    fixtures.add(fix)
                    if (fix.status == FixtureStatus.FINISHED && fix.homeScore != null && fix.awayScore != null) {
                        results.add(
                            MatchResultRecord(
                                matchId = fix.matchId,
                                dateEpochMs = fix.kickoffEpochMs,
                                competition = fix.competition,
                                homeTeamId = fix.homeTeamId,
                                homeTeam = fix.homeTeam,
                                awayTeamId = fix.awayTeamId,
                                awayTeam = fix.awayTeam,
                                homeGoals = fix.homeScore,
                                awayGoals = fix.awayScore,
                                sourceName = "TheSportsDB"
                            )
                        )
                    }
                }
            }
        }
        Pair(fixtures, results)
    }

    private fun fetchEvents(url: String): org.json.JSONArray? {
        return try {
            val req = Request.Builder().url(url).build()
            SharedHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return null
                val json = JSONObject(body)
                json.optJSONArray("events") ?: json.optJSONArray("results")
            }
        } catch (e: Exception) {
            Log.w("TheSportsDbAdapter", "fetchEvents failed $url: ${e.message}")
            null
        }
    }

    private fun parseFixture(obj: JSONObject, fallbackComp: CompetitionInfo, retrievedAt: Long): FixtureItem? {
        val eventId = obj.optString("idEvent").takeIf { it.isNotBlank() } ?: return null
        val homeName = obj.optString("strHomeTeam").takeIf { it.isNotBlank() } ?: return null
        val awayName = obj.optString("strAwayTeam").takeIf { it.isNotBlank() } ?: return null
        val kickoffMs = parseTimestamp(obj.optString("strTimestamp"), obj.optString("dateEvent"), obj.optString("strTime"))
            ?: return null

        val rawHomeScore = obj.optString("intHomeScore").toIntOrNull()
        val rawAwayScore = obj.optString("intAwayScore").toIntOrNull()
        val rawStatus = obj.optString("strStatus").uppercase(Locale.US)
        val status = when {
            rawStatus.contains("POST") -> FixtureStatus.POSTPONED
            rawStatus.contains("CANC") -> FixtureStatus.CANCELLED
            rawStatus.contains("LIVE") || rawStatus.contains("1H") || rawStatus.contains("2H") || rawStatus.contains("HT") -> FixtureStatus.LIVE
            rawStatus.contains("FT") || rawStatus.contains("FINISHED") || (rawHomeScore != null && rawAwayScore != null) -> FixtureStatus.FINISHED
            else -> FixtureStatus.SCHEDULED
        }

        val leagueName = obj.optString("strLeague").ifBlank { fallbackComp.name }
        val country = obj.optString("strCountry").ifBlank { fallbackComp.country }
        val isWomen = leagueName.contains("Women", ignoreCase = true) || leagueName.contains("WSL", ignoreCase = true)

        return FixtureItem(
            matchId = "tsdb_$eventId",
            homeTeamId = "tsdb_t_${obj.optString("idHomeTeam")}",
            homeTeam = homeName,
            homeCrestUrl = obj.optString("strHomeTeamBadge").takeIf { it.startsWith("http") },
            awayTeamId = "tsdb_t_${obj.optString("idAwayTeam")}",
            awayTeam = awayName,
            awayCrestUrl = obj.optString("strAwayTeamBadge").takeIf { it.startsWith("http") },
            competitionId = fallbackComp.id,
            competition = leagueName,
            country = country,
            gender = if (isWomen) GenderCategory.WOMEN else fallbackComp.gender,
            kickoffEpochMs = kickoffMs,
            status = status,
            homeScore = rawHomeScore,
            awayScore = rawAwayScore,
            liveMinute = null,
            historicalMatchesSample = 0,
            sourceId = sourceId,
            sourceName = "TheSportsDB",
            sourceUrl = "https://www.thesportsdb.com/event/$eventId",
            retrievedEpochMs = retrievedAt
        )
    }

    private fun parseTimestamp(ts: String, date: String, time: String): Long? {
        val candidates = listOfNotNull(
            ts.takeIf { it.isNotBlank() && it != "null" },
            if (date.isNotBlank() && time.isNotBlank()) "${date}T${time}" else null,
            if (date.isNotBlank()) "${date}T15:00:00" else null
        )
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (raw in candidates) {
            for (pat in patterns) {
                try {
                    val sdf = SimpleDateFormat(pat, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = sdf.parse(raw)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
        }
        return null
    }
}
