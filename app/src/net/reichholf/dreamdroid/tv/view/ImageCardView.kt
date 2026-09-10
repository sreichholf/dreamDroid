package net.reichholf.dreamdroid.tv.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.BaseCardView
import net.reichholf.dreamdroid.R

/**
 * Leanback service/settings card with ImageView picon + Compose text (Phase 3.1c-ii).
 * Keeps [BaseCardView] focus/selection; CardPresenter binds picons via [getMainImageView].
 */
open class ImageCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.leanback.R.attr.imageCardViewStyle,
) : BaseCardView(context, attrs, defStyleAttr) {

    private var titleText by mutableStateOf("")
    private var contentPrimary by mutableStateOf("")
    private var nextStart by mutableStateOf("")
    private var nextTitle by mutableStateOf("")
    private var contentExpanded by mutableStateOf(false)
    private var imageWidthPx by mutableIntStateOf(
        resources.getDimensionPixelSize(R.dimen.card_width),
    )
    private var imageHeightPx by mutableIntStateOf(
        resources.getDimensionPixelSize(R.dimen.card_height),
    )

    private val mainImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        isFocusable = false
        isFocusableInTouchMode = false
    }
    private val composeView = ComposeView(context)

    init {
        cardType = CARD_TYPE_INFO_UNDER
        isFocusable = true
        isFocusableInTouchMode = true
        // Leanback must keep focus on the card, not Compose / ImageView children.
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        composeView.isFocusable = false
        composeView.isFocusableInTouchMode = false
        composeView.id = androidx.leanback.R.id.info_field

        val imageLp = LayoutParams(imageWidthPx, imageHeightPx)
        imageLp.viewType = LayoutParams.VIEW_TYPE_MAIN
        addView(mainImageView, imageLp)

        val infoLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        infoLp.viewType = LayoutParams.VIEW_TYPE_INFO
        addView(composeView, infoLp)

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        composeView.setContent {
            ImageCardContent(
                title = titleText,
                contentPrimary = contentPrimary,
                nextStart = nextStart,
                nextTitle = nextTitle,
                contentExpanded = contentExpanded,
                imageWidthPx = imageWidthPx,
            )
        }
    }

    fun getMainImageView(): ImageView = mainImageView

    fun setMainImage(drawable: Drawable?) {
        mainImageView.setImageDrawable(drawable)
    }

    fun setMainImageDimensions(width: Int, height: Int) {
        imageWidthPx = width
        imageHeightPx = height
        val lp = mainImageView.layoutParams
        if (lp != null) {
            lp.width = width
            lp.height = height
            mainImageView.layoutParams = lp
        }
    }

    fun setTitleText(text: CharSequence?) {
        titleText = text?.toString().orEmpty()
    }

    fun getTitleText(): CharSequence? = titleText.ifEmpty { null }

    /** Plain content line (no next-event styling). Clears structured next fields. */
    fun setContentText(text: CharSequence?) {
        contentPrimary = text?.toString().orEmpty()
        nextStart = ""
        nextTitle = ""
    }

    /**
     * Now/next content: primary line + bold-italic next start + next title
     * (matches former Spannable on Leanback ImageCardView).
     */
    fun setNowNextContent(primary: String, startReadable: String, eventTitle: String) {
        contentPrimary = primary
        nextStart = startReadable
        nextTitle = eventTitle
    }

    fun clearContent() {
        contentPrimary = ""
        nextStart = ""
        nextTitle = ""
    }

    /** API compat with Leanback ImageCardView; badge not used on this card. */
    @Suppress("UNUSED_PARAMETER")
    fun setBadgeImage(drawable: Drawable?) {
        // no-op
    }

    override fun setSelected(selected: Boolean) {
        contentExpanded = selected
        super.setSelected(selected)
    }

    override fun hasOverlappingRendering(): Boolean = false
}

@Composable
fun ImageCardContent(
    title: String,
    contentPrimary: String,
    nextStart: String,
    nextTitle: String,
    contentExpanded: Boolean,
    imageWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardWidth = if (imageWidthPx > 0) {
        with(density) { imageWidthPx.toDp() }
    } else {
        dimensionResource(R.dimen.card_width)
    }
    val titleColor = Color.White
    val bodyColor = Color.White.copy(alpha = 0.85f)
    val hasNext = nextStart.isNotEmpty() || nextTitle.isNotEmpty()
    val hasContent = contentPrimary.isNotEmpty() || hasNext
    Column(
        modifier = modifier
            .width(cardWidth)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasContent) {
            val body = buildAnnotatedString {
                if (contentPrimary.isNotEmpty()) {
                    append(contentPrimary)
                }
                if (hasNext) {
                    if (contentPrimary.isNotEmpty()) {
                        append('\n')
                    }
                    if (nextStart.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                        ) {
                            append(nextStart)
                        }
                        if (nextTitle.isNotEmpty()) {
                            append(' ')
                        }
                    }
                    if (nextTitle.isNotEmpty()) {
                        append(nextTitle)
                    }
                }
            }
            Text(
                text = body,
                color = bodyColor,
                fontSize = 12.sp,
                maxLines = if (contentExpanded) 4 else 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}
