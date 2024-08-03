package eu.kanade.tachiyomi.extension.all.easyyomi

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EasyyomiHelper {
    val formatterDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val formatterDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val formatterDateTimeMilli = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
}
