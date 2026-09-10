package net.reichholf.dreamdroid.tv.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.BaseCardView;

import net.reichholf.dreamdroid.R;

/**
 * A card view with an {@link TextView} as its main region.
 */
public class TextCardView extends BaseCardView {

	@Nullable
	protected TextView mTitle;

	@Nullable
	protected TextView mContent;

	public TextCardView(@NonNull Context context) {
		this(context, null);
	}

	public TextCardView(@NonNull Context context, AttributeSet attrs) {
		this(context, attrs, androidx.leanback.R.attr.imageCardViewStyle);
	}

	public TextCardView(@NonNull Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
		LayoutInflater inflater = LayoutInflater.from(context);
		View v = inflater.inflate(R.layout.tv_text_card_item, this);
		mTitle = v.findViewById(R.id.title_text);
		mContent = v.findViewById(R.id.content_text);
	}

	public void setTitleText(CharSequence text) {
		if (mTitle == null) {
			return;
		}

		mTitle.setText(text);
	}

	@Nullable
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

	@Nullable
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
