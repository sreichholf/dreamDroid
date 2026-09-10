package net.reichholf.dreamdroid.tv.presenter;

/*
 * Copyright (c) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.ServiceNowNext;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;
import net.reichholf.dreamdroid.tv.BrowseItem;
import net.reichholf.dreamdroid.tv.view.TextCardView;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
	private int mSelectedBackgroundColor = -1;
	private int mDefaultBackgroundColor = -1;
	private Drawable mDefaultCardImage;

	private ItemMode mMode;

	public enum ItemMode {
		MODE_IMAGE,
		MODE_TEXT,
	}

	public CardPresenter(ItemMode mode) {
		super();
		mMode = mode;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
		Context ctx = parent.getContext();
		Resources res = parent.getResources();
		Resources.Theme theme = ctx.getTheme();
		mDefaultBackgroundColor =
				ResourcesCompat.getColor(res, R.color.primary_dreamdroid, theme);
		mSelectedBackgroundColor =
				ResourcesCompat.getColor(res, androidx.appcompat.R.color.primary_material_dark, theme);
		mDefaultCardImage =
				ResourcesCompat.getDrawable(res, (R.drawable.dreamdroid_logo_simple), theme);

		BaseCardView cardView;
		if (mMode == ItemMode.MODE_TEXT) {
			cardView = new TextCardView(parent.getContext()) {
				@Override
				public void setSelected(boolean selected) {
					updateCardBackgroundColor(this, selected);
					super.setSelected(selected);
				}
			};
		} else {
			cardView = new ImageCardView(parent.getContext()) {
				@Override
				public void setSelected(boolean selected) {
					updateCardBackgroundColor(this, selected);
					TextView content = findViewById(R.id.content_text);
					if (selected) {
						content.setMaxLines(4);
					} else {
						content.setMaxLines(1);
					}
					super.setSelected(selected);
				}
			};
		}

		cardView.setFocusable(true);
		cardView.setFocusableInTouchMode(true);
		updateCardBackgroundColor(cardView, false);
		return new ViewHolder(cardView);
	}

	private void updateCardBackgroundColor(@NonNull BaseCardView view, boolean selected) {
		int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

		// Both background colors should be set because the view's
		// background is temporarily visible during animations.
		view.setBackgroundColor(color);
		View info = view.findViewById(androidx.leanback.R.id.info_field);
		if (info != null)
			info.setBackgroundColor(color);
	}

	@Override
	public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object item) {
		if (item instanceof BrowseItem.Service) {
			bindServiceViewHolder(viewHolder, (BrowseItem.Service) item);
		} else if (item instanceof BrowseItem.Movie) {
			bindMovieViewHolder(viewHolder, (BrowseItem.Movie) item);
		} else if (item instanceof BrowseItem.Settings) {
			bindSettingsViewHolder(viewHolder, (BrowseItem.Settings) item);
		}
	}

	protected void bindSettingsViewHolder(@NonNull Presenter.ViewHolder viewHolder, @NonNull BrowseItem.Settings item) {
		ImageCardView cardView = (ImageCardView) viewHolder.view;
		cardView.setTitleText(item.getTitle());
		cardView.setMainImage(ResourcesCompat.getDrawable(cardView.getResources(), item.getIconRes(),
				cardView.getContext().getTheme()));
		cardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
		Resources res = cardView.getResources();
		int width = res.getDimensionPixelSize(R.dimen.card_width);
		int height = res.getDimensionPixelSize(R.dimen.card_height);
		cardView.setMainImageDimensions(width, height);
	}

	protected void bindServiceViewHolder(@NonNull Presenter.ViewHolder viewHolder, @NonNull BrowseItem.Service item) {
		ServiceNowNext row = item.getRow();
		Event now = row.getNow();
		Event next = row.getNext();
		ImageCardView cardView = (ImageCardView) viewHolder.view;

		Picon.setPiconForView(cardView.getContext(), cardView.getMainImageView(),
				row.getServiceReference(), row.getServiceName(), "tv_picon", null);
		String nowTitle = now != null ? now.getTitle() : "";
		String serviceName = row.getServiceName();
		cardView.setTitleText(serviceName);
		if (next == null || next.getTitle().isEmpty()) {
			if (!nowTitle.isEmpty()) {
				cardView.setTitleText(nowTitle);
			}
		} else {
			String displayTitle = !nowTitle.isEmpty() ? nowTitle : serviceName;
			String nextStart = next.getStartTimeReadable();
			String t = String.format("%s\n%s %s", displayTitle, nextStart, next.getTitle());
			Spannable spannable = new SpannableString(t);
			int offset = displayTitle.length();
			int end = offset + nextStart.length() + 1;
			spannable.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), offset, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			TextView content = cardView.findViewById(R.id.content_text);
			content.setText(spannable, TextView.BufferType.SPANNABLE);
		}
		cardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
		Resources res = cardView.getResources();
		int width = res.getDimensionPixelSize(R.dimen.card_width);
		int height = res.getDimensionPixelSize(R.dimen.card_height);
		cardView.setMainImageDimensions(width, height);
	}

	protected void bindMovieViewHolder(@NonNull Presenter.ViewHolder viewHolder, @NonNull BrowseItem.Movie item) {
		net.reichholf.dreamdroid.enigma.Movie movie = item.getMovie();
		TextCardView cardView = (TextCardView) viewHolder.view;
		cardView.setTitleText(movie.getTitle());
		// Match helpers.enigma2.Movie.descriptionExtended(): literal "\n" → newline.
		String descriptionEx = movie.getDescriptionExtended().replace("\\n", "\n");
		if (!descriptionEx.isEmpty())
			cardView.setContentText(descriptionEx);
		else
			cardView.setContentText(movie.getDescription());
	}

	@Override
	public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
		if (mMode == ItemMode.MODE_TEXT)
			return;
		ImageCardView cardView = (ImageCardView) viewHolder.view;

		// Remove references to images so that the garbage collector can free up memory.
		cardView.setBadgeImage(null);
		cardView.setMainImage(null);
	}
}
