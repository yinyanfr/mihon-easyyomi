package eu.kanade.tachiyomi.extension.all.easyyomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class EasyyomiFactory : SourceFactory {

    override fun createSources(): List<Source> =
        listOf(
            Easyyomi(),
            Easyyomi("2"),
            Easyyomi("3"),
        )
}
