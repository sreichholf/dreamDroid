package net.reichholf.dreamdroid.ui.services

import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuProvider
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.launchMovieListLoad
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailModalSheet
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Tag
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieDeleteRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.widget.AnchorPopup
import net.reichholf.dreamdroid.helpers.enigma2.Movie as MovieKeys

/**
 * Phase 2.7h: one Movies hub location page as Compose (parity with former MovieListFragment).
 *
 * Tag filter uses an in-composition [MultiChoiceAlertDialog] (Phase 2.1g-ii-e).
 * Pass the same [HubMovieListSession] instance into [HubMovieListPage] so menu actions
 * reach this page.
 *
 * Options menu (tags) and delete-confirm dialog actions are registered here while this
 * page stays in composition.
 */
@Composable
fun HubMovieListPage(
    hostFragment: PhoneNavHostFragment,
    location: String,
    locationIndex: Int,
    modifier: Modifier = Modifier,
    session: HubMovieListSession = remember { HubMovieListSession() },
) {
    val context = LocalContext.current
    val view = LocalView.current
    val listState = remember { MovieListState() }
    val refresh = remember { ComposeRefreshState() }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var detailContent by remember { mutableStateOf<MovieDetailContent?>(null) }
    session.onShowDetail = { detailContent = it }
    var zapJob by remember { mutableStateOf<Job?>(null) }
    var deleteJob by remember { mutableStateOf<Job?>(null) }

    var selectedTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    session.hostFragment = hostFragment
    session.context = context
    session.popupRoot = view.rootView as? ViewGroup ?: view as? ViewGroup
    session.location = location
    session.locationIndex = locationIndex
    session.listState = listState
    session.refresh = refresh
    session.selectedTags = ArrayList(selectedTags)
    session.onSelectedTags = { selectedTags = it.toList() }
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }
    session.onZapJob = { zapJob = it }
    session.onDeleteJob = { deleteJob = it }
    session.onRequestTagPicker = { showTagPicker = true }
    session.onRequestDeleteConfirm = { title -> showDeleteConfirm = title }

    DisposableEffect(hostFragment, session) {
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(session, hostFragment.viewLifecycleOwner)
        session.setToolbarTitle(session.finishedTitle())
        onDispose {
            activity?.removeMenuProvider(session)
            loadJob?.cancel()
            loadJob = null
            zapJob?.cancel()
            zapJob = null
            deleteJob?.cancel()
            deleteJob = null
            session.dismissProgress()
        }
    }

    LaunchedEffect(location, locationIndex) {
        session.reload()
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { session.reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        if (listState.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (emptyMessage != null) {
                    Text(
                        text = emptyMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            MovieListScreen(
                items = listState.items,
                onItemClick = { item, x, y -> session.onItemClick(item, isLong = false, x, y) },
                onItemLongClick = { item, x, y -> session.onItemClick(item, isLong = true, x, y) },
            )
        }
    }

    detailContent?.let { content ->
        MovieDetailModalSheet(
            content = content,
            onDismiss = { detailContent = null },
        )
    }

    if (showTagPicker) {
        val tags = DreamDroid.getTags().map { it.toString() }
        val checked = BooleanArray(tags.size) { i ->
            selectedTags.contains(DreamDroid.getTags()[i])
        }
        MultiChoiceAlertDialog(
            title = stringResource(R.string.choose_tags),
            items = tags,
            initialChecked = checked,
            onDismiss = { showTagPicker = false },
            onConfirm = { indices ->
                session.applyTagSelection(indices)
                showTagPicker = false
            },
        )
    }

    showDeleteConfirm?.let { title ->
        ConfirmAlertDialog(
            title = title,
            message = stringResource(R.string.delete_confirm),
            onDismiss = { showDeleteConfirm = null },
            onConfirm = {
                session.deleteMovie()
                showDeleteConfirm = null
            },
        )
    }
}

/**
 * Mutable movie-list working copy for one hub Movies page.
 *
 * Tag filter is requested via [onRequestTagPicker]; the page hosts [MultiChoiceAlertDialog].
 */
class HubMovieListSession :
    MenuProvider {

    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var popupRoot: ViewGroup? = null
    var location: String = ""
    var locationIndex: Int = -1
    var listState: MovieListState? = null
    var refresh: ComposeRefreshState? = null
    var selectedTags: ArrayList<String> = ArrayList()
    var onSelectedTags: ((List<String>) -> Unit)? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    var onZapJob: ((Job?) -> Unit)? = null
    var onDeleteJob: ((Job?) -> Unit)? = null
    var onShowDetail: ((MovieDetailContent) -> Unit)? = null
    var onRequestTagPicker: (() -> Unit)? = null
    var onRequestDeleteConfirm: ((String) -> Unit)? = null

    private val movies = ArrayList<Movie>()
    private var selectedMovie: ExtendedHashMap? = null
    private var tagsChanged = false
    private var reloadOnSimpleResult = false
    private var progress: ProgressDialog? = null
    private var loadJob: Job? = null
    private var zapJob: Job? = null
    private var deleteJob: Job? = null

    fun dismissProgress() {
        progress?.takeIf { it.isShowing }?.dismiss()
        progress = null
    }

    fun setToolbarTitle(title: String) {
        (context as? AppCompatActivity)?.title = title
    }

    fun finishedTitle(): String {
        val ctx = context ?: return ""
        return location.takeIf { it.isNotEmpty() } ?: ctx.getString(R.string.movies)
    }

    fun toast(message: CharSequence) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    fun httpParams(): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        if (location.isNotEmpty()) {
            params.add(NameValuePair("dirname", location))
        }
        if (selectedTags.isNotEmpty()) {
            params.add(NameValuePair("tag", Tag.implodeTags(selectedTags)))
        }
        return params
    }

    fun reload() {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        if (state.items.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = host.launchMovieListLoad(httpParams()) { success, next, errorText ->
            refreshState.setRefreshing(false)
            setToolbarTitle(finishedTitle())
            movies.clear()
            if (!success) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(errorText)
                return@launchMovieListLoad
            }
            if (next.isEmpty()) {
                state.replaceAll(emptyList())
                onEmptyMessage?.invoke(ctx.getString(R.string.no_list_item))
            } else {
                onEmptyMessage?.invoke(null)
                movies.addAll(next)
                state.replaceAll(movieListItemsFromMovies(movies))
            }
        }
        onLoadJob?.invoke(loadJob)
    }

    fun onItemClick(item: MovieListItem, isLong: Boolean, windowX: Int, windowY: Int) {
        val index = item.index
        if (index < 0 || index >= movies.size) {
            return
        }
        val typed = movies[index]
        selectedMovie = movieToExtendedHashMap(typed)
        val ctx = context ?: return
        val instantZap = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false)
        if ((instantZap && !isLong) || (!instantZap && isLong)) {
            zapTo(typed.reference)
        } else {
            showPopupMenu(windowX, windowY)
        }
    }

    fun showPopupMenu(windowX: Int, windowY: Int) {
        val root = popupRoot ?: return
        AnchorPopup.showAtWindow(root, windowX, windowY) { menu ->
            menu.menuInflater.inflate(R.menu.popup_movielist, menu.menu)
            menu.setOnMenuItemClickListener { menuItem ->
                onMovieAction(menuItem.itemId)
            }
        }
    }

    fun pickTags() {
        tagsChanged = false
        onRequestTagPicker?.invoke()
    }

    fun applyTagSelection(indices: List<Int>) {
        val tags = DreamDroid.getTags()
        val next = ArrayList<String>()
        for (which in indices) {
            if (which in tags.indices) {
                next.add(tags[which])
            }
        }
        tagsChanged = next != selectedTags
        selectedTags = next
        onSelectedTags?.invoke(next.toList())
        if (tagsChanged) {
            reload()
        }
    }

    fun zapTo(ref: String) {
        val host = hostFragment ?: return
        val ctx = context ?: return
        zapJob?.cancel()
        zapJob = host.launchSimpleResultLoad(
            ZapRequestHandler(),
            listOf(NameValuePair("sRef", ref)),
        ) { _, result, http ->
            var toastText = ctx.getText(R.string.get_content_error).toString()
            val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
            when {
                !stateText.isNullOrEmpty() -> toastText = stateText
                http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
            }
            toast(toastText)
        }
        onZapJob?.invoke(zapJob)
    }

    fun deleteMovie() {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val movie = selectedMovie ?: return
        dismissProgress()
        progress = ProgressDialog.show(ctx, "", ctx.getText(R.string.deleting), true)
        reloadOnSimpleResult = true
        deleteJob?.cancel()
        deleteJob = host.launchSimpleResultLoad(
            MovieDeleteRequestHandler(),
            MovieKeys.getDeleteParams(movie),
        ) { _, result, http ->
            dismissProgress()
            var toastText = ctx.getText(R.string.get_content_error).toString()
            val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
            when {
                !stateText.isNullOrEmpty() -> toastText = stateText
                http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
            }
            toast(toastText)
            if (reloadOnSimpleResult && Python.TRUE == result.getString(SimpleResult.KEY_STATE)) {
                reloadOnSimpleResult = false
                reload()
            }
        }
        onDeleteJob?.invoke(deleteJob)
    }

    fun onMovieAction(action: Int): Boolean {
        val ctx = context ?: return false
        val movie = selectedMovie
        when (action) {
            R.id.menu_info -> {
                val descriptionEx = movie?.getString(MovieKeys.KEY_DESCRIPTION_EXTENDED)
                if (descriptionEx.isNullOrEmpty()) {
                    toast(ctx.getString(R.string.no_epg_available))
                    return true
                }
                val typed = findSelectedTypedMovie()
                val content = if (typed != null) {
                    typed.toMovieDetailContent()
                } else {
                    net.reichholf.dreamdroid.helpers.enigma2.Movie(movie).toMovieDetailContent()
                }
                onShowDetail?.invoke(content)
            }
            R.id.menu_zap -> {
                val ref = movie?.getString(MovieKeys.KEY_REFERENCE).orEmpty()
                if (ref.isNotEmpty()) {
                    zapTo(ref)
                }
            }
            R.id.menu_delete -> {
                onRequestDeleteConfirm?.invoke(movie?.getString(MovieKeys.KEY_TITLE).orEmpty())
            }
            Statics.ACTION_DELETE_CONFIRMED -> deleteMovie()
            R.id.menu_download -> {
                val file = movie?.getString(MovieKeys.KEY_FILE_NAME).orEmpty()
                val params = arrayListOf(NameValuePair("file", file))
                val url = SimpleHttpClient.getInstance().buildUrl(URIStore.FILE, params)
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            R.id.menu_stream -> {
                try {
                    val activity = ctx as AppCompatActivity
                    activity.startActivity(
                        IntentFactory.getStreamFileIntent(
                            activity,
                            movie?.getString(MovieKeys.KEY_REFERENCE).orEmpty(),
                            movie?.getString(MovieKeys.KEY_FILE_NAME),
                            movie?.getString(MovieKeys.KEY_TITLE),
                            movie,
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    toast(ctx.getText(R.string.missing_stream_player))
                }
            }
            else -> return false
        }
        return true
    }

    private fun findSelectedTypedMovie(): Movie? {
        val movie = selectedMovie ?: return null
        val ref = movie.getString(MovieKeys.KEY_REFERENCE)
        val file = movie.getString(MovieKeys.KEY_FILE_NAME)
        return movies.firstOrNull { it.reference == ref && it.fileName == file }
    }


    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.locactions_and_tags, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            Statics.ITEM_TAGS -> {
                pickTags()
                true
            }
            else -> false
        }
    }
}
