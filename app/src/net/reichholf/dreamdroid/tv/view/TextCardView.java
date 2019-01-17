package net.reichholf.dreamdroid.tv.view;

import android.content.Context;

import androidx.leanback.widget.BaseCardView;
import butterknife.BindView;
import butterknife.ButterKnife;

import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;

/**
 * A card view with an {@link TextView} as its main region.
 */
public class TextCardView extends BaseCardView {

    @BindView(R.id.title_text)
    protected TextView mTitle;

    @BindView(R.id.content_text)
    protected TextView mContent;

    public TextCardView(Context context) {
        this(context, null);
    }

    public TextCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageCardViewStyle);
    }


    public TextCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(R.layout.tv_text_card_item, this);
        ButterKnife.bind(this, v);
    }

    public void setTitleText(CharSequence text) {
        if (mTitle == null) {
            return;
        }

        mTitle.setText(text);
    }

    public CharSequence getTitleText() {
        if (mTitle == null) {
            return null;
        }

        return mTitle.getText();
    }

    public void setContentText(CharSequence text) {
        if (mContent == null) {
            return;
        }

        mContent.setText(text);
    }

    public CharSequence getContentText() {
        if (mContent == null) {
            return null;
        }

        return mContent.getText();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

}
