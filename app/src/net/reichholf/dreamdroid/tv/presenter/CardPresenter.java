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


import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent) {
		mDefaultBackgroundColor =
				ContextCompat.getColor(parent.getContext(), R.color.primary_dreamdroid);
		mSelectedBackgroundColor =
				ContextCompat.getColor(parent.getContext(), R.color.primary_material_dark);
		mDefaultCardImage = parent.getResources().getDrawable(R.drawable.dreamdroid_logo_simple, null);


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
					super.setSelected(selected);
				}
			};
		}

		cardView.setFocusable(true);
		cardView.setFocusableInTouchMode(true);
		updateCardBackgroundColor(cardView, false);
		return new ViewHolder(cardView);
	}

	private void updateCardBackgroundColor(BaseCardView view, boolean selected) {
		int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

		// Both background colors should be set because the view's
		// background is temporarily visible during animations.
		view.setBackgroundColor(color);
		View info = view.findViewById(R.id.info_field);
		if (info != null)
			info.setBackgroundColor(color);
	}

	@Override
	public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
		BrowseItem browseItem = (BrowseItem) item;
		switch(browseItem.type) {
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

	protected void bindSettingsViewHolder(Presenter.ViewHolder viewHolder, BrowseItem item) {
		ExtendedHashMap settings = item.data;

		ImageCardView cardView = (ImageCardView) viewHolder.view;
		cardView.setTitleText(settings.getString("title"));

		Integer mainImageId = (Integer) settings.get("icon");
		if(mainImageId != null)
			cardView.setMainImage(cardView.getResources().getDrawable(mainImageId, null));
		else
			cardView.setMainImage(mDefaultCardImage);
		cardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
		Resources res = cardView.getResources();
		int width = res.getDimensionPixelSize(R.dimen.card_width);
		int height = res.getDimensionPixelSize(R.dimen.card_height);
		cardView.setMainImageDimensions(width, height);
	}

	protected void bindServiceViewHolder(Presenter.ViewHolder viewHolder, BrowseItem item) {

		Event event = new Event(item.data);
		ImageCardView cardView = (ImageCardView) viewHolder.view;
		//cardView.setMainImage(mDefaultCardImage);

		Picon.setPiconForView(cardView.getContext(), cardView.getMainImageView(), event, "tv_picon");
		cardView.setTitleText(event.serviceName());
		cardView.setContentText(event.title());
		cardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
		Resources res = cardView.getResources();
		int width = res.getDimensionPixelSize(R.dimen.card_width);
		int height = res.getDimensionPixelSize(R.dimen.card_height);
		cardView.setMainImageDimensions(width, height);
	}

	protected void bindMovieViewHolder(Presenter.ViewHolder viewHolder, BrowseItem item) {
		Movie movie = new Movie(item.data);
		TextCardView cardView = (TextCardView) viewHolder.view;
		cardView.setTitleText(movie.title());
		if (!movie.descriptionExtended().isEmpty())
			cardView.setContentText(movie.descriptionExtended());
		else
			cardView.setContentText(movie.description());
	}

	@Override
	public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
		if (mMode == ItemMode.MODE_TEXT)
			return;
		ImageCardView cardView = (ImageCardView) viewHolder.view;

		// Remove references to images so that the garbage collector can free up memory.
		cardView.setBadgeImage(null);
		cardView.setMainImage(null);
	}
}
