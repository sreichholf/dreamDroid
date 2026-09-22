package net.reichholf.dreamdroid.ui.services

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.MenuProvider
import androidx.preference.PreferenceManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.MovieListLoadResult
import net.reichholf.dreamdroid.enigma.loadMovieList
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Movie as MovieKeys
import net.reichholf.dreamdroid.helpers.enigma2.Tag
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieDeleteRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.ZapRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.MovieDao
import net.reichholf.dreamdroid.room.MovieSnapshotStore
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.dialogs.ConfirmAlertDialog
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.dialogs.MultiChoiceAlertDialog
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailModalSheet
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchSimpleResultLoad
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.widget.AnchorPopup

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
    handle: PhoneNavHandle,
    location: String,
    locationIndex: Int,
    modifier: Modifier = Modifier,
    session: HubMovieListSession = remember { HubMovieListSession() }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
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

    session.handle = handle
    session.context = context
    session.popupRoot = AnchorPopup.overlayRoot(view)
    session.location = location
    session.locationIndex = locationIndex
    session.listState = listState
    session.refresh = refresh
    session.scope = scope
    session.selectedTags = ArrayList(selectedTags)
    session.onSelectedTags = { selectedTags = it.toList() }
    session.onEmptyMessage = { emptyMessage = it }
    session.onLoadJob = { loadJob = it }
    session.onZapJob = { zapJob = it }
    session.onDeleteJob = { deleteJob = it }
    session.onRequestTagPicker = { showTagPicker = true }
    session.onRequestDeleteConfirm = { title -> showDeleteConfirm = title }
    session.profileId = DreamDroid.getCurrentProfile().id
    session.movieDao = AppDatabase.movie(context)

    DisposableEffect(handle, session) {
        val activity = context as? AppCompatActivity
        activity?.addMenuProvider(session)
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
        modifier = modifier
    ) {
        if (listState.items.isEmpty()) {
            ListEmptyState(
                loading = refresh.isRefreshing,
                message = emptyMessage,
                onRetry = { session.reload() }
            )
        } else {
            MovieListScreen(
                items = listState.items,
                onItemClick = { item, x, y -> session.onItemClick(item, isLong = false, x, y) },
                onItemLongClick = { item, x, y -> session.onItemClick(item, isLong = true, x, y) }
            )
        }
    }

    detailContent?.let { content ->
        MovieDetailModalSheet(
            content = content,
            onDismiss = { detailContent = null }
        )
    }

    if (showTagPicker) {
        val tags = DreamDroid.getTags()
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
            }
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
            confirmLabel = stringResource(R.string.delete),
            destructive = true
        )
    }

    IndeterminateProgressHost(session.progress)
}

/**
 * Mutable movie-list working copy for one hub Movies page.
 *
 * Tag filter is requested via [onRequestTagPicker]; the page hosts [MultiChoiceAlertDialog].
 */
class HubMovieListSession : MenuProvider {

    var handle: PhoneNavHandle? = null
    var context: android.content.Context? = null
    var popupRoot: ViewGroup? = null
    var location: String = ""
    var locationIndex: Int = -1
    var listState: MovieListState? = null
    var refresh: ComposeRefreshState? = null
    var scope: kotlinx.coroutines.CoroutineScope? = null
    var selectedTags: ArrayList<String> = ArrayList()
    var onSelectedTags: ((List<String>) -> Unit)? = null
    var onEmptyMessage: ((String?) -> Unit)? = null
    var onLoadJob: ((Job?) -> Unit)? = null
    var onZapJob: ((Job?) -> Unit)? = null
    var onDeleteJob: ((Job?) -> Unit)? = null
    var onShowDetail: ((MovieDetailContent) -> Unit)? = null
    var onRequestTagPicker: (() -> Unit)? = null
    var onRequestDeleteConfirm: ((String) -> Unit)? = null
    var profileId: Int? = null
    var movieDao: MovieDao? = null
    var loadMovies: suspend (
        android.content.Context,
        List<NameValuePair>
    ) -> MovieListLoadResult = { context, params ->
        loadMovieList(context, params)
    }

    private val movies = ArrayList<Movie>()
    private var selectedMovie: Movie? = null
    private var tagsChanged = false
    private var reloadOnSimpleResult = false
    private var loadGeneration = 0
    var progress by mutableStateOf<IndeterminateProgressState?>(null)
    private var loadJob: Job? = null
    private var zapJob: Job? = null
    private var deleteJob: Job? = null

    fun beginLoad(): Int = ++loadGeneration

    fun applyLoadResult(generation: Int, success: Boolean, next: List<Movie>, errorText: String?) {
        if (generation != loadGeneration) {
            return
        }
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        refreshState.setRefreshing(false)
        setToolbarTitle(finishedTitle())
        movies.clear()
        if (!success) {
            state.replaceAll(emptyList())
            onEmptyMessage?.invoke(errorText)
            return
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

    fun dismissProgress() {
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
        val ctx = context ?: return
        val state = listState ?: return
        val refreshState = refresh ?: return
        val coroutineScope = scope ?: return
        if (state.items.isEmpty()) {
            onEmptyMessage?.invoke(ctx.getString(R.string.loading))
        } else {
            onEmptyMessage?.invoke(null)
        }
        refreshState.setRefreshing(true)
        setToolbarTitle(ctx.getString(R.string.loading))
        val generation = beginLoad()
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            loadAndApply(generation)
        }
        onLoadJob?.invoke(loadJob)
    }

    suspend fun loadAndApply(generation: Int) {
        val ctx = context ?: return
        val result = loadMovies(ctx.applicationContext, httpParams())
        if (generation != loadGeneration) {
            return
        }
        if (result.success) {
            persistMovies(result.movies)
            applyLoadResult(generation, true, result.movies, null)
            return
        }
        if (selectedTags.isNotEmpty()) {
            applyLoadResult(generation, false, emptyList(), result.errorText)
            return
        }
        val dao = movieDao
        val pid = profileId
        val cached = if (dao != null && pid != null) {
            MovieSnapshotStore.loadMovies(dao, pid, location)
        } else {
            null
        }
        if (cached != null) {
            applyLoadResult(generation, true, cached, null)
        } else {
            applyLoadResult(generation, false, emptyList(), result.errorText)
        }
    }

    private suspend fun persistMovies(loaded: List<Movie>) {
        if (selectedTags.isNotEmpty()) {
            return
        }
        val dao = movieDao ?: return
        val pid = profileId ?: return
        MovieSnapshotStore.replaceMovies(dao, pid, location, loaded)
    }

    fun onItemClick(item: MovieListItem, isLong: Boolean, windowX: Int, windowY: Int) {
        val index = item.index
        if (index < 0 || index >= movies.size) {
            return
        }
        val typed = movies[index]
        selectedMovie = typed
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
        val host = handle ?: return
        val ctx = context ?: return
        host.runOnlineOnly {
            zapJob?.cancel()
            zapJob = host.launchSimpleResultLoad(
                ZapRequestHandler(),
                listOf(NameValuePair("sRef", ref))
            ) { _, result, error ->
                var toastText = ctx.getText(R.string.get_content_error).toString()
                val stateText = result.stateText
                when {
                    !stateText.isNullOrEmpty() -> toastText = stateText
                    error != null -> toastText = error.resolve(ctx).orEmpty()
                }
                toast(toastText)
            }
            onZapJob?.invoke(zapJob)
        }
    }

    fun deleteMovie() {
        if (progress != null) {
            return
        }
        val host = handle ?: return
        val ctx = context ?: return
        val movie = selectedMovie ?: return
        host.runOnlineOnly {
            progress = IndeterminateProgressState(message = ctx.getString(R.string.deleting))
            reloadOnSimpleResult = true
            deleteJob?.cancel()
            deleteJob = host.launchSimpleResultLoad(
                MovieDeleteRequestHandler(),
                MovieKeys.getDeleteParams(movie)
            ) { _, result, error ->
                dismissProgress()
                var toastText = ctx.getText(R.string.get_content_error).toString()
                val stateText = result.stateText
                when {
                    !stateText.isNullOrEmpty() -> toastText = stateText
                    error != null -> toastText = error.resolve(ctx).orEmpty()
                }
                toast(toastText)
                if (reloadOnSimpleResult && Python.TRUE == result.state) {
                    reloadOnSimpleResult = false
                    reload()
                }
            }
            onDeleteJob?.invoke(deleteJob)
        }
    }

    fun onMovieAction(action: Int): Boolean {
        val ctx = context ?: return false
        val movie = selectedMovie
        when (action) {
            R.id.menu_info -> {
                if (movie == null || movie.descriptionExtended.isEmpty()) {
                    toast(ctx.getString(R.string.no_epg_available))
                    return true
                }
                onShowDetail?.invoke(movie.toMovieDetailContent())
            }

            R.id.menu_zap -> {
                val ref = movie?.reference.orEmpty()
                if (ref.isNotEmpty()) {
                    zapTo(ref)
                }
            }

            R.id.menu_delete -> {
                onRequestDeleteConfirm?.invoke(movie?.title.orEmpty())
            }

            Statics.ACTION_DELETE_CONFIRMED -> deleteMovie()

            R.id.menu_download -> downloadSelectedMovie()

            R.id.menu_stream -> {
                val host = handle ?: return false
                host.runOnlineOnly {
                    try {
                        val activity = ctx as AppCompatActivity
                        activity.startActivity(
                            IntentFactory.getStreamFileIntent(
                                activity,
                                movie?.reference.orEmpty(),
                                movie?.fileName,
                                movie?.title,
                                movie
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        toast(ctx.getText(R.string.missing_stream_player))
                    }
                }
            }

            else -> return false
        }
        return true
    }

    private fun downloadSelectedMovie() {
        val ctx = context ?: return
        val remotePath = selectedMovie?.fileName.orEmpty()
        if (remotePath.isEmpty()) {
            return
        }
        val profile = DreamDroid.currentProfileOrNull() ?: return
        val params = arrayListOf(NameValuePair("file", remotePath))
        if (!profile.login) {
            val url = EnigmaUrls.page(profile, URIStore.FILE, params)
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return
        }
        val host = handle ?: return
        val coroutineScope = scope ?: return
        host.runOnlineOnly {
            if (progress != null) {
                return@runOnlineOnly
            }
            progress = IndeterminateProgressState(message = ctx.getString(R.string.loading))
            coroutineScope.launch {
                try {
                    val outcome = withContext(Dispatchers.IO) {
                        downloadMovieFile(ctx, profile, remotePath)
                    }
                    when (outcome) {
                        is MovieFileDownload.HttpFailed -> {
                            toastMovieDownloadFailure(outcome.result)
                        }

                        is MovieFileDownload.Ready -> openCachedMovie(outcome.file)
                    }
                } finally {
                    dismissProgress()
                }
            }
        }
    }

    private fun toastMovieDownloadFailure(result: EnigmaHttpResult.Failure) {
        val ctx = context ?: return
        var toastText = ctx.getText(R.string.get_content_error).toString()
        val resolved = result.error.resolve(ctx)
        if (!resolved.isNullOrEmpty()) {
            toastText = resolved
        }
        toast(toastText)
    }

    private fun openCachedMovie(file: File) {
        val ctx = context ?: return
        try {
            ctx.startActivity(movieViewIntent(ctx, file))
        } catch (_: ActivityNotFoundException) {
            toast(ctx.getText(R.string.missing_stream_player))
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.locactions_and_tags, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        Statics.ITEM_TAGS -> {
            pickTags()
            true
        }

        else -> false
    }
}
