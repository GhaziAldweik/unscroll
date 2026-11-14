package dev.ghazi.igfocus.stats

/**
 * How many times Unscroll stepped in per day: a visit to a blocked screen that it covered or
 * left. Exact counts only, never an estimate of time. Kept for [KEEP_DAYS] days, on the phone.
 * Days are epoch days (`LocalDate.toEpochDay()`), so the class needs no Android code.
 */
class StepIns private constructor(private val byDay: Map<Long, Int>) {

    fun on(day: Long): Int = byDay[day] ?: 0

    /** The seven days ending with [today], oldest first. */
    fun week(today: Long): List<Int> = (today - 6..today).map(::on)

    fun weekTotal(today: Long): Int = week(today).sum()

    /** One more step-in on [today]; days older than [KEEP_DAYS] are dropped. */
    fun plusOne(today: Long): StepIns =
        StepIns((byDay + (today to on(today) + 1)).filterKeys { it > today - KEEP_DAYS })

    /** "20406:3,20407:12", the stored form. */
    fun encode(): String = byDay.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        const val KEEP_DAYS = 14

        val EMPTY = StepIns(emptyMap())

        /** Unreadable entries are skipped rather than failing, so a bad value never loses the rest. */
        fun decode(stored: String?): StepIns {
            if (stored.isNullOrBlank()) return EMPTY
            val days = stored.split(',').mapNotNull { entry ->
                val (day, count) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val d = day.trim().toLongOrNull() ?: return@mapNotNull null
                val c = count.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                d to c
            }
            return StepIns(days.toMap())
        }
    }
}
