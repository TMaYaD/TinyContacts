package dev.loonybin.tempcontacts.ui

import java.util.concurrent.TimeUnit

/** Preset lifetimes offered by the add-contact duration picker. */
enum class DurationOption(val label: String, val millis: Long) {
    ONE_HOUR("1 hour", TimeUnit.HOURS.toMillis(1)),
    FOUR_HOURS("4 hours", TimeUnit.HOURS.toMillis(4)),
    ONE_DAY("1 day", TimeUnit.DAYS.toMillis(1)),
    THREE_DAYS("3 days", TimeUnit.DAYS.toMillis(3)),
    ONE_WEEK("1 week", TimeUnit.DAYS.toMillis(7)),
    ;

    companion object {
        val DEFAULT = ONE_DAY
    }
}
