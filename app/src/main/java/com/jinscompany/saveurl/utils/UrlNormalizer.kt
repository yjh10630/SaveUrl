package com.jinscompany.saveurl.utils

import android.net.Uri

object UrlNormalizer {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_reader", "utm_name",
        "fbclid", "gclid", "gclsrc", "dclid",
        "ref", "ref_src", "ref_url",
        "_ga", "_gl",
        "mc_cid", "mc_eid",
        "igshid",
        "si",         // YouTube share
        "feature",    // YouTube
        "pp",         // YouTube
        "WT.mc_id",
        "zanpid", "origin",
    )

    fun normalize(rawUrl: String): String {
        return try {
            val uri = Uri.parse(rawUrl.trim())

            val scheme = "https"
            var host = (uri.host ?: return rawUrl).lowercase()
            if (host.startsWith("www.")) host = host.removePrefix("www.")

            // youtube 단축 URL 통일
            val path: String
            val queryParams = mutableListOf<String>()

            if (host == "youtu.be") {
                host = "youtube.com"
                val videoId = uri.pathSegments.firstOrNull() ?: ""
                path = "/watch"
                if (videoId.isNotEmpty()) queryParams.add("v=$videoId")
            } else {
                path = uri.path?.trimEnd('/') ?: ""
                uri.queryParameterNames
                    .filter { it !in TRACKING_PARAMS }
                    .sorted()
                    .forEach { key ->
                        val value = uri.getQueryParameter(key) ?: ""
                        queryParams.add("$key=$value")
                    }
            }

            val query = if (queryParams.isEmpty()) "" else "?${queryParams.joinToString("&")}"
            "$scheme://$host$path$query"
        } catch (e: Exception) {
            rawUrl
        }
    }
}
