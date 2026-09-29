package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.MovieDownload
import net.reichholf.dreamdroid.data.MovieListLoad
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Something the page hands to another app. */
sealed interface MovieOpen {
    /** Stream the recording from [url] with the player. */
    data class Stream(val movie: Movie, val url: String) : MovieOpen

    /** A URL the receiver serves without a login. */
    data class Link(val url: String) : MovieOpen

    /** A recording copied into the app cache. */
    data class CachedFile(val file: File) : MovieOpen
}

/**
 * One Movies hub location. [emptyMessage] is shown instead of the list when [items] is
 * empty. [tagPicker] holds the tags to pick from while that dialog is open, [deleteConfirm]
 * the title of the recording to delete while that dialog is open.
 */
data class HubMovieListUiState(
    val location: String,
    val items: List<MovieListItem> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val selectedTags: List<String> = emptyList(),
    val menu: RowMenuState<MovieRowAction>? = null,
    val detail: MovieDetailContent? = null,
    val tagPicker: List<String>? = null,
    val deleteConfirm: String? = null,
    val progress: UiText? = null,
    val open: MovieOpen? = null,
    val blocked: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = when {
            refreshing -> UiText.Resource(R.string.loading)
            location.isEmpty() -> UiText.Resource(R.string.movies)
            else -> UiText.Raw(location)
        }
}

/**
 * Movies of one location on the hub. Keyed by [location] on the hub back-stack entry, so a
 * tab change keeps the loaded list; the tag filter survives process death. Zap, delete,
 * download, and stream are Online-only: the page explains a blocked tap, and this ViewModel
 * ignores it.
 */
@HiltViewModel(assistedFactory = HubMovieListViewModel.Factory::class)
class HubMovieListViewModel @AssistedInject constructor(
    @Assisted private val location: String,
    private val savedStateHandle: SavedStateHandle,
    private val movieRepository: MovieRepository,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(location: String): HubMovieListViewModel
    }

    private val _uiState = MutableStateFlow(
        HubMovieListUiState(
            location = location,
            selectedTags = savedStateHandle.get<ArrayList<String>>(KEY_TAGS).orEmpty(),
            blocked = sessions.status.value.blocksMutations
        )
    )
    val uiState: StateFlow<HubMovieListUiState> = _uiState.asStateFlow()

    private var movies: List<Movie> = emptyList()
    private var selected: Movie? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(blocked = status.blocksMutations) }
            }
        }
        reload()
    }

    fun reload() {
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.items.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        val tags = _uiState.value.selectedTags
        loadJob?.cancel()
        loadJob = viewModelScope.launch { apply(movieRepository.movies(location, tags)) }
    }

    private fun apply(load: MovieListLoad) {
        movies = (load as? MovieListLoad.Movies)?.movies.orEmpty()
        val emptyMessage = when (load) {
            is MovieListLoad.Failed ->
                load.error?.contentErrorText() ?: UiText.Resource(R.string.error_parsing)

            is MovieListLoad.Movies ->
                if (load.movies.isEmpty()) UiText.Resource(R.string.no_list_item) else null
        }
        // Rows changed under an open menu; its row would be stale.
        _uiState.update {
            it.copy(
                items = movieListItemsFromMovies(movies),
                refreshing = false,
                emptyMessage = emptyMessage,
                menu = null
            )
        }
    }

    /** A plain tap opens the row menu; [zap] is the other tap (instant-zap setting). */
    fun onItemMenu(index: Int) {
        val movie = movies.getOrNull(index) ?: return
        selected = movie
        _uiState.update { it.copy(menu = RowMenuState(index, MovieRowAction.entries)) }
    }

    fun onMenuDismiss() {
        _uiState.update { it.copy(menu = null) }
    }

    fun zap(index: Int) {
        val movie = movies.getOrNull(index) ?: return
        selected = movie
        zapTo(movie)
    }

    /** Online-only actions ([MovieRowAction.onlineOnly]) do nothing while blocked. */
    fun onMenuAction(action: MovieRowAction) {
        val movie = selected ?: return
        if (action.onlineOnly && sessions.status.value.blocksMutations) {
            return
        }
        when (action) {
            MovieRowAction.Info -> if (movie.descriptionExtended.isEmpty()) {
                showMessage(UiText.Resource(R.string.no_epg_available))
            } else {
                _uiState.update { it.copy(detail = movie.toMovieDetailContent()) }
            }

            MovieRowAction.Zap -> zapTo(movie)

            MovieRowAction.Delete -> _uiState.update { it.copy(deleteConfirm = movie.title) }

            MovieRowAction.Download -> download(movie)

            MovieRowAction.Stream -> {
                val open = MovieOpen.Stream(movie, movieRepository.streamUrl(movie))
                _uiState.update { it.copy(open = open) }
            }
        }
    }

    fun onDetailDismissed() {
        _uiState.update { it.copy(detail = null) }
    }

    fun onDeleteDismissed() {
        _uiState.update { it.copy(deleteConfirm = null) }
    }

    /** Deletes the recording of the confirm dialog; a successful delete reloads the list. */
    fun onDeleteConfirmed() {
        _uiState.update { it.copy(deleteConfirm = null) }
        val movie = selected ?: return
        if (sessions.status.value.blocksMutations || _uiState.value.progress != null) {
            return
        }
        _uiState.update { it.copy(progress = UiText.Resource(R.string.deleting)) }
        viewModelScope.launch {
            val response = try {
                movieRepository.delete(movie)
            } finally {
                _uiState.update { it.copy(progress = null) }
            }
            showMessage(response.userMessageText())
            if (response.value?.state == Python.TRUE) {
                reload()
            }
        }
    }

    fun onPickTags() {
        _uiState.update { it.copy(tagPicker = profiles.tags().toList()) }
    }

    fun onTagPickerDismissed() {
        _uiState.update { it.copy(tagPicker = null) }
    }

    /** [indices] into [HubMovieListUiState.tagPicker]. A changed filter reloads. */
    fun onTagsPicked(indices: List<Int>) {
        val available = _uiState.value.tagPicker ?: return
        val next = indices.mapNotNull { available.getOrNull(it) }
        val changed = next != _uiState.value.selectedTags
        savedStateHandle[KEY_TAGS] = ArrayList(next)
        _uiState.update { it.copy(tagPicker = null, selectedTags = next) }
        if (changed) {
            reload()
        }
    }

    fun onOpened() {
        _uiState.update { it.copy(open = null) }
    }

    /** No app could take [HubMovieListUiState.open]. */
    fun onOpenFailed() {
        _uiState.update {
            it.copy(open = null, userMessage = UiText.Resource(R.string.missing_stream_player))
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun zapTo(movie: Movie) {
        if (sessions.status.value.blocksMutations) {
            return
        }
        viewModelScope.launch { showMessage(receiver.zap(movie.reference).userMessageText()) }
    }

    private fun download(movie: Movie) {
        val remotePath = movie.fileName
        if (remotePath.isEmpty() || _uiState.value.progress != null) {
            return
        }
        val link = movieRepository.directLink(remotePath)
        if (link != null) {
            _uiState.update { it.copy(open = MovieOpen.Link(link)) }
            return
        }
        _uiState.update { it.copy(progress = UiText.Resource(R.string.loading)) }
        viewModelScope.launch {
            val download = try {
                movieRepository.downloadToCache(remotePath)
            } finally {
                _uiState.update { it.copy(progress = null) }
            }
            when (download) {
                is MovieDownload.Ready ->
                    _uiState.update { it.copy(open = MovieOpen.CachedFile(download.file)) }

                is MovieDownload.Failed -> {
                    val text = download.error.failure.userMessageText()
                    showMessage(
                        if (text == UiText.Raw("")) {
                            UiText.Resource(R.string.get_content_error)
                        } else {
                            text
                        }
                    )
                }
            }
        }
    }

    private fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    private companion object {
        const val KEY_TAGS = "hub_movie_selected_tags"
    }
}
