package com.portalcast.tv.data.net

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgStreamTest {
    private val now = 1_000_000L
    private fun parse(json: String, max: Int = 4) = EpgStream.parse(Buffer().writeUtf8(json), nowSec = now, aheadSec = 3600, maxPerChannel = max)

    @Test fun `cold cache array is null`() = assertNull(parse("[]"))

    @Test fun `keeps only programmes overlapping the window, sorted, with numeric or string timestamps`() {
        val epg = parse(
            """{"timestamp":"t","extra":{"deep":[1,2,3]},"data":{
              "1":[
                {"start_timestamp":"${now - 7200}","stop_timestamp":"${now - 3600}","name":"old"},
                {"start_timestamp":${now + 1800},"stop_timestamp":${now + 3600},"name":"next","description":"long text","x":{"y":1}},
                {"start_timestamp":"${now - 600}","stop_timestamp":"${now + 1800}","name":"current"},
                {"start_timestamp":"${now + 7200}","stop_timestamp":"${now + 9000}","name":"too far"}
              ],
              "2":[{"start_timestamp":"${now - 7200}","stop_timestamp":"${now - 3600}","name":"old only"}],
              "3":"not an array",
              "4":[{"name":"no times"}, 5, null]
            }}""",
        )!!
        assertEquals("t", epg.timestamp)
        assertEquals(listOf("current", "next"), epg.forChannel("1").map { it.name })
        assertTrue("channels with nothing in the window are dropped", epg.forChannel("2").isEmpty())
        assertTrue(epg.forChannel("3").isEmpty())
        assertTrue(epg.forChannel("4").isEmpty())
        val (cur, nxt) = epg.nowNext("1", nowMs = now * 1000)
        assertEquals("current", cur?.name); assertEquals("next", nxt?.name)
        assertNull("descriptions are not kept", epg.forChannel("1")[1].description)
    }

    @Test fun `caps programmes per channel`() {
        val items = (0 until 10).joinToString(",") { i -> """{"start_timestamp":"${now + i * 60}","stop_timestamp":"${now + i * 60 + 60}","name":"p$i"}""" }
        val epg = parse("""{"data":{"1":[$items]}}""", max = 3)!!
        assertEquals(listOf("p0", "p1", "p2"), epg.forChannel("1").map { it.name })
    }
}
