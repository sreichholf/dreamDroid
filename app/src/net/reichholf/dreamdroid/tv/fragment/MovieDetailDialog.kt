package net.reichholf.dreamdroid.tv.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Movie
import androidx.fragment.app.DialogFragment
import net.reichholf.dreamdroid.helpers.enigma2.Movie as HashMovie
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailScreen
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * TV fullscreen movie detail. Reuses phone [MovieDetailScreen] under [DreamDroidTheme]
 * (Phase 3.1b). Prefers typed [Movie]; hash kept for legacy callers.
 */
class MovieDetailDialog : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        retainInstance = true
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dreamdroid_FullscreenDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun detailContent(): MovieDetailContent {
        val args = requireArguments()
        val typed = args.getSerializable(ARG_TYPED_MOVIE) as? Movie
        if (typed != null) {
            return typed.toMovieDetailContent()
        }
        val hash = args.getSerializable(ARG_HASH_MOVIE) as HashMovie
        return hash.toMovieDetailContent()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val content = detailContent()
        val host = this
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DreamDroidTheme {
                    MovieDetailScreen(
                        content = content,
                        modifier = Modifier.fillMaxSize(),
                        heightCap = null,
                    )
                }
            }
        }
    }

    companion object {
        private const val ARG_TYPED_MOVIE = "typedMovie"
        private const val ARG_HASH_MOVIE = "Movie"

        @JvmStatic
        fun newInstance(movie: Movie): MovieDetailDialog {
            val fragment = MovieDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_TYPED_MOVIE, movie)
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun newInstance(movie: HashMovie): MovieDetailDialog {
            val fragment = MovieDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_HASH_MOVIE, movie)
            fragment.arguments = args
            return fragment
        }
    }
}
