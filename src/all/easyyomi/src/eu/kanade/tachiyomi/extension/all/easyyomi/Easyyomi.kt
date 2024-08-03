package eu.kanade.tachiyomi.extension.all.easyyomi

import android.app.Application
import android.content.SharedPreferences
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.all.easyyomi.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.easyyomi.dto.PagesDto
import eu.kanade.tachiyomi.extension.all.easyyomi.dto.SeriesDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

open class Easyyomi(private val suffix: String = "") : ConfigurableSource, UnmeteredSource, HttpSource() {
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/series", headers)

    override fun popularMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/series", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/search/$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.empty()

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/chapters/${manga.title}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val series: SeriesDto = Json.decodeFromString(body)
        return SManga.create().apply {
            title = series.name
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl/api/chapters/${manga.title}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val chapters: List<ChapterDto> = Json.decodeFromString(body)
        return chapters.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                url = chapter.seriesName
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl/api/pages/${chapter.url}/${chapter.name}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val pages: PagesDto = Json.decodeFromString(body)

        return pages.pages.mapIndexed { index, it ->
            Page(
                index = index,
                imageUrl = "$baseUrl/api/${pages.seriesName}/${pages.chapterName}/${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}",
            )
        }
    }

    private fun processSeriesPage(response: Response): MangasPage {
        val body = response.body.string()
        val series: List<SeriesDto> = Json.decodeFromString(body)
        return MangasPage(series.map { SManga.create().apply { title = it.name } }, false)
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not Used")

    private data class CollectionFilterEntry(
        val name: String,
        val id: String? = null,
    ) {
        override fun toString() = name
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Nothing"),
    )

    // keep the previous ID when lang was "en", so that preferences and manga bindings are not lost
    override val id by lazy {
        val key = "Easyyomi${if (suffix.isNotBlank()) " ($suffix)" else ""}/en/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    private val displayName by lazy { preferences.displayName }
    final override val baseUrl by lazy { preferences.baseUrl }
    private val username by lazy { preferences.username }
    private val password by lazy { preferences.password }
    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder {
        val header = Headers.Builder()
            .add("User-Agent", "Mihonyomi-Easyyomi/${AppInfo.getVersionName()}")
        return header
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val name = "Easyyomi${displayName.ifBlank { suffix }.let { if (it.isNotBlank()) " ($it)" else "" }}"
    override val lang = "all"
    override val supportsLatest = true
    private val LOG_TAG = "extension.all.easyyomi${if (suffix.isNotBlank()) ".$suffix" else ""}"

    override val client: OkHttpClient =
        network.client.newBuilder()
            .authenticator { _, response ->
                if (response.request.header("Authorization") != null) {
                    null // Give up, we've already failed to authenticate.
                } else {
                    response.request.newBuilder()
                        .addHeader("Authorization", Credentials.basic(username, password))
                        .build()
                }
            }
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .build()

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addEditTextPreference(
            title = "Source display name",
            default = suffix,
            summary = displayName.ifBlank { "Here you can change the source displayed suffix" },
            key = PREF_DISPLAYNAME,
        )
        screen.addEditTextPreference(
            title = "Address",
            default = ADDRESS_DEFAULT,
            summary = baseUrl.ifBlank { "The server address (with port if necessary)" },
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            validate = { it.toHttpUrlOrNull() != null },
            validationMessage = "The URL is invalid or malformed",
            key = PREF_ADDRESS,
        )
        screen.addEditTextPreference(
            title = "Username",
            default = USERNAME_DEFAULT,
            summary = username.ifBlank { "The basic auth username" },
            key = PREF_USERNAME,
        )
        screen.addEditTextPreference(
            title = "Password",
            default = PASSWORD_DEFAULT,
            summary = if (password.isBlank()) "The basic auth password" else "*".repeat(password.length),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_PASSWORD,
        )
    }

    private fun PreferenceScreen.addEditTextPreference(
        title: String,
        default: String,
        summary: String,
        inputType: Int? = null,
        validate: ((String) -> Boolean)? = null,
        validationMessage: String? = null,
        key: String = title,
    ) {
        val preference = EditTextPreference(context).apply {
            this.key = key
            this.title = title
            this.summary = summary
            this.setDefaultValue(default)
            dialogTitle = title

            setOnBindEditTextListener { editText ->
                if (inputType != null) {
                    editText.inputType = inputType
                }

                if (validate != null) {
                    editText.addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(editable: Editable?) {
                                requireNotNull(editable)

                                val text = editable.toString()

                                val isValid = text.isBlank() || validate(text)

                                editText.error = if (!isValid) validationMessage else null
                                editText.rootView.findViewById<Button>(android.R.id.button1)
                                    ?.isEnabled = editText.error == null
                            }
                        },
                    )
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(this.key, newValue as String).commit()
                    Toast.makeText(context, "Restart the app to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        addPreference(preference)
    }

    private val SharedPreferences.displayName
        get() = getString(PREF_DISPLAYNAME, "")!!

    private val SharedPreferences.baseUrl
        get() = getString(PREF_ADDRESS, ADDRESS_DEFAULT)!!.removeSuffix("/")

    private val SharedPreferences.username
        get() = getString(PREF_USERNAME, USERNAME_DEFAULT)!!

    private val SharedPreferences.password
        get() = getString(PREF_PASSWORD, PASSWORD_DEFAULT)!!

    companion object {
        private const val PREF_DISPLAYNAME = "Source display name"
        private const val PREF_ADDRESS = "Address"
        private const val ADDRESS_DEFAULT = ""
        private const val PREF_USERNAME = "Username"
        private const val USERNAME_DEFAULT = "username"
        private const val PREF_PASSWORD = "Password"
        private const val PASSWORD_DEFAULT = "password"

        private val supportedImageTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/jxl", "image/heif", "image/avif")

        private const val TYPE_SERIES = "Series"
        private const val TYPE_READLISTS = "Read lists"
    }
}
