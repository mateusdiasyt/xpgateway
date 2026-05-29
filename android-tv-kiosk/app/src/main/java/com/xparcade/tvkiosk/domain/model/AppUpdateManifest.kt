package com.xparcade.tvkiosk.domain.model

data class AppUpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val required: Boolean = true,
    val publishedAt: String? = null,
    val releaseNotes: List<String> = emptyList()
)
