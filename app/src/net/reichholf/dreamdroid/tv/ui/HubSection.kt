package net.reichholf.dreamdroid.tv.ui

import androidx.annotation.StringRes
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * What the TV hub shows. Live TV is home: Back from any other section returns there. Live TV
 * and Guide show a bouquet, Recordings a movie location; null while there is none.
 */
sealed interface HubSection {
    data class Live(val bouquetRef: String?) : HubSection

    data class Guide(val bouquetRef: String?) : HubSection

    data class Recordings(val location: String?) : HubSection

    data object Timers : HubSection

    data object Settings : HubSection
}

/** The top bar tabs, ordered by use. Settings is the trailing icon button, not a tab. */
enum class HubTab(@param:StringRes val titleRes: Int) {
    Live(R.string.tv_hub_live),
    Guide(R.string.tv_hub_guide),
    Recordings(R.string.tv_hub_recordings),
    Timers(R.string.tv_hub_timers)
}

/** A drawer row: a bouquet ref or a movie location, and its label. */
data class HubDrawerItem(val id: String, val title: String)

val HubSection.tab: HubTab?
    get() = when (this) {
        is HubSection.Live -> HubTab.Live
        is HubSection.Guide -> HubTab.Guide
        is HubSection.Recordings -> HubTab.Recordings
        HubSection.Timers -> HubTab.Timers
        HubSection.Settings -> null
    }

/** The drawer row this section shows. */
val HubSection.drawerSelection: String?
    get() = when (this) {
        is HubSection.Live -> bouquetRef
        is HubSection.Recordings -> location
        is HubSection.Guide, HubSection.Timers, HubSection.Settings -> null
    }

/** Guide needs a bouquet, Recordings a movie location. */
fun hubTabs(bouquetRows: List<HubBouquetRow>, movieLocations: List<String>): List<HubTab> =
    buildList {
        add(HubTab.Live)
        if (bouquetRows.hasBouquets()) {
            add(HubTab.Guide)
        }
        if (movieLocations.isNotEmpty()) {
            add(HubTab.Recordings)
        }
        add(HubTab.Timers)
    }

/**
 * The drawer of [this]: the bouquets for Live TV, the movie locations for Recordings. Null for
 * a section without a drawer, whose content is full width: Guide picks its bouquet with the
 * MultiEPG's own bouquet chip. A bouquet without a name is titled [unnamedBouquet].
 */
fun HubSection.drawerItems(
    bouquetRows: List<HubBouquetRow>,
    movieLocations: List<String>,
    unnamedBouquet: String
): List<HubDrawerItem>? = when (this) {
    is HubSection.Live ->
        bouquetRows
            .filter { it.bouquet.reference.isNotBlank() }
            .map { row ->
                HubDrawerItem(row.bouquet.reference, row.bouquet.name.ifBlank { unnamedBouquet })
            }

    is HubSection.Recordings -> movieLocations.map { HubDrawerItem(it, it) }

    is HubSection.Guide, HubSection.Timers, HubSection.Settings -> null
}

/** The drawer icon of [this]; the collapsed TV drawer shows only the icon. */
val HubSection.drawerIconRes: Int
    get() = when (this) {
        is HubSection.Recordings -> R.drawable.ic_menu_movie
        else -> R.drawable.ic_menu_tv
    }

/**
 * The content title of [this]: the bouquet or location shown, or Settings, which has no tab.
 * Guide titles itself and Timers is named by its tab.
 */
fun HubSection.title(bouquetRows: List<HubBouquetRow>): UiText? = when (this) {
    is HubSection.Live -> bouquetRef?.let { ref ->
        val name = bouquetRows.firstOrNull { it.bouquet.reference == ref }?.bouquet?.name
        if (name.isNullOrBlank()) UiText.Resource(R.string.services) else UiText.Raw(name)
    }

    is HubSection.Recordings -> location?.let(UiText::Raw)

    HubSection.Settings -> UiText.Resource(R.string.settings)

    is HubSection.Guide, HubSection.Timers -> null
}

/**
 * The hub's selection: the [section] on screen, plus the bouquet Live TV and Guide share and
 * the movie location Recordings returns to while another section is shown.
 */
data class HubNavigation(
    val section: HubSection = HubSection.Live(null),
    val bouquetRef: String? = null,
    val location: String? = null
) {
    /** Opens [tab] on the remembered bouquet or location, else the first one. */
    fun open(
        tab: HubTab,
        bouquetRows: List<HubBouquetRow>,
        movieLocations: List<String>
    ): HubNavigation {
        val bouquet = validBouquet(bouquetRef, bouquetRows)
        val dirname = validLocation(location, movieLocations)
        val section = when (tab) {
            HubTab.Live -> HubSection.Live(bouquet)
            HubTab.Guide -> HubSection.Guide(bouquet)
            HubTab.Recordings -> HubSection.Recordings(dirname)
            HubTab.Timers -> HubSection.Timers
        }
        return HubNavigation(section, bouquet, dirname)
    }

    fun openSettings(): HubNavigation = copy(section = HubSection.Settings)

    /**
     * Selects the row [id] of the section on screen: a drawer row, or the bouquet picked with
     * the Guide's bouquet chip.
     */
    fun select(id: String): HubNavigation = when (section) {
        is HubSection.Live -> copy(section = HubSection.Live(id), bouquetRef = id)
        is HubSection.Guide -> copy(section = HubSection.Guide(id), bouquetRef = id)
        is HubSection.Recordings -> copy(section = HubSection.Recordings(id), location = id)
        HubSection.Timers, HubSection.Settings -> this
    }

    /** Back: Live TV on its bouquet, or null on Live TV, where Back leaves the hub. */
    fun back(): HubNavigation? = if (section is HubSection.Live) {
        null
    } else {
        copy(section = HubSection.Live(bouquetRef))
    }

    /**
     * After a reload: a bouquet or location that is gone falls back to the first one, and a
     * tab that is gone (Guide without bouquets, Recordings without locations) to Live TV.
     */
    fun reconcile(bouquetRows: List<HubBouquetRow>, movieLocations: List<String>): HubNavigation {
        val bouquet = validBouquet(bouquetRef, bouquetRows)
        val dirname = validLocation(location, movieLocations)
        val section = when (section) {
            is HubSection.Live -> HubSection.Live(bouquet)

            is HubSection.Guide ->
                if (bouquet == null) HubSection.Live(null) else HubSection.Guide(bouquet)

            is HubSection.Recordings ->
                if (dirname == null) HubSection.Live(bouquet) else HubSection.Recordings(dirname)

            HubSection.Timers, HubSection.Settings -> section
        }
        return HubNavigation(section, bouquet, dirname)
    }

    /** The Live TV bouquet on screen, whose EPG the hub syncs. */
    val liveBouquetRef: String?
        get() = (section as? HubSection.Live)?.bouquetRef

    val recordingsLocation: String?
        get() = (section as? HubSection.Recordings)?.location
}

private fun List<HubBouquetRow>.hasBouquets(): Boolean = any { it.bouquet.reference.isNotBlank() }

private fun validBouquet(ref: String?, bouquetRows: List<HubBouquetRow>): String? {
    val refs = bouquetRows.map { it.bouquet.reference }.filter { it.isNotBlank() }
    return ref?.takeIf { it in refs } ?: refs.firstOrNull()
}

private fun validLocation(location: String?, movieLocations: List<String>): String? =
    location?.takeIf { it in movieLocations } ?: movieLocations.firstOrNull()
