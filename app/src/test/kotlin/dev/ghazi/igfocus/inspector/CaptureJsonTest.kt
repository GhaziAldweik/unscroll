package dev.ghazi.igfocus.inspector

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureJsonTest {

    private val captures = listOf(
        Capture("Home", 1_794_000_000_000, "453.0.0.1", listOf("sticky_header_list", "tab_bar")),
        Capture("DM inbox", 1_794_000_060_000, "453.0.0.1", emptyList()),
    )

    @Test
    fun `captures survive a round trip`() {
        assertEquals(captures, CaptureJson.decode(CaptureJson.encode(captures)))
    }

    @Test
    fun `an empty list survives a round trip`() {
        assertEquals(emptyList<Capture>(), CaptureJson.decode(CaptureJson.encode(emptyList())))
    }

    /** The privacy rule: an export holds labels, times, the Instagram version and IDs. Nothing else. */
    @Test
    fun `the export contains only the allowed fields`() {
        val root = JSONObject(CaptureJson.encode(captures))
        assertEquals(setOf("format", "captures"), root.fieldNames())

        val items = root.getJSONArray("captures")
        for (i in 0 until items.length()) {
            assertEquals(
                setOf("label", "timestampMs", "instagramVersion", "ids"),
                items.getJSONObject(i).fieldNames(),
            )
        }
    }

    // keys() exists in both Android's org.json and the library used by these tests.
    private fun JSONObject.fieldNames(): Set<String> = keys().asSequence().toSet()

    @Test
    fun `text that is not a capture file is rejected`() {
        assertThrows(JSONException::class.java) { CaptureJson.decode("not json") }
        assertThrows(JSONException::class.java) { CaptureJson.decode("{}") }
    }
}
