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
import net.reichholf.dreamdroid.fragment.dialogs.AbstractDialog
import net.reichholf.dreamdroid.helpers.enigma2.Movie
import net.reichholf.dreamdroid.ui.movies.MovieDetailScreen
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * TV fullscreen movie detail. Reuses phone [MovieDetailScreen] under [DreamDroidTheme]
 * (Phase 3.1b).
 */
class MovieDetailDialog : AbstractDialog() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dreamdroid_FullscreenDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val movie = requireArguments().getSerializable(Movie::class.java.simpleName) as Movie
        val content = movie.toMovieDetailContent()
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
        @JvmStatic
        fun newInstance(movie: Movie): MovieDetailDialog {
            val fragment = MovieDetailDialog()
            val args = Bundle()
            args.putSerializable(Movie::class.java.simpleName, movie)
            fragment.arguments = args
            return fragment
        }
    }
}
