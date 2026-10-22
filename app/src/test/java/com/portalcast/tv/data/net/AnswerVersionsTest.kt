package com.portalcast.tv.data.net

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerVersionsTest {
    private fun req(url: String, method: String = "GET") =
        Request.Builder().url(url).method(method, if (method == "GET") null else okhttp3.RequestBody.create(null, ByteArray(0))).build()

    private fun answer(url: String, vararg headers: Pair<String, String>): Response {
        val b = Response.Builder().request(req(url)).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    @After fun reset() = AnswerVersions.report(answer("http://s/api/v2/health"))

    @Test
    fun `catalog reads carry the version, other requests do not`() {
        AnswerVersions.report(answer("http://s/api/user/progress", "X-Catalog-Version" to "c1", "X-Home-Version" to "h1"))
        assertEquals("c1", AnswerVersions.pin(req("http://s/api/v2/discover/browse?genre=Drama&page=2")).url.queryParameter("v"))
        assertEquals("c1", AnswerVersions.pin(req("http://s/api/v2/movies?category=*&page=1")).url.queryParameter("v"))
        assertEquals("c1", AnswerVersions.pin(req("http://s/api/v2/people/42")).url.queryParameter("v"))
        assertEquals("h1", AnswerVersions.pin(req("http://s/api/v2/home?type=movie")).url.queryParameter("v"))
        assertNull(AnswerVersions.pin(req("http://s/api/user/progress")).url.queryParameter("v"))
        assertNull(AnswerVersions.pin(req("http://s/api/v2/discover/browse", "POST")).url.queryParameter("v"))
    }

    @Test
    fun `a title's own answer never carries the version (play links)`() {
        AnswerVersions.report(answer("http://s/api/v2/health", "X-Catalog-Version" to "c1", "X-Home-Version" to "h1"))
        assertNull(AnswerVersions.pin(req("http://s/api/v2/movies?movieId=123&category=*")).url.queryParameter("v"))
        assertNull(AnswerVersions.pin(req("http://s/api/v2/series?movieId=9&seasonId=2")).url.queryParameter("v"))
        assertNull(AnswerVersions.pin(req("http://s/api/v2/series?category=1&episodeId=7")).url.queryParameter("v"))
        assertEquals("c1", AnswerVersions.pin(req("http://s/api/v2/movies?movieId=0&category=3")).url.queryParameter("v"))
    }

    @Test
    fun `an answer without the header clears the version and non-api answers are ignored`() {
        AnswerVersions.report(answer("http://s/api/v2/health", "X-Catalog-Version" to "c1", "X-Home-Version" to "h1"))
        AnswerVersions.report(answer("http://cdn/image.jpg"))
        assertEquals("c1", AnswerVersions.catalog)
        AnswerVersions.report(answer("http://s/api/v2/health"))
        assertNull(AnswerVersions.catalog)
        assertNull(AnswerVersions.home)
        assertNull(AnswerVersions.pin(req("http://s/api/v2/discover/facets")).url.queryParameter("v"))
    }

    @Test
    fun `an address that already has v is left alone`() {
        AnswerVersions.report(answer("http://s/api/v2/health", "X-Catalog-Version" to "c2"))
        assertEquals("old", AnswerVersions.pin(req("http://s/api/v2/discover/facets?v=old")).url.queryParameter("v"))
    }
}
