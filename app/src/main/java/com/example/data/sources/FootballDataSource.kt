package com.example.data.sources

import com.example.domain.model.CompetitionInfo
import com.example.domain.model.DataSourceMetadata
import com.example.domain.model.FixtureItem
import com.example.domain.model.GenderCategory
import com.example.domain.model.MatchResultRecord
import com.example.domain.model.SourceHealth
import com.example.domain.model.StandingRow
import com.example.domain.model.TeamInfo
import com.example.domain.model.WebSummaryResult
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SharedHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "AllFootballPredictions-Android/1.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
    }
}

interface FootballDataAdapter {
    val sourceId: String
    val defaultMetadata: DataSourceMetadata
    suspend fun fetchCompetitions(): List<CompetitionInfo>
    suspend fun fetchUpcomingAndRecent(competition: CompetitionInfo): Pair<List<FixtureItem>, List<MatchResultRecord>>
    suspend fun fetchStandings(competition: CompetitionInfo): List<StandingRow>
}

object FootballSourceCatalog {
    val defaultCompetitions = listOf(
        CompetitionInfo("openliga_bl1", "Bundesliga", "Germany", GenderCategory.MEN, "Domestic League", "openligadb"),
        CompetitionInfo("openliga_fbl1", "Frauen-Bundesliga", "Germany", GenderCategory.WOMEN, "Domestic League", "openligadb"),
        CompetitionInfo("openliga_bl2", "2. Bundesliga", "Germany", GenderCategory.MEN, "Domestic League", "openligadb"),
        CompetitionInfo("openliga_dfb", "DFB-Pokal", "Germany", GenderCategory.MEN, "Domestic Cup", "openligadb"),
        CompetitionInfo("tsdb_4328", "English Premier League", "England", GenderCategory.MEN, "Domestic League", "thesportsdb"),
        CompetitionInfo("tsdb_4335", "Spanish La Liga", "Spain", GenderCategory.MEN, "Domestic League", "thesportsdb"),
        CompetitionInfo("tsdb_4332", "Italian Serie A", "Italy", GenderCategory.MEN, "Domestic League", "thesportsdb"),
        CompetitionInfo("tsdb_4334", "French Ligue 1", "France", GenderCategory.MEN, "Domestic League", "thesportsdb"),
        CompetitionInfo("tsdb_4480", "UEFA Champions League", "Europe", GenderCategory.MEN, "Continental", "thesportsdb"),
        CompetitionInfo("tsdb_4899", "FA Women's Super League", "England", GenderCategory.WOMEN, "Domestic League", "thesportsdb")
    )

    fun initialMetadata(now: Long = System.currentTimeMillis()): List<DataSourceMetadata> = listOf(
        DataSourceMetadata(
            sourceId = "openligadb",
            sourceName = "OpenLigaDB",
            license = "Open Community Sports Data (Free Public API)",
            commercialUseNote = "Free public access; verify league attribution terms before commercial resale.",
            coverageScope = "German Men's & Women's Leagues, DFB-Pokal, selected European/International tournaments (with minute-level goal timestamps)",
            updateFrequency = "Matchday & live goal event polling",
            requiresApiKey = false,
            health = SourceHealth.ACTIVE,
            lastRetrievedEpochMs = now,
            baseUrl = "https://api.openligadb.de"
        ),
        DataSourceMetadata(
            sourceId = "thesportsdb",
            sourceName = "TheSportsDB (Free Public v1)",
            license = "TheSportsDB Free Public Tier (Key 3)",
            commercialUseNote = "Free tier intended for non-commercial/educational use; commercial deployment requires a licensed tier.",
            coverageScope = "Global Men's & Women's Clubs, National Teams, Premier League, La Liga, Serie A, Ligue 1, WSL, Champions League",
            updateFrequency = "Periodic fixture and table sync",
            requiresApiKey = false,
            health = SourceHealth.ACTIVE,
            lastRetrievedEpochMs = now,
            baseUrl = "https://www.thesportsdb.com/api/v1/json/3"
        ),
        DataSourceMetadata(
            sourceId = "openfootball",
            sourceName = "OpenFootball Public JSON",
            license = "CC0-1.0 Public Domain",
            commercialUseNote = "Public domain (CC0); commercial and non-commercial use permitted without restriction.",
            coverageScope = "Open historical & seasonal league schedules (cross-check dataset)",
            updateFrequency = "Repository commit cadence",
            requiresApiKey = false,
            health = SourceHealth.ACTIVE,
            lastRetrievedEpochMs = now,
            baseUrl = "https://raw.githubusercontent.com/openfootball/football.json/master"
        ),
        DataSourceMetadata(
            sourceId = "wikipedia_search",
            sourceName = "Wikimedia Open Football Search",
            license = "CC BY-SA 4.0 / Wikimedia REST API",
            commercialUseNote = "Permitted with attribution link; serves as free online search fallback instead of paid Google Custom Search API.",
            coverageScope = "Global clubs, national teams, women's teams, competitions, and encyclopedic football overviews",
            updateFrequency = "Real-time search query",
            requiresApiKey = false,
            health = SourceHealth.ACTIVE,
            lastRetrievedEpochMs = now,
            baseUrl = "https://en.wikipedia.org/w/api.php"
        )
    )
}
