package com.example.data.sources

import android.util.Log
import com.example.domain.model.WebSummaryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Legitimate free, no-key Wikimedia/Wikipedia open search adapter.
 * Serves as the compliant free search fallback without scraping Google SERPs
 * or exposing paid Search API credentials.
 */
class WikipediaSearchAdapter {

    suspend fun searchFootballInfo(query: String): List<WebSummaryResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode("$trimmed football", "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=5&utf8=1"
        val now = System.currentTimeMillis()
        val results = mutableListOf<WebSummaryResult>()

        try {
            val req = Request.Builder().url(url).build()
            SharedHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val json = JSONObject(body)
                val queryObj = json.optJSONObject("query") ?: return@withContext emptyList()
                val searchArr = queryObj.optJSONArray("search") ?: return@withContext emptyList()
                for (i in 0 until searchArr.length()) {
                    val item = searchArr.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val rawSnippet = item.optString("snippet")
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .trim()
                    val slug = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                    results.add(
                        WebSummaryResult(
                            title = title,
                            snippet = rawSnippet,
                            url = "https://en.wikipedia.org/wiki/$slug",
                            sourceName = "Wikipedia Open Search",
                            retrievedEpochMs = now
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("WikipediaSearchAdapter", "Search failed: ${e.message}")
        }
        results
    }
}
