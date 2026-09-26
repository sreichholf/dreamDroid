package net.reichholf.dreamdroid.ui.nav

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.Timer

/**
 * Stable route ids. Saved start routes and drawer highlight still use these
 * strings. The NavHost navigates the [Serializable] types, whose [SerialName]
 * matches the id.
 */
object PhoneNavRoutes {
    const val DEVICE_INFO = "device_info"
    const val SIGNAL = "signal"
    const val SCREENSHOT = "screenshot"
    const val CURRENT = "current"
    const val ZAP = "zap"
    const val BACKUP = "backup"
    const val PROFILES = "profiles"
    const val EPG = "epg"
    const val MULTI_EPG = "multi_epg"
    const val REMOTE = "remote"
    const val SETTINGS = "settings"
    const val HUB = "hub"
    const val TOOLS = "tools"
    const val ABOUT = "about"
    const val POWER = "power"
    const val SEND_MESSAGE = "send_message"
    const val SLEEP_TIMER = "sleep_timer"
    const val CHANGELOG = "changelog"
    const val PROFILE_CHECK = "profile_check"
    const val SERVICE_EPG = "service_epg"
    const val EPG_SEARCH = "epg_search"
    const val PICK_SERVICE = "pick_service"
    const val PROFILE_EDIT = "profile_edit"
    const val TIMER_EDIT = "timer_edit"
    const val TIMER_SERVICE_PICK = "timer_service_pick"

    /** Absent [Epg.timeSec] / [MultiEpg.timeSec]. Zero is a real instant. */
    const val ABSENT_TIME_SEC = -1L

    fun showsShellDestinationBar(route: String?): Boolean {
        val key = routeKey(route)
        return key == HUB || key == TOOLS
    }
}

/** Route id, ignoring query arguments, path arguments, and a package prefix. */
fun routeKey(route: String?): String? {
    if (route.isNullOrEmpty()) {
        return null
    }
    return route.substringBefore('?').substringBefore('/').substringAfterLast('.')
}

private val startRouteIds = setOf(
    PhoneNavRoutes.DEVICE_INFO,
    PhoneNavRoutes.SIGNAL,
    PhoneNavRoutes.SCREENSHOT,
    PhoneNavRoutes.CURRENT,
    PhoneNavRoutes.ZAP,
    PhoneNavRoutes.BACKUP,
    PhoneNavRoutes.PROFILES,
    PhoneNavRoutes.EPG,
    PhoneNavRoutes.MULTI_EPG,
    PhoneNavRoutes.REMOTE,
    PhoneNavRoutes.SETTINGS,
    PhoneNavRoutes.HUB,
    PhoneNavRoutes.TOOLS,
    PhoneNavRoutes.PROFILE_CHECK
)

/** NavHost destination for a saved id. Unknown ids become [Hub]. */
fun routeForId(id: String): Any = when (routeKey(id)) {
    PhoneNavRoutes.DEVICE_INFO -> DeviceInfo
    PhoneNavRoutes.SIGNAL -> Signal
    PhoneNavRoutes.SCREENSHOT -> Screenshot
    PhoneNavRoutes.CURRENT -> Current
    PhoneNavRoutes.ZAP -> Zap
    PhoneNavRoutes.BACKUP -> Backup
    PhoneNavRoutes.PROFILES -> Profiles
    PhoneNavRoutes.EPG -> Epg()
    PhoneNavRoutes.MULTI_EPG -> MultiEpg()
    PhoneNavRoutes.REMOTE -> Remote
    PhoneNavRoutes.SETTINGS -> Settings
    PhoneNavRoutes.HUB -> Hub
    PhoneNavRoutes.TOOLS -> Tools
    PhoneNavRoutes.ABOUT -> About
    PhoneNavRoutes.POWER -> Power
    PhoneNavRoutes.SEND_MESSAGE -> SendMessage
    PhoneNavRoutes.SLEEP_TIMER -> SleepTimerRoute()
    PhoneNavRoutes.CHANGELOG -> Changelog
    PhoneNavRoutes.PROFILE_CHECK -> ProfileCheck
    PhoneNavRoutes.PICK_SERVICE -> PickService
    PhoneNavRoutes.PROFILE_EDIT -> ProfileEdit()
    PhoneNavRoutes.TIMER_EDIT -> TimerEdit()
    PhoneNavRoutes.TIMER_SERVICE_PICK -> TimerServicePick
    PhoneNavRoutes.SERVICE_EPG -> ServiceEpg(serviceRef = "")
    PhoneNavRoutes.EPG_SEARCH -> EpgSearch()
    else -> Hub
}

/**
 * Start destination for a saved back-stack id. Leaves such as profile edit are
 * not start routes, so they fall back instead of throwing on restore.
 */
fun startDestinationForSavedId(id: String, fallback: Any): Any {
    val key = routeKey(id) ?: return fallback
    if (key !in startRouteIds) {
        return fallback
    }
    return routeForId(key)
}

fun normalizeRoute(route: Any): Any = when (route) {
    is String -> routeForId(route)
    else -> route
}

/** Bare route id for a route object or a saved string id. */
fun routeId(route: Any): String {
    val normalized = if (route is String) routeForId(route) else route
    return when (normalized) {
        DeviceInfo -> PhoneNavRoutes.DEVICE_INFO
        Signal -> PhoneNavRoutes.SIGNAL
        Screenshot -> PhoneNavRoutes.SCREENSHOT
        Current -> PhoneNavRoutes.CURRENT
        Zap -> PhoneNavRoutes.ZAP
        Backup -> PhoneNavRoutes.BACKUP
        Profiles -> PhoneNavRoutes.PROFILES
        Remote -> PhoneNavRoutes.REMOTE
        Settings -> PhoneNavRoutes.SETTINGS
        Hub -> PhoneNavRoutes.HUB
        Tools -> PhoneNavRoutes.TOOLS
        About -> PhoneNavRoutes.ABOUT
        Power -> PhoneNavRoutes.POWER
        SendMessage -> PhoneNavRoutes.SEND_MESSAGE
        Changelog -> PhoneNavRoutes.CHANGELOG
        ProfileCheck -> PhoneNavRoutes.PROFILE_CHECK
        PickService -> PhoneNavRoutes.PICK_SERVICE
        TimerServicePick -> PhoneNavRoutes.TIMER_SERVICE_PICK
        is ServiceEpg -> PhoneNavRoutes.SERVICE_EPG
        is EpgSearch -> PhoneNavRoutes.EPG_SEARCH
        is Epg -> PhoneNavRoutes.EPG
        is MultiEpg -> PhoneNavRoutes.MULTI_EPG
        is SleepTimerRoute -> PhoneNavRoutes.SLEEP_TIMER
        is ProfileEdit -> PhoneNavRoutes.PROFILE_EDIT
        is TimerEdit -> PhoneNavRoutes.TIMER_EDIT
        else -> PhoneNavRoutes.HUB
    }
}

@Serializable
@SerialName(PhoneNavRoutes.DEVICE_INFO)
data object DeviceInfo

@Serializable
@SerialName(PhoneNavRoutes.SIGNAL)
data object Signal

@Serializable
@SerialName(PhoneNavRoutes.SCREENSHOT)
data object Screenshot

@Serializable
@SerialName(PhoneNavRoutes.CURRENT)
data object Current

@Serializable
@SerialName(PhoneNavRoutes.ZAP)
data object Zap

@Serializable
@SerialName(PhoneNavRoutes.BACKUP)
data object Backup

@Serializable
@SerialName(PhoneNavRoutes.PROFILES)
data object Profiles

@Serializable
@SerialName(PhoneNavRoutes.REMOTE)
data object Remote

@Serializable
@SerialName(PhoneNavRoutes.SETTINGS)
data object Settings

@Serializable
@SerialName(PhoneNavRoutes.HUB)
data object Hub

@Serializable
@SerialName(PhoneNavRoutes.TOOLS)
data object Tools

@Serializable
@SerialName(PhoneNavRoutes.ABOUT)
data object About

@Serializable
@SerialName(PhoneNavRoutes.POWER)
data object Power

@Serializable
@SerialName(PhoneNavRoutes.SEND_MESSAGE)
data object SendMessage

@Serializable
@SerialName(PhoneNavRoutes.CHANGELOG)
data object Changelog

@Serializable
@SerialName(PhoneNavRoutes.PROFILE_CHECK)
data object ProfileCheck

@Serializable
@SerialName(PhoneNavRoutes.PICK_SERVICE)
data object PickService

@Serializable
@SerialName(PhoneNavRoutes.TIMER_SERVICE_PICK)
data object TimerServicePick

@Serializable
@SerialName(PhoneNavRoutes.SERVICE_EPG)
data class ServiceEpg(val serviceRef: String, val serviceName: String = "")

@Serializable
@SerialName(PhoneNavRoutes.EPG_SEARCH)
data class EpgSearch(val query: String = "")

@Serializable
@SerialName(PhoneNavRoutes.EPG)
data class Epg(
    val serviceRef: String = "",
    val serviceName: String = "",
    val timeSec: Long = PhoneNavRoutes.ABSENT_TIME_SEC
) {
    fun timeOrNull(): Long? = timeSec.takeIf { it >= 0L }
}

@Serializable
@SerialName(PhoneNavRoutes.MULTI_EPG)
data class MultiEpg(
    val serviceRef: String = "",
    val serviceName: String = "",
    val focusedServiceRef: String = "",
    val timeSec: Long = PhoneNavRoutes.ABSENT_TIME_SEC
) {
    fun timeOrNull(): Long? = timeSec.takeIf { it >= 0L }

    fun focusedOrNull(): String? = focusedServiceRef.ifEmpty { null }
}

@Serializable
@SerialName(PhoneNavRoutes.SLEEP_TIMER)
data class SleepTimerRoute(
    val minutes: Int = 90,
    val enabled: Boolean = false,
    val action: String = ""
)

@Serializable
@SerialName(PhoneNavRoutes.PROFILE_EDIT)
data class ProfileEdit(
    val profileId: Int = -1,
    val name: String = "",
    val host: String = "",
    val streamHost: String = "",
    val port: Int = 0,
    val user: String = "",
    val simpleRemote: Boolean = false
) {
    fun tag(): String = if (profileId > 0) {
        "profile_edit:$profileId"
    } else {
        "profile_edit:new"
    }

    /** Unsaved discovery prefill. A blank route is a pure create. */
    fun launchProfile(): Profile? {
        if (profileId > 0 || (host.isEmpty() && name.isEmpty())) {
            return null
        }
        val profile = Profile.getDefault()
        profile.name = name
        profile.host = host
        profile.streamHost = streamHost.ifEmpty { host }
        if (port > 0) {
            profile.port = port
        }
        profile.user = user
        profile.simpleRemote = simpleRemote
        return profile
    }
}

fun Profile?.toProfileEditRoute(): ProfileEdit {
    if (this == null) {
        return ProfileEdit()
    }
    val savedId = id ?: -1
    if (savedId > 0) {
        return ProfileEdit(profileId = savedId)
    }
    if (host.isNullOrEmpty() && name.isNullOrEmpty()) {
        return ProfileEdit()
    }
    return ProfileEdit(
        name = name.orEmpty(),
        host = host.orEmpty(),
        streamHost = streamHost.orEmpty(),
        port = port,
        user = user.orEmpty(),
        simpleRemote = simpleRemote
    )
}

@Serializable
@SerialName(PhoneNavRoutes.TIMER_EDIT)
data class TimerEdit(
    val create: Boolean = false,
    val reference: String = "",
    val serviceName: String = "",
    val eit: String = "",
    val name: String = "",
    val description: String = "",
    val descriptionExtended: String = "",
    val disabled: String = "",
    val begin: String = "",
    val end: String = "",
    val duration: String = "",
    val beginReadable: String = "",
    val endReadable: String = "",
    val durationReadable: String = "",
    val startPrepare: String = "",
    val justPlay: String = "",
    val afterEvent: String = "",
    val location: String = "",
    val tags: String = "",
    val logEntries: String = "",
    val fileName: String = "",
    val backOff: String = "",
    val nextActivation: String = "",
    val firstTryPrepare: String = "",
    val state: String = "",
    val repeated: String = "",
    val dontSave: String = "",
    val canceled: String = "",
    val toggleDisabled: String = ""
) {
    fun tag(): String = if (create) {
        "timer_edit:new:$begin"
    } else {
        "timer_edit:$reference:$begin"
    }

    fun toTimer(): Timer = Timer(
        reference = reference,
        serviceName = serviceName,
        eit = eit,
        name = name,
        description = description,
        descriptionExtended = descriptionExtended,
        disabled = disabled,
        begin = begin,
        end = end,
        duration = duration,
        beginReadable = beginReadable,
        endReadable = endReadable,
        durationReadable = durationReadable,
        startPrepare = startPrepare,
        justPlay = justPlay,
        afterEvent = afterEvent,
        location = location,
        tags = tags,
        logEntries = logEntries,
        fileName = fileName,
        backOff = backOff,
        nextActivation = nextActivation,
        firstTryPrepare = firstTryPrepare,
        state = state,
        repeated = repeated,
        dontSave = dontSave,
        canceled = canceled,
        toggleDisabled = toggleDisabled
    )

    companion object {
        fun from(timer: Timer, create: Boolean): TimerEdit = TimerEdit(
            create = create,
            reference = timer.reference,
            serviceName = timer.serviceName,
            eit = timer.eit,
            name = timer.name,
            description = timer.description,
            descriptionExtended = timer.descriptionExtended,
            disabled = timer.disabled,
            begin = timer.begin,
            end = timer.end,
            duration = timer.duration,
            beginReadable = timer.beginReadable,
            endReadable = timer.endReadable,
            durationReadable = timer.durationReadable,
            startPrepare = timer.startPrepare,
            justPlay = timer.justPlay,
            afterEvent = timer.afterEvent,
            location = timer.location,
            tags = timer.tags,
            logEntries = timer.logEntries,
            fileName = timer.fileName,
            backOff = timer.backOff,
            nextActivation = timer.nextActivation,
            firstTryPrepare = timer.firstTryPrepare,
            state = timer.state,
            repeated = timer.repeated,
            dontSave = timer.dontSave,
            canceled = timer.canceled,
            toggleDisabled = timer.toggleDisabled
        )
    }
}
