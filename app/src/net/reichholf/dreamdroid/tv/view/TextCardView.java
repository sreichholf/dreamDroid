package net.reichholf.dreamdroid.tv.view;

import android.content.Context;
import androidx.leanback.widget.BaseCardView;
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

	private final TextView mTitleView;
	private final TextView mContentView;

	public  TextCardView(Context context) {
		this(context, null);
	}

	public  TextCardView(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.imageCardViewStyle);
	}

	public TextCardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		LayoutInflater inflater = LayoutInflater.from(context);
		View v = inflater.inflate(R.layout.tv_text_card_item, this);

		mTitleView = v.findViewById(R.id.title_text);
		mContentView = v.findViewById(R.id.content_text);
	}

	public  void setTitleText(CharSequence text) {
		if (mTitleView == null) {
			return;
		}

		mTitleView.setText(text);
		setTextMaxLines();
	}

	public  CharSequence getTitleText() {
		if (mTitleView == null) {
			return null;
		}

		return mTitleView.getText();
	}

	public  void setContentText(CharSequence text) {
		if (mContentView == null) {
			return;
		}

		mContentView.setText(text);
		setTextMaxLines();
	}

	public  CharSequence getContentText() {
		if (mContentView == null) {
			return null;
		}

		return mContentView.getText();
	}

	@Override
	public  boolean hasOverlappingRendering() {
		return false;
	}

	private  void setTextMaxLines() {
		if (TextUtils.isEmpty(getTitleText())) {
			mContentView.setMaxLines(2);
		} else {
			mContentView.setMaxLines(1);
		}
		if (TextUtils.isEmpty(getContentText())) {
			mTitleView.setMaxLines(2);
		} else {
			mTitleView.setMaxLines(1);
		}
	}

}
