package dev.ghazi.igfocus.inspector

import org.json.JSONArray
import org.json.JSONObject

/**
 * One labelled screen signature. These four fields are everything a capture may hold:
 * no message text, usernames, captions or images.
 */
data class Capture(
    val label: String,
    val timestampMs: Long,
    val instagramVersion: String,
    val ids: List<String>,
)

/**
 * The fixed labels offered when capturing. The first group is the screens not yet measured;
 * the second is the original list, kept for recapturing.
 */
object CaptureLabels {
    val ALL = listOf(
        "Story camera",
        "Reel camera",
        "Editor (post, story or reel)",
        "Caption / share screen",
        "Own profile: scrolled down",
        "Own profile: Reels tab",
        "Profile I follow",
        "Other profile opened from a chat",
        "Reel opened from my profile",
        "Reel opened from the Home feed",
        "Reel opened from a profile",
        "Post page",
        "Following / Favorites feed",
        "Message requests",
        "Inside a Saved collection",
        "Notifications",
        "Instagram settings",
        "Search: typing #",
        "Home",
        "Reels tab",
        "Search / Explore",
        "Search results: name",
        "Search results: hashtag",
        "Hashtag page",
        "Saved",
        "Saved reel: before swipe",
        "Saved reel: after swipe",
        "DM inbox",
        "DM thread",
        "Reel opened from a DM",
        "Story",
        "Own profile",
        "Other profile",
        "Other profile: Reels tab",
        "Create",
        "Other",
    )
}

object CaptureJson {
    private const val FORMAT = 1

    fun encode(captures: List<Capture>): String {
        val array = JSONArray()
        for (capture in captures) {
            array.put(
                JSONObject()
                    .put("label", capture.label)
                    .put("timestampMs", capture.timestampMs)
                    .put("instagramVersion", capture.instagramVersion)
                    .put("ids", JSONArray(capture.ids))
            )
        }
        return JSONObject().put("format", FORMAT).put("captures", array).toString(2)
    }

    /** Throws [org.json.JSONException] if [json] is not a capture file. */
    fun decode(json: String): List<Capture> {
        val array = JSONObject(json).getJSONArray("captures")
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            val ids = item.getJSONArray("ids")
            Capture(
                label = item.getString("label"),
                timestampMs = item.getLong("timestampMs"),
                instagramVersion = item.getString("instagramVersion"),
                ids = List(ids.length()) { ids.getString(it) },
            )
        }
    }
}
