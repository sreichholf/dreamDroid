package net.reichholf.dreamdroid.enigma.autotimer

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * The plugin's id of an AutoTimer. It is a position in the box's list, valid for one read of
 * its config: the box numbers its AutoTimers 1..n again whenever the config file changed.
 */
@JvmInline
value class AutoTimerId(val value: Int)

/** One `<timer>` of `/autotimer`. */
data class AutoTimer(val id: AutoTimerId, val settings: AutoTimerSettings, val extras: Extras)

/** An entry of the list; one the model cannot represent is [Unreadable]. */
sealed interface AutoTimerEntry {
    val id: AutoTimerId
    val name: String

    data class Readable(val autoTimer: AutoTimer) : AutoTimerEntry {
        override val id: AutoTimerId get() = autoTimer.id
        override val name: String get() = autoTimer.settings.name
    }

    data class Unreadable(
        override val id: AutoTimerId,
        override val name: String,
        val reason: String
    ) : AutoTimerEntry
}

/** What dreamDroid reads and edits of an AutoTimer. */
data class AutoTimerSettings(
    val name: String,
    val match: String,
    val enabled: Boolean,
    val searchType: SearchType,
    val caseSensitive: Boolean,
    val targets: List<Target>,
    val timeWindow: ClockWindow?,
    val dateWindow: DateWindow?,
    val offset: Offset?,
    val maxDurationMinutes: Int?,
    val location: String?,
    val tags: List<String>,
    val include: Filters,
    val exclude: Filters,
    val afterEvent: AfterEvent,
    val recordMode: RecordMode,
    val duplicates: DuplicateCheck
)

/** Settings the list shows that dreamDroid never writes, so an edit keeps them. */
data class Extras(
    val counter: Boolean = false,
    val vps: Boolean = false,
    val seriesPlugin: Boolean = false,
    val overrideAlternatives: Boolean = false
)

enum class SearchType(val token: String) {
    Partial("partial"),
    Exact("exact"),
    Description("description")
}

/** A channel or a whole bouquet the AutoTimer searches. */
sealed interface Target {
    val ref: String
    val name: String

    data class Channel(override val ref: String, override val name: String) : Target

    data class Bouquet(override val ref: String, override val name: String) : Target

    companion object {
        fun of(ref: String, name: String): Target =
            if (ref.startsWith(BOUQUET_PREFIX)) Bouquet(ref, name) else Channel(ref, name)

        private const val BOUQUET_PREFIX = "1:7:"
    }
}

/** Daily clock window; [to] before [from] wraps midnight. */
data class ClockWindow(val from: LocalTime, val to: LocalTime)

data class DateWindow(val after: Instant, val before: Instant)

/** Margins around a recording; null in [AutoTimerSettings] means the receiver's default. */
data class Offset(val beforeMinutes: Int, val afterMinutes: Int)

data class Filters(
    val title: List<String> = emptyList(),
    val shortDescription: List<String> = emptyList(),
    val description: List<String> = emptyList(),
    val days: List<DayFilter> = emptyList()
) {
    val isEmpty: Boolean
        get() = title.isEmpty() && shortDescription.isEmpty() && description.isEmpty() &&
            days.isEmpty()
}

/** A `dayofweek` filter value: `0` (Monday) to `6`, `weekday`, or `weekend`. */
sealed interface DayFilter {
    val token: String

    data class On(val day: DayOfWeek) : DayFilter {
        override val token: String get() = (day.value - 1).toString()
    }

    data object Weekdays : DayFilter {
        override val token: String = "weekday"
    }

    data object Weekend : DayFilter {
        override val token: String = "weekend"
    }

    companion object {
        fun of(token: String): DayFilter? = when (token) {
            Weekdays.token -> Weekdays
            Weekend.token -> Weekend
            else -> token.toIntOrNull()?.takeIf { it in 0..6 }?.let { On(DayOfWeek.of(it + 1)) }
        }
    }
}

/** What the box does after a recording. The list and `edit` name two actions differently. */
enum class AfterEventAction(val listToken: String, val editToken: String) {
    Nothing("none", "nothing"),
    Standby("standby", "standby"),
    DeepStandby("shutdown", "deepstandby"),
    Auto("auto", "auto");

    companion object {
        /** The config reader takes `deepstandby` as well as `shutdown`. */
        fun ofListToken(token: String): AfterEventAction? =
            entries.firstOrNull { it.listToken == token } ?: DeepStandby.takeIf {
                token == DeepStandby.editToken
            }
    }
}

sealed interface AfterEvent {
    /** No `<afterevent>`: the receiver's setting applies. */
    data object ReceiverDefault : AfterEvent

    data class Fixed(val action: AfterEventAction, val window: ClockWindow? = null) : AfterEvent

    /** More than one `<afterevent>`. `edit` can set only one, so dreamDroid never writes it. */
    data class Several(val entries: List<Fixed>) : AfterEvent
}

sealed interface RecordMode {
    data object Record : RecordMode

    data class Zap(val setEndTime: Boolean) : RecordMode
}

sealed interface DuplicateCheck {
    data object Off : DuplicateCheck

    data class On(val scope: DuplicateScope, val compare: DescriptionCompare) : DuplicateCheck
}

enum class DuplicateScope(val code: Int) {
    SameService(1),
    AnyService(2),
    AnyServiceOrRecording(3)
}

enum class DescriptionCompare(val code: Int) {
    Title(0),
    TitleAndShort(1),
    All(2)
}
