package net.reichholf.dreamdroid.tv.view

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.BaseCardView
import net.reichholf.dreamdroid.R

/**
 * Leanback movie card with Compose body (Phase 3.1c-i beachhead).
 * Keeps [BaseCardView] focus/selection; CardPresenter API unchanged.
 */
open class TextCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.leanback.R.attr.imageCardViewStyle,
) : BaseCardView(context, attrs, defStyleAttr) {

    private var titleText by mutableStateOf("")
    private var contentText by mutableStateOf("")
    private val composeView = ComposeView(context)

    init {
        cardType = CARD_TYPE_MAIN_ONLY
        isFocusable = true
        isFocusableInTouchMode = true
        // Leanback must keep focus on the card, not the Compose child.
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        composeView.isFocusable = false
        composeView.isFocusableInTouchMode = false
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.viewType = LayoutParams.VIEW_TYPE_MAIN
        addView(composeView, lp)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        composeView.setContent {
            TextCardContent(
                title = titleText,
                content = contentText,
            )
        }
    }

    fun setTitleText(text: CharSequence?) {
        titleText = text?.toString().orEmpty()
    }

    fun getTitleText(): CharSequence? = titleText.ifEmpty { null }

    fun setContentText(text: CharSequence?) {
        contentText = text?.toString().orEmpty()
    }

    fun getContentText(): CharSequence? = contentText.ifEmpty { null }

    override fun hasOverlappingRendering(): Boolean = false
}

@Composable
fun TextCardContent(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    val cardWidth = dimensionResource(R.dimen.card_width)
    val cardHeight = dimensionResource(R.dimen.card_height)
    // Match Leanback basic-card info colors on the presenter-tinted background.
    val titleColor = Color.White
    val bodyColor = Color.White.copy(alpha = 0.85f)
    Column(
        modifier = modifier
            .width(cardWidth)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 16.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .padding(top = 4.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = content,
                color = bodyColor,
                fontSize = 12.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
