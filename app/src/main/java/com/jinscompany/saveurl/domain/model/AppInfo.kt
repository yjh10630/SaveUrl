package com.jinscompany.saveurl.domain.model

import com.google.gson.annotations.SerializedName

data class AppInfo(
    @SerializedName("latest_version") val latestVersion: Int,
    @SerializedName("min_version") val minVersion: Int
)
