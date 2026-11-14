package dev.ghazi.igfocus.policy

/**
 * Whose Stories may be shown. The whole rule from the brief:
 * show a story by X only if Stories are on and X is not hidden, or if X is the user.
 */
object StoriesRule {

    /** Usernames are compared without a leading @, surrounding spaces or letter case. */
    fun normalize(username: String): String = username.trim().removePrefix("@").trim().lowercase()

    /** [hidden] must hold normalized usernames, which is how the settings store keeps them. */
    fun shouldShow(author: String, storiesOn: Boolean, hidden: Set<String>, ownUsername: String): Boolean {
        val who = normalize(author)
        if (who.isNotEmpty() && who == normalize(ownUsername)) return true
        return storiesOn && who !in hidden
    }
}
