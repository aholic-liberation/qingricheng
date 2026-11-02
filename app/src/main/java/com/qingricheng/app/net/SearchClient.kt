package com.qingricheng.app.net

import com.qingricheng.app.data.SearchResult
import com.qingricheng.app.data.SecretStore
import com.qingricheng.app.data.SettingsStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

class SearchClient(private val settings: SettingsStore, private val secrets: SecretStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean =
        settings.searchProvider == "bing" || !secrets.get(KEY_SEARCH).isNullOrBlank()

    fun search(query: String, maxResults: Int = 5): Result<List<SearchResult>> = runCatching {
        val provider = settings.searchProvider
        if (provider == "bing") return@runCatching searchBing(query, maxResults)
        val key = secrets.get(KEY_SEARCH) ?: throw IOException("还没有配置联网搜索 API Key")
        when (provider) {
            "serper" -> searchSerper(key, query, maxResults)
            else -> searchTavily(key, query, maxResults)
        }
    }

    fun fetchUrl(url: String): Result<String> = runCatching {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw IOException("只支持 http/https 网页")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) QingRiCheng/0.1")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("网页请求失败（HTTP ${response.code}）")
            val html = response.body.string()
            val doc = Jsoup.parse(html, url)
            doc.select("script,style,nav,footer,header,aside,noscript,svg").remove()
            val text = (doc.body()?.text() ?: doc.text())
                .replace(Regex("\\s+"), " ")
                .trim()
            if (text.length > 8000) text.take(8000) + "…" else text
        }
    }


    /** No-key fallback: Bing's public HTML result page. Good for personal use; use an API for production. */
    private fun searchBing(query: String, maxResults: Int): List<SearchResult> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://cn.bing.com/search?q=$encoded&count=${maxResults.coerceIn(1, 10)}&setlang=zh-hans&cc=cn")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bing 搜索失败（HTTP ${response.code}）")
            val doc = Jsoup.parse(response.body.string(), "https://cn.bing.com/")
            val out = ArrayList<SearchResult>()
            for (li in doc.select("li.b_algo")) {
                if (out.size >= maxResults) break
                val a = li.selectFirst("h2 a") ?: continue
                val href = a.attr("href")
                if (!href.startsWith("http")) continue
                val snippet = li.selectFirst(".b_caption p")?.text()
                    ?: li.selectFirst(".b_caption")?.text()
                    ?: ""
                out += SearchResult(
                    title = a.text().trim(),
                    url = href,
                    snippet = snippet.take(800)
                )
            }
            if (out.isEmpty()) throw IOException("Bing 没有返回可解析的结果，请换用 Tavily/Serper")
            return out
        }
    }

    private fun searchTavily(key: String, query: String, maxResults: Int): List<SearchResult> {
        val body = JSONObject().apply {
            put("api_key", key)
            put("query", query)
            put("max_results", maxResults.coerceIn(1, 10))
            put("search_depth", "basic")
            put("include_answer", false)
            put("include_raw_content", false)
        }
        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("搜索请求失败（HTTP ${response.code}）")
            val json = JSONObject(response.body.string())
            val arr = json.optJSONArray("results") ?: return emptyList()
            val out = ArrayList<SearchResult>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                out += SearchResult(
                    title = item.optString("title"),
                    url = item.optString("url"),
                    snippet = item.optString("content").take(800)
                )
            }
            return out
        }
    }

    private fun searchSerper(key: String, query: String, maxResults: Int): List<SearchResult> {
        val body = JSONObject().apply {
            put("q", query)
            put("num", maxResults.coerceIn(1, 10))
        }
        val request = Request.Builder()
            .url("https://google.serper.dev/search")
            .header("X-API-KEY", key)
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("搜索请求失败（HTTP ${response.code}）")
            val json = JSONObject(response.body.string())
            val arr = json.optJSONArray("organic") ?: return emptyList()
            val out = ArrayList<SearchResult>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                out += SearchResult(
                    title = item.optString("title"),
                    url = item.optString("link"),
                    snippet = item.optString("snippet").take(800)
                )
            }
            return out
        }
    }

    companion object {
        const val KEY_SEARCH = "search_api_key"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
