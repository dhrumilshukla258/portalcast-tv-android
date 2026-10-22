package com.portalcast.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlDownsizeTest {
    @Test
    fun `larger TMDB sizes come down to the target, never up`() {
        assertEquals("http://s/api/images/tmdb/w342/a.jpg", ImageUrl.downsize("http://s/api/images/tmdb/w500/a.jpg", "w342"))
        assertEquals("http://s/api/images/tmdb/w1280/b.jpg", ImageUrl.downsize("http://s/api/images/tmdb/original/b.jpg", "w1280"))
        assertEquals("https://image.tmdb.org/t/p/w780/c.jpg", ImageUrl.downsize("https://image.tmdb.org/t/p/original/c.jpg", "w780"))
        assertEquals("http://s/api/images/tmdb/w185/p.jpg", ImageUrl.downsize("http://s/api/images/tmdb/w185/p.jpg", "w342"))
        assertEquals("http://s/api/images/tmdb/w342/a.jpg", ImageUrl.downsize("http://s/api/images/tmdb/w342/a.jpg", "w342"))
    }

    @Test
    fun `other images are left as they are`() {
        assertEquals("http://s/api/images/proxy?u=abc&s=def", ImageUrl.downsize("http://s/api/images/proxy?u=abc&s=def", "w342"))
        assertEquals("http://s/api/images/tmdb/h632/a.jpg", ImageUrl.downsize("http://s/api/images/tmdb/h632/a.jpg", "w342"))
        assertNull(ImageUrl.downsize(null, "w342"))
    }

    @Test
    fun `NAS art asks the server for the width`() {
        assertEquals("http://s/api/images/local?a=12.poster.3&s=sig&w=342", ImageUrl.downsize("http://s/api/images/local?a=12.poster.3&s=sig", "w342"))
        assertEquals("http://s/api/images/local?a=12.poster.3&s=sig&w=780", ImageUrl.downsize("http://s/api/images/local?a=12.poster.3&s=sig&w=780", "w342"))
    }
}
