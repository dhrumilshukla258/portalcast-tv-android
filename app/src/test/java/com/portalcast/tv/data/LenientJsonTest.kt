package com.portalcast.tv.data

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.net.AppJson
import com.portalcast.tv.data.net.DynamicBaseUrlInterceptor
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LenientJsonTest {

    @Test
    fun `channels parse with mixed id types and unknown keys`() {
        val body = """
            [{"id": 101, "name": "BBC One", "number": "4", "tv_genre_id": 7, "screenshot_uri": "/misc/logos/bbc.png",
              "cmd": "http://srv/live.m3u8?t=abc&id=101&proxy=1", "enable_tv_archive": "1", "weird_portal_field": {"x": 1}},
             {"id": "102", "name": null, "tv_genre_id": null}]
        """.trimIndent()
        val list = AppJson.decodeFromString(ListSerializer(Channel.serializer()), body)
        assertEquals("101", list[0].id)
        assertEquals(4, list[0].number)
        assertEquals("7", list[0].groupId)
        assertEquals(1, list[0].archiveEnabled)
        assertEquals("102", list[1].id)
        assertEquals("", list[1].displayName)
        assertNull(list[1].groupId)
    }

    @Test
    fun `paged media items tolerate numeric flags and string numbers`() {
        val body = """
            {"success":true,"page":"2","total_items":40,"total_loaded":14,"isPortal":false,
             "data":[{"id":"55","stream_id":55,"name":"Film","is_series":0,"series_number":"2","episode_num":3,
                      "year":2019,"rating_imdb":"7.4","description":"-","genres_str":"Action, Drama"}]}
        """.trimIndent()
        val paged = AppJson.decodeFromString(Paged.serializer(MediaItem.serializer()), body)
        assertEquals(2, paged.page)
        assertTrue(paged.hasMore(14))
        val item = paged.data.single()
        assertEquals(false, item.isSeries)
        assertEquals(2, item.seriesNumber)
        assertEquals("2019", item.displayYear)
        assertEquals("7.4", item.displayRating)
        assertNull("placeholder description must be hidden", item.overview)
        assertEquals("Action, Drama", item.labelCategory)
    }

    @Test
    fun `epg parses object and treats empty array as missing`() {
        val obj = AppJson.parseToJsonElement("""{"timestamp":"2026-10-18T10:00:00Z","data":{"101":[{"start_timestamp":"1760781600","stop_timestamp":"1760785200","name":"News"}]}}""")
        val epg = EpgData.parse(obj)!!
        val (now, next) = epg.nowNext("101", nowMs = 1760783400L * 1000)
        assertEquals("News", now?.name)
        assertNull(next)
        assertNull(EpgData.parse(AppJson.parseToJsonElement("[]")))
    }

    @Test
    fun `preferences patch omits nulls and keeps whole maps`() {
        val patch = Preferences(favorites = listOf("1", "2"), lastSelectedTvGroup = mapOf("stalker_host" to "7")).toPatch()
        assertEquals(setOf("favorites", "lastSelectedTvGroup"), patch.keys)
    }

    @Test
    fun `server url normalisation and rebasing`() {
        assertEquals("http://192.168.1.10:3000/", DynamicBaseUrlInterceptor.normalize("192.168.1.10:3000/").toString())
        assertEquals("https://iptv.example.com/", DynamicBaseUrlInterceptor.normalize("https://iptv.example.com").toString())
        assertNull(DynamicBaseUrlInterceptor.normalize("   "))

        val base = "https://iptv.example.com/portalcast/".toHttpUrl()
        val req = "http://placeholder.invalid/api/v2/movies?category=*&page=1".toHttpUrl()
        assertEquals("https://iptv.example.com/portalcast/api/v2/movies?category=*&page=1", DynamicBaseUrlInterceptor.rebase(req, base).toString())
    }

    @Test
    fun `user agent never carries smart tv tokens`() {
        val forbidden = Regex("Tizen|SMART-TV|SmartTV|Web0S|WebOS", RegexOption.IGNORE_CASE)
        assertFalse(forbidden.containsMatchIn(com.portalcast.tv.data.net.UserAgent.value))
    }
}
