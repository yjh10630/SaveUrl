package com.jinscompany.saveurl.data.source

import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object YouTubeOEmbedParser {

    private val youtubePattern = Regex(
        "(https?://)?(www\\.)?(youtube\\.com/watch|youtu\\.be/)(.+)"
    )

    fun isYouTubeUrl(url: String): Boolean = youtubePattern.containsMatchIn(url)

    suspend fun parse(url: String): UrlData? = withContext(Dispatchers.IO) {
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=${URL(url)}&format=json"
            val response = URL(oembedUrl).readText()
            val json = JSONObject(response)
            UrlData(
                url = url,
                title = json.optString("title").ifEmpty { null },
                imgUrl = json.optString("thumbnail_url").ifEmpty { null },
                siteName = json.optString("provider_name", "YouTube"),
                description = json.optString("author_name").let {
                    if (it.isEmpty()) null else it
                },
            )
        } catch (e: Exception) {
            null
        }
    }
}
