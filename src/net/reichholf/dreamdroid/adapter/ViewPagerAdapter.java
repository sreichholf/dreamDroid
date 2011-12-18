package net.reichholf.dreamdroid.adapter;

import android.content.Context;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.TextView;

import com.viewpagerindicator.TitleProvider;

public class ViewPagerAdapter extends PagerAdapter implements TitleProvider {
	private static String[] mTitles;
	private static View[] mViews;
	private final Context mContext;

	public ViewPagerAdapter(Context context, String[] titles, View[] views) {
		mContext = context;
		mTitles = titles;
		mViews = views;
	}

	@Override
	public String getTitle(int position) {
		return mTitles[position];
	}

	@Override
	public int getCount() {
		return mTitles.length;
	}

	@Override
	public Object instantiateItem(View pager, int position) {
		View v = mViews[position];
		((ViewPager)pager).addView( v, 0 );
		return v;
	}

	@Override
	public void destroyItem(View pager, int position, Object view) {
		((ViewPager) pager).removeView((TextView) view);
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return view.equals(object);
	}

	@Override
	public void finishUpdate(View view) {
	}

	@Override
	public void restoreState(Parcelable p, ClassLoader c) {
	}

	@Override
	public Parcelable saveState() {
		return null;
	}

	@Override
	public void startUpdate(View view) {
	}
}