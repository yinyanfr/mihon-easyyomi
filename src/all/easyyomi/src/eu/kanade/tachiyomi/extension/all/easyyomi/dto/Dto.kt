package eu.kanade.tachiyomi.extension.all.easyyomi.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val name: String,
    val lastModified: String?,
)

@Serializable
data class ChapterDto(
    val seriesName: String,
    val name: String,
    val lastModified: String?,
)

@Serializable
data class PagesDto(
    val seriesName: String,
    val chapterName: String,
    val pages: List<String>,
)
