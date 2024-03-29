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
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;
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
		BrowseItem browseItem = (BrowseItem) item;
		switch (browseItem.type) {
			case Service:
				bindServiceViewHolder(viewHolder, browseItem);
				break;
			case Movie:
				bindMovieViewHolder(viewHolder, browseItem);
				break;
			case Reload:
			case Preferences:
			case Profile:
				bindSettingsViewHolder(viewHolder, browseItem);
				break;
			default:
				break;
		}
	}

	protected void bindSettingsViewHolder(@NonNull Presenter.ViewHolder viewHolder, @NonNull BrowseItem item) {
		ExtendedHashMap settings = item.data;

		ImageCardView cardView = (ImageCardView) viewHolder.view;
		cardView.setTitleText(settings.getString("title"));

		Integer mainImageId = (Integer) settings.get("icon");
		if (mainImageId != null)
			cardView.setMainImage(ResourcesCompat.getDrawable(cardView.getResources(), mainImageId, cardView.getContext().getTheme()));
		else
			cardView.setMainImage(mDefaultCardImage);
		cardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
		Resources res = cardView.getResources();
		int width = res.getDimensionPixelSize(R.dimen.card_width);
		int height = res.getDimensionPixelSize(R.dimen.card_height);
		cardView.setMainImageDimensions(width, height);
	}

	protected void bindServiceViewHolder(@NonNull Presenter.ViewHolder viewHolder, @NonNull BrowseItem item) {

		Event event = new Event(item.data);
		Event nextEvent = new Event(Event.fromNext(item.data));
		ImageCardView cardView = (ImageCardView) viewHolder.view;
		//cardView.setMainImage(mDefaultCardImage);

		Picon.setPiconForView(cardView.getContext(), cardView.getMainImageView(), event, "tv_picon");
		cardView.setTitleText(event.serviceName());
		if (nextEvent.title().isEmpty()) {
			cardView.setTitleText(event.title());
		} else {
			String t = String.format("%s\n%s %s", event.title(), nextEvent.startTimeReadable(), nextEvent.title());
			Spannable spannable = new SpannableString(t);
			int offset = event.title().length();
			int end = offset + nextEvent.startTimeReadable().length() + 1;
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

	protected void bindMovieViewHolder(@NonNull Presenter.ViewHolder viewHolder, @NonNull BrowseItem item) {
		Movie movie = new Movie(item.data);
		TextCardView cardView = (TextCardView) viewHolder.view;
		cardView.setTitleText(movie.title());
		if (!movie.descriptionExtended().isEmpty())
			cardView.setContentText(movie.descriptionExtended());
		else
			cardView.setContentText(movie.description());
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
