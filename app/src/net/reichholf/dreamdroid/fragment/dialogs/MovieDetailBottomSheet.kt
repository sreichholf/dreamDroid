package net.reichholf.dreamdroid.fragment.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.helpers.enigma2.Movie as HashMovie
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailScreen
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Movie detail bottom sheet. Compose Material 3 body; info-only (list popup owns actions).
 * Typed [Movie] preferred; hash [HashMovie] kept for list/video overlay until those paths are typed.
 */
class MovieDetailBottomSheet : BottomSheetActionDialog() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val content: MovieDetailContent? = when {
            args.containsKey(ARG_TYPED_MOVIE) -> {
                val movie = args.getSerializable(ARG_TYPED_MOVIE) as? Movie
                movie?.toMovieDetailContent()
            }
            else -> {
                val movie = args.getSerializable(ARG_HASH_MOVIE) as? HashMovie
                movie?.toMovieDetailContent()
            }
        }

        if (content == null || content.title.isEmpty()) {
            return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.not_available)
                .setMessage(R.string.no_epg_available)
                .setPositiveButton(R.string.close) { _, _ -> dismiss() }
                .create()
        }

        val dialog = super.onCreateDialog(savedInstanceState)
        val host = this
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DreamDroidTheme {
                    MovieDetailScreen(content = content)
                }
            }
        }
        dialog.setContentView(composeView)
        val parent = composeView.parent as? View
        if (parent != null) {
            BottomSheetBehavior.from(parent).state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    companion object {
        private const val ARG_TYPED_MOVIE = "typedMovie"
        private const val ARG_HASH_MOVIE = "Movie"

        @JvmStatic
        fun newInstance(movie: Movie): MovieDetailBottomSheet {
            val args = Bundle()
            args.putSerializable(ARG_TYPED_MOVIE, movie)
            val fragment = MovieDetailBottomSheet()
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun newInstance(movie: HashMovie): MovieDetailBottomSheet {
            val args = Bundle()
            args.putSerializable(ARG_HASH_MOVIE, movie)
            val fragment = MovieDetailBottomSheet()
            fragment.arguments = args
            return fragment
        }
    }
}
