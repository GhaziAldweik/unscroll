package dev.ghazi.igfocus.inspector

/** The most recent distinct signatures, oldest first. In memory only. Not thread-safe. */
class SignatureHistory(private val capacity: Int = 50) {
    private val recent = LinkedHashSet<ScreenSignature>()

    val size: Int get() = recent.size

    /** Records [signature] as the newest entry. Returns true if it was not already present. */
    fun add(signature: ScreenSignature): Boolean {
        val isNew = !recent.remove(signature)
        recent.add(signature)
        if (recent.size > capacity) recent.remove(recent.first())
        return isNew
    }

    fun snapshot(): List<ScreenSignature> = recent.toList()
}
