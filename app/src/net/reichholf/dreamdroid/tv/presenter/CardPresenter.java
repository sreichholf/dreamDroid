package net.reichholf.dreamdroid.tv.presenter;

/**
 * Created by Stephan on 16.10.2016.
 */

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
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v4.content.ContextCompat;
import android.view.ViewGroup;
import android.widget.ImageView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;
import net.reichholf.dreamdroid.helpers.enigma2.Picon;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
	private int mSelectedBackgroundColor = -1;
	private int mDefaultBackgroundColor = -1;
	private Drawable mDefaultCardImage;

	private boolean mSettingsMode;

	public CardPresenter() {
		super();
		mSettingsMode = false;
	}

	public CardPresenter(boolean isSettings) {
		super();
		mSettingsMode = isSettings;
	}


	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent) {
		mDefaultBackgroundColor =
				ContextCompat.getColor(parent.getContext(), R.color.primary_dreamdroid);
		mSelectedBackgroundColor =
				ContextCompat.getColor(parent.getContext(), R.color.primary_material_dark);
		mDefaultCardImage = parent.getResources().getDrawable(R.drawable.dreamdroid_logo_simple, null);

		ImageCardView cardView = new ImageCardView(parent.getContext()) {
			@Override
			public void setSelected(boolean selected) {
				updateCardBackgroundColor(this, selected);
				super.setSelected(selected);
			}
		};

		cardView.setFocusable(true);
		cardView.setFocusableInTouchMode(true);
		updateCardBackgroundColor(cardView, false);
		return new ViewHolder(cardView);
	}

	private void updateCardBackgroundColor(ImageCardView view, boolean selected) {
		int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

		// Both background colors should be set because the view's
		// background is temporarily visible during animations.
		view.setBackgroundColor(color);
		view.findViewById(R.id.info_field).setBackgroundColor(color);
	}

	@Override
	public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
		if (mSettingsMode)
			bindSettingsViewHolder(viewHolder, item);
		else
			bindServiceViewHolder(viewHolder, item);
	}

	protected void bindSettingsViewHolder(Presenter.ViewHolder viewHolder, Object item) {
		ExtendedHashMap it = (ExtendedHashMap) item;
		ImageCardView cardView = (ImageCardView) viewHolder.view;
		cardView.setTitleText(it.getString("title"));

		Integer mainImageId = (int) it.get("icon");
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

	protected void bindServiceViewHolder(Presenter.ViewHolder viewHolder, Object item) {
		ExtendedHashMap event = (ExtendedHashMap) item;
		String title = event.getString(Event.KEY_SERVICE_NAME);
		String eventTitle = event.getString(Event.KEY_EVENT_TITLE);
		ImageCardView cardView = (ImageCardView) viewHolder.view;
		//cardView.setMainImage(mDefaultCardImage);

		Picon.setPiconForView(cardView.getContext(), cardView.getMainImageView(), event, "tv_picon");
		cardView.setTitleText(title);
		cardView.setContentText(eventTitle);
		cardView.getMainImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
		Resources res = cardView.getResources();
		int width = res.getDimensionPixelSize(R.dimen.card_width);
		int height = res.getDimensionPixelSize(R.dimen.card_height);
		cardView.setMainImageDimensions(width, height);
	}

	@Override
	public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
		ImageCardView cardView = (ImageCardView) viewHolder.view;

		// Remove references to images so that the garbage collector can free up memory.
		cardView.setBadgeImage(null);
		cardView.setMainImage(null);
	}
}
